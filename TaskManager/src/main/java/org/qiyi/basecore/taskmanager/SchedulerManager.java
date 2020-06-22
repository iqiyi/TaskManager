/*
 *
 * Copyright (C) 2020 iQIYI (www.iqiyi.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.qiyi.basecore.taskmanager;

import android.os.Handler;

import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.IdleScheduler;
import org.qiyi.basecore.taskmanager.other.TMLog;
import java.util.LinkedList;

class SchedulerManager {

    private static final String TAG = "TManager_SchedulerManager";
    private ITaskExecutor executor;
    private Handler workHandler;
    private TaskContainer taskContainer;
    private IdleScheduler idleScheduler;

    public SchedulerManager(TaskManager mTaskManagerImpl) {
        executor = mTaskManagerImpl.getTaskExecutor();
        workHandler = executor.getWorkHandler();
        taskContainer = TaskContainer.getInstance();
        idleScheduler = new IdleScheduler();
    }

    //[检查任务执行的必要条件： 是否有任务依赖]
    private void doTask(Task taskRequest, boolean isPending) {
        if (taskContainer.enqueueSerialTask(taskRequest)) {
            return;
        }
        if (taskRequest.hasDependantTasks()) {// v10.6 新增的task 依赖功能
            taskContainer.add(taskRequest);
            Task mTask = taskRequest;
            int ids[] = taskRequest.getDependantTaskIds();
            if (ids != null && mTask != null) {
                for (int taskID : ids) {
                    //taskInfo is null : might be : task is finished or no such task ; need to check in TaskRecorder
//                    TaskInfo taskInfo = getTaskInfoByTaskId(taskID, true);
                    // only res depe
                    if (taskID > Task.TASKID_EVENT_RANGE) {
                        if (TMLog.isDebug() && taskRequest.getTaskId() > Task.TASKID_RES_RANGE) {
                            //[检测循环依赖的设定：循环依赖设定是编译期间确定的，因此只在debug 情况下检测]
                            testRecursiveDependency(mTask, taskID);
                        }
                        TaskRecorder.addSuccessorForTask(mTask, taskID);
                    }
                }
            } else {
                TMLog.e(TAG, "there might have bugs :  has dependantTasks , but has no ids");
                TaskWrapper.obtain(taskRequest).setExecutor(executor);
            }
        } else if (isPending || taskRequest.isIdleRunEnabled()) {
            if (TM.isFullLogEnabled()) {
                TMLog.e(TAG, "doTask add pending task " + taskRequest);
            }
            if (taskRequest.mRunningThread.isRunningInUIThread()) {
                idleScheduler.increase();
                taskRequest.setIdleScheduler(idleScheduler);
            }
            taskContainer.add(taskRequest);
        } else { // task 获得到 executor 资源
            TaskRecorder.enqueue(taskRequest);// fix bug task not tracked
            TaskWrapper.obtain(taskRequest).setExecutor(executor);
        }
    }


    /**
     * 直接向调度器增加一个任务
     * @param taskRequest
     */
    public void schedule(final Task taskRequest) {
        if (taskRequest.getState() == Task.STATE_IDLE) {
            int delayTime = taskRequest.getDelayTime();
            if (delayTime == 0 || delayTime == Integer.MAX_VALUE) {
                taskRequest.updateDelay(0);//[避免依赖状态改变后，再重新进入等待队列的bug]
                doTask(taskRequest, delayTime == Integer.MAX_VALUE);
            } else {
                // 只有对一次post 执行的时候，会调用执行这里;
                taskRequest.updateDelay(0);//[避免依赖状态改变后，再重新进入等待队列的bug]
                taskContainer.add(taskRequest);
                // register task dependant: 只有在或延迟的逻辑下,才先添加依赖关系. 依赖关系,先于 或延迟时间满足的情况下,就会触发任务.
                // 普通的延迟,需要等到延迟时间满足后,才注册依赖关系,准备执行;
                if (taskRequest.isOrDelay() && taskRequest.hasDependantTasks()) {
                    Task mTask = taskRequest;
                    int ids[] = taskRequest.getDependantTaskIds();
                    if (ids != null) {
                        for (int taskID : ids) {
                            //taskInfo is null : might be : task is finished or no such task ; need to check in TaskRecorder
//                    TaskInfo taskInfo = getTaskInfoByTaskId(taskID, true);
                            if (taskID > Task.TASKID_EVENT_RANGE) {  // 当被依赖的ID > res 的情况下 才允许添加依赖
                                if (TMLog.isDebug() && mTask.getTaskId() > Task.TASKID_RES_RANGE) {
                                    //[检测循环依赖的设定：循环依赖设定是编译期间确定的，因此只在debug 情况下检测]
                                    testRecursiveDependency(mTask, taskID);
                                }
                                TaskRecorder.addSuccessorForTask(mTask, taskID);
                            }
                        }
                    }
                } else {
                    // disable dependency trigger : may cause new not run problems
                    taskRequest.setDisableDependencyRun(true);
                }

                workHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        taskRequest.setDisableDependencyRun(false);
                        if (taskContainer.remove(taskRequest)) { //[otherwise been taken by others]
                            if (taskRequest.isOrDelay()) {
                                taskRequest.clearDependants();
                            }
                            doTask(taskRequest, false);
                        }
                    }
                }, delayTime);
            }
        }
    }

    //[check task inside executor & task container]
    private Task getTaskInfoByTaskId(int taskID, boolean checkInExecutor) {
        Task taskInfo = taskContainer.findTaskById(taskID);
        if (taskInfo == null && checkInExecutor) {
            taskInfo = TaskRecorder.findTaskById(taskID);
        }
        return taskInfo;
    }

    public void needTaskSync(int taskID, int timeOut, boolean directRun) {

        if (TM.isFullLogEnabled()) {
            TMLog.d(TAG, taskID + "needTaskSync called " + taskID);
        }
        if (TaskRecorder.isTaskFinishedNoSync(taskID)) {
            return;
        }
        //not finished: need check unfinished tasks
        LinkedList<Task> taskInfos = new LinkedList<>();

        // get all need unfinished task Infos here
        checkTaskUnFinished(taskInfos, taskID, true);

        //[loop and try cancel all need tasks]
        while (!taskInfos.isEmpty()) {
            Task taskInfo = taskInfos.pollFirst();
            taskContainer.remove(taskInfo);//remove from waiting queue: post delay not execute
            // task will be blocked if is running ; wait tile task finished
            // 变更: 当任务等待超时后, 就在当前线程直接执行,而不是再重新递交了;解决Task 内部状态同步,导致无法二次执行的问题
            cancelAndSync(executor, taskInfo, timeOut, directRun);
        }

    }


    /* find all dependent tasks by tid */
    private void checkTaskUnFinished(LinkedList<Task> taskInfos, int tid, boolean checkInExecutor) {

        LinkedList<Integer> idsList = new LinkedList<>();
        idsList.add(tid);

        while (!idsList.isEmpty()) {
            int taskID = idsList.pollFirst();

            if (!TaskRecorder.isTaskFinished(taskID)) {

                Task taskInfo = getTaskInfoByTaskId(taskID, checkInExecutor);

                //else : task is not posted
                if (taskInfo != null) {
                    Task task = taskInfo;
                    if (task.hasDependantTasks()) {
                        int ids[] = task.getDependantTaskIds();
                        //fix bug: npt ids : dependentState may be set to null: after task finished
                        if (ids != null) {
                            for (int id : ids) {
                                idsList.addLast(id);
                            }
                        }
                    }
                    taskInfos.addFirst(taskInfo);
                }
            }


        }

    }

    /**
     * 通知任务可以异步执行了， 如果正在running 就不必理会了；
     * 只需要将条件等待的任务添加到就绪执行中就可以了；
     * notify task to run in background .
     *
     * @param taskID
     */
    public void needTaskAsync(final int taskID) {
        //[do not block(synchronize) calling thread]
        executor.workPostDelay(new Runnable() {
            @Override
            public void run() {
                if (TaskRecorder.isTaskFinishedNoSync(taskID)) {
                    return;
                }
                if (TM.isFullLogEnabled()) {
                    TMLog.d("TAG", taskID + " need TaskAsync called " + taskID);
                }

                LinkedList<Task> taskInfos = new LinkedList<>();
                // get all need unfinished task Infos here
                checkTaskUnFinished(taskInfos, taskID, false);

                //[loop and try cancel all need tasks]
                while (!taskInfos.isEmpty()) {
                    Task request = taskInfos.pollFirst();
                    //sync
                    boolean removed = taskContainer.remove(request);
                    if (removed && request != null) {
                        schedule(request);
                    }
                }
            }
        }, 0);

    }


    //check needed task , if it has a root to mTaskId
    //getTaskInfoByTaskId returns unfinished tasks: if its already finished ; no bother
    private void testRecursiveDependency(Task task, int neededTaskId) {
        LinkedList<Integer> list = new LinkedList<>();
        LinkedList<TNode> checkQueue = new LinkedList<>();
        LinkedList<Integer> prierIndex = new LinkedList<>();
        LinkedList<String> taskNames = new LinkedList<>();
        list.addLast(task.getTaskId());
        prierIndex.add(-1);
        taskNames.add(task.getName());
        checkQueue.add(new TNode(-1, neededTaskId));
        int preIndex;

        while (!checkQueue.isEmpty()) {
            TNode node = checkQueue.pollFirst();
            neededTaskId = node.taskId;
            preIndex = node.preIndex;

            Task info = getTaskInfoByTaskId(neededTaskId, true);
            if (info != null && info.hasDependantTasks()) {
                if (list.contains(neededTaskId)) {

                    //print log:
                    StringBuilder builder = new StringBuilder();
                    builder.append("Task " + info.getName());
                    builder.append(" id " + info.getTaskId());
                    builder.append(" -> ");

                    while (preIndex >= 0) {
                        String name = taskNames.get(preIndex);
                        int tid = list.get(preIndex);
                        builder.append("Task " + name);
                        builder.append("id " + tid);
                        builder.append(" -> ");
                        preIndex = prierIndex.get(preIndex);
                    }

                    if (TaskManager.isDebugCrashEnabled()) {
                        throw new RuntimeException(" detected recursive dependency for Task : " + builder.toString());
                    }
                }
                int index = list.size();
                list.addLast(neededTaskId);
                taskNames.add(info.getName());
                prierIndex.add(preIndex);

                int ids[] = info.getDependantTaskIds();
                if (ids != null) {
                    for (int id : ids) {
                        checkQueue.add(new TNode(index, id));
                    }
                }
            }
        }

    }


    //如果任务成功取消，是需要移除任务纪录的
    public void cancelTask(int taskId) {
        if (!TaskRecorder.isTaskFinished(taskId)) {
            //[do sync]
            if (!taskContainer.cancelTaskByTaskId(taskId)) {
                //[might be running： now in executor： mark state running , so task wont run]
                //remove task form Executor
                if (TaskRecorder.cancelTaskByTaskId(taskId)) {
                    executor.removeTask(taskId);
                }
            }
        }
    }


    // TaskRecorder Record is removed form executor
    public boolean cancelTaskByToken(Object token) {
        //do remove task in taskContainer
        boolean canceled = taskContainer.cancelTaskByToken(token);
        // 不能因为 TaskContainer 已经remove 成功了，就不remove 了。因为token 肯能存在多个task 为同一个token
        if (TaskRecorder.cancelTaskByToken(token)) {
            executor.removeTaskByToken(token);
            canceled = true;
        }
        return canceled;
    }


    /**
     * 查询一个任务是否已经被注册过: 注册过的任务,都会在记录中找到;
     *
     * @param tid
     * @return
     */
    public boolean isTaskRegistered(int tid) {

        Task info = taskContainer.findTaskById(tid);
        if (info == null) {
            info = TaskRecorder.findTaskById(tid);
        }
        if (info == null) {
            return TaskRecorder.isTaskFinished(tid);
        } else {
            return true;
        }
    }

    private static class TNode {
        int taskId;
        int preIndex;

        public TNode(int index, int tid) {
            taskId = tid;
            preIndex = index;
        }
    }


    /**
     * 如果任务没有执行就取消任务在当前的TaskRequest 中执行
     * 如果任务已经执行了, 就最多等待超时时间去执行任务
     * 如果任务已经执行完成了,就不操作;
     *
     * @param task
     * @return : false : 任务已经执行完成了; true: 任务没有执行完成,需要在调用处再执行;
     */
    boolean cancelAndSync(ITaskExecutor executor, Task task, int timeOut, boolean runAfterTimeOut) {


        int state = task.compareAndSetState(Task.STATE_RUNNING);
        if (TM.isFullLogEnabled()) {
            TMLog.d(TAG, "cancelAndSync" + state + " " + task.getName());
        }
        if (state < 0) {//[need run: current idle ; taken ;]

            RunningThread mRunningThread = task.getRunningThread();
            if (mRunningThread.isRunningThreadCorrect()) {
                directRunTask(task);
            } else if (mRunningThread.isRunningInUIThread()) {
                // 如果是需要在主线程执行，但是当前是在子线程， 则等待:
                final Task taskVar = task;
                TM.getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        directRunTask(taskVar);
                    }
                });
                task.postUI();
                return waitForTaskRun(task, timeOut, runAfterTimeOut);
            } else {
                //如果当前是在主线程，但是预期在子线程， 则直接执行
                directRunTask(task);
            }

        } else if (state == Task.STATE_FINISHED) {
            return false;
        } else if (state == Task.STATE_RUNNING) { //wait
            return waitForTaskRun(task, timeOut, runAfterTimeOut);
        }

        return false;
    }

    private boolean waitForTaskRun(Task task, int timeOut, boolean runAfterTimeOut) {
        //「check thread : if current thread is task working thread ; do not wait: return true and do nothing ;」
        // if task is finished ; return false; so that will not run again
        TMLog.e(TAG, task.getTaskId() + " wait for task to run ");
        TaskManagerDeliverHelper.track(" wait[", task.getName(), task.getTaskId(), " at ", System.currentTimeMillis());
        boolean result = task.waitFor(timeOut);
        TaskManagerDeliverHelper.track("wait] ", task.getName(), task.getTaskId(), " with result ", result, " at ", System.currentTimeMillis());

        // 二次执行: 当等待超时后,不检查线程 直接二次执行;
        if (result && runAfterTimeOut) {
            task.resetRunCount();
            directRunTask(task);
        }

        return result;
    }


    private void directRunTask(Task task) {
        //if is Parallel task : do right now
//        SingleTaskLog.print(task.getTaskId(), "cancel sync call direct run");
        task.doBeforeTask();
        TaskManagerDeliverHelper.track("cancelAndSync do ", task.getName(), task.getTaskId(), " at ", System.currentTimeMillis());
        task.doTask();
        task.doAfterTask();
    }


}

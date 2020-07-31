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

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import org.qiyi.basecore.taskmanager.config.TaskManagerConfig;
import org.qiyi.basecore.taskmanager.deliver.ITracker;
import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.IPrinter;
import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.iface.ITaskManagerConfig;
import org.qiyi.basecore.taskmanager.iface.ITaskStateListener;
import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.ExceptionUtils;
import org.qiyi.basecore.taskmanager.other.TMLog;
import org.qiyi.basecore.taskmanager.pool.CleanUp;
import org.qiyi.basecore.taskmanager.threadpool.GroupedThreadPool;

import java.util.List;

/**
 * 新设计为启动阶段的线程池管理工具
 * 策略：线程缓慢建立； 并且具备自动销毁能力
 * 1) enqueue: task  may exec on Main or background
 * 05.16： 新增并发执行逻辑
 */
public class TaskManager {

    private ITaskExecutor mTaskExecutor;
    private SchedulerManager mSchedulerManager;
    //新增的动态调度开关，用于在极端情况下回退并行执行逻辑为串形执行。
    static boolean enableDebugCheckCrash = true;
    private Application mApplication;
    private ITaskStateListener taskStateListener;
    private int defaultTimeOut = 2000;
    private int taskPriorityTimePerGrade = 10;
    private static TaskManagerConfig mConfig;
    private Handler mainHandler;
    private boolean isFullLogEnabled;


    public static TaskManager getInstance() {
        return InstanceHolder.holder;
    }


    private TaskManager() {
        getTaskManagerConfig();
        defaultTimeOut = mConfig.getDefaultTimeout();
        taskPriorityTimePerGrade = mConfig.getTaskPriorityGradePerTime();
        this.mTaskExecutor = new GroupedThreadPool();
        //ThreadPoolFactory.createExecutor(mConfig.getThreadPoolStrategy());
        // reuse pool clean up & reference pool clean up
        mainHandler = mTaskExecutor.getMainHandler();
        if (mConfig.isMemoryCleanUpEnabled()) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    CleanUp.go();
                }
            }, 5000);
        }
        this.mSchedulerManager = new SchedulerManager(this);

    }


    /**
     * support & fix :  依赖的任务 修改为等待条件满足后再执行； 保证任务依赖关系的时序列;
     * 当在被依赖的任务都执行按成后， 从从新调用这里进入队列 ： 但是这个时候 dependant state 已经被设置为空 ， 因此可以成立
     *
     * @param baseTask
     */
    public void enqueue(@NonNull Task baseTask) {
        mSchedulerManager.schedule(baseTask);
    }


    /**
     * execute all tasks in current thread , except  that a task is declared to run on UI thread, but current thread is background thread.
     * @param baseTaskList
     */
    public void executeDirect(@NonNull List<? extends Task> baseTaskList) {
        if (baseTaskList.size() > 0) {
            for (Task task : baseTaskList) {
                executeDirect(task);
            }
        }
    }

    /**
     * 直接执行任务:
     *  refer to executeDirect(List)
     * @param baseTask
     */
    public void executeDirect(@NonNull Task baseTask) {
        // avoid npe
        if (baseTask == null) return;
        mTaskExecutor.executeDirect(baseTask);
    }


    public ITaskExecutor getTaskExecutor() {
        return mTaskExecutor;
    }


    private void assertBackgroundThread(String errorMessage) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread() && TaskManager.enableDebugCheckCrash) {
            throw new IllegalStateException(errorMessage);
        }
    }

    static class InstanceHolder {
        public final static TaskManager holder = new TaskManager();
    }

    void executeParallel(Task... tasks) {
        if (tasks != null && tasks.length > 0) {
            ParallelRequest request = new ParallelRequest(tasks);
            request.setExecutor(mTaskExecutor);
        }
    }

    void executeParallel(int timeout, Task... tasks) {
        if (tasks != null && tasks.length > 0) {
            ParallelRequest request = new ParallelRequest(tasks);
            request.setParallelTimeOut(timeout);
            request.setExecutor(mTaskExecutor);
        }
    }


    public final void enqueue(@NonNull Task... tasks) {
        if (tasks != null && tasks.length > 0) {
            for (Task task : tasks) {
//                SingleTaskLog.print(task, "enqueue");
                mSchedulerManager.schedule(task);
            }
        }
    }


    /**
     * 当前线程执行时序依赖的任务
     * 如果被依赖的任务没有完成，将等待任务执行完成或者直接执行任务
     * 不会等待没有添加的任务去执行
     * @param taskId
     */
    public void needTaskSync(int taskId) {
        mSchedulerManager.needTaskSync(taskId, defaultTimeOut, true);
    }


    /**
     * 当前线程执行时序依赖的任务
     * 如果被依赖的任务没有完成，将等待任务执行完成或者直接执行任务
     * @param taskId
     * @param timeOutMillions : 设置超时的时间，if < 0 : 将会一直等待。
     */
    public void needTaskSyncWithTimeout(int taskId, int timeOutMillions) {
        mSchedulerManager.needTaskSync(taskId, timeOutMillions, true);
    }

    public void waitForTaskWithTimeout(int taskId, int timeOutMillions) {
        mSchedulerManager.needTaskSync(taskId, timeOutMillions, false);
    }

    /**
     * 通知任务可以准备进行异步执行了；
     *
     * @param taskId
     */
    public void needTaskAsync(int taskId) {
        mSchedulerManager.needTaskAsync(taskId);
    }


    /**
     * 不不建立任务，直接宣告任务执行完成，可直接触发等待这个任务执行完成的任务启动执行
     * 10.7.5: 新增check 触发当前的task 完成.
     *
     * @param taskID
     */
    public void triggerEventTaskFinished(int taskID) {
        needTaskSync(taskID);//新增支持等待已经添加到任务队列中的任务执行完成.
//        TM.asset(taskID < Task.TASKID_SELF_DEFINE_EVENT_RANGE, "trigger self defined event should call triggerEvent");
        TaskRecorder.onTaskFinished(null, taskID);
    }


    /**
     * @param taskId
     * @param data   通知任务完成的时候， 并携带数据
     */
    public void triggerEventTaskFinished(int taskId, Object data) {
        needTaskSync(taskId);//新增支持等待已经添加到任务队列中的任务执行完成.
//        TM.asset(taskId < Task.TASKID_SELF_DEFINE_EVENT_RANGE, "trigger self defined event should call triggerEvent");
        TaskRecorder.onTaskFinished(taskId, data);
    }


    public void triggerEvent(int event) {
        if (TMLog.isDebug()) {
            TM.crashIf(event < Task.TASKID_SELF_DEFINE_EVENT_RANGE, "trigger self defined event should call triggerEvent");
        }
        TaskRecorder.onTaskFinished(event, null);
    }

    /**
     * @param groupIdentity ： 任意对象， 同一个对象为同一个任务组
     * @param event         : 可以是自定义的事件id
     * @param message
     */
    public void triggerEvent(Object groupIdentity, int event, Object message) {
        int groupId = TaskRecorder.generateGroupId(groupIdentity);
        triggerEvent(groupId, event, message);
    }

    /**
     * @param groupId
     * @param event
     * @param message
     */
    public void triggerEvent(int groupId, int event, Object message) {
        int eid;
        if (event < Task.TASKID_SELF_DEFINE_EVENT_RANGE) {
            eid = TM.genEventIdbyGroup(groupId, event);
        } else {
            eid = event;
        }
        TaskRecorder.onTaskFinished(eid, message);
    }

    /**
     * @param event
     * @param message
     */
    public void triggerEvent(int event, Object message) {
        if (TMLog.isDebug()) {
            TM.crashIf(event < Task.TASKID_SELF_DEFINE_EVENT_RANGE, "trigger self defined event should call triggerEvent(IILjava/lang/Object;) or triggerEvent(Ljava/lang/Object;ILjava/lang/Object;) ");
        }
        TaskRecorder.onTaskFinished(event, message);
    }

    /**
     * 将声明为idle run 的任务通知到执行队列中；
     * 如果 idle run 有依赖的任务， 则将被依赖的任务通知到执行队列中。
     * 每次仅仅通知执行一个任务
     * 被通过时间延迟的任务，将不在收到时间的限制；
     * 只能trigger 主线程idle 任务: 子线程任务 由 TM 通知执行
     */
    public void triggerIdleRun() {
        // removed from taskContainer
        Task request = TaskContainer.getInstance().offerTaskInIdleState(false);
        if (request != null) {
            if (request instanceof IdleTask) {
                //can run more than one task in one trigger
                ((IdleTask) request).updateIdleOffset(mConfig.getIdleTaskOffset());
            }
            request.updateDelay(0);//[已经设置过的]
            if (Looper.myLooper() == Looper.getMainLooper()) {
                executeDirect(request);
            } else {
                enqueue(request);
            }
        } else {
            // when is idle , notify executor to run junk tasks
            mTaskExecutor.trigger();
        }
    }


    public void workPostDelay(Runnable runnable, int delay) {
        mTaskExecutor.workPostDelay(runnable, delay);
    }


    /**
     * dump all important info here
     */
    public void dumpInfo() {
        //task manager :
        TaskContainer.getInstance().dump2Track();
        // executor
        mTaskExecutor.dumpData();
        TaskRecorder.dump(new IPrinter() {
            @Override
            public void print(String var) {
                TaskManagerDeliverHelper.track(var);
            }
        });

    }


    public Handler getWorkHandler() {
        return mTaskExecutor.getWorkHandler();
    }

    public Handler getMainHandler() {
        return mainHandler;
    }


    public static void enableDebugCrash(boolean enable) {
        enableDebugCheckCrash = enable;
    }

    public static boolean isDebugCrashEnabled() {
        return enableDebugCheckCrash;
    }

    /**
     * cancel the task by task ID
     *
     * @param taskId
     */
    public void cancelTask(int taskId) {
        mSchedulerManager.cancelTask(taskId);
    }

    //remove task if is not running
    public void cancelTaskByToken(Object token) {
        if (token != null) {
            mSchedulerManager.cancelTaskByToken(token);
        }
    }


    public static TaskManagerConfig init(@NonNull Application application, ITracker tracker) {

        if (tracker != null) {
            TaskManagerDeliverHelper.init(tracker);
        }

        getInstance().setApplication(application);
        return new TaskManagerConfig();
    }

    public static ITaskManagerConfig config() {
        return new TaskManagerConfig();
    }


    /**
     * use config(Application app) instead
     */
    @Deprecated
    public static TaskManagerConfig init(@NonNull Application application) {
        getInstance().setApplication(application);
        return new TaskManagerConfig();
    }


    /**
     * @return @Nullable
     */
    public Application getApplication() {
        return mApplication;
    }

    void setApplication(Application application) {
        mApplication = application;
    }

    public void registerTaskStateListener(ITaskStateListener listener) {
        taskStateListener = listener;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void notifyTaskStateChange(Task task, int state) {
        if (taskStateListener != null) {
            taskStateListener.onTaskStateChange(task, state);
        }
    }

    public static void setConfig(Application application, TaskManagerConfig config) {
        mConfig = config;
        enableDebugCheckCrash = config.isDebugCrashEnabled();
        ITracker tracker = config.getLogger();
        if (tracker != null) {
            TMLog.setLogger(tracker);
            TaskManagerDeliverHelper.init(tracker);
        }
        ExceptionUtils.setExceptionHandler(config.getExceptionHandler());
        TaskManager taskManager = TaskManager.getInstance();
        taskManager.setApplication(application);
        taskManager.defaultTimeOut = config.getDefaultTimeout();
        taskManager.isFullLogEnabled = config.isFullLogEnabled();
    }

    public static void setConfig(TaskManagerConfig config) {
        mConfig = config;
        enableDebugCheckCrash = config.isDebugCrashEnabled();
        ExceptionUtils.setExceptionHandler(config.getExceptionHandler());
        TaskManager.getInstance().defaultTimeOut = config.getDefaultTimeout();
    }

    public int getTaskPriorityTimePerGrade() {
        return taskPriorityTimePerGrade;
    }

    public static TaskManagerConfig getTaskManagerConfig() {
        if (mConfig == null) {
            mConfig = new TaskManagerConfig();
        }
        return mConfig;
    }

    /**
     * to check if a task has been posted to TM.
     * this will check in TaskContainer(pending task )  & task recorder.
     * if a task is a anonymous id task (TM.generated id ), and if the task is already finished ,
     * this function will return false. (As we don't save record for such tasks)
     * @param taskId
     * @return
     */
    public boolean isTaskRegistered(int taskId) {
        return mSchedulerManager.isTaskRegistered(taskId);
    }


    public int getCpuCount() {
        return mTaskExecutor.getCpuCount();
    }

    /**
     * not fully tested.
     * @param size
     */
    public void setMaxRunningThreadCount(int size) {
        mTaskExecutor.setMaxRunningThreadCount(size);
    }

    /**
     * 直接提交到线程池
     *
     * @param taskRequest
     */
    void quickRun(Task taskRequest) {
        if (TMLog.isDebug() && taskRequest.hasDependantTasks() || taskRequest.getDelayTime() != 0) {
            throw new IllegalStateException("call <enqueue> instead as u have dependant task or time delay");
        }
//        SingleTaskLog.print(taskRequest, "doTask ready to run");
        TaskRecorder.enqueue(taskRequest);// fix bug task not tracked
        TaskWrapper.obtain(taskRequest).setExecutor(mTaskExecutor);
    }

    public boolean isFullLogEnabled() {
        return isFullLogEnabled;
    }

    public void enableFullLog(boolean enable) {
        isFullLogEnabled = enable;
    }

}

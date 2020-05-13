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

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.ITaskStateListener;
import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.ExceptionUtils;
import org.qiyi.basecore.taskmanager.other.IdleScheduler;
import org.qiyi.basecore.taskmanager.other.LogUtils;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Task extends Job {

    private static final String TAG = "TManager_Task";
    static final int STATE_IDLE = 0;
    static final int STATE_CANCELED = 3;
    static final int STATE_RUNNING = 2;
    static final int STATE_FINISHED = 4;

    private WeakReference<TaskWrapper> mWrapper;
    volatile int taskState;
    private final List<TaskDependentState> dependentStates = new CopyOnWriteArrayList<>();
    private long runningThreadId;
    public final static int TASKID_RES_RANGE = 0x70000000;
    public final static int TASKID_EVENT_RANGE = 0x50000000;
    public final static int TASKID_SELF_DEFINE_EVENT_RANGE = 0xffff;
    private final static long TASK_MAX_WAIT_TIME = 5000L; //debug 包下默认最多等 5s
    private int delayAfterDependant; //依赖满足后, 再延迟多长时间执行.
    private int priority;
    private Object mToken;
    private int delayTime;// 延迟多长时间后,再提交任务执行;  当延迟时间是负数的时候, 标记为或延迟
    // 默认任务id < TASKID_RES_RANGE 的，将不保存任务记录；如果仍然要保存任务记录，enableRecord。
    // 保存记录，可以在任务，或者事件发生后，能够直接执行任务；
    private final SparseArray<Runnable> waitTimeoutCallbacks = new SparseArray<>();
    private TaskResultCallback resultCallback;
    private boolean callBackOnUIThread;
    private String serialGroupName;
    private LinkedList<Task> successors;//用于支持匿名任务依赖，不添加任务记录的场景
    private IdleScheduler idleScheduler;
    private Object taskResult;
    /**
     * Idle Run : 0x1
     * dependency run : 0x1<<2
     * OrDelay: 0x1<<3
     */
    private int flag = 0;

    /**
     * 任务期望的运行线程
     */
    RunningThread mRunningThread = RunningThread.BACKGROUND_THREAD;

    private AtomicInteger exeCount = new AtomicInteger();//如果一个任务执行了两次， 那么就抛出异常

    public Task(String name) {
        super(name);
    }

    public Task() {
        super();
    }

    public Task(int tid) {
        super(tid);
    }

    public Task(String name, int tid) {
        super(name, tid);
    }

    /**
     * 重写方法，真正的任务
     */
    public abstract void doTask();

    /**
     * 执行任务前操作
     */
    @CallSuper
    protected void doBeforeTask() {

        if (TMLog.isDebug()) {
            int count = exeCount.incrementAndGet();
            if (count > 1) {
                TaskManagerDeliverHelper.printDump();
                throw new IllegalStateException("task twice :::" + getName() + " " + getTaskId() + " ref: " + this);
            }
        }

        if (taskId > TASKID_RES_RANGE) {
            TaskManagerDeliverHelper.track("start task ", name, " #", taskId);
        }
        taskState = STATE_RUNNING;
        runningThreadId = Thread.currentThread().getId();
        TaskManager.getInstance().notifyTaskStateChange(this, ITaskStateListener.ON_START);
    }

    /**
     * 执行任务后操作
     */
    @CallSuper
    protected void doAfterTask() {
        synchronized (this) {
            taskState = STATE_FINISHED;
            this.notifyAll();
        }
        if (TM.isFullLogEnabled()) {
            TMLog.d(TAG, "this task finished, notify all  " + getName());
        } else if (taskId > TASKID_RES_RANGE) {
            // used to track task state info back for bug analyze
            TaskManagerDeliverHelper.track("end task ", name, " #", taskId);
        }
        TaskManager.getInstance().notifyTaskStateChange(this, ITaskStateListener.ON_FINISHED);

        if (serialGroupName == null) {
            LinkedList<Task> subs = successors;
            if (subs == null) {
                TaskRecorder.onTaskFinished(this, taskId);
            } else if (!subs.isEmpty()) {
                LinkedList<WeakReference<Job>> list = new LinkedList<>();
                for (Task task : successors) {
                    list.add(new WeakReference<Job>(task));
                }
                TaskRecorder.handleSuccesors(list, this, getTaskId(), null);
            }
        } else {
            Task task = TaskContainer.getInstance().pollSerialTask(serialGroupName);
            if (task != null) {
                TaskManager.getInstance().quickRun(task);
            }
        }
        TaskContainer.getInstance().remove(this);
        TaskRecorder.dequeue(this);

        callBackResult();
        clear();
    }


    @Override
    protected void clear() {
        super.clear();
        if (successors != null) {
            successors.clear();
            successors = null;
        }

        if (idleScheduler != null) {
            idleScheduler.decrease();
            idleScheduler = null;
        }

    }

    void setWrapper(TaskWrapper wrapper) {
        mWrapper = new WeakReference<>(wrapper);
    }


    //[used as remove token]
    public Task setToken(Object token) {
        this.mToken = token;
        return this;
    }

    TaskWrapper getTaskWrapper() {
        if (mWrapper != null) {
            return mWrapper.get();
        }
        return null;
    }

    public boolean cancel() {
        boolean rs = false;
        synchronized (this) {
            if (taskState == STATE_IDLE) {
                taskState = STATE_CANCELED;
                TMLog.e(TAG, "this task cancel " + getName());
                TaskManager.getInstance().notifyTaskStateChange(this, ITaskStateListener.ON_CANCELED);
                rs = true;
            }
        }

        if (rs) {
            TaskRecorder.removeSuccessorForTask(taskId);
        }
        return rs;
    }


    // call super for chain invoke

    @Override
    public Task setGroup(Object gid) {
        super.setGroup(gid);
        return this;
    }

    @Override
    public Task setGroup(int gid) {
        super.setGroup(gid);
        return this;
    }


    @Override
    public Task setName(String name) {
        super.setName(name);
        return this;
    }


    @Override
    public Task setTaskID(int tid) {
        super.setTaskID(tid);
        return this;
    }


    @Override
    public Task setTaskPriority(int priority) {
        super.setTaskPriority(priority);
        return this;
    }

    // end for chain invoke]


    /**
     * @param time : 或者延迟时间执行:当时间到达的时候, 即使有依赖条件没有满足 也会执行.
     * @return
     */
    public Task orDelay(int time) {
        checktOrDelay(time);
        flag |= (1 << 3);
        return this;
    }


    /**
     * 延迟任务提交的方法; 不暴露给外部, 外部直接通过  postUIDelay 之类的设置;
     *
     * @param delay
     */
    void setDelay(int delay) {
        checktOrDelay(delay);
    }


    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void updateDelay(int time) {
        delayTime = time;
    }


    private void checktOrDelay(int delay) {
        if (TMLog.isDebug()) {
            if (delayTime != 0) {
                throw new IllegalStateException("Task Delay Time can only be called once,  last seting time is : " + getDelayTime());
            } else if (delay < 0) {
                throw new IllegalStateException("Task Delay Time can only be called once,  last seting time is : " + getDelayTime());
            }
        }
        delayTime = delay;
    }

    public int getDelayTime() {
        return delayTime;
    }


    boolean isOrDelay() {
        return (flag & (1 << 3)) > 0;
    }

    /**
     * if stat is not finished wait ; return false if is finishe;
     *
     * @param timeMillions < = 0 , makes waiting till task finished;
     * @return
     */
    public boolean waitFor(int timeMillions) {
        final long start = System.currentTimeMillis();

        //fix : not waiting on its own running thread
        if (runningThreadId == Thread.currentThread().getId()) {
            TMLog.e(TAG, "this task wait might be called inappropriately, wait before self finished ");
            return false; //return as finished
        }

        synchronized (this) {
            if (taskState != Task.STATE_FINISHED) {
                try {
                    // important log:
                    TMLog.d(TAG, "wait for task " + getName() + " " + getTaskId());
                    if (timeMillions < 0) {
                        trackWaitTimeInOtherThread();
                        wait();
                    } else {
                        wait(timeMillions);
                    }
                } catch (Exception e) {
                    ExceptionUtils.printStackTrace(e);
                } finally {
                    removeWaitTimeoutCallback();
                    TMLog.d(TAG, "wait finished " + getName() + " " + getTaskId());
                }
            }
        }

        if (timeMillions >= 0) {
            trackWaitTime(System.currentTimeMillis() - start);
        }
        //fix 当异步方法超时后，needSync  无法确保方法执行的问题
        return taskState != Task.STATE_FINISHED;
    }

    private void removeWaitTimeoutCallback() {
        Runnable callback = waitTimeoutCallbacks.get((int) Thread.currentThread().getId());
        if (callback != null) {
            TaskManager.getInstance().getWorkHandler().removeCallbacks(callback);
            waitTimeoutCallbacks.remove((int) Thread.currentThread().getId());
        }
    }

    private void trackWaitTimeInOtherThread() {
        final String log = generateWaitInfoLog(TASK_MAX_WAIT_TIME);
        Runnable timeoutCallback = new Runnable() {
            @Override
            public void run() {
                TMLog.d(TAG, log);
                TaskManagerDeliverHelper.trackCritical(log); // 加入到 log buffer 供后续 dump 或 add to FeedbackW
                TaskManagerDeliverHelper.deliver(TaskManagerDeliverHelper.DELIVER_LOG_CRITICAL);
            }
        };
        waitTimeoutCallbacks.put((int) Thread.currentThread().getId(), timeoutCallback);
        TaskManager.getInstance().getWorkHandler().postDelayed(timeoutCallback, TASK_MAX_WAIT_TIME);
    }

    // 新增任务状态；
    private String generateWaitInfoLog(long waitTime) {
        String waitInfo = "Wait:"
                + " #Task["
                + getName()
                + "-"
                + getTaskId()
                + "] "
                + waitTime
                + "ms @thread:"
                + Thread.currentThread().getName()
                + "st: "
                + taskState
                + "TF:"
                + TaskRecorder.isTaskFinished(taskId);

        String log = LogUtils.getTMCallInfo(waitInfo, Task.class.getPackage().getName());
        return log;
    }

    private void trackWaitTime(long waitTime) {
        if (TMLog.isDebug() || waitTime >= TaskManager.getTaskManagerConfig().getWaitTimeCollectThreshold()) {
            String log = generateWaitInfoLog(waitTime);
            TMLog.d(TAG, log);
            TaskManagerDeliverHelper.trackCritical(log);
        }
    }

    /**
     * 设置当前任务，依赖与的task ID
     */
    public Task dependOn(int... taskIds) {
        if (!dependentStates.isEmpty() && TMLog.isDebug() && TaskManager.enableDebugCheckCrash) {
            throw new IllegalStateException("dependOn can only call once. please call: orDependOn instead");
        }
        return orDependOn(taskIds);
    }

    public Task dependOn(Task... tasks) {
        if (!dependentStates.isEmpty() && TMLog.isDebug() && TaskManager.enableDebugCheckCrash) {
            throw new IllegalStateException("dependOn can only call once. please call: orDependOn instead");
        }
        return orDependOn(tasks);
    }


    public Task orDependOn(Task... tasks) {
        if (tasks != null && tasks.length > 0) {
            int taskIds[] = new int[tasks.length];
            int p = 0;
            for (Task task : tasks) {
                taskIds[p++] = task.getTaskId();
                task.addSuccesor(this);
            }
            dependentStates.add(new TaskDependentState(taskIds.length, taskIds));
        }
        return this;
    }


    synchronized void addSuccesor(Task task) {
        if (taskState < STATE_CANCELED) {
            if (successors == null) {
                successors = new LinkedList<>();
            }
            successors.add(task);
        } else if (taskState == STATE_CANCELED) {
            TMLog.e(TAG, "task is already canceled " + this + " requested: " + task);
            if (TMLog.isDebug()) {
                throw new IllegalStateException("dependant task is canceled");
            }
        } else {
            // finished, task will run if other meet
            task.copyData(this);
            task.onDependantTaskFinished(this, getTaskId());
        }
    }

    LinkedList<Task> getSuccesors() {
        return successors;
    }

    /**
     * 设置当前任务，添加或依赖
     */
    public Task orDependOn(int... taskIds) {

        if (TMLog.isDebug()) {
            // check task id:
            if (taskIds != null) {
                for (int id : taskIds) {
                    TM.crashIf(id < Task.TASKID_EVENT_RANGE, "cant depend anonymous tasks, try set res id , or depend on a task instead ");
                }
            }
        }
        if (taskIds != null && taskIds.length > 0) {
            dependentStates.add(new TaskDependentState(taskIds.length, taskIds));
        }
        return this;
    }


    public Task delayAfter(int delayTime, int... taskIds) {
        dependOn(taskIds);
        delayAfterDependant = delayTime;
        return this;
    }


    public Task delayAfter(int delayTime, Task... tasks) {
        dependOn(tasks);
        delayAfterDependant = delayTime;
        return this;
    }


    //设置后，如果任务延时了， 但是在任务队列空闲的时候，将会自动执行，不受延时执行（post delay 的影响）
    public Task enableIdleRun() {
        flag |= 0x1;
        setTaskPriority(Task.TASK_PRIORITY_MIN);
        return this;
    }


    public Task disableIdleRun() {
        flag &= (~0x1);
        return this;
    }

    void clearDependants() {
        if (dependentStates != null) {
            dependentStates.clear();
        }
    }

    /**
     * 在依赖条件完成后,过一段时间在执行
     *
     * @param time
     * @return
     */
    public Task delayAfterDependantMeet(int time) {
        TM.crashIf(time < 0, "delayAfterDependant time must > 0 + " + time);
        delayAfterDependant = time;
        return this;
    }

    /**
     * 请使用delayAfterDependantMeet
     *
     * @param time
     * @return
     */
    @Deprecated
    public Task delayAfterDependant(int time) {
        return delayAfterDependantMeet(time);
    }


    //TM 先会执行延时等待，然后才去执行依赖逻辑检测;因此当这里被调用的时候，延迟条件已经满足了。在这里不考虑延时执行部分的问题。
    //10.8.0 新增idle run: 新增检测 如果是idle task : 那么只有满足idle run 条件的才进就绪队列
    Task onDependantTaskFinished(@Nullable Task finishedTask, int taskId) {
        if (!checkGroupSameOrDefault(finishedTask, taskId)) {
            return null;
        }
        for (TaskDependentState dependentState : dependentStates) {
            if (dependentState != null && dependentState.onTaskFinished(taskId)) {
                dependentStates.clear();
                if (this.taskId <= 0 && TMLog.isDebug() && TaskManager.enableDebugCheckCrash) {
                    throw new IllegalStateException("this task should have task id , as it has some depenant tasks" + "  " + getName());
                }
                // fix bug : dependency meet triggered task , while post delay time not arrived;
                if (isDependencyRunDisabled()) {
                    return null;
                }

                if (TM.isFullLogEnabled()) {
                    TMLog.d(TAG, taskId + "on dependant meet " + getName() + " " + getTaskId());
                }
                //「remove pending task & run」
                TaskContainer.getInstance().remove(this.taskId);

                // 通过任务状态，保证任务只运行一次
                if (taskState != Task.STATE_IDLE) return null;

                Task request = this;
                //only sync task will return for direct run at current thread
                if (isSyncRequest(request) && delayAfterDependant == 0 && !request.isIdleRunEnabled()) {
                    //for exec sync: // runIfIdle : default task return idle true
                    return request;
                } else {
                    if (delayAfterDependant != 0) {
                        request.delayTime = delayAfterDependant;
                    }
                    TaskManager.getInstance().enqueue(request);
                }
            }
        }
        return null;
    }

    private boolean checkGroupSameOrDefault(@Nullable Task finishedTask, int taskId) {
        if (finishedTask != null) {
            return finishedTask.groupId == 0 || finishedTask.groupId == groupId;
        } else {
            return TaskRecorder.checkTaskInGroup(taskId, 0, groupId);
        }
    }

    boolean hasDependantTasks() {
        return !dependentStates.isEmpty();
    }


    /**
     * 判断任务是否是sync 条件执行的需求:Sync or UI Sync
     *
     * @param request
     * @return
     */
    private boolean isSyncRequest(Task request) {
        RunningThread runningThread = request.getRunningThread();
        if (runningThread == RunningThread.UI_THREAD_SYNC) {
            return isUIThread();
        }
        return runningThread == RunningThread.BACKGROUND_THREAD_SYNC;
    }

    /**
     * 获取所有依赖（包含或依赖）的任务id
     *
     * @return id list or null
     */
    int[] getDependantTaskIds() {
        if (dependentStates.isEmpty()) {
            return null;
        }
        int[] taskIds = null;
        for (TaskDependentState next : dependentStates) {
            if (taskIds == null) {
                taskIds = next.taskIds;
            } else {
                int[] result = new int[taskIds.length + next.taskIds.length];
                System.arraycopy(taskIds, 0, result, 0, taskIds.length);
                System.arraycopy(next.taskIds, 0, result, taskIds.length, next.taskIds.length);
                taskIds = result;
            }
        }
        return taskIds;
    }

    /**
     * 若没有依赖任务, 则认为依赖条件已经满足
     *
     * @return
     */
    public boolean isDependentsComplete() {

        if (hasDependantTasks()) {
            for (TaskDependentState dependentState : dependentStates) {
                if (TaskRecorder.isAllTaskFinished(dependentState.taskIds)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }


    /**
     * 在子线程中立即执行
     */
    public void executeAsyncNow() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            taskPriority = Integer.MAX_VALUE;
            TaskManager.getInstance().enqueue(this);
        }
    }

    public void postUIDelay(int ms) {
        checktOrDelay(ms);
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            mRunningThread = RunningThread.UI_THREAD;
            TaskManager.getInstance().enqueue(this);
        }
    }


    public void postUI() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            enqueuePreferred(RunningThread.UI_THREAD);
        }
    }

    // 新增need Sync 方法，当有依赖的时候，在依赖的任务执行完成后，直接执行。
    public void executeSync() {
        if (taskState == STATE_IDLE) {
            if (hasDependantTasks()) {
                enqueuePreferred(RunningThread.BACKGROUND_THREAD_SYNC);
            } else {
                TaskManager.getInstance().executeDirect(this);
            }
        }
    }


    // 当前直接执行，或者在满足条件后，与当前线程类似的线程上执行。
    public void executeSyncCurrentThread() {
        if (taskState == STATE_IDLE) {
            if (hasDependantTasks()) {
                enqueuePreferred(isUIThread() ? RunningThread.UI_THREAD_SYNC : RunningThread.BACKGROUND_THREAD_SYNC);
            } else {
                TaskManager.getInstance().executeDirect(this);
            }
        }
    }

    // 新增need Sync 方法，当有依赖的时候，在依赖的任务执行完成后，直接执行。
    public void executeSyncUI() {

        if (taskState == STATE_IDLE) {
            if (hasDependantTasks()) {
                enqueuePreferred((RunningThread.UI_THREAD_SYNC));
            } else if (isUIThread()) {
                TaskManager.getInstance().executeDirect(this);
            } else { // not ui thread
                enqueuePreferred((RunningThread.UI_THREAD_SYNC));
            }
        }
    }


    public void executeSerial(String groupName) {
        executeSerialDelay(groupName, 0);
    }


    public void executeSerialDelay(String groupName, int delay) {
        checktOrDelay(delay);
        if (taskState == STATE_IDLE) {
            if (groupName == null || groupName.length() == 0) {
                throw new IllegalStateException("group name  of task cant be null");
            }
            serialGroupName = groupName;
            setName(groupName + "#" + name);
            TaskManager.getInstance().enqueue(this);
        }
    }


    public void postAsync() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            TaskManager.getInstance().enqueue(this);
        }
    }


    public void postAsyncDelay(int delay) {
        checktOrDelay(delay);
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            TaskManager.getInstance().enqueue(this);
        }
    }

    public void postDelay(int delay) {
        checktOrDelay(delay);
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            this.mRunningThread = Looper.myLooper() == Looper.getMainLooper() ? RunningThread.UI_THREAD : RunningThread.BACKGROUND_THREAD;
            TaskManager.getInstance().enqueue(this);
        }
    }

    private void enqueuePreferred(RunningThread preferred) {
        this.mRunningThread = preferred;
        TaskManager.getInstance().enqueue(this);
    }


    //任务进入等待队列，等待执行
    public void postPending() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            mRunningThread = isUIThread() ? RunningThread.UI_THREAD : RunningThread.BACKGROUND_THREAD;
            checktOrDelay(Integer.MAX_VALUE);
            TaskManager.getInstance().enqueue(this);
        }
    }

    //任务进入等待队列，等待执行
    public void postAsyncPending() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            checktOrDelay(Integer.MAX_VALUE);
            TaskManager.getInstance().enqueue(this);
        }
    }

    //任务进入等待队列，等待执行
    public void postUIPending() {
        if (taskState == STATE_IDLE) { // when call need Task; in case run more than once
            checktOrDelay(Integer.MAX_VALUE);
            mRunningThread = RunningThread.UI_THREAD;
            TaskManager.getInstance().enqueue(this);
        }
    }


    /**
     * this task is bind to context :
     * if context is activity context : when activity is destroyed: this task will be canceled if is not triggered
     *
     * @param context
     */
    public Task bind(Context context) {
        int bindHash = TaskRecorder.bindTask(context, taskId);
        if (bindHash < 0) {
            cancel();
            TaskManager.getInstance().notifyTaskStateChange(this, ITaskStateListener.ON_CANCELED);
            bindHash = 0;
        }
        bindActivityHash = bindHash;
        return this;
    }

    public Task setThreadPriority(int threadPriority) {
        priority = threadPriority;
        return this;
    }

    public int getThreadPriority() {
        return priority;
    }

    int getBoundActivityHash() {
        return bindActivityHash;
    }


    public Object getToken() {
        return mToken;
    }


    int compareAndSetState(int newState) {
        synchronized (this) {
            if (newState <= taskState) {
                //[状态错误 需要停止执行]
                return taskState;
            }
            taskState = newState;
        }

        return -1;
    }

    int getState() {
        return taskState;
    }


    public boolean isIdleRunEnabled() {
        return (flag & 0x1) > 0;
    }

    public RunningThread getRunningThread() {
        return mRunningThread;
    }

    public Task setRunningThread(RunningThread thread) {
        mRunningThread = thread;
        return this;
    }

    void resetRunCount() {
        if (TMLog.isDebug()) {
            exeCount.decrementAndGet();
        }
    }

    public void setResult(final Object var) {
        Log.d("Test", " set r " + var);
        taskResult = var;
    }

    private void callBackResult() {
        if (resultCallback != null) {
            if (callBackOnUIThread) {
                TaskManager.getInstance().getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        resultCallback.onResultCallback(Task.this, taskResult);
                    }
                });
            } else {
                resultCallback.onResultCallback(Task.this, taskResult);
            }
        }
    }

    public Task setCallBackOnFinished(TaskResultCallback resultCallback, boolean ui) {
        TM.crashIf(this.resultCallback != null && this.resultCallback != resultCallback, "task result might be overridden " + getName());
        this.resultCallback = resultCallback;
        callBackOnUIThread = ui;
        return this;
    }

    public Task setCallBackOnFinished(TaskResultCallback resultCallback) {
        TM.crashIf(this.resultCallback != null && this.resultCallback != resultCallback, "task result might be overridden " + getName());
        this.resultCallback = resultCallback;
        return this;
    }

    public abstract static class TaskResultCallback {
        public abstract void onResultCallback(Task task, Object var);
    }

    public String getSerialGroupName() {
        return serialGroupName;
    }

    @Override
    public String toString() {
        if (name != null) {
            return name + " " + getTaskId();
        } else {
            return super.toString();
        }
    }

    // to support task successor run after post delay time meets
    void setDisableDependencyRun(boolean enabled) {
        if (enabled) {
            flag |= 0x2;
        } else {
            flag &= ~(0x2);
        }
    }

    // to support task successor run after post delay time meets
    private boolean isDependencyRunDisabled() {
        return (flag & 0x2) > 0;
    }

    public void setIdleScheduler(IdleScheduler scheduler) {
        idleScheduler = scheduler;
    }
}

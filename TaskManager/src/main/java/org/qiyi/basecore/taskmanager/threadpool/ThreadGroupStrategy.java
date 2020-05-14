package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * todo :
 * 1) to support max thread count : not supported yet
 * 2) #support execute Now : use task priority to support. (jump queue)
 * 3) #enableIdle Run
 */
public final class ThreadGroupStrategy implements IThreadStrategy, IThreadIdleCallback {

    private final static String TAG = "TM_ThreadGroupStrategy";
    private TMThreadGroup normalThreadGroup;
    private TMThreadGroup highThreadGroup;
    private AtomicInteger threadCount;
    private int maxRunningAmount;
    private final int IDLE_TASK_MIN_THREAD_COUNT = 2;
    private ITaskQueue normalQueue;
    private ITaskQueue highQueue;


    public ThreadGroupStrategy(ITaskQueue high, ITaskQueue normal, int cpuCount) {
        int core = cpuCount / 2;
        if (core < 3) {
            core = 3;
        }
        maxRunningAmount = Integer.MAX_VALUE;
        threadCount = new AtomicInteger();
        normalThreadGroup = new TMThreadGroup(normal, this, "tmn-", Thread.NORM_PRIORITY, 3, cpuCount);
        highThreadGroup = new TMThreadGroup(high, this, "tmh-", Thread.MAX_PRIORITY, 0, core);
        normalQueue = normal;
        highQueue = high;
    }

    @Override
    public void executeOnBackgroundThread(TaskWrapper runnable, int threadPriority, int taskPriority) {
        runnable.enqueueMark(taskPriority);
        if (threadPriority == Thread.NORM_PRIORITY || threadPriority == 0) {
            normalThreadGroup.execute(runnable, taskPriority);
        } else if (threadPriority == Thread.MAX_PRIORITY) {
            highThreadGroup.execute(runnable, taskPriority);
        } else {// flexible, make sure to run quickly if thread is available
            if (!normalThreadGroup.tryExecute(runnable, taskPriority)) {
                highThreadGroup.execute(runnable, taskPriority);
            }
        }
    }

    //called when task starts to run
    @Override
    public void onLoseThread(int priority) {
        // do nothing
        threadCount.decrementAndGet();
    }

    //called when task is finished
    @Override
    public void onGainThread() {
        // do nothing
        threadCount.incrementAndGet();
    }

    // to check if anything can run
    @Override
    public void trigger() {
        // wake up some thread to check task to run
        normalQueue.notify();
        highQueue.notify();
    }

    @Override // not supported right now
    public void setMaxRunningThreadCount(int count) {
        if (count < 0) {
            maxRunningAmount = Integer.MAX_VALUE;// reset
        }
        maxRunningAmount = count;
    }

    @Override
    public void onIdle(boolean idle) {
        if (idle && threadCount.get() < IDLE_TASK_MIN_THREAD_COUNT) {
            Task request = TaskContainer.getInstance().offerTaskInIdleState(true);
            if (request != null) {
                if (TM.isFullLogEnabled()) {
                    TMLog.d(TAG, "!!! idle task is to run " + request);
                }
                request.disableIdleRun();
                TaskManager.getInstance().enqueue(request);
            }
        }
    }
}

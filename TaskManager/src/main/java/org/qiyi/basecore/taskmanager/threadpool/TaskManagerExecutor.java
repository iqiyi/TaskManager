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
package org.qiyi.basecore.taskmanager.threadpool;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.qiyi.basecore.taskmanager.RunningThread;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.ThreadPriority;
import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 线程池:
 * 1) 支持主线程执行 & 子线程执行
 * 2） 并行化原理：
 * 提供主线程执行能力
 * 提供默认优先级的 子线程执行能力
 * 当有多个核心的时候，提供其他线程并发执行能力
 * 3）新增一个立即执行的线程池；（临时方案， 后期改造为 thread group 方式）
 * thread executor design need to be optimized!!!
 * Need to be optimised later!
 */
class TaskManagerExecutor implements ITaskExecutor {
    private static final String TAG = "TM_TaskManagerExecutor";
    //chief executor to run background tasks
    private ThreadPoolExecutor mBackgroundExecutor;
    //高优先级线程池
    private ThreadPoolExecutor mHighPriorityExecutor;
    // 用于支持高优先级任务，需要无等待立即执行的情况。
    private ThreadPoolExecutor extensionExecutor;

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    //used as a timer, only to handel low time consuming tasks
    private Handler workHandler;
    // private LinkedList<TaskRequest> taskList;  moved to task record
    private TaskBlockingQueue rejectedQueue = new TaskBlockingQueue();

    private TaskBlockingQueue highPriorityRejectedQueue = new TaskBlockingQueue();
    private volatile Handler workHandlerLowPriority;//低优先级执行队列
    private final int HIGH_PRIORITY_THREAD_MAX_SIZE = 3;
    private int NORMAL_PRIORITY_THREAD_MAX_SIZE = 5;
    private int REJECT_QUEUE_BUSY_TASK_SIZE = 3; // 堆积了三个任务,任务是忙的状态
    private volatile boolean executeIdleHigh = false;
    private volatile boolean executeIdleNormal = false;

    private int aliveThreadCount;
    private int maxRunningAmount;
    private int lock[] = new int[0];

    private Runnable hiRunnable = new Runnable() {
        @Override
        public void run() {
            handleTaskDeque(Thread.MAX_PRIORITY);
        }
    };
    private Runnable normalRunnable = new Runnable() {
        @Override
        public void run() {
            handleTaskDeque(Thread.NORM_PRIORITY);
        }
    };

    private int cupCores;

    public TaskManagerExecutor() {
        cupCores = getCpuCores();// >1
        HandlerThread handlerThread = new HandlerThread("TaskManager-back");
        handlerThread.start();
        workHandler = new Handler(handlerThread.getLooper());
//        taskList = new LinkedList<>();
        initThreadPool();//[will not create at firt]
        initHightPriorityThreadPool();
//        MAX_THREAD_COUNT = NORMAL_PRIORITY_THREAD_MAX_SIZE + HIGH_PRIORITY_THREAD_MAX_SIZE;
        // 设置限制数量， 设置为最大值， 就相当与不限制。 大于最大可用线程数量的限制也是没有意义的。
        maxRunningAmount = Integer.MAX_VALUE;
    }


    private void initThreadPool() {
        if (mBackgroundExecutor == null) {
            int max = cupCores - 2;
            if (max < 3) {
                max = 3;
            }
            NORMAL_PRIORITY_THREAD_MAX_SIZE = max;
            mBackgroundExecutor = new ThreadPoolExecutor(2, NORMAL_PRIORITY_THREAD_MAX_SIZE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    new DefaultThreadFactory("TMn", Thread.NORM_PRIORITY));
        }


        // thread will be destroyed soon, max thread count cpu core count
        if (extensionExecutor == null) {
            extensionExecutor = new ThreadPoolExecutor(0, cupCores,
                    10L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    new DefaultThreadFactory("TMr", Thread.NORM_PRIORITY));
        }
    }

    private void initHightPriorityThreadPool() {
        if (mHighPriorityExecutor == null) {
            mHighPriorityExecutor = new ThreadPoolExecutor(0, HIGH_PRIORITY_THREAD_MAX_SIZE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(),
                    new DefaultThreadFactory("TMh", Thread.MAX_PRIORITY));
        }
    }

    @Override //「might be Parallel tasks」
    public void executeDirect(Task taskRequest) {
        TaskWrapper wrapper = TaskWrapper.obtain(taskRequest);
        RunningThread thread = taskRequest.getRunningThread();
        if (thread == RunningThread.BACKGROUND_THREAD) {
            wrapper.run();
        } else if (Looper.myLooper() != Looper.getMainLooper()
                && thread.isRunningInUIThread()) {
            postToMainThread(wrapper);
        } else { //[might run on UI thread , and now is on UI thread]
            wrapper.run();
        }
    }


    @Override
    public void postToMainThread(TaskWrapper runnable) {
        mMainThreadHandler.post(runnable);
    }


    @Override
    public void executeOnBackgroundThread(TaskWrapper runnable, int priority, int taskPriority) {
        runnable.enqueueMark(taskPriority);

        if (priority == 0 || priority == ThreadPriority.NORMAL) {
            handleNormalPriority(runnable, taskPriority, true);
        } else if (priority == ThreadPriority.MAX) {
            handleHighPriority(runnable, taskPriority, true);
        } else if (priority == ThreadPriority.MIN) {
            handleLowPriority(runnable);
        } else { //for flexible
            // 优先复用 优先线程池
            if (mHighPriorityExecutor != null && mHighPriorityExecutor.getPoolSize() > 0 && highPriorityRejectedQueue.isEmpty()) {
                handleHighPriority(runnable, taskPriority, true);
            } else if (rejectedQueue.size() < REJECT_QUEUE_BUSY_TASK_SIZE || isThreadPoolIdle(mBackgroundExecutor)) {// 再使用默认线程池塘
                handleNormalPriority(runnable, taskPriority, true);
                //添加的时候 不执行高优先级线程创建策略;
//            } else if (mHighPriorityExecutor == null) { // 如果默认线程池阻塞 那么就再尝试使用优先线程池
//                handleHighPriority(runnable, taskPriority, true);
            } else {
                handleNormalPriority(runnable, taskPriority, true);
            }
        }
    }


    //return true : if this pool is empty or has some thread idle
    private boolean isThreadPoolIdle(ThreadPoolExecutor executor) {
        if (executor != null) {
            int poolSize = executor.getPoolSize();
            return poolSize < executor.getMaximumPoolSize() ||
                    executor.getActiveCount() < poolSize;
        } else {
            return true;
        }
    }

    /**
     * 只是用来标记一种任务完成; 通知有新的资源了 可以进一步执行工作了;
     */
    public void dequeue(int pri) {

        // 在子线程里面执行， 避免占用当前的线程任务，再添加导致 大量的 reject 的问题
        synchronized (lock) {
            aliveThreadCount--;
        }
        if (pri == Thread.MAX_PRIORITY) {
            workHandler.post(hiRunnable);
        } else {
            workHandler.post(normalRunnable);
        }
    }


    //
    private void handleTaskDeque(int pri) {

        TaskWrapper runnable = null;
        if (pri == Thread.MAX_PRIORITY) {
            runnable = highPriorityRejectedQueue.pollFirst();
            if (runnable == null) {
                runnable = rejectedQueue.pollFirst();
            }
        } else {
            runnable = rejectedQueue.pollFirst();
            if (runnable == null) {
                runnable = highPriorityRejectedQueue.pollFirst();
            }
        }

        if (runnable != null) {
            executeOnBackgroundThread(runnable, pri, runnable.getTaskPriority());
        } else {
            if (pri == Thread.MAX_PRIORITY) {
                executeIdleHigh = true;
            } else {
                executeIdleNormal = true;
            }
            if (TM.isFullLogEnabled()) {
                TMLog.e(TAG, "dequeue fail , nothing to run " + aliveThreadCount);
            }
            if (aliveThreadCount < 2) {
                offerFromWaitingQueue();
            }
        }
    }

    //[闲时调度： 返回延迟的非主线程执行的]
    private void offerFromWaitingQueue() {
        Task request = TaskContainer.getInstance().offerTaskInIdleState(true);
        if (request != null) {
            if (TM.isFullLogEnabled()) {
                TMLog.d(TAG, "!!! idle task is to run " + request);
            }
            request.disableIdleRun();
            TaskManager.getInstance().enqueue(request);
        }
    }


    public void workPostDelay(Runnable runnable, int time) {
        if (time != 0) {
            workHandler.postDelayed(runnable, time);
        } else {
            workHandler.post(runnable);
        }
    }

    /**
     * The default thread factory.
     */
    private static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private int priority;

        DefaultThreadFactory(String var, int pri) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = var + "-" + pri + "-" +
                    poolNumber.getAndIncrement();
            priority = pri;

        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            t.setPriority(priority);

            return t;
        }
    }

    private static int getCpuCores() {
        int cores;
        try {
            cores = new File("/sys/devices/system/cpu/").listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return Pattern.matches("cpu[0-9]", pathname.getName());
                }
            }).length;
        } catch (Exception e) {
            cores = 1;
        }

        return cores < 1 ? 1 : cores;
    }


    @Override
    public void bringToFront(int taskId) {
        rejectedQueue.bringToFront(taskId);
    }


    /**
     * data dump, for lens
     */
    public void dumpData() {

        StringBuilder builder = new StringBuilder();
        builder.append("EXE：rejected queue size is ");
        synchronized (rejectedQueue) {
            builder.append(rejectedQueue.size());
            TaskManagerDeliverHelper.track(builder.toString());
            rejectedQueue.printTasks();
        }

        synchronized (highPriorityRejectedQueue) {
            if (!highPriorityRejectedQueue.isEmpty()) {
                builder.append(highPriorityRejectedQueue.size());
                TaskManagerDeliverHelper.track(builder.toString());
                highPriorityRejectedQueue.printTasks();
            }
        }


    }

    @Override
    public Handler getWorkHandler() {
        return workHandler;
    }


    @Override
    public Handler getMainHandler() {
        return mMainThreadHandler;
    }

    @Override
    // 限制最大同时运行的线程数量
    public void setMaxRunningThreadCount(int count) {
        if (count < 0) {
            maxRunningAmount = Integer.MAX_VALUE;// reset
        }
        maxRunningAmount = count;
    }

    @Override
    public boolean removeTaskByToken(Object token) {
        return rejectedQueue.removeTaskByToken(token) || highPriorityRejectedQueue.removeTaskByToken(token);
    }

    @Override
    public boolean removeTask(int taskId) {
        return rejectedQueue.removeTaskById(taskId) || highPriorityRejectedQueue.removeTaskById(taskId);
    }


    public void onGainThread() {
        synchronized (lock) {
            aliveThreadCount++;
        }
    }

    @Override
    public void trigger() {
        workHandler.post(hiRunnable);
    }


    // 支持普通优先级任务策略
    private void handleNormalPriority(TaskWrapper runnable, int taskPriority, boolean resign) {
        //{分发策略： 如何当前等待完成的任务太多，就由线程池加载}
        if (resign) {
            synchronized (lock) {
                //by default this is not in use
                if (aliveThreadCount > maxRunningAmount) {
                    TMLog.d(TAG, runnable + " normal task is rejected");
                    TMLog.d(TAG, "task rejected as exceeded max count " + aliveThreadCount + " " + maxRunningAmount);
                    onRejectTask(runnable, executeIdleNormal, taskPriority);
                    return;
                }
            }
        }

        try {
            mBackgroundExecutor.execute(runnable);
        } catch (Exception reject) {
            if (resign && mHighPriorityExecutor != null && isThreadPoolIdle(mHighPriorityExecutor)) {
                handleHighPriority(runnable, taskPriority, false);
                return;
            }
            onRejectTask(runnable, executeIdleNormal, taskPriority);
            return;
        }
        executeIdleNormal = false;
    }


    private void onRejectTask(TaskWrapper runnable, boolean isIdle, int taskPriority) {
        // 任务必须立即执行的情况
        if (taskPriority == Integer.MAX_VALUE) {
            // 创建新线程用于任务执行
            boolean finalTrySuccess = true;
            try {
                extensionExecutor.execute(runnable);
            } catch (Exception e) {
                finalTrySuccess = false;
            }
            if (finalTrySuccess) {
                TMLog.d(TAG, "task is handled by extension executor.");
                return;
            }
        }

        if (taskPriority == Thread.MAX_PRIORITY) {
            highPriorityRejectedQueue.addLast(runnable, taskPriority);
        } else {
            rejectedQueue.addLast(runnable, taskPriority);
        }
        notifyAdd(isIdle, taskPriority);
    }


    // 支持低优先级任务执行
    private void handleLowPriority(Runnable runnable) {
        if (workHandlerLowPriority == null) {
            synchronized (this) {
                if (workHandlerLowPriority == null) {
                    HandlerThread handlerThread = new HandlerThread("TaskManager-back-low");
                    handlerThread.start();
                    workHandlerLowPriority = new Handler(handlerThread.getLooper());
                }
            }
        }

        workHandlerLowPriority.post(runnable);
    }


    //支持高优先级任务执行
    private void handleHighPriority(TaskWrapper runnable, int taskPriority, boolean resign) {

        //{分发策略： }

        //  同步问题： 当A最大执行任务数为1， if A running， B reject ； A finished；& notify reject queue to run ; B add to reject queue; then B run fail
        if (resign) {
            synchronized (lock) {

                if (aliveThreadCount > maxRunningAmount) {
                    TMLog.d(TAG, runnable + " normal task is rejected");
                    TMLog.d(TAG, "task rejected as exceeded max count " + aliveThreadCount + " " + maxRunningAmount);
                    onRejectTask(runnable, executeIdleHigh, taskPriority);
                    return;
                }
            }
        }

        try {
            mHighPriorityExecutor.execute(runnable);
        } catch (Exception reject) {
            int size = highPriorityRejectedQueue.size();
            if (resign && size > HIGH_PRIORITY_THREAD_MAX_SIZE) {
                if (rejectedQueue.size() < NORMAL_PRIORITY_THREAD_MAX_SIZE / 2) {
                    //degrade to normal queue to run
                    handleNormalPriority(runnable, taskPriority, false);
                    return;
                }
            }
            onRejectTask(runnable, executeIdleHigh, taskPriority);
            return;
        }
        executeIdleHigh = false;
    }

    // in case a sync problem: offer Task : size =0 ; add list : size 1:
    private void notifyAdd(boolean isIdle, final int pri) {
        if (isIdle) {
            // notify add
            Runnable runnable;
            if (pri == Thread.MAX_PRIORITY) {
                runnable = hiRunnable;
                executeIdleHigh = false;
            } else {
                executeIdleNormal = false;
                runnable = normalRunnable;
            }
            workHandler.removeCallbacks(runnable);
            workHandler.post(runnable);
        }
    }

    public int getCpuCount() {
        return cupCores;
    }

}

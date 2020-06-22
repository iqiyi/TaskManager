package org.qiyi.basecore.taskmanager.threadpool;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.qiyi.basecore.taskmanager.RunningThread;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;

/**
 * TM new ITaskExecutor implementation;
 * 'strategy' is left to customize a different thread pool behavior.
 */
public class GroupedThreadPool implements ITaskExecutor {
    private static final String TAG = "TM_GroupedThreadPool";
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    //used as a timer, only to handel low time consuming tasks
    private Handler workHandler;
    private int cupCores;
    private IThreadStrategy strategy;
    // low priority task will be execute in single thread.
    private volatile Handler workHandlerLowPriority;//低优先级执行队列
    // high thread priority task will be stored here.
    private ITaskQueue highQueue;
    // normal thread priority task will be stored here.
    private ITaskQueue normalQueue;


    public GroupedThreadPool() {
        createWorkHandler();
        cupCores = getCpuCores();
        highQueue = new PendingTaskQueue(workHandler);
        normalQueue = new PendingTaskQueue(workHandler);
        //default : running task size is not constraint
        strategy = new ThreadGroupStrategy(highQueue, normalQueue, cupCores);
    }

    private void createWorkHandler() {
        HandlerThread handlerThread = new HandlerThread("TaskManager-back");
        handlerThread.start();
        workHandler = new Handler(handlerThread.getLooper());
    }

    @Override
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
        if (priority == Thread.MIN_PRIORITY) {
            handleLowPriority(runnable);
        } else {
            strategy.executeOnBackgroundThread(runnable, priority, taskPriority);
        }
    }

    @Override
    //mark a task has been finished
    public void dequeue(int priority) {
        strategy.onLoseThread(priority);
    }

    public void workPostDelay(Runnable runnable, int time) {
        if (time != 0) {
            workHandler.postDelayed(runnable, time);
        } else {
            workHandler.post(runnable);
        }
    }

    @Override
    public void bringToFront(int taskId) {
        // do nothing
    }

    // for debug: dump inside data
    @Override
    public void dumpData() {
        StringBuilder builder = new StringBuilder();
        builder.append("EXE：pending queue size is ");
        synchronized (normalQueue) {
            builder.append(normalQueue.size());
            TaskManagerDeliverHelper.track(builder.toString());
            normalQueue.printTasks();
        }
        builder.setLength(0);
        if (highQueue.size() > 0) {
            builder.append("high: ");
            synchronized (highQueue) {
                builder.append(highQueue.size());
                TaskManagerDeliverHelper.track(builder.toString());
                highQueue.printTasks();
            }
        }

    }

    @Override
    public Handler getWorkHandler() {
        return workHandler;
    }

    @Override
    public boolean removeTaskByToken(Object token) {
        return normalQueue.removeTaskByToken(token) || highQueue.removeTaskByToken(token);
    }

    @Override
    public boolean removeTask(int taskId) {
        return normalQueue.removeTaskByToken(taskId) || highQueue.removeTaskByToken(taskId);
    }

    @Override
    public Handler getMainHandler() {
        return mMainThreadHandler;
    }

    @Override
    public void setMaxRunningThreadCount(int count) {
        strategy.setMaxRunningThreadCount(count);
    }

    @Override
    public int getCpuCount() {
        return cupCores;
    }

    @Override
    public void onGainThread() {
        strategy.onGainThread();
    }

    @Override
    public void trigger() {
        strategy.trigger();
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

}

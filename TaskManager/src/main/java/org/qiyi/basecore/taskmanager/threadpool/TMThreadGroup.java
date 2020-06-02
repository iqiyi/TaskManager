package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.util.concurrent.atomic.AtomicInteger;

import static org.qiyi.basecore.taskmanager.threadpool.TMThreadGroup.*;

public class TMThreadGroup implements IThreadIdleCallback {
    private int mPriority;
    private int mCoreSize;
    private int mMaxSize;
    private ITaskQueue mTaskQueue;
    private TMThread threads[];
    private int mSize;
    private String mName;
    private final static String TAG = "TM_ThreadGroup";
    private IThreadIdleCallback idleCallback;
    private AtomicInteger activeCount;
    private int tempThreadCount;
    private final int TEMP_THREAD_MAX = 10;


    public TMThreadGroup(ITaskQueue queue, IThreadIdleCallback callback, String name, int priority, int core, int max) {
        mPriority = priority;
        mCoreSize = core;
        mMaxSize = max;
        threads = new TMThread[max + TEMP_THREAD_MAX];
        mName = name;
        mTaskQueue = queue;
        idleCallback = callback;
        activeCount = new AtomicInteger();
    }

    //add thread if thread size < max
    private void addWorker(int max, boolean autoQuit) {
        if (mSize < max) {
            TMThread tmThread = null;
            synchronized (this) {
                if (mSize < max) {
                    tmThread = new TMThread(this, this, mTaskQueue, mName, mPriority, mSize, mSize * 10000, autoQuit);
                    threads[mSize] = tmThread;
                    mSize++;
                }
            }
            if (tmThread != null) {
                tmThread.start();
            }
        }
    }

    //todo check
    synchronized void remove(Thread t) {
        int p = 0;
        for (int i = 0; i < mMaxSize; i++) {
            if (threads[i] == t || threads[i] == null) {
                continue;
            } else {
                if (p != i) {
                    threads[p] = threads[i];
                }
                p++;
            }
        }

        // clear the rest
        for (int i = p; i < mMaxSize; i++) {
            threads[i] = null;
        }
        mSize = p;

        // no need to sync
        tempThreadCount--;
        if (tempThreadCount < 0) {
            tempThreadCount = 0;
        }
    }

    public void execute(TaskWrapper taskWrapper, int taskPriority) {
        mTaskQueue.offer(taskWrapper, taskPriority);
        int state = mTaskQueue.getQueueState();
        if (TM.isFullLogEnabled()) TMLog.d(TAG, "execute called " + state);

        switch (state) {
            case ITaskQueue.AVERAGE:
                //add worker to core size
                // when < core : prefer to create thread , not reuse one
                addWorker(mCoreSize, false);
                break;
            case ITaskQueue.BUSY:
                // add worker to max size
                if (mSize <= activeCount.get()) {
                    addWorker(mMaxSize, true);
                } else if (taskPriority == Task.TASK_PRIORITY_MAX) {
                    //for execuye now
                    if (tempThreadCount < TEMP_THREAD_MAX) {
                        tempThreadCount++;
                    }
                    addWorker(mMaxSize + tempThreadCount, true);
                }
                break;
            case ITaskQueue.HEAVY:
                TMLog.e(TAG, "too much task to run !", mTaskQueue.size(), mName);
                if (tempThreadCount < TEMP_THREAD_MAX) {
                    tempThreadCount++;
                }
                addWorker(mMaxSize + tempThreadCount, true);
                break;
            default:
                // idle:
                //do nothing
        }
    }

    public boolean tryExecute(TaskWrapper runnable, int taskPriority) {
        if (mSize > 0 && mTaskQueue.getQueueState() <= ITaskQueue.AVERAGE) {
            execute(runnable, taskPriority);
            return true;
        }
        return false;
    }

    @Override
    public void onIdle(boolean idle) {
        if (idle) {
            activeCount.decrementAndGet();
        } else {
            activeCount.incrementAndGet();
        }
        if (idleCallback != null) {
            idleCallback.onIdle(idle);
        }
    }

}

package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.other.TMLog;

public class TMThread extends Thread {
    private boolean started;
    private ITaskQueue mQueue;
    private int mPriority;
    private int liveTime;
    private TMThreadGroup mGroup;
    private boolean autoQuit;
    private IThreadIdleCallback idleCallback;
    private static final String TAG = "TM_Thread";

    public TMThread(TMThreadGroup group, IThreadIdleCallback callback, ITaskQueue queue, String name, int priority, int index, int live, boolean quit) {
        super(name + priority + "-" + index);
        liveTime = live;
        mQueue = queue;
        mPriority = priority;
        mGroup = group;
        autoQuit = quit;
        idleCallback = callback;
        if (!autoQuit) {
            // blocked , need notify to wake up
            liveTime = 0;
        }
    }

    @Override
    public void run() {
        long waitTime;
        while (started) {
            Runnable runnable = mQueue.dequeue(mPriority);
            if (runnable == null) {
                if (idleCallback != null) {
                    idleCallback.onIdle(true);
                }
                synchronized (mQueue) {
                    try {
                        if (TM.isFullLogEnabled()) {
                            TMLog.d(TAG, " on waiting...", this.getName() + "  " + this.getId());
                        }

                        waitTime = System.currentTimeMillis();
                        mQueue.wait(liveTime);

                        if (TM.isFullLogEnabled()) {
                            TMLog.d(TAG, " on wake up" + this.getName() + "  " + this.getId());
                        }

                        if (autoQuit) {
                            waitTime = System.currentTimeMillis() - waitTime;
                            if (waitTime > liveTime) {
                                started = false;
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (idleCallback != null) {
                    idleCallback.onIdle(false);
                }
            } else {
                runnable.run();
            }
        }
        TMLog.d(TAG, this.getName(), " on quit" + getId());
        mGroup.remove(this);
    }

    @Override
    public void start() {
        started = true;
        super.start();
    }

    public void quit() {
        started = false;
        mQueue.notifyAll();
    }

}

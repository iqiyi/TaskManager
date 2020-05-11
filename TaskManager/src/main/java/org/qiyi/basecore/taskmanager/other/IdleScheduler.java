package org.qiyi.basecore.taskmanager.other;

import android.os.Looper;
import android.os.MessageQueue;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;


public class IdleScheduler {
    private int count;
    IdleHandler handler;

    public void increase() {
        synchronized (this) {
            count++;
            if (handler == null) {
                handler = new IdleHandler();
                final IdleHandler handle = handler;
                new Task() {
                    @Override
                    public void doTask() {
                        if (handle.started) {
                            Looper.myQueue().addIdleHandler(handle);
                        }
                    }
                }.postUI();
            }
        }
    }

    public void decrease() {
        synchronized (this) {
            count--;
            if (count == 0) {
                final IdleHandler handle = handler;
                if (handle != null) {
                    handle.stop();
                }
                new Task() {
                    @Override
                    public void doTask() {
                        Looper.myQueue().removeIdleHandler(handle);
                    }
                }.postUI();
                handler = null;
            }
        }

    }

    class IdleHandler implements MessageQueue.IdleHandler {

        boolean started;

        public IdleHandler() {
            started = true;
        }

        public void stop() {
            started = false;
        }

        @Override
        public boolean queueIdle() {
            TaskManager.getInstance().triggerIdleRun();
            return started;
        }
    }

}

package org.qiyi.basecore.taskmanager.threadpool;

import android.os.Handler;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.other.TMLog;

public class PendingTaskQueue implements ITaskQueue {
    private TaskBlockingQueue rejectedQueue = new TaskBlockingQueue();
    private Handler workHandler;


    public PendingTaskQueue(Handler handler){
        workHandler = handler;
    }

    @Override
    public Runnable dequeue(int priority) {
        return rejectedQueue.pollFirst();
    }

    @Override
    public int size() {
        return rejectedQueue.size();
    }

    @Override
    public int getQueueState() {
        int size = rejectedQueue.size();
        if (size < 1) {
            return IDLE;
        } else if (size < 3) {
            return AVERAGE;
        } else if (size > 100) {
            return HEAVY;
        } else {
            return BUSY;
        }
    }

    @Override
    public void offer(final TaskWrapper wrapper, final int taskPriority) {
        rejectedQueue.addLast(wrapper, taskPriority);
        workHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (PendingTaskQueue.this) {
                    PendingTaskQueue.this.notify();
                }
            }
        });
    }

    @Override
    public boolean removeTaskById(int taskId) {
        return rejectedQueue.removeTaskById(taskId);
    }

    @Override
    public boolean removeTaskByToken(Object token) {
        return rejectedQueue.removeTaskByToken(token);
    }

    @Override
    public void printTasks() {
        rejectedQueue.printTasks();
    }

}

package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TaskWrapper;

public interface ITaskQueue {
    int BUSY = 2;
    int IDLE = 0;
    int AVERAGE = 1;
    int HEAVY = 3;

    Runnable dequeue(int priority);

    int size();

    // if no task in queue , return idle
    int getQueueState();

    void offer(TaskWrapper wrapper, int taskPriority);

    boolean removeTaskById(int taskId);

    boolean removeTaskByToken(Object token);

    void printTasks();

}

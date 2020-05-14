package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TaskWrapper;

public interface IThreadStrategy {
    void executeOnBackgroundThread(TaskWrapper runnable, int priority, int taskPriority);

    void dequeue(int priority);

    void onGainThread();

    void trigger();

    void setMaxRunningThreadCount(int count);
}


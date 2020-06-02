package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.iface.ITaskManagerConfig;

public class ThreadPoolFactory {
    public static ITaskExecutor createExecutor(int strategy) {
        if (strategy == ITaskManagerConfig.STRATEGY_POOL_EXECUTOR) {
            return new TaskManagerExecutor();
        } else {
            return new GroupedThreadPool();
        }
    }
}

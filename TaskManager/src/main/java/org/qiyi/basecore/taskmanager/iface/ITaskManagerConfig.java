package org.qiyi.basecore.taskmanager.iface;

import android.app.Application;

import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.config.TaskManagerConfig;
import org.qiyi.basecore.taskmanager.deliver.ITracker;
import org.qiyi.basecore.taskmanager.pool.ObjectPool;

public interface ITaskManagerConfig {
    int STRATEGY_POOL_EXECUTOR = 1;
    int STRATEGY_THREAD_GROUP = 2;

    ITaskManagerConfig enableObjectReuse(boolean b);

    ITaskManagerConfig setDefaultTimeOut(int timeOut);

    ITaskManagerConfig setLogTracker(ITracker logger);

    ITaskManagerConfig setIdleTaskOffset(int offset);

    ITaskManagerConfig setExceptionHandler(IException exceptionHandler);

    ITaskManagerConfig enableStrictModeCrash(boolean enable);

    ITaskManagerConfig enableFullLog(boolean enable);

    ITaskManagerConfig enableMemoryCleanUp(boolean enable);

    //default is 2000
    ITaskManagerConfig setLowTaskPriorityTaskMaxWaitTime(int timeInms);

    ITaskManagerConfig setWaitTimeCollectThreshold(long waitTimeCollectThreshold);

    /**
     * from 1.4.0 on : Thread pool only support STRATEGY_THREAD_GROUP
     * @param strategy
     * @return
     */
    @Deprecated
    ITaskManagerConfig setThreadPoolStrategy(int strategy);

    void initTaskManager(Application application);
}

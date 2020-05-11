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
package org.qiyi.basecore.taskmanager.config;

import android.app.Application;

import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.deliver.ITracker;
import org.qiyi.basecore.taskmanager.iface.IException;
import org.qiyi.basecore.taskmanager.iface.ITaskManagerConfig;
import org.qiyi.basecore.taskmanager.pool.ObjectPool;

public class TaskManagerConfig implements ITaskManagerConfig {

    private boolean enableDebugCrash;
    private int defaultTimeOut = 3000;
    private ITracker logger;
    private IException exceptionHandler;
    private int gradePerRate = 10;
    private long waitTimeCollectThreshold;
    private boolean memoryCleanUpEnabled;
    private boolean enableFullLog;
    private int idleTaskOffset = 50;// for idle task serial run ; ms

    public TaskManagerConfig setDefaultTimeOut(int timeOut) {
        defaultTimeOut = timeOut;
        return this;
    }

    /**
     * use setLogTracker instead
     *
     * @param logger
     * @return
     */
    @Deprecated
    public TaskManagerConfig setLogger(ITracker logger) {
        this.logger = logger;
        return this;
    }

    public TaskManagerConfig setLogTracker(ITracker logger) {
        this.logger = logger;
        return this;
    }


    public TaskManagerConfig setIdleTaskOffset(int offset) {
        idleTaskOffset = offset;
        return this;
    }

    public TaskManagerConfig setExceptionHandler(IException exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public TaskManagerConfig enableStrictModeCrash(boolean enable) {
        enableDebugCrash = enable;
        return this;
    }

    public TaskManagerConfig enableFullLog(boolean enable) {
        enableFullLog = enable;
        return this;
    }


    public TaskManagerConfig enableObjectReuse(boolean enable) {
        ObjectPool.enableDataPool(enable);
        return this;
    }

    public TaskManagerConfig enableMemoryCleanUp(boolean enable) {
        memoryCleanUpEnabled = enable;
        return this;
    }

    public ITracker getLogger() {
        return logger;
    }


    public IException getExceptionHandler() {
        return exceptionHandler;
    }


    public int getIdleTaskOffset() {
        return idleTaskOffset;
    }


    public int getDefaultTimeout() {
        return defaultTimeOut;
    }

    public boolean isDebugCrashEnabled() {
        return enableDebugCrash;
    }

    public boolean isMemoryCleanUpEnabled() {
        return memoryCleanUpEnabled;
    }

    public boolean isFullLogEnabled() {
        return enableFullLog;
    }

    //default is 2000
    public TaskManagerConfig setLowTaskPriorityTaskMaxWaitTime(int timeInms) {
        gradePerRate = timeInms / 200;
        return this;
    }

    public int getTaskPriorityGradePerTime() {
        return gradePerRate;
    }

    public long getWaitTimeCollectThreshold() {
        return waitTimeCollectThreshold;
    }

    public TaskManagerConfig setWaitTimeCollectThreshold(long waitTimeCollectThreshold) {
        this.waitTimeCollectThreshold = waitTimeCollectThreshold;
        return this;
    }

    @Deprecated
    public void commit() {
        TaskManager.setConfig(this);
    }

    public void initTaskManager(Application application) {
        TaskManager.setConfig(application, this);
    }

}

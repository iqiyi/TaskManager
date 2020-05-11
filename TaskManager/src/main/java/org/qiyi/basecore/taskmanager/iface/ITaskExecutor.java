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
package org.qiyi.basecore.taskmanager.iface;

import android.os.Handler;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskWrapper;

/**
 * 任务执行类
 */
public interface ITaskExecutor {

    /**
     * 直接执行在调用处
     */
    void executeDirect(Task task);

    /**
     * 任务post至主线程
     */
    void postToMainThread(TaskWrapper runnable);

    /**
     * 任务执行在子线程
     */
    void executeOnBackgroundThread(TaskWrapper runnable, int priority, int taskPriority);

    void dequeue(int priority);

    void workPostDelay(Runnable runnable, int time);

    //    TaskInfo findTaskById(int taskId);
    void bringToFront(int taskId);

    void dumpData();

    Handler getWorkHandler();

    boolean removeTaskByToken(Object token);

    boolean removeTask(int taskId);

    Handler getMainHandler();

    void setMaxRunningThreadCount(int max);

    int getCpuCount();

    void onGainThread();

    void trigger();

}

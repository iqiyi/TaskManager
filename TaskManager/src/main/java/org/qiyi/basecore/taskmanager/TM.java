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
package org.qiyi.basecore.taskmanager;

import android.os.Handler;

import org.qiyi.basecore.taskmanager.other.TMLog;
import org.qiyi.basecore.taskmanager.pool.ObjectPool;
import org.qiyi.basecore.taskmanager.struct.DataMaker;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class TM {


    private static TaskManager manager = TaskManager.getInstance();
    private static AtomicInteger eventId = new AtomicInteger(Task.TASKID_EVENT_RANGE);
    private static AtomicInteger groupId = new AtomicInteger(1);
    private static TMExecutor executor = new TMExecutor();
    static final int GROUP_ID_RANGE = 0x1 << 12;

    public static void cancelTaskByToken(Object token) {
        manager.cancelTaskByToken(token);
    }

    public static void cancelTaskById(int taskId) {
        manager.cancelTask(taskId);
    }

    public static void triggerEventTaskFinished(int taskID) {
        manager.triggerEventTaskFinished(taskID);
    }

    public static void triggerEvent(Object group, int event, Object data) {
        manager.triggerEvent(group, event, data);
    }

    public static void triggerEvent(int groupId, int event, Object data) {
        manager.triggerEvent(groupId, event, data);
    }

    public static void triggerEvent(int event) {
        manager.triggerEvent(event);
    }

    public static void triggerEvent(int event, Object data) {
        manager.triggerEvent(event, data);
    }

    public static void needTaskSyncWithTimeout(int taskId, int timeOutMillions) {
        manager.needTaskSyncWithTimeout(taskId, timeOutMillions);
    }

    public static void needTaskAsync(int taskId) {
        manager.needTaskAsync(taskId);
    }

    public static void needTaskSync(int taskId) {
        manager.needTaskSync(taskId);
    }

    public static void waitForTaskWithTimeout(int taskId, int timeOutMillions) {
        manager.waitForTaskWithTimeout(taskId, timeOutMillions);
    }

    public static void executeParallel(int timeout, Task... tasks) {
        manager.executeParallel(timeout, tasks);
    }

    public static void triggerEventTaskFinished(int taskId, Object var) {
        manager.triggerEventTaskFinished(taskId, var);
    }

    // 与直接执行的方法类似：将在当前线程中提供并发执行能力；如果当前线程是主线程 主线程可能等待
    public void executeParallel(Task... tasks) {
        manager.executeParallel(tasks);
    }


    public static Handler getWorkHandler() {
        return manager.getWorkHandler();
    }

    public static Handler getMainHandler() {
        return manager.getMainHandler();
    }


    //判断一个任务是否已经提交到TM： 如果任务已经执行完成 , 正在执行， 等待执行， 都会返回true
    public static boolean isTaskRegistered(int tid) {
        return manager.isTaskRegistered(tid);
    }

    public static int genNewEventId() {
        return eventId.incrementAndGet();
    }

    public static DataMaker createDataBuilder() {
        return new DataMaker();
    }

    public static short genGroupId() {
        // group ID 总共支持 0~ 0xfff;
        return (short) groupId.incrementAndGet();
    }

    public static int genGroupId(Object groupIdentity) {
        return TaskRecorder.generateGroupId(groupIdentity);
    }

    static int genEventIdbyGroup(int groupId, int event) {
        return 0x40000000 + (groupId << 16) + event;
    }


    public static void crashIf(boolean bl, String msg) {
        if (bl && TMLog.isDebug()) {
            throw new IllegalStateException(msg);
        }
    }

    public static void setMaxRunningThreadCount(int size) {
        manager.setMaxRunningThreadCount(size);
    }

    public static void postUI(Runnable runnable) {
        new RunnableTask(runnable).postUI();
    }


    public static void postUIDelay(Runnable runnable, int delay) {
        new RunnableTask(runnable).postUIDelay(delay);
    }


    public static void postAsyncDelay(Runnable runnable, int delay) {
        new RunnableTask(runnable).postAsyncDelay(delay);

    }

    public static void postAsync(Runnable runnable) {
        new RunnableTask(runnable).postAsync();
    }


    /**
     * @param enable
     */
    public static void enableObjectReuse(boolean enable) {
        ObjectPool.enableDataPool(enable);
    }

    /**
     * 无等待，在子线程中立即执行。如果有阻塞将创建新线程执行
     *
     * @param runnable
     */
    public static void executeAsyncNow(Runnable runnable) {
        new RunnableTask(runnable).executeAsyncNow();
    }

    public static boolean isFullLogEnabled() {
        return manager.isFullLogEnabled();
    }

    /**
     * 顺序执行任务替代handler
     */
    public static void postSerial(Runnable runnable, String groupName){
        if(runnable != null) {
            new RunnableTask(runnable).postSerial(groupName);
        }
    }

    public static Executor getExecutor(){
        return executor;
    }

}

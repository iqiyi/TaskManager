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

import android.os.Looper;

import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.other.TMLog;
import org.qiyi.basecore.taskmanager.pool.ObjectPool;
import org.qiyi.basecore.taskmanager.pool.RecycleObject;

import java.util.LinkedList;

/**
 * 将{@link Task}封装为可执行{@link }
 * 删除取消方法: 之前是给 并发任务, 任务taken 使用的; 任务执行状态可保证 不重复执行;
 */
public class TaskWrapper implements Runnable, Comparable<TaskWrapper>, RecycleObject {
    private static final String TAG = "TM_TaskWrapper";
    private Task mTaskRequest;
    private LinkedList<Task> pendingTasks;
    private int taskPriority;
    private long enqueueTime;
    private ITaskExecutor executor;


    TaskWrapper(Task taskRequest) {
        this.mTaskRequest = taskRequest;
        pendingTasks = new LinkedList<>();
    }

    TaskWrapper() {
    }

    public void set(Task taskRequest) {
        this.mTaskRequest = taskRequest;
        pendingTasks = new LinkedList<>();
    }

    protected void runTask() {
        //并发任务中 override  this and return index task

        Task task = mTaskRequest;//.getTask();
        if (task != null) {

            int stateCheck = task.compareAndSetState(Task.STATE_RUNNING);
            // this is to make sure the taken onse can run
            if (stateCheck < 0) {
                task.setWrapper(this);
                task.doBeforeTask();
                task.doTask();
                task.doAfterTask();

            } else {
                TMLog.e(TAG, task.getName() + "running state was changed , before run : task might be executed more than once" +
                        task.getTaskId()
                );
            }

        } else if (TM.isFullLogEnabled()) {
            TMLog.e(TAG, this + " task is null");
        }
    }

    @Override
    public void run() {
        //v10.7 support pending tasks
        //mTaskRequest.onTaskStateChange(taskId, Task.STATE_FINISHED); will call add pending task if some
        //this task request is already been dequeued form container in onTaskStateChange
//        DebugLog.d(TAG, this + " gain thread on run");
        if (executor != null) {
            executor.onGainThread();
        }
        do {
            runTask();
        } while ((mTaskRequest = fetchPendingRequest()) != null);

        // for execute direct run : needDequeue is false
        if (executor != null) {
            executor.dequeue(taskPriority);
        }

        ObjectPool.recycle(this);
    }


    //a current task
    public Task getTaskRequest() {
        return mTaskRequest;
    }

    @Override
    public String toString() {
        if (mTaskRequest != null) {
            return mTaskRequest.getName() + " " + mTaskRequest.getTaskId() + " " + super.toString();
        }
        return super.toString();
    }

    synchronized void addPendingTasks(LinkedList<Task> list) {
        if (list != null && !list.isEmpty()) {
            pendingTasks.addAll(list);
        }
    }

    synchronized void addPendingTask(Task request) {
        if (request != null) {
            pendingTasks.add(request);
        }
    }


    private synchronized Task fetchPendingRequest() {
        Task request = pendingTasks.poll();
        if (request != null) {
            onRequestChange();
        }
        return request;
    }

    protected void onRequestChange() {
        // empty
    }


    public int getTaskPriority() {
        return taskPriority;// return default
    }

    public void updateTaskPriority(int taskPriority){
        this.taskPriority = taskPriority;
    }

    public long getTaskEnqueueTime(){
        return enqueueTime;
    }

    public void enqueueMark(int taskPriority) {
        this.taskPriority = taskPriority;
        enqueueTime = System.currentTimeMillis();
    }


    public void setExecutor(ITaskExecutor executor) {
        this.executor = executor;
        if (mTaskRequest != null) {
            RunningThread mRunningThread = mTaskRequest.mRunningThread;
            int requestId = mTaskRequest.taskId;
            //「ui thread sync should not goes here 」
            if (isRunningOnMainThread(mRunningThread)) {
                if (Looper.getMainLooper() == Looper.myLooper() && mRunningThread == RunningThread.UI_THREAD_SYNC) {
                    run();
                } else {
                    executor.postToMainThread(this);
                }
            } else {
                executor.executeOnBackgroundThread(this, mTaskRequest.getThreadPriority(), mTaskRequest.getTaskPriority());
            }
        }
    }

    public boolean isRunningOnMainThread(RunningThread mRunningThread) {
        return mRunningThread == RunningThread.UI_THREAD
                || mRunningThread == RunningThread.UI_THREAD_SYNC;
    }


    @Override
    public int compareTo(TaskWrapper taskWrapper) {
        return taskWrapper.taskPriority - taskPriority;
    }


    public static TaskWrapper obtain(Task task) {
        TaskWrapper wrapper = ObjectPool.obtain(TaskWrapper.class);
        if (wrapper == null) {
            return new TaskWrapper(task);
        }
        wrapper.set(task);
        return wrapper;
    }


    @Override
    public void recycle() {
        mTaskRequest = null;
        pendingTasks = null;
        taskPriority = 0;
        enqueueTime = 0;
        executor = null;
    }
}

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


import org.qiyi.basecore.taskmanager.other.TMLog;
import org.qiyi.basecore.taskmanager.pool.ObjectPool;


/**
 * 用于运行并发任务的 wrapper
 */
class ParallelTaskWrapper extends TaskWrapper {
    private volatile int taskIndex;
    private ParallelRequest mTaskRequest;
    private final String TAG = "TM_ParallelTaskWrapper";
    private boolean takenRun;

    public ParallelTaskWrapper(ParallelRequest taskRequest, int index) {
        super(null);
        taskIndex = index;
        mTaskRequest = taskRequest;
    }

    @Override
    protected void runTask() {
        int taskId;
        do {

            taskId = taskIndex;
            //并发任务中 override  this and return index task
            Task task = mTaskRequest.getTaskAt(taskIndex);

            int stateCheck = task.compareAndSetState(Task.STATE_RUNNING);
            if (TM.isFullLogEnabled()) {
                TMLog.d("TaskManager", task.getName() + " in wrapper " + stateCheck + " " + takenRun + " " + mTaskRequest);
            }
            // this is to make sure the taken onse can run
            if (stateCheck < 0 || (takenRun && stateCheck == Task.STATE_RUNNING)) {
                takenRun = false;
                task.setWrapper(this);
                task.doBeforeTask();
                task.doTask();
                task.doAfterTask();
                mTaskRequest.onTaskStateChange(taskId, Task.STATE_FINISHED);
            } else {
                mTaskRequest.requestNextIdle(this);
                TMLog.d(TAG, "running state was changed , before run : task might be executed more than once");
            }

        } while (taskId != taskIndex); //taskIndex might be changed during call : mTaskRequest.onTaskStateChange(taskIndex, Task.STATE_FINISHED)

    }


    public void changeTask(int taskId) {
        taskIndex = taskId;
        takenRun = true;
        if (TM.isFullLogEnabled()) {
            TMLog.d(TAG, " launchD task>>> index is changed " + taskId + this);
        }
    }

    @Override
    protected void onRequestChange() {
        taskIndex = 0;
    }


    @Override
    public String toString() {
        if (mTaskRequest != null) {
            Task task = mTaskRequest.getTaskAt(taskIndex);
            if (task != null) {
                return task.getName() + " " + task.getTaskId() + super.toString();
            }
        }
        return super.toString();
    }

    public void set(ParallelRequest taskRequest, int index) {
        super.set(null);
        taskIndex = index;
        mTaskRequest = taskRequest;
    }


    public static ParallelTaskWrapper obtain(ParallelRequest taskRequest, int index) {
        ParallelTaskWrapper wrapper = ObjectPool.obtain(ParallelTaskWrapper.class);
        if (wrapper == null) {
            return new ParallelTaskWrapper(taskRequest, index);
        }
        wrapper.set(taskRequest, index);
        return wrapper;
    }

    @Override
    public void recycle() {
        super.recycle();
        mTaskRequest = null;
        taskIndex = 0;
        takenRun = false;
    }

}

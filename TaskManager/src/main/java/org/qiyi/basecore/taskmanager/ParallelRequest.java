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

import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.iface.ITaskExecutor;
import org.qiyi.basecore.taskmanager.other.TMLog;

class ParallelRequest {
    private TaskWrapper syncTaskWrapper;
    private ParallelStateLatch stateLatch;
    private Task[] mTasks;
    private int mSize;
    private static final String TAG = "TM_ParallelRequest";
    protected static final int DEFAULT_PARALLEL_TIMEOUT = 3000;
    private int parallelTimeOut = DEFAULT_PARALLEL_TIMEOUT;
    private int requestId;
    private String taskName;

    public ParallelRequest(String name, Task[] array) {

        mTasks = array;
        mSize = getTaskSize();

        if (name == null && mSize > 0 && array[0] != null) {
            taskName = array[0].getName();
        } else {
            taskName = name;
        }

        if (taskName == null) {
            taskName = "";
        }

        stateLatch = new ParallelStateLatch(array);
        if (mSize > 0) {
            requestId = getTaskAt(0).taskId;
        }
    }


    public ParallelRequest(Task[] array) {
        this(null, array);
    }

    //[任务的取消，没有采用去移除线程池中的队列 而是用task wrapper cancel 的方式执行]
    public void setExecutor(ITaskExecutor executor) {
        int size = getTaskSize();
        if (mTasks != null && size > 0) {
            for (int i = size - 1; i >= 0; i--) {
                Task task = mTasks[i];
                if (task != null) {
                    //[do task : ]
                    ParallelTaskWrapper wrapper = ParallelTaskWrapper.obtain(this, i);
                    wrapper.setExecutor(executor);
                    task.setWrapper(wrapper);
                    if (i == 0) {
                        syncTaskWrapper = wrapper;
                        wrapper.run();
                    } else {
                        executor.executeOnBackgroundThread(wrapper, ThreadPriority.FLEXIBLE, task.getTaskPriority());
                    }
                }
            }
        }
    }

    public int getTaskSize() {
        if (mTasks != null) {
            return mTasks.length;
        }
        return 0;
    }

    public Task getTaskAt(int index) {
        int count = getTaskSize();
        if (index < count) {
            return mTasks[index];
        }
        return null;
    }


    private String getTaskName() {
        return taskName;
    }


    void onTaskStateChange(int index, int newState) {
        try {
            if (newState == Task.STATE_FINISHED) {
                stateLatch.onTaskFinished(index);
                ParallelTaskWrapper wrapper = getTaskWrapper(index);
                if (wrapper == null) {
                    TaskManagerDeliverHelper.trackCritical("TaskWrapper of this task is null : " + index, getTaskName());
                    return;
                } else if (requestNextIdle(wrapper)) {
                    TaskManagerDeliverHelper.trackCritical("Parallel task one finish: call run next idle success");
                    return;
                }
                //[no task to run : to check if need sync wait]
                //TaskWrapper wrapper = getTaskWrapper(index);
                if (wrapper == syncTaskWrapper || syncTaskWrapper == null) { // wait for unfinished
                    if (!stateLatch.isAllTaskFinished()) {
                        String var = "wait task to finish time out: " + parallelTimeOut;
                        TMLog.e(TAG, var);
                        TaskManagerDeliverHelper.trackCritical(var);
                        long sys = System.currentTimeMillis();
                        stateLatch.waitForUnfinished(parallelTimeOut);
                        TMLog.e(TAG, "wait for : " + (System.currentTimeMillis() - sys) + taskName);
                    }
                    if (getTaskSize() > 1) {
                        String taskName = getTaskName();
                        TaskManagerDeliverHelper.trackCritical("Parallel task is done ", taskName);
                    }
                }

            }
        } catch (Exception e) {
            // add criticalLog for bug
            if (TMLog.isDebug()) {
                throw e;
            }
            StackTraceElement[] traceElements = e.getStackTrace();
            if (traceElements != null && traceElements.length > 0) {
                TaskManagerDeliverHelper.trackCritical("crash stack[0] ", traceElements[0].toString());
            }
            Task task = getTask();
            if (task != null) {
                TaskManagerDeliverHelper.trackCritical("crashed ", task.getName(), e.toString());
            } else {
                TaskManagerDeliverHelper.trackCritical("crashed ", e.toString());
            }
        }
    }


    /**
     * 是否有下一个可执行的任务
     *
     * @param wrapper
     * @return
     */
    boolean requestNextIdle(ParallelTaskWrapper wrapper) {
        if (wrapper != null) {
            int nextIndex = stateLatch.getIdleTaskAndCancel();
            if (nextIndex > 0) {
                wrapper.changeTask(nextIndex);
                return true;
            }
        }
        return false;
    }


    private void baseAppendRequestInfo(StringBuilder sb) {
        sb.append("state:")
                .append("\ntaskName:").append(taskName)
                .append("\nrequestId:").append(requestId);
    }


    protected void appendRequestInfo(StringBuilder sb) {
        baseAppendRequestInfo(sb);
        sb.append("\nsyncTaskWrapper:").append(syncTaskWrapper)
                .append("\nstateLatch:").append(stateLatch)
                .append("\nrequestId:").append(requestId);
    }

    private ParallelTaskWrapper getTaskWrapper(int index) {
        Task currentTask = getTaskAt(index);
        return (ParallelTaskWrapper) currentTask.getTaskWrapper();
    }


    public void setParallelTimeOut(int timeout) {
        parallelTimeOut = timeout;
    }

    public Task getTask() {
        if (mSize > 0) {
            return mTasks[0];
        }
        return null;
    }

}

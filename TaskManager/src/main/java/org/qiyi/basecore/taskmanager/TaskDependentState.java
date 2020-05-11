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

import androidx.annotation.NonNull;

// 为了避免同一个Task 被多次执行的错误，不使用计数方式来控制
class TaskDependentState {
    private int[] mTaskStates;
    private int mSize;
    @NonNull
    int[] taskIds;

    public TaskDependentState(int size, @NonNull int[] dependantIds) {
        mTaskStates = new int[size];
        mSize = size;
        taskIds = dependantIds;
    }


    //whether task is loaded for more than once , leaves TaskRequest to check state;
    // if returns true: task can be post to execute now
    boolean onTaskFinished(int taskId) {
        int count = 0;
        synchronized (this) {
            for (int i = 0; i < mSize; i++) {
                if (mTaskStates[i] != 1) {
                    if (taskId == taskIds[i]) {
                        mTaskStates[i] = 1;
                        count++;
                    }
                } else {
                    count++;
                }
            }
        }
        return count == mSize;
    }


    // 不支持在任务提交后，还添加依赖；
    // not synchronized here : as we dont support change dependency after task run
    public void addDependant(int taskId) {
        int size = mSize++;
        mTaskStates = new int[mSize];
        int nTasks[] = new int[mSize];
        System.arraycopy(taskIds, 0, nTasks, 0, size);
        nTasks[size] = taskId;
        taskIds = nTasks;
    }


}

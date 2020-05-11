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


import org.qiyi.basecore.taskmanager.other.ExceptionUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class ParallelStateLatch {
    private int size;
    private CountDownLatch syncLatch;
    private Task[] mTasks;

    ParallelStateLatch(Task[] tasks) {
        this.size = tasks == null ? 0 : tasks.length;
        syncLatch = new CountDownLatch(size);
        mTasks = tasks;

    }

    int getIdleTaskAndCancel() {
        if (size > 1) {
            for (int i = 1; i < size; i++) {
                if (mTasks[i].taskState == Task.STATE_IDLE) {
                    if (mTasks[i].compareAndSetState(Task.STATE_RUNNING) < 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }


    //return -1 if set success, else fail;
    int compareAndSetState(int index, int newState) {

        int var = mTasks[index].compareAndSetState(newState);
        if (newState == Task.STATE_FINISHED) {
            syncLatch.countDown();
        }
        return var;
    }


    void onTaskFinished(int index) {
        syncLatch.countDown();
    }


    boolean isAllTaskFinished() {
        return syncLatch.getCount() == 0;
    }

    void waitForUnfinished(int timeOut) {
        //[针对并发执行的情况： 主线程已经执行完成，但是其他线程的正在执行，并且没有执行完成]
        try {
            if (timeOut < 0) {
                syncLatch.await();
            } else {
                syncLatch.await(timeOut, TimeUnit.MILLISECONDS);
            }

        } catch (InterruptedException e) {
            ExceptionUtils.printStackTrace(e);
        }
    }

    @Override
    public String toString() {
        if (size > 1) {

            StringBuilder builder = new StringBuilder();
            builder.append('[');
            int p = 0;
            while (p < size) {
                builder.append(mTasks[p++].taskState);
                builder.append(' ');
            }
            builder.append(']');
            builder.append(System.identityHashCode(this));
            return builder.toString();

        } else if (size == 1) {
            return mTasks[0].taskState + " " + System.identityHashCode(this);
        } else {
            return " []" + System.identityHashCode(this);
        }

    }
}

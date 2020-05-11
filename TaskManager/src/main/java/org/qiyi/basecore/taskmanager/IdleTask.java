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

import androidx.annotation.UiThread;

import org.qiyi.basecore.taskmanager.impl.model.TaskContainer;
import org.qiyi.basecore.taskmanager.other.TMLog;

/**
 * call :
 * setRunningThread() to setup running thread;
 * postPending() to post task.
 * <p>
 * 1) for UI thread Task: call updateIdelOffset &  TaskManager.getInstance().triggerIdleRun (in UI thread)
 * 2) for background thread: will run automatically
 */
public abstract class IdleTask extends Task {
    long idleRunLimit = Long.MAX_VALUE;
    private static final String TAG = "TM_IdleTask";

    public IdleTask(String name) {
        super(name);
        enableIdleRun();
    }

    public IdleTask() {
        super();
        enableIdleRun();
    }

    public IdleTask(int tid) {
        super(tid);
        enableIdleRun();
    }


    public IdleTask(String name, int tid) {
        super(name, tid);
        enableIdleRun();
    }

    //default is 0 : not constrain is set
    public boolean runIfIdle() {
        boolean idle = idleRunLimit > System.currentTimeMillis();
        if (TM.isFullLogEnabled()) {
            TMLog.d(TAG, getName() + "run if idle ? " + idle);
        }
        return idle;
    }

    /**
     * if is running on UI thread,  may fetch next idle task to to run:
     */
    @Override
    public void doAfterTask() {
        super.doAfterTask();
        TaskWrapper wrapper = getTaskWrapper();
        if (wrapper != null && Looper.myLooper() == Looper.getMainLooper() && runIfIdle()) {
            Task request = TaskContainer.getInstance().offerTaskInIdleState(false);
            if (request != null) {
                wrapper.addPendingTask(request);
            }
        }
    }

    @UiThread
    public void updateIdleOffset(int offset) {
        if (offset == 0) {
            idleRunLimit = Long.MAX_VALUE;
        } else {
            idleRunLimit = System.currentTimeMillis() + offset;
        }
        TMLog.d(TAG, "set idleTask offset " + idleRunLimit);
    }

    @Override
    public void postPending() {
        if (taskState == STATE_IDLE) {
            // when call need Task; in case of running more than once
            setDelay(Integer.MAX_VALUE);
            TaskManager.getInstance().enqueue(this);
        }
    }
}

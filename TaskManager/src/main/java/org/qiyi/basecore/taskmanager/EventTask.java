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

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.qiyi.basecore.taskmanager.other.TMLog;

/**
 * register event and watch event happening
 */
public abstract class EventTask extends Job {
    private int eventIds[];
    // 0 :  不关心线程, 1: UI 线程  2: back线程
    private RunningThread preferredThread;

    public abstract void onEvent(int eventId, Object msg);

    /**
     * @param eventIds : global events ( ids declaired in xml or use TM.genNewEventId)
     * @return
     */
    public EventTask registerEvents(int... eventIds) {

        if (eventIds == null) {
            return this;
        }
        // change event ids
        if (TMLog.isDebug()) {
            for (int i : eventIds) {
                if (groupId == 0) {
                    // should use global event ids
                    TM.crashIf(i < Task.TASKID_SELF_DEFINE_EVENT_RANGE, "you should call setGroup , while you has self defined event ids. Call registerGroupedEvents instead ");
                } else {
                    TM.crashIf(i > Task.TASKID_SELF_DEFINE_EVENT_RANGE, " self defined event ids range form 0 ~ N < 0xffff");
                }
            }
        }

        if (groupId != 0) {
            int len = eventIds.length;
            for (int i = 0; i < len; i++) {
                // 自定义 event ID 生成  0x X XXX XXXX
                if (eventIds[i] < Task.TASKID_SELF_DEFINE_EVENT_RANGE) {
                    TM.crashIf(groupId > TM.GROUP_ID_RANGE, "group id should be < 0xffff");
                    eventIds[i] = TM.genEventIdbyGroup(groupId, eventIds[i]);
                }
            }
        }

        this.eventIds = eventIds;
        TaskRecorder.registerTaskEventRelations(taskId, eventIds);
        if (eventIds.length > 0) {
            for (int i : eventIds) {
                TaskRecorder.addEventSuccessForTask(this, i);
            }

            TaskRecorder.attachEventTask(this);
        }
        return this;
    }

    public EventTask registerGroupedEvents(Object groupIdentity, int... eventIds) {
        setGroupObject(groupIdentity);
        registerEvents(eventIds);
        return this;
    }

    // CallBack 仍然是支持多次回调的。
    public int generateEventId() {
        int eventId = TM.genNewEventId();
        this.eventIds = new int[]{eventId};
        TaskRecorder.registerTaskEventRelations(taskId, eventIds);
        TaskRecorder.addEventSuccessForTask(this, eventId);
        return eventId;
    }

    public EventTask bind(Context context) {
        int bindHash = TaskRecorder.bindTask(context, taskId);
        if (bindHash < 0) {
            bindHash = 0;
        }
        bindActivityHash = bindHash;
        return this;
    }

    public void unregister() {
        int ar[];
        synchronized (this) {
            if (eventIds == null) {
                return;
            }
            ar = eventIds;
            eventIds = null;
        }
        for (int i : ar) {
            TaskRecorder.removeEventSuccessorForTask(this, i);
        }
        TaskRecorder.detachEventTask(this);
    }

    public void unregisterEvent(int eventId) {
        if (TaskRecorder.removeEventSuccessorForTask(this, eventId)) {
            // if is empty
            TaskRecorder.detachEventTask(this);
        }
    }

    Task onDependantTaskFinished(@Nullable Task finishedTask, int taskId) {
        if (preferredThread == null || isUIThread() == (preferredThread == RunningThread.UI_THREAD)) {
            handleEvent(taskId);
        } else if (preferredThread == RunningThread.UI_THREAD) {
            CallBackTask t = new CallBackTask(taskId);
            t.passData(taskId, getData(taskId));
            t.postUI();
        } else {
            CallBackTask t = new CallBackTask(taskId);
            t.passData(taskId, getData(taskId));
            t.postAsync();
        }
        return null;
    }


    @Override
    public void post() {
        checkEventId();
        preferredThread = null;
    }

    @Override
    public void postUI() {
        checkEventId();
        preferredThread = RunningThread.UI_THREAD;
    }

    @Override
    public void postAsync() {
        checkEventId();
        preferredThread = RunningThread.BACKGROUND_THREAD;
    }

    private void checkEventId() {
        if (TMLog.isDebug()) {
            if (eventIds == null) {
                throw new IllegalStateException("plz call registerEvents(int ...) or generateEventId before task post ");
            }
        }
    }

    // call super for chain invoke

    @Override
    public EventTask setGroupObject(Object gid) {
        TM.crashIf(gid instanceof Integer || gid instanceof Long || gid instanceof Short,
                "please don't use Long , Integer, Short as Group Object identifier ");
        super.setGroupObject(gid);
        if (eventIds != null && TMLog.isDebug()) {
            throw new IllegalStateException("should call set group before register events");
        }
        return this;
    }

    @Override
    public EventTask setGroupId(short gid) {
        super.setGroupId(gid);
        if (eventIds != null && TMLog.isDebug()) {
            throw new IllegalStateException("should call set group before register events");
        }
        return this;
    }


    @Override
    public EventTask setName(String name) {
        super.setName(name);
        return this;
    }


    @Override
    public EventTask setTaskID(int tid) {
        super.setTaskID(tid);
        return this;
    }


    @Override
    public EventTask setTaskPriority(int priority) {
        super.setTaskPriority(priority);
        return this;
    }


    // end for chain invoke]
    class CallBackTask extends Task {
        int tid;

        public CallBackTask(int id) {
            tid = id;
        }

        @Override
        public void doTask() {
            handleEvent(tid);
        }
    }


    private void handleEvent(int tid) {
        //decode self defined event id
        if (EventTask.this.groupId > 0 && (tid >> 28 == 4)) {
            onEvent(tid & 0xffff, getData(tid));
        } else {
            onEvent(tid, getData(tid));
        }
    }
}

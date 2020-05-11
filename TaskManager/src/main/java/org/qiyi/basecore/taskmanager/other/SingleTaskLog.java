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
package org.qiyi.basecore.taskmanager.other;

import org.qiyi.basecore.taskmanager.Task;

/**
 * 用于排查某一个task 任务的执行流程
 * 支持任务流程调试功能: 打印设定的任务 的执行流程；
 * 支持设置group id ; 或者任务id
 */
public class SingleTaskLog {
    private static int taskId;
    private static int groupId;

    public static synchronized void setTaskId(int tid) {
        taskId = tid;
    }

    public static synchronized void setTaskId(int tid, int gid) {
        taskId = tid;
        groupId = gid;
    }

    public static void print(int tid, String msg) {
        if (tid == taskId) {
            TMLog.d("taskPrint", msg);
        }
    }

    public static void print(Task task, String msg) {

        if (taskId > 0) { //
            if (taskId == task.getTaskId()) {
                TMLog.d("taskPrint", msg);
            }
        } else if (task.getGroupId() == groupId) {
            TMLog.d("taskPrint", msg);
        }
    }


}

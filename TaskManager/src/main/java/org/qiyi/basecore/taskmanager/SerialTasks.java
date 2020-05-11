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

import java.util.LinkedList;

/**
 * task runs in one thread , on by one
 */
public final class SerialTasks extends Task {
    LinkedList<Task> tasks = new LinkedList<>();

    public SerialTasks addSubTask(Task task) {
        tasks.add(task);
        return this;
    }

    public SerialTasks addSubTasks(LinkedList<Task> list) {

        if (list != null && !list.isEmpty()) {
            tasks.addAll(list);
        }
        return this;
    }

    public SerialTasks addSubTasks(Task[] list) {

        if (list != null) {
            for (Task t : list) {
                tasks.add(t);
            }
        }
        return this;
    }

    @Override
    public void doTask() {
        if (!tasks.isEmpty()) {

            for (Task t : tasks) {
                t.executeSyncCurrentThread();
            }
        }

    }
}

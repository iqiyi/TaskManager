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

public class ParallelTask {


    private LinkedList<Task> tasks = new LinkedList<>();
    private int timeOut;
    private TaskResultCallback resultCallback;
    private boolean callBackOnUIThread;

    public ParallelTask addSubTask(Runnable runnable) {
        tasks.add(new RunnableTask(runnable));
        return this;
    }

    public ParallelTask addSubTask(Task runnable) {
        tasks.add(runnable);
        return this;
    }


    public ParallelTask addSubTask(Runnable runnable, String name) {
        tasks.add(new RunnableTask(runnable, name));
        return this;
    }


    public ParallelTask addSubTasks(Task[] tasks) {

        if (tasks != null) {
            for (Task t : tasks) {
                this.tasks.add(t);
            }
        }
        return this;
    }

    public ParallelTask addSubTasks(LinkedList<Task> list) {
        if (list != null && !list.isEmpty()) {
            this.tasks.addAll(list);
        }
        return this;
    }

    public ParallelTask setTimeOut(int timeOut) {
        this.timeOut = timeOut;
        return this;
    }

    private int getIndexByTask(Task task) {
        if (tasks != null && !tasks.isEmpty()) {
            return tasks.indexOf(task);
        }
        return -1;
    }

    public void execute() {
        if (!tasks.isEmpty()) {
            int size = tasks.size();
            Task ar[] = new Task[size];
            int p = 0;

            Task.TaskResultCallback tr = null;
            if (resultCallback != null) {
                tr = new Task.TaskResultCallback() {
                    @Override
                    public void onResultCallback(Task task, Object var) {
                        resultCallback.onResult(task, var, getIndexByTask(task));
                    }
                };
            }
            for (Task task : tasks) {
                task.setCallBackOnFinished(tr, callBackOnUIThread);
                ar[p++] = task;
            }

            if (timeOut != 0) {
                TaskManager.getInstance().executeParallel(timeOut, ar);
            } else {
                TaskManager.getInstance().executeParallel(ar);
            }
        }
    }

    public int getSubTaskCount() {
        return tasks == null ? 0 : tasks.size();
    }

    public ParallelTask setCallBackOnSubTaskFinished(TaskResultCallback resultCallback) {
        this.resultCallback = resultCallback;
        return this;

    }

    public ParallelTask setCallBackOnSubTaskFinished(TaskResultCallback resultCallback, boolean ui) {
        this.resultCallback = resultCallback;
        callBackOnUIThread = ui;
        return this;
    }

    public interface TaskResultCallback {
        void onResult(Task task, Object var, int index);
    }

}

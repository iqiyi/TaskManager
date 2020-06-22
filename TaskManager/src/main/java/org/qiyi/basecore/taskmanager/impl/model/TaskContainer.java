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
package org.qiyi.basecore.taskmanager.impl.model;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.TaskRecorder;
import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.dump.TMDump;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 这个容器只是用来保存等待执行的，等待条件执行的任务
 * A vessel to hold all pending tasks to run;
 */
public class TaskContainer implements Container {
    private static final String TAG = "TManager_TaskContainer";
    private final LinkedList<Task> mTable = new LinkedList<>();
    private static volatile TaskContainer instance;
    private final static HashMap<String, LinkedList<Task>> serialMap = new HashMap<>();

    public static TaskContainer getInstance() {
        if (instance == null) {
            synchronized (TaskContainer.class) {
                if (instance == null) {
                    instance = new TaskContainer();
                }
            }
        }
        return instance;
    }

    private TaskContainer() {
    }


    @TMDump
    public String _dump() {
        StringBuilder sb = new StringBuilder("TaskContainer#mTable\n-\n");
        synchronized (this) {
            for (Task taskRequest : mTable) {
                if (taskRequest != null) {
                    sb.append(taskRequest).append("\n-\n");
                }
            }
        }
        return sb.toString();
    }

    public void dump2Track() {
        StringBuilder sb = new StringBuilder();
        synchronized (this) {
            for (Task taskRequest : mTable) {
                if (taskRequest != null) {
                    sb.append(taskRequest).append("\n-\n");
                }
            }
        }
        TaskManagerDeliverHelper.track("TaskContainer#mTable\n-\n", sb);
    }

    @Override
    public synchronized boolean add(Task taskRequest) {
        if (!mTable.contains(taskRequest)) {
            mTable.addLast(taskRequest);
            return true;
        } else if (taskRequest.getTaskId() >= Task.TASKID_RES_RANGE && TMLog.isDebug() && TaskManager.isDebugCrashEnabled()) {
            String name = taskRequest.getName();
            throw new IllegalStateException("Task has already been submitted in queue " + name + " " + taskRequest.getTaskId());
        }
        return false;
    }

    @Override
    public synchronized void add(List<? extends Task> taskRequestList) {
        if (taskRequestList != null && taskRequestList.size() > 0) {
            for (Task taskRequest : taskRequestList) {
                if (!mTable.contains(taskRequest)) {
                    mTable.addLast(taskRequest);
                }
            }
        }
    }

    @Override
    public synchronized boolean contains(Task request) {
        return mTable.contains(request);
    }

    @Override
    public synchronized boolean remove(Task taskRequest) {
        if (mTable.remove(taskRequest)) {
            TaskRecorder.enqueue(taskRequest);
            return true;
        }
        return false;
    }


    public synchronized boolean cancelTask(Task taskRequest) {
        if (mTable.remove(taskRequest)) {
            return true;
        }
        return false;
    }

    public boolean cancelTaskByTaskId(int taskId) {
        boolean canceled = false;


        synchronized (this) {
            Iterator<Task> iterator = mTable.iterator();
            while (iterator.hasNext()) {
                Task request = iterator.next();
                if (request != null && request.getTaskId() == taskId) {
                    request.cancel();
                    iterator.remove();
                    canceled = true;
                }
            }

        }

        LinkedList<LinkedList<Task>> lists = new LinkedList<>();
        synchronized (serialMap) {
            lists.addAll(serialMap.values());
        }

        if (!lists.isEmpty()) {
            for (LinkedList<Task> list : lists) {
                synchronized (list) {
                    // remove item by token
                    Iterator<Task> iterator = list.iterator();
                    while (iterator.hasNext()) {
                        Task task = iterator.next();
                        if (task.getTaskId() == taskId) {
                            iterator.remove();
                            canceled = true;
                        }
                    }
                }
            }
        }


        return canceled;
    }


    //不包含重复任务， 因此找到后就返回;但是token 是可能重复的一系列任务; 因此全遍历
    public boolean cancelTaskByToken(Object token) {

        boolean removed = false;
        synchronized (this) {
            Iterator<Task> iterator = mTable.iterator();
            while (iterator.hasNext()) {
                Task request = iterator.next();
                if (request.getToken() == token) {
                    request.cancel();
                    iterator.remove();
                    removed = true;
                }
            }
        }

        LinkedList<LinkedList<Task>> lists = new LinkedList<>();
        synchronized (serialMap) {
            lists.addAll(serialMap.values());
        }

        if (!lists.isEmpty()) {
            for (LinkedList<Task> list : lists) {
                synchronized (list) {
                    // remove item by token
                    Iterator<Task> iterator = list.iterator();
                    while (iterator.hasNext()) {
                        Task task = iterator.next();
                        if (task.getToken() == token) {
                            iterator.remove();
                            removed = true;
                        }
                    }
                }
            }
        }
        return removed;
    }


    @Override
    public synchronized int size() {
        return mTable.size();
    }

    @Override
    public synchronized void clear() {
        mTable.clear();
    }


    /**
     * 如果依赖没有完成的，不能idle 运行：可以通过使用post delay 配合enable idle 来使用。
     *
     * @param isBgTask
     * @return
     */
    @Override
    public Task offerTaskInIdleState(boolean isBgTask) {
        if (mTable.size() > 0) {
            synchronized (this) {

                if (mTable.isEmpty()) return null;

                Iterator<Task> iterator = mTable.iterator();
                while (iterator.hasNext()) {
                    Task request = iterator.next();
                    //将异步任务 && 非peimits 的情况的 && 没有依赖的任务的
                    //case : isBgTask true : task on UI thread false : else isBG false : Task ONUI Thread true : then : isBG != isOnUIThread
                    if (request.isIdleRunEnabled() && (isBgTask != request.getRunningThread().isRunningInUIThread())
                            && request.isDependentsComplete()) {
                        iterator.remove();
                        return request;
                    }
                }
            }
        }
        return null;
    }


    public Task findTaskById(int taskId) {
        synchronized (this) {
            for (Task request : mTable) {
                if (request.getTaskId() == taskId) {

                    return request;
                }
            }
        }
        return null;
    }


    public synchronized void remove(int taskId) {
        Task request = null;
        Iterator<Task> iter = mTable.iterator();
        while (iter.hasNext()) {
            request = iter.next();
            if (request.getTaskId() == taskId) {
                iter.remove();
                TaskRecorder.enqueue(request);
                break;
            }
        }

    }

    /**
     * @param task: if is empty , return false; else return true & add data pending
     */
    public boolean enqueueSerialTask(Task task) {
        String name = task.getSerialGroupName();
        if (name != null && name.length() > 0) {
            LinkedList<Task> list;
            synchronized (serialMap) {
                list = serialMap.get(name);
                if (list == null) {
                    list = new LinkedList<>();
                    serialMap.put(name, list);
                }
            }

            synchronized (list) {
                list.add(task);
                return list.size() != 1;
            }

        }

        return false;
    }


    public Task pollSerialTask(String name) {
        if (name != null && name.length() > 0) {
            LinkedList<Task> list;
            synchronized (serialMap) {
                list = serialMap.get(name);
                if (list == null) {
                    return null;
                }
            }

            synchronized (list) {
                list.poll();
                if (!list.isEmpty()) {
                    return list.peek();
                }
            }
        }

        return null;
    }
}

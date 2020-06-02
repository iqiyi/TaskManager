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
package org.qiyi.basecore.taskmanager.threadpool;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.TaskWrapper;
import org.qiyi.basecore.taskmanager.deliver.TaskManagerDeliverHelper;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * 使用动态优先级，如果任务存在的时间越久，那么优先级越高
 */
public class TaskBlockingQueue {
    private final static String TAG = "TM_TaskBlockingQueue";
    private final PriorityQueue<TaskWrapper> highPriorityRejectedQueue = new PriorityQueue<>();
    //store default priority
    private final LinkedList<TaskWrapper> normalPriorityRejectedQueue = new LinkedList<>();
    //store low priority
    private final PriorityQueue<TaskWrapper> lowPriorityRejectedQueue = new PriorityQueue<>();
    private int TIME_TO_GRADE = 10; // MAX WAIT (100+ 100 ) * 10 ms


    public TaskBlockingQueue() {
        if (TaskManager.getTaskManagerConfig() != null) {
            TIME_TO_GRADE = TaskManager.getTaskManagerConfig().getTaskPriorityGradePerTime();
            if (TIME_TO_GRADE == 0) {
                TIME_TO_GRADE = 10;
            }
        }
    }

    public synchronized int size() {
        return highPriorityRejectedQueue.size() + lowPriorityRejectedQueue.size() + normalPriorityRejectedQueue.size();
    }

    //[poll hight prio]
    synchronized TaskWrapper pollFirst() {

        TaskWrapper wrapperA = highPriorityRejectedQueue.isEmpty() ? null : highPriorityRejectedQueue.peek();
        TaskWrapper wrapperB = normalPriorityRejectedQueue.isEmpty() ? null : normalPriorityRejectedQueue.peekFirst();
        TaskWrapper wrapper = comparePriority(wrapperA, wrapperB);
        if (wrapper == null) {  // try C
            return lowPriorityRejectedQueue.poll();
        } else {
            TaskWrapper wrapperC = lowPriorityRejectedQueue.isEmpty() ? null : lowPriorityRejectedQueue.peek();
            wrapper = comparePriority(wrapper, wrapperC);
            if (wrapper == null) {
                return null;
            } else if (wrapper == wrapperA) {
                return highPriorityRejectedQueue.poll();
            } else if (wrapper == wrapperB) {
                return normalPriorityRejectedQueue.pollFirst();
            } else {
                return lowPriorityRejectedQueue.poll();
            }
        }
    }

    private TaskWrapper comparePriority(TaskWrapper a, TaskWrapper b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            int var = b.getTaskPriority() - a.getTaskPriority();
            int bvar = (int) ((a.getTaskEnqueueTime() - b.getTaskEnqueueTime()) / TIME_TO_GRADE);
            if (var + bvar > 0) {
                return b;
            } else {
                return a;
            }
        }
    }


    synchronized boolean isEmpty() {
        return highPriorityRejectedQueue.isEmpty() &&
                normalPriorityRejectedQueue.isEmpty() &&
                lowPriorityRejectedQueue.isEmpty();
    }


    /**
     * 将等待的任务添加到队列中
     *
     * @param runnable
     * @param priority
     */
    final synchronized void addLast(TaskWrapper runnable, int priority) {
        if (priority == 0) {
            synchronized (normalPriorityRejectedQueue) {
                normalPriorityRejectedQueue.addLast(runnable);
            }
        } else if (priority > 0) {
            synchronized (highPriorityRejectedQueue) {
                highPriorityRejectedQueue.add(runnable);
            }
        } else {
            synchronized (lowPriorityRejectedQueue) {
                lowPriorityRejectedQueue.add(runnable);
            }
        }


    }


    /**
     * this is called by TM.needTaskAsync:  its not used quiye often.
     * when its called : task will bring to front of the queue , so that it can run faster;
     *
     * @param rejectedQueue
     * @param taskId
     * @return
     */
    private synchronized boolean bringToFront(Collection<TaskWrapper> rejectedQueue, int taskId) {
        if (rejectedQueue.size() > 0) {
            Iterator<TaskWrapper> iter = rejectedQueue.iterator();
            TaskWrapper foundRequest = null;
            while (iter.hasNext()) {
                TaskWrapper runnable = iter.next();
                Task request = runnable.getTaskRequest();
                if (request != null && request.getTaskId() != taskId) {
                    foundRequest = runnable;
                    iter.remove();
                    break;
                }
            }
            if (foundRequest != null) {
                foundRequest.updateTaskPriority(Task.TASK_PRIORITY_MAX);
                if (TM.isFullLogEnabled()) {
                    TMLog.d(TAG, "needTaskAsync Task " + taskId + " has been made a hight priority");
                }
                rejectedQueue.add(foundRequest);
            }
        }
        return false;
    }


    // change priority of this task ID
    void bringToFront(int taskId) {

        if (bringToFront(normalPriorityRejectedQueue, taskId)) {

        } else if (bringToFront(highPriorityRejectedQueue, taskId)) {

        } else {
            bringToFront(lowPriorityRejectedQueue, taskId);
        }
    }

    public void printTasks() {
        if (!highPriorityRejectedQueue.isEmpty()) {
            TaskManagerDeliverHelper.track(highPriorityRejectedQueue);
        }

        if (!normalPriorityRejectedQueue.isEmpty()) {
            TaskManagerDeliverHelper.track(normalPriorityRejectedQueue);
        }

        if (!lowPriorityRejectedQueue.isEmpty()) {
            TaskManagerDeliverHelper.track(lowPriorityRejectedQueue);
        }

    }


    public boolean removeTaskById(int id) {
        return
                removeTaskById(normalPriorityRejectedQueue, id) ||
                        removeTaskById(highPriorityRejectedQueue, id) ||
                        removeTaskById(lowPriorityRejectedQueue, id);
    }


    public boolean removeTaskByToken(Object token) {

        return removeTaskByToken(normalPriorityRejectedQueue, token) ||
                removeTaskByToken(highPriorityRejectedQueue, token) ||
                removeTaskByToken(lowPriorityRejectedQueue, token);
    }


    private synchronized boolean removeTaskById(Collection<TaskWrapper> rejectedQueue, int taskId) {
        if (rejectedQueue.size() > 0) {
            Iterator<TaskWrapper> iter = rejectedQueue.iterator();
            while (iter.hasNext()) {
                TaskWrapper runnable = iter.next();
                Task request = runnable.getTaskRequest();
                if (request != null && request.getTaskId() == taskId) {
                    iter.remove();
                }
            }
        }
        return false;
    }


    private synchronized boolean removeTaskByToken(Collection<TaskWrapper> rejectedQueue, Object token) {
        int size = rejectedQueue.size();
        if (size > 0) {
            Iterator<TaskWrapper> iter = rejectedQueue.iterator();
            while (iter.hasNext()) {
                TaskWrapper runnable = iter.next();
                Task request = runnable.getTaskRequest();
                if (request != null && request.getToken() == token) {
                    iter.remove();
                }
            }
        }
        return size != rejectedQueue.size();
    }
}

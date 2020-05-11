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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.qiyi.basecore.taskmanager.dump.TMDump;
import org.qiyi.basecore.taskmanager.iface.IPrinter;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class TaskRecorder {
    private static final String TAG = "TManager_TaskRecorder";

    static volatile SparseIntArray array = new SparseIntArray();
    //将TaskID 与 GroupId 信息在内存中存储起来， 解决任务先执行完成，再添加依赖的问题;
    static SparseArray<List<Integer>> completedTasksGroup = new SparseArray<>();
    static final HashMap<Integer, LinkedList<WeakReference<Job>>> successorMap = new HashMap<>();
    // 与successorMap 不同在于  event 时间发生后， 不会立即被删除; 事件可以被重复触发
    static final HashMap<Integer, LinkedList<WeakReference<Job>>> successorEventMap = new HashMap<>();

    // used for lock Task record Array;
    private static ReentrantReadWriteLock arrayLock = new ReentrantReadWriteLock();
    private static ReentrantReadWriteLock.ReadLock arrayReadLock = arrayLock.readLock();
    //avoid BAAB case
    private static ReentrantReadWriteLock.WriteLock arrayWriteLock = arrayLock.writeLock();
    private static Application.ActivityLifecycleCallbacks lifeCycleCallBack = null;
    //used for binding task with activity life cycle
    private static HashMap<Integer, ActivityTask> taskActivityBindingMap = new HashMap<>();
    private static final SparseArray<String> sTaskIdNameMap = new SparseArray<>();
    private static final HashMap<Integer, int[]> eventIdMap = new HashMap<>();
    // 准备进入执行状态的任务列表; 从之前的executor 中移动过来，范围比以前更大，存储从TaskContainer 中移除，的任务; 确保未完成任务在 TaskContainer 和 这里总是能够查询到
    private static LinkedList<Task> taskList = new LinkedList<>();
    // used as an object lock
    private static int[] syncTaskList = new int[0];
    private static HashMap<String, Integer> serialGroupMap = new HashMap<>();
    // used as an object lock
    private static int[] syncGroupId = new int[0];
    private static SparseIntArray groupObjectIdMap = new SparseIntArray();
    private static LinkedList<EventTask> eventList = new LinkedList<>();


    private static Comparator<WeakReference<Job>> taskPriorityComparable = new Comparator<WeakReference<Job>>() {

        @Override
        public int compare(WeakReference<Job> o1, WeakReference<Job> o2) {
            return getPriority(o2) - getPriority(o1);
        }

        private int getPriority(WeakReference<Job> wk) {
            if (wk != null) {
                Job task = wk.get();
                if (task != null) {
                    return task.getTaskPriority();
                }
            }
            return 0;
        }
    };


    /**
     * Task record is useful when : the needed task finished before you post a task;
     * Only when task id is larger than Task.TASKID_EVENT_RANGE we will record tasks, in order to save memory;
     *
     * @param task
     * @param taskId
     */
    public static void onTaskFinished(@Nullable Task task, int taskId) {
        if (task != null && TM.isFullLogEnabled()) {
            TMLog.d(TAG, "task " + task.getName() + "is finished ");
        }

        // 对于匿名任务，不保存任务记录；
        if (taskId > Task.TASKID_EVENT_RANGE) {
            arrayWriteLock.lock();
            try {
                array.put(taskId, 1);
                int groupId = task == null ? 0 : task.groupId;
                List<Integer> groups = completedTasksGroup.get(taskId);
                if (groups == null) {
                    groups = new ArrayList<>();
                }
                if (!groups.contains(groupId)) {
                    groups.add(groupId);
                }
                completedTasksGroup.put(taskId, groups);
            } finally {
                arrayWriteLock.unlock();
            }
        }
        notifyTaskFinished(task, taskId, null);
    }


    //[由task obj 去同步。 同一个task ， task 完成 和添加依赖需要同步]
    public static void onTaskFinished(int taskId, Object data) {

        arrayWriteLock.lock();
        try {
            array.put(taskId, 1);
            int groupId = 0;
            List<Integer> groups = completedTasksGroup.get(taskId);
            if (groups == null) {
                groups = new ArrayList<>();
            }
            if (!groups.contains(groupId)) {
                groups.add(groupId);
            }
            completedTasksGroup.put(taskId, groups);
        } finally {
            arrayWriteLock.unlock();
        }
        notifyTaskFinished(null, taskId, data);
    }


    /* check whether the complete taskId is in any of given groupIds */
    static boolean checkTaskInGroup(int completedTaskId, int... groupIds) {
        if (groupIds == null) {
            return false;
        }
        // in case of ConcurrentModify on completedTasksGroup with same taskId
        arrayReadLock.lock();
        try {
            List<Integer> groups = completedTasksGroup.get(completedTaskId);
            if (groups == null && TMLog.isDebug() && TaskManager.enableDebugCheckCrash) {
                throw new IllegalStateException("complete taskId " + completedTaskId + " missing group");
            }
            if (groups != null) {
                for (int gid : groupIds) {
                    if (groups.contains(gid)) {
                        return true;
                    }
                }
            }
        } finally {
            arrayReadLock.unlock();
        }
        return false;
    }

    /**
     * @param finishedTask
     * @param taskID
     * @param data         : when finished task is null:
     *                     是否会出现succesormap 同步问题： 这里删除， 那里添加？ 不会！添加的地方是使用文件记录来做的同步。 删除前，已经同步的写出了完成日志。添加的时候，先同步的去查看是否已经完成，
     *                     如果代码执行到这里， 对应任务状态必定是已经执行完成的状态；
     */
    private static void notifyTaskFinished(@Nullable Task finishedTask, int taskID, @Nullable Object data) {

        LinkedList<WeakReference<Job>> list = new LinkedList<>();
        LinkedList<WeakReference<Job>> link;
        synchronized (successorMap) {
            link = successorMap.get(taskID);
            if (link != null) {
                successorMap.remove(taskID);
            }
        }
        if (link != null) {
            // this warning is checked; use the refed object to lock
            synchronized (link) {
                if (!link.isEmpty()) {
                    list.addAll(link);
                }
            }
        }

        // not remove after event happens
        synchronized (successorEventMap) {
            LinkedList<WeakReference<Job>> eventList = successorEventMap.get(taskID);
            if (eventList != null && !eventList.isEmpty()) {
                list.addAll(eventList);
            }
        }
        handleSuccesors(list, finishedTask, taskID, data);
    }


    final static void handleSuccesors(LinkedList<WeakReference<Job>> list, @Nullable Task finishedTask, int taskID, @Nullable Object data) {
        LinkedList<Task> pendingTasks = null;
        LinkedList<Task> pendingUITasks = null;
        boolean isFullLogEnabled = TM.isFullLogEnabled();

        if (!list.isEmpty()) {
            pendingTasks = new LinkedList<>();
            pendingUITasks = new LinkedList<>();

            if (list.size() > 1) {
                // do sort
                Collections.sort(list, taskPriorityComparable);
            }

            for (WeakReference<Job> wf : list) {
                Task request = null;
                Job succesor = wf.get();
                if (succesor != null) {
                    if (finishedTask != null) {
                        succesor.copyData(finishedTask);
                    } else if (data != null) { //处理事件触发任务执行的时候，携带数据
                        succesor.passData(taskID, data);
                    }
                    request = succesor.onDependantTaskFinished(finishedTask, taskID);
                    if (request != null) {
                        if (request.mRunningThread.isRunningInUIThread()) {
                            pendingUITasks.add(request);
                        } else {
                            pendingTasks.add(request);
                        }
                    }
                } else {
                    TMLog.d(TAG, "successor reference is null");
                }
            }
            list.clear();
        } else {
            TMLog.d(TAG, "successor is null");
        }

        // these tasks need to run UI thread sync ： 目前以下代码都只会在主线程执行
        if (isListNoneEmpty(pendingTasks) || isListNoneEmpty(pendingUITasks)) {

            if (isFullLogEnabled) {
                TMLog.e(TAG, taskID + " exe sync : " + pendingTasks.size());
            }

            if (pendingTasks.isEmpty()) {
                // only ui tasks
                for (Task task : pendingUITasks) {
                    TaskManager.getInstance().executeDirect(task);
                }
            } else if (pendingUITasks.isEmpty()) {
                if (pendingTasks.size() == 1) {
                    // only one pending task
                    TaskManager.getInstance().executeDirect(pendingTasks.get(0));
                } else {
                    new ParallelTask().addSubTasks(pendingTasks).execute();
                }
            } else {// both
                new ParallelTask()
                        .addSubTask(new SerialTasks().addSubTasks(pendingUITasks))
                        .addSubTasks(pendingTasks)
                        .execute();
            }
            if (isFullLogEnabled) {
                // used for bug analyze
                String vars = toNames(pendingTasks);
                TMLog.d(TAG, "param run :  " + vars);
                vars = toNames(pendingUITasks);
                TMLog.d(TAG, "param run UI :  " + vars);
                TMLog.e(TAG, taskID + " param done ! " + pendingTasks.size());
            }

        }
    }

    private static boolean isListNoneEmpty(LinkedList<Task> taskList) {
        return taskList != null && !taskList.isEmpty();
    }

    /**
     * use for test code only
     *
     * @param tasks
     * @return
     */
    private static String toNames(LinkedList<Task> tasks) {
        if (tasks != null) {
            StringBuilder builder = new StringBuilder();
            for (Task task : tasks) {
                String var = task.getName();
                if (var == null || var.length() == 0) {
                    var = task.getClass().getSimpleName();
                }
                builder.append(var);
                builder.append(' ');

            }
            return builder.toString();
        }
        return "[]";
    }


    /**
     * ventTas
     * 需要手动移除
     *
     * @param successor
     * @param taskId
     */
    public static void addEventSuccessForTask(@NonNull Job successor, int taskId) {

        synchronized (successorEventMap) {
            LinkedList<WeakReference<Job>> list = successorEventMap.get(taskId);
            if (list != null) {

                // check not contain this successor:
                if (!list.isEmpty()) {
                    for (WeakReference<Job> job : list) {
                        if (job.get() == successor) {
                            return;
                        }
                    }
                }
                list.add(new WeakReference<>(successor));

            } else {
                list = new LinkedList<>();
                list.add(new WeakReference<>(successor));
                successorEventMap.put(taskId, list);
            }
        }
    }

    public static boolean removeEventSuccessorForTask(@NonNull Job successor, int taskId) {
        synchronized (successorEventMap) {
            LinkedList<WeakReference<Job>> list = successorEventMap.get(taskId);
            if (list != null && !list.isEmpty()) {

                Iterator<WeakReference<Job>> iterator = list.iterator();
                while (iterator.hasNext()) {
                    WeakReference<Job> wj = iterator.next();
                    if (wj.get() == successor) {
                        iterator.remove();
//                        return; // try remove all : in case have more instance been added
                    }
                }
            }
            return list.isEmpty();
        }
    }


    public static void removeEventSuccessorForTask(int eventTaskId, int[] taskIds) {
        if (taskIds == null || taskIds.length == 0) {
            return;
        }
        synchronized (successorEventMap) {
            for (int taskId : taskIds) {
                LinkedList<WeakReference<Job>> list = successorEventMap.get(taskId);
                if (list != null && !list.isEmpty()) {

                    Iterator<WeakReference<Job>> iterator = list.iterator();
                    while (iterator.hasNext()) {
                        WeakReference<Job> wj = iterator.next();
                        Job job = wj.get();
                        if (job != null && job.getTaskId() == eventTaskId) {
                            iterator.remove();
//                        return; // try remove all : in case have more instance been added
                        }
                    }
                }
            }
        }
    }

    /**
     * if task is not finished , add task successor;
     * Or else task will run right now
     */
    public static void addSuccessorForTask(@NonNull Job successor, int taskId) {
        arrayReadLock.lock();
        int index;
        try {// if this event has already finished
            index = array.indexOfKey(taskId);
            if (index < 0) {
                LinkedList<WeakReference<Job>> list;
                synchronized (successorMap) {
                    list = successorMap.get(taskId);
                    if (list == null || list.isEmpty()) {
                        //we have to crate a new link list , to avoid some concurrent issue
                        list = new LinkedList<>();
                        successorMap.put(taskId, list);
                        // 避免这里刚创建了节点，在清楚线程中，把这个节点清楚了的问题； fix 与 clean up 数据同步的问题
                        list.add(new WeakReference<>(successor));
                        return;
                    }
                }
                synchronized (list) {
                    list.add(new WeakReference<>(successor));
                }
                return;
            }
        } finally {
            arrayReadLock.unlock();
        }

        //task is finished ： index > 0
        Task request = successor.onDependantTaskFinished(null, taskId);
        if (request != null) {
            //case dependant task is finished & this task is set to be run UI thread Sync: do execute directly
            TaskManager.getInstance().executeDirect(request);
        }

    }


    /**
     * its possible that: the task is finished , and is waiting for the lock to write state. and we returned false;
     *
     * @param taskId
     * @return
     */
    public static boolean isTaskFinished(int taskId) {
        arrayReadLock.lock();
        try {
            return array.indexOfKey(taskId) >= 0;
        } finally {
            arrayReadLock.unlock();
        }
    }


    /**
     * used for a quick access of task states
     *
     * @param taskId
     * @return
     */
    public static boolean isTaskFinishedNoSync(int taskId) {
        return array.indexOfKey(taskId) >= 0;
    }

    /**
     * if some task is not finished , return false
     * no sync ; not important
     *
     * @return
     */
    public static boolean isAllTaskFinished(int[] ids) {
        arrayReadLock.lock();
        try {
            if (ids != null) {
                for (int id : ids) {
                    if (array.indexOfKey(id) < 0) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            arrayReadLock.unlock();
        }

    }

    static void registerTaskName(final int taskId, final String taskName) {
        TaskManager.getInstance().getWorkHandler().post(new Runnable() {
            @Override
            public void run() {
                sTaskIdNameMap.put(taskId, taskName);
            }
        });
    }


    static void registerTaskEventRelations(final int taskId, final int[] events) {
        TM.getWorkHandler().post(new Runnable() {
            @Override
            public void run() {
                eventIdMap.put(taskId, events);
            }
        });
    }


    // all data dump to string
    @TMDump
    public static String _dump() {

        final StringBuilder builder = new StringBuilder();
        dump(new IPrinter() {
            @Override
            public void print(String var) {
                builder.append(var);
            }
        });
        return builder.toString();
    }

    // all data dump tp trackVar
    public static void dump(IPrinter printer) {
        printer.print("TaskRecord: finished tasks:");
        StringBuilder stringBuilder = new StringBuilder();
        arrayReadLock.lock();
        try {
            for (int i = 0; i < array.size(); i++) {
                int id = array.keyAt(i);
                int success = array.valueAt(i);
                String name = sTaskIdNameMap.get(id, "");
                stringBuilder.append(id).append('(').append(name).append(')').append('=').append(success).append('\n');
            }
        } finally {
            arrayReadLock.unlock();
        }
        printer.print(stringBuilder.toString());
        stringBuilder.setLength(0);

        stringBuilder.append("TaskRecord: needed tasks ");
        stringBuilder.append("successorMap size");
        stringBuilder.append(successorMap.size());
        stringBuilder.append('\n');

        LinkedList<Map.Entry<Integer, LinkedList<WeakReference<Job>>>> entries = new LinkedList<>();
        if (!successorMap.isEmpty()) {
            synchronized (successorMap) {
                entries.addAll(successorMap.entrySet());
            }
        }

        for (Map.Entry<Integer, LinkedList<WeakReference<Job>>> entry : entries) {
            stringBuilder.append("\n");
            stringBuilder.append("TaskId: ").append(entry.getKey());
            stringBuilder.append("needed by:[");
            LinkedList<WeakReference<Job>> list = entry.getValue();
            if (list != null && !list.isEmpty()) {
                // no bother if empty is changed
                synchronized (list) {
                    for (WeakReference<Job> wk : list) {
                        Job task = wk.get();
                        if (task != null) {
                            stringBuilder.append(" id: ").append(task.getTaskId());
                            stringBuilder.append(" Name:").append(task.getName());
                            stringBuilder.append(";");
                        }
                    }
                }
            }
            stringBuilder.append("]");
        }

        printer.print(stringBuilder.toString());
        stringBuilder.setLength(0);
        stringBuilder.append("\nEXE：in queue size ");
        if (!taskList.isEmpty()) {

            synchronized (syncTaskList) {
                stringBuilder.append(taskList.size());
                if (!taskList.isEmpty()) {
                    stringBuilder.append("\n running task[");
                    for (Task task : taskList) {
                        stringBuilder.append(task.getName());
                        stringBuilder.append(':');
                        stringBuilder.append(task.getTaskId());
                        stringBuilder.append(',');
                        printer.print(stringBuilder.toString());
                        stringBuilder.setLength(0);
                    }
                    stringBuilder.append(']');
                }
            }
        }
        printer.print(stringBuilder.toString());

    }


    /**
     * call deleteRecode instead
     *
     * @param ids
     */
    @Deprecated
    public static void removeTasks(int... ids) {
        arrayWriteLock.lock();
        try {
            for (int i : ids) {
                array.delete(i);
            }
        } finally {
            arrayWriteLock.unlock();
        }
    }


    // remove task recode
    public static void deleteRecode(int... ids) {
        arrayWriteLock.lock();
        try {
            for (int i : ids) {
                array.delete(i);
            }
        } finally {
            arrayWriteLock.unlock();
        }
    }


    public static void removeTasks(LinkedList<Integer> ids) {
        arrayWriteLock.lock();
        try {
            for (int i : ids) {
                array.delete(i);
            }
        } finally {
            arrayWriteLock.unlock();
        }

    }


    public static void unbindTasks(Context context) {

        if (context != null) {
            final int hash = context.hashCode();
            //cancel task the remove recode
            // make it execute serially
            TaskManager.getInstance().workPostDelay(new Runnable() {
                @Override
                public void run() {
                    ActivityTask object = taskActivityBindingMap.remove(hash);
                    if (object != null) {
                        object.removeAll();
                    }
                }
            }, 0);
        }

    }

    /**
     * @param context
     * @param taskId
     * @return int : < 0  bind  fail: only if activity if destroy
     */
    public static int bindTask(@NonNull Context context, final int taskId) {
        //bind tasks
        if (context != null) {
            if (lifeCycleCallBack == null) {
                registerLifeCircle(TaskManager.getInstance().getApplication());
            }
            final int hash = context.hashCode();

            if (context instanceof Activity) {
                Activity activity = (Activity) context;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed() || activity.isFinishing()) {
                    // do not bind : will cancel this task
                    TMLog.e(TAG, " task is to be canceled , bcz its binding activity is destroyed");
                    return -1;
                } else {
                    TaskManager.getInstance().workPostDelay(new Runnable() {
                        @Override
                        public void run() {
                            ActivityTask tasks = taskActivityBindingMap.get(hash);
                            if (tasks == null) {
                                tasks = new ActivityTask();
                                taskActivityBindingMap.put(hash, tasks);
                            }
                            tasks.addTaskId(taskId);
                        }
                    }, 0);
                    return hash;
                }
            }

        }

        return 0;
    }


    private static void registerLifeCircle(Application application) {
        if (application != null) {
            lifeCycleCallBack = new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

                }

                @Override
                public void onActivityStarted(Activity activity) {

                }

                @Override
                public void onActivityResumed(Activity activity) {

                }

                @Override
                public void onActivityPaused(Activity activity) {

                }

                @Override
                public void onActivityStopped(Activity activity) {

                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    unbindTasks(activity);
                }
            };
            application.registerActivityLifecycleCallbacks(lifeCycleCallBack);
        }

    }

    static class ActivityTask {
        LinkedList<Integer> list = new LinkedList<>();

        public void addTaskId(int id) {
            list.add(id);
        }

        //remove all task with the ids specified in the list
        public void removeAll() {
            if (!list.isEmpty()) {
                for (int taskId : list) {
                    TM.cancelTaskById(taskId);
                }
                // remove the record of this task
                TaskRecorder.removeTasks(list);
                TaskRecorder.unbindEventTask(list);
            }
        }
    }


    /**
     * ready to run
     * one task cant be added more than onces
     *
     * @param request
     */
    public static boolean enqueue(Task request) {
        synchronized (syncTaskList) {
            if (taskList.contains(request)) {
                return false;
            }
            taskList.addLast(request);
        }
        return true;
    }

    public static void dequeue(Task request) {
        synchronized (syncTaskList) {
            taskList.remove(request);
        }
    }


    /**
     * @param token
     * @return : 是否有成功取消的： 不管是否成功取消都从记录中移除; 如果移除没有全部成功， 就需要从executer 中删除
     */
    public static boolean cancelTaskByToken(Object token) {
        //find tasks by token:
        boolean canceledSuccess = false; // 如果有任意一个没有删除掉，都需要到线程池中移除
        synchronized (syncTaskList) {
            Iterator<Task> iterator = taskList.iterator();
            while (iterator.hasNext()) {
                Task request = iterator.next();
                if (request.getToken() == token) {
                    canceledSuccess |= request.cancel();
                    iterator.remove();
                }
            }
        }
        return canceledSuccess;
    }


    public static boolean cancelTaskByTaskId(int taskId) {
        //find tasks by token:
        boolean canceledSuccess = true; // 如果有任意一个没有删除掉，都需要到线程池中移除
        synchronized (syncTaskList) {
            Iterator<Task> iterator = taskList.iterator();
            while (iterator.hasNext()) {
                Task request = iterator.next();
                if (request.getTaskId() == taskId) {
                    if (!request.cancel()) { // cancel success, do remove request
                        canceledSuccess = false;
                    }
                    iterator.remove();
                }
            }
        }
        return canceledSuccess;
    }

    //including the rejected , all tasks are inside this list
    public static Task findTaskById(int taskId) {

        synchronized (syncTaskList) {
            for (Task request : taskList) {
                if (taskId == request.getTaskId()) {
                    return request;
                }
            }
        }
        return null;
    }


    public static int swapSerialTaskId(String groupName, int tid) {
        Integer var;
        synchronized (syncGroupId) {
            var = serialGroupMap.get(groupName);
            serialGroupMap.put(groupName, tid);
        }
        return var == null ? -1 : var;
    }


    /**
     * @param list 中对应的全部的 event task 全部移除时间监听
     */
    public static void unbindEventTask(LinkedList<Integer> list) {
        if (list != null && !list.isEmpty()) {
            for (int i : list) {
                unbindEventTask(i);
            }
        }
    }

    public static void unbindEventTask(int taskId) {
        int events[];
        synchronized (eventIdMap) {
            events = eventIdMap.remove(taskId);
        }
        if (events != null) {
            removeEventSuccessorForTask(taskId, events);
        }
    }

    public static int generateGroupId(Object groupIdentity) {
        int groupIdKey = System.identityHashCode(groupIdentity);
        synchronized (groupObjectIdMap) {
            int var = groupObjectIdMap.get(groupIdKey);
            if (var > 0) {
                return var;
            }
        }
        int var = TM.genGroupId();
        synchronized (groupObjectIdMap) {
            groupObjectIdMap.put(groupIdKey, var);
        }
        return var;
    }


    public static synchronized void attachEventTask(EventTask job) {
        eventList.add(job);
    }

    public static synchronized void detachEventTask(EventTask job) {
        eventList.remove(job);
    }


    /**
     * 当任务取消的时候，移除依赖任务执行的任务id
     *
     * @param taskId
     */
    public static void removeSuccessorForTask(int taskId) {
        synchronized (successorMap) {
            successorMap.remove(taskId);
        }
    }


    /**
     * 清理引用链关系
     */
    public static boolean cleanUp() {
        LinkedList<LinkedList<WeakReference<Job>>> entries = new LinkedList<>();
        boolean removed = false;
        if (!successorMap.isEmpty()) {
            synchronized (successorMap) {
                entries.addAll(successorMap.values());
            }
        }
        // cant use successorMap.values() to iterate : as its not a copy of Collection;  will cause concurrent exception
        boolean listEmpty = false;
        if (entries != null && !entries.isEmpty()) {
            for (LinkedList<WeakReference<Job>> list : entries) {
                if (list == null) {
                    continue;
                }
                // rm null references
                synchronized (list) {
                    Iterator<WeakReference<Job>> iterator = list.iterator();
                    while (iterator.hasNext()) {
                        WeakReference<Job> wk = iterator.next();
                        Job job = wk.get();
                        if (job == null) {
                            iterator.remove();
                            removed = true;
                        }
                    }
                    listEmpty |= list.isEmpty();
                }
            }
        }


        //clear map
        if (listEmpty) {
            synchronized (successorMap) {
                Iterator<Map.Entry<Integer, LinkedList<WeakReference<Job>>>> iterator = successorMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, LinkedList<WeakReference<Job>>> entry = iterator.next();
                    LinkedList<WeakReference<Job>> list = entry.getValue();
                    // 同步问题：如果现在正好 list is empty , & is adding data
                    if (list == null || list.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
        return removed;
    }

}

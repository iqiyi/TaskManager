package com.qiyi.tm.demo.test;

import android.util.Log;

import org.qiyi.basecore.taskmanager.ParallelTask;
import org.qiyi.basecore.taskmanager.Task;

public class TaskResultTest extends Test {
    @Override
    public void doTest() {
        final int ar[] = new int[3];
        new ParallelTask().addSubTask(new Task() {
            @Override
            public void doTask() {
                setResult(1);
            }
        })
                .addSubTask(new Task() {
                    @Override
                    public void doTask() {
                        setResult(2);
                    }
                })
                .addSubTask(new Task() {
                    @Override
                    public void doTask() {
                        setResult(3);
                    }
                })
                .setCallBackOnSubTaskFinished(new ParallelTask.TaskResultCallback() {
                    @Override
                    public void onResult(Task task, Object var, int index) {
                        Log.d(TAG, "index " + index + " " + var);
                        ar[index] = (int) var;
                    }
                })
                .execute();

        Log.d(TAG, "" + ar[0] + " " + ar[1] + " " + ar[2]);
    }
}

package com.qiyi.tm.demo.test;

import android.os.Looper;
import android.util.Log;

import com.qiyi.tm.demo.tasks.ThreadCheckTask;

import org.qiyi.basecore.taskmanager.Task;

public abstract class Test implements ITest {
    int var;
    protected final static String TAG = "Test";

    protected ThreadCheckTask getTask(int time) {
        return getTask((++var) + "", time, null);
    }

    protected ThreadCheckTask getTask(int time, RunCallback callback) {
        return getTask((++var) + "", time, callback);
    }

    protected ThreadCheckTask getTask(final String nameName, final int taskTime) {
        return getTask(nameName, taskTime, null);
    }

    protected ThreadCheckTask getTask(final String nameName, int tid, final int taskTime) {
        return new ThreadCheckTask(nameName, tid) {

            @Override
            public void doTask() {
                long b = System.currentTimeMillis() + taskTime;
                double cc = 0;
                while (System.currentTimeMillis() < b) {
                    cc += Math.PI * Math.sin(0.23145f);
                }
                setResult(getTaskId());
            }
        };
    }

    protected ThreadCheckTask getTask(final String nameName, final int taskTime, final RunCallback cb) {
        return new ThreadCheckTask(nameName) {
            @Override
            public void doTask() {
                if (cb != null) {
                    cb.onRun();
                }
                long a = System.currentTimeMillis();
                long b = System.currentTimeMillis() + taskTime;
                double cc = 0;
                while (System.currentTimeMillis() < b) {
                    cc += Math.PI * Math.sin(0.23145f);
                }
                setResult(getTaskId());
            }
        };
    }

    protected ThreadCheckTask getTask(final String nameName) {
        return new ThreadCheckTask(nameName) {
            @Override
            public void doTask() {
            }
        };
    }

    protected ThreadCheckTask getTask() {
        return new ThreadCheckTask() {
            @Override
            public void doTask() {
            }
        };
    }


    protected void log(String s) {
        Log.d(TAG, s);
    }

    protected void loge(String s) {
        Log.e(TAG, s);
    }


    protected int time() {
        return (int) (20 + Math.random() * 800);
    }

    interface RunCallback {
        void onRun();
    }

}

package com.qiyi.tm.demo.tasks;

import android.os.Looper;

import com.qiyi.tm.demo.SumChecker;

import org.qiyi.basecore.taskmanager.Task;

public abstract class ThreadCheckTask extends Task {
    boolean threadCheckEnabled = true;
    SumChecker checker;

    public ThreadCheckTask() {
        super();
    }

    public ThreadCheckTask(String name) {
        super(name);
    }

    public ThreadCheckTask(String name, int tid) {
        super(name, tid);
    }

    public ThreadCheckTask disableThreadCheck() {
        threadCheckEnabled = false;
        return this;
    }

    @Override
    public void doBeforeTask() {
        if (threadCheckEnabled) {
            checkThread(getRunningThread().isRunningInUIThread());
        }
        super.doBeforeTask();
    }

    @Override
    public void doAfterTask() {
        super.doAfterTask();
        if (checker != null) {
            checker.increase();
        }
    }

    // back thread may run on ui thread; for para run
    protected void checkThread(boolean ui) {
        // crash when defined run on UI thread & is now running on back thread
        if (ui && (Looper.myLooper() != Looper.getMainLooper())) {
            throw new IllegalStateException("not running demanded thread " + (Looper.myLooper() == Looper.getMainLooper()));
        }
    }

    public ThreadCheckTask register(SumChecker checker) {
        this.checker = checker;
        checker.count();
        return this;
    }
}

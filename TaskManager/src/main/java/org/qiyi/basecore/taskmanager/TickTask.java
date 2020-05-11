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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.qiyi.basecore.taskmanager.iface.ITask;

/**
 * 当任务触发执行后，可多次执行
 * On tick will be called each time when the setting time comes;
 */
public abstract class TickTask implements Runnable, ITask {
    private String taskName;
    private int interval;
    private Handler timerHandler;
    private volatile boolean isStopped;
    private int maxLoop;
    private int loopTimes;
    //default false ： 在任务执行完成后，再计时启动下一个任务
    private boolean intervalModeBefore;
    // isRunning in ui thread
    private boolean isOnUIThread;
    private int delayTime;
    private long netPostTime;

    public TickTask(String name) {
        this.taskName = name;
    }

    public TickTask() {

    }

    @Override
    public void run() {

        if (isOnUIThread) {
            runTask();
        } else {
            new Task(taskName) {

                @Override
                public void doTask() {
                    runTask();
                }
            }.postAsync();
        }

    }

    private void runTask() {
        if (timerHandler != null) {
            timerHandler.removeCallbacks(this);
        }
        loopTimes++;
        timerGo(true);
        doTask();
        timerGo(false);
    }

    public void stop() {
        isStopped = true;
        if (timerHandler != null) {
            timerHandler.removeCallbacks(this);
        }
    }

    // 只针对主线程，前沿，执行时间点post; 子线程 由work handler 保证。
    private void timerGo(boolean foreground) {
        if (intervalModeBefore == foreground && (maxLoop == 0 || loopTimes < maxLoop)) {

            int nextInterValTime = figureInterval(loopTimes, interval);
            if (timerHandler != null && !isStopped && nextInterValTime > 0) {
                if (foreground && isOnUIThread) {
                    //asset
                    long now = SystemClock.uptimeMillis();
                    if (netPostTime > now) {
                        timerHandler.postAtTime(this, netPostTime);
                    } else {
                        timerHandler.post(this);
                    }
                    netPostTime += nextInterValTime;
                } else {
                    timerHandler.postDelayed(this, nextInterValTime);
                }
            }
        }
    }

    public int getCurrentLoopTimes() {
        return loopTimes;
    }

    public TickTask setMaxLoopTime(int max) {
        maxLoop = max;
        return this;
    }

    public void doTask() {
        onTick(getCurrentLoopTimes() - 1);
    }

    public abstract void onTick(int loopTime);


    @Deprecated
    public TickTask setInterval(int time) {
        this.interval = time;
        intervalModeBefore = false;
        return this;
    }

    public TickTask setIntervalWithFixedDelay(int time) {
        this.interval = time;
        intervalModeBefore = false;
        return this;
    }

    public TickTask setIntervalWithFixedRate(int time) {
        this.interval = time;
        intervalModeBefore = true;
        return this;
    }


    private void prepare() {
        if (isOnUIThread) {
            timerHandler = new Handler(Looper.getMainLooper());
        } else {
            timerHandler = TM.getWorkHandler();
        }
        if (interval == 0) {
            throw new IllegalStateException("interval mast be given");
        }

        int intervalTime = figureInterval(0, interval);

        if (delayTime == 0) {
            netPostTime = SystemClock.uptimeMillis() + intervalTime;
            run();
        } else {
            netPostTime = SystemClock.uptimeMillis() + delayTime + intervalTime;
            timerHandler.postDelayed(this, delayTime);
        }
    }

    public void post() {
        isOnUIThread = Looper.myLooper() == Looper.getMainLooper();
        prepare();
    }

    public void postUI() {
        isOnUIThread = true;
        prepare();
    }

    public void postAsync() {
        isOnUIThread = false;
        prepare();
    }

    public void postUIDelay(int delay) {
        isOnUIThread = true;
        delayTime = delay;
        prepare();
    }

    public void postAsyncDelay(int delay) {
        isOnUIThread = false;
        delayTime = delay;
        prepare();
    }

    /**
     * 计算出间隔时间
     *
     * @return
     */
    protected int figureInterval(int times, int interval) {
        return interval;
    }

}

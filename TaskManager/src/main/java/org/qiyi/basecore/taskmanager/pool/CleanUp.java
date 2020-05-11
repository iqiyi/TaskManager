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
package org.qiyi.basecore.taskmanager.pool;

import org.qiyi.basecore.taskmanager.TaskRecorder;
import org.qiyi.basecore.taskmanager.TickTask;

/**
 * 周期性的执行对象引用清理任务，对succesor map ， 与EventPool 进行清理工作。
 * 一次处理的时间越少，下次处理的事件越长
 */
public class CleanUp extends TickTask {
    private static boolean isRunning;
    private long gap;
    private int lastInterval = 0;
    private boolean removed;
    private static final int TEN_MINUTE = 10 * 60 * 1000;// 10

    @Override
    public void onTick(int loopTime) {
        long s1 = System.currentTimeMillis();
        removed |= TaskRecorder.cleanUp();
        removed |= ObjectPool.cleanUp();
        gap = System.currentTimeMillis() - s1;
    }

    @Override
    public int figureInterval(int times, int interval) {

        int var = 0;
        if (removed) {
            if (lastInterval == 0) {
                lastInterval = interval;
            }
            var = lastInterval << 1;
            if (var > TEN_MINUTE) {
                var = TEN_MINUTE;
            }
        } else {
            var = 0;
        }

        if (gap < 10) {
            return interval + var;
        } else if (gap < 50) {
            return 2 * interval + var;
        } else {
            return 3 * interval + var;
        }
    }


    public static void go() {
        synchronized (CleanUp.class) {
            if (isRunning) return;
            isRunning = true;
        }
        new CleanUp()
                .setIntervalWithFixedDelay(120 * 1000)
                .postAsyncDelay(1000);
    }
}

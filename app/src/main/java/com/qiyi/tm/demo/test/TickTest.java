package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.TickTask;
import org.qiyi.basecore.taskmanager.other.TMLog;

public class TickTest extends Test {

    @Override
    public void doTest() {
        new TickTask() {

            protected int figureInterval(int times, int interval) {
                if (times % 2 == 0) {
                    return 1000;
                }
                return interval;
            }

            @Override
            public void onTick(int loopTime) {
                TMLog.d(TAG, "loopTime " + loopTime);
                getTask("1000").executeSync();
            }
        }.setIntervalWithFixedRate(500)
                .setMaxLoopTime(10)
                .postAsync();

    }


}

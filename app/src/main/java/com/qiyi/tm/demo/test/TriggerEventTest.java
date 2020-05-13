package com.qiyi.tm.demo.test;

import com.qiyi.tm.demo.R;

import org.qiyi.basecore.taskmanager.EventTask;
import org.qiyi.basecore.taskmanager.IdleTask;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TickTask;

public class TriggerEventTest extends Test {
    @Override
    public void doTest() {
        test2();
    }

    private void test1(){
        new Task(){

            @Override
            public void doTask() {
                // do sth
            }
        }.dependOn(R.id.event_vv)
                .postAsync();

        // Trigger task to run
        TM.triggerEvent(R.id.event_vv);
    }

    private void test2(){
        // 支持多次任务执行，需要手动调用 unregister
        new EventTask(){

            @Override
            public void onEvent(int eventId, Object msg) {
                log("on event " + msg);
            }
        }.registerGroupedEvents(this, 1)
                .postUI();

        // 模拟多次触发事件
        new TickTask(){
            @Override
            public void onTick(int loopTime) {
                TM.triggerEvent(TriggerEventTest.this, 1, new Integer(320));
            }
        }.setMaxLoopTime(10)
                .setIntervalWithFixedDelay(100)
                .postAsync();

    }
}

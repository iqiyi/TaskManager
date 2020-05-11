package com.qiyi.tm.demo.test;

import com.qiyi.tm.demo.R;

import org.qiyi.basecore.taskmanager.EventTask;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TickTask;

/**
 * 测试各种事件
 * 1, 自定义事件
 * 2, id 事件
 * 3, 事件携带数据
 */
public class EventTaskTest extends Test {

    @Override
    public void doTest() {

        triggerEventTask();
//        triggerEvent();
//        triggerSelfDefinedEvent();
//        triggerGlobalEvent();
    }


    /**
     * 全局的任务 trigger
     */
    private void triggerGlobalEvent() {


        new EventTask() {

            @Override
            public void onEvent(int eventId, Object msg) {
                log("gochar globacl evt " + msg);
            }
        }.registerEvents(R.id.event_vv)
                .postAsync();


        new TickTask() {

            @Override
            public void onTick(int loopTime) {
                TM.triggerEvent(R.id.event_vv, 5);
            }
        }.setIntervalWithFixedDelay(100)
                .setMaxLoopTime(7)
                .post();

    }

    private void triggerEventTask() {
        Task task = getTask("event task");
        task.postPending();
        TM.triggerEventTaskFinished(task.getTaskId());
    }

    /**
     * id define
     */
    private void triggerEvent() {
        Task task = new Task("alashk") {
            @Override
            public void doTask() {
                Integer tye = getData(R.id.event_vv, Integer.class);
                log("gochar: " + tye);

            }
        };
        task.dependOn(R.id.event_vv);
        task.postAsync();

        new Task() {
            @Override
            public void doTask() {
                TM.triggerEvent(R.id.event_vv, new Integer(2));
            }
        }.postUIDelay(1000);

    }

    int var = 0;

    /**
     *
     */
    private void triggerSelfDefinedEvent() {


        new EventTask() {

            @Override
            public void onEvent(int eventId, Object msg) {

                log("gochar: evt " + eventId + " msg: " + msg);

                if (eventId == 30) {
                    unregister();
                }

            }
        }.registerGroupedEvents(this, 1, 2, 3)
                .post();


        new TickTask() {

            @Override
            public void doTask() {
                var++;
                int p = var % 4;
                TM.triggerEvent(EventTaskTest.this, p, new Integer(211));

            }

            @Override
            public void onTick(int loopTime) {

            }
        }.setIntervalWithFixedDelay(1000)
                .setMaxLoopTime(100)
                .postUI();

    }

}

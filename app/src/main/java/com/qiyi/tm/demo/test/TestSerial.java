package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TickTask;

/**
 * 任务按照 FIFO 的顺序先后执行。  需要等待前面的额任务执行完成后 才能执行后面的任务。
 */
public class TestSerial extends Test {
    String groupName = "waka";

    @Override
    public void doTest() {
        new Task() {

            @Override
            public void doTask() {
                odoTest();
            }
        }.postAsync();

    }

    public void odoTest() {

        //模拟随机提交任务
        new TickTask() {

            protected int figureInterval(int times, int interval) {
                return (int) (Math.random() *1000 + 100);
            }

            @Override
            public void onTick(int loopTime) {
                int count = (int) (Math.random() * 5);
                while (count > 0) {
                    getTask(time()).postAsync();
                    count--;
                }

            }
        }.setMaxLoopTime(30)
                .setIntervalWithFixedDelay(200)
                .postAsync();

        int p = 0;
        while (p < 60) {
            getTask("t-" + p, time()).executeSerial(groupName);
            p++;
        }
    }


}

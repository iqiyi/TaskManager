package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TickTask;

/**
 * 验证在 任务闲置后，过一段时间再启动大量并发任务 TM 的表现
 */
public class GroupThreadPoolTest extends Test{
    @Override
    public void doTest() {

        new TestExecuteNow().doTest();

        new Task(){

            @Override
            public void doTask() {
                new TestExecuteNow().doTest();
            }
        }.postAsyncDelay(60000);


        new TickTask(){

            @Override
            public void onTick(int loopTime) {
            }
        }.setIntervalWithFixedDelay(5000)
                .postAsync();
    }
}

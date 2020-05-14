package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;

/**
 * 测试任务兜底的方法执行是否符合预期
 */
public class NeedSyncTest extends Test {
    public NeedSyncTest() {
        super();
    }

    @Override
    public void doTest() {

        testPending();
//        testRunning();
//        testFinished();
    }

    /**
     * pending 的懒加载的任务。 是否能被 need Sync  正确的触发
     */
    private void testPending() {

        final Task task = getTask("waka- pending", 500);
        task.postPending();


        new Task() {

            @Override
            public void doTask() {

                TM.needTaskSync(task.getTaskId());
                log("need task sync called finished ");

            }
        }.postUIDelay(1000);

    }

    /**
     * 测试正在执行的任务，当调用needSync 的时候，执行情况是否符合预期。
     */
    private void testRunning() {

        final Task task = getTask(TAG+" waka-running", 8000);
        task.postAsync();

        new Task() {

            @Override
            public void doTask() {

                log("before call need sync ");
                TM.needTaskSync(task.getTaskId());
                log("need task sync called finished ");

            }
        }.postUIDelay(100);


    }

    /**
     * 当任务已经完成的情况下，是否符合预期
     */
    private void testFinished() {


        final Task task = getTask("waka-finished", 80);
        task.postAsync();

        new Task() {

            @Override
            public void doTask() {

                log("before call need task sync ");
                TM.needTaskSync(task.getTaskId());
                log("need task sync called finished ");

            }
        }.postUIDelay(1500);


    }
}

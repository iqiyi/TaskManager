package com.qiyi.tm.demo.test;

import com.qiyi.tm.demo.R;

import org.qiyi.basecore.taskmanager.Task;

/**
 * 测试各种或逻辑是否正常执行
 */
public class OrdependTest extends Test {

    @Override
    public void doTest() {
//        test2();
        test1();
    }


    /**
     * 任务3 在任务1 或者 任务2 完成后，执行
     */
    private void test1() {
        Task task1 = new Task("task1", R.id.tash_v1) {
            @Override
            public void doTask() {

            }
        };
        //getTask("task1");
        Task task2 = new Task("task2", R.id.tash_v1) {
            @Override
            public void doTask() {

            }
        };
        //getTask("task2");
        Task task3 = getTask("task3");
        task1.postUIDelay(3000);
        task2.postAsyncDelay(500);
        task3.dependOn(task1)
                .orDependOn(task2)
                .postAsync();
    }

    private void test2() {
        Task task1 = new Task("task1", R.id.tash_v1) {
            @Override
            public void doTask() {

            }
        };
        //getTask("task1");
        Task task2 = new Task("task2", R.id.tash_v1) {
            @Override
            public void doTask() {

            }
        };
        //getTask("task2");

        Task task3 = getTask("task3");

        task1.postUIDelay(3000);
        task2.postAsyncDelay(3500);
        task3.dependOn(task1.getTaskId())
                .orDependOn(task2.getTaskId())
//                .postAsyncDelay(6000);
                .postAsync();
    }
}

package com.qiyi.tm.demo.test;

import com.qiyi.tm.demo.R;

import org.qiyi.basecore.taskmanager.Task;

public class DependantAfterTest extends Test {

    @Override
    public void doTest() {
        Task task1 = new Task("DependantAfterTest", R.id.task_1) {
            @Override
            public void doTask() {
                // do sth
            }
        };
        task1.postAsync();

        // task 2 should run 1000ms later after task 1 is finished
        Task task2 = getTask("task2");
        task2.delayAfter(1000, task1.getTaskId())
                .postAsync();


        // task would be hold for 300ms, then check task1 state , if its already finished , then  wait 1000ms to run;
        Task task3 = getTask("task3");
        task3.delayAfter(1000, task1.getTaskId())
                .postAsyncDelay(300);
    }


}

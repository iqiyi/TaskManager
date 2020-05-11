package com.qiyi.tm.demo.test;

import android.util.Log;

import org.qiyi.basecore.taskmanager.IdleTask;
import org.qiyi.basecore.taskmanager.RunningThread;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TickTask;

/**
 * test if a task has enabled idle run :
 */
public class IdleTest extends Test {
    @Override
    public void doTest() {
//        idleTest();
        a();
    }

    /**
     * test when idle task post first
     */
    private void a() {
        new IdleTask("Idle-Task-mmmmmmmm") {

            @Override
            public void doTask() {
                Log.d("YAYS", " on run");
            }
        }
                .setRunningThread(RunningThread.BACKGROUND_THREAD)
                .postPending();
        getTask("Non-Idle-1", 100).postAsync();
        getTask("Non-Idle-2", 100).postAsync();
        getTask("Non-Idle-3", 100).postAsync();
        getTask("Non-Idle-4", 100).postAsync();
        getTask("Non-Idle-5", 1000).postAsync();
        getTask("Non-Idle-6", 100).postAsync();
        getTask("Non-Idle-7", 100).postAsync();
        getTask("Non-Idle-8", 100).postAsync();
        getTask("Non-Idle-9", 100).postAsync();
        getTask("Non-Idle-10", 100).postAsync();
        getTask("Non-Idle-11", 100).postAsync();
        getTask("Non-Idle-12", 1000).postAsync();
        getTask("Non-Idle-13", 100).postAsync();
        getTask("Non-Idle-14", 100).postAsync();


        Task tt = getTask("Non-Idle-wkkka ", 800);
        tt.postAsyncDelay(2000);


        new Task("Non-Idle-n2") {

            @Override
            public void doTask() {

            }
        }.postAsync();

        new Task("Idle-Task-mimimi") {

            @Override
            public void doTask() {

            }
        }.enableIdleRun().postAsync();
    }

    private void idleTest() {

        getTask("kksksksks(((", 100).enableIdleRun()
                .postAsync();


        new TickTask() {

            @Override
            public void onTick(int loopTime) {
                getTask("IdleTest1", 100).enableIdleRun()
                        .postUI();
                getTask("IdleTest2", 100).enableIdleRun()
                        .postUI();
                getTask("IdleTest3", 100).enableIdleRun()
                        .postUI();

            }
        }.setMaxLoopTime(10)
                .setIntervalWithFixedDelay(2000)
                .postAsync();


    }
}

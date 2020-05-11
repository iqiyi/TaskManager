package com.qiyi.tm.demo.test;

import android.util.Log;

import com.qiyi.tm.demo.R;
import com.qiyi.tm.demo.SumChecker;

import org.qiyi.basecore.taskmanager.ParallelTask;
import org.qiyi.basecore.taskmanager.RunningThread;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskRecorder;

import java.util.PriorityQueue;

public class MultiSyncTest extends Test {


    @Override
    public void doTest() {
//        testTaskSync();
//        testEventSync();
//        testPara();
//        testParaM();
        tsetSyncUIHybride();
    }


    /**
     * 测试executeSync 部分申明再UI 线程执行的情况
     * needSync 的并发优化必须保证 C & G 在主线程执行。
     */
    private void tsetSyncUIHybride() {

        TaskRecorder.deleteRecode(R.id.event_vv);

        getTask("A", 50).dependOn(R.id.event_vv).executeSync();
        getTask("B", 100).dependOn(R.id.event_vv).executeSync();
        getTask("C", 150).dependOn(R.id.event_vv).executeSyncUI();
        getTask("D", 80).dependOn(R.id.event_vv).executeSync();
        getTask("E", R.id.task_3, 150).dependOn(R.id.event_vv).executeSync();
        getTask("F", 80).dependOn(R.id.event_vv).executeSync();
        getTask("G", R.id.task_4, 150).dependOn(R.id.event_vv).executeSyncUI();
        getTask("H", 80).dependOn(R.id.event_vv).executeSync();

        new Task() {

            @Override
            public void doTask() {
                TM.triggerEvent(R.id.event_vv);
            }
        }.postAsync();

    }

    /**
     * 任务与事件混合的情况下 ，验证复杂的依赖关系，执行顺序是否符合预期
     */
    private void testEventSync() {

        int p = 0;
        while (p < 1) {

            int var = (int) (75 + Math.random() * 10);
            Task taskA = getTask("A", R.id.task_1, 50);
            Task taskB = getTask("B", R.id.task_2, 100);
            Task taskC = getTask("C", R.id.task_3, 150);
            Task taskD = getTask("D", R.id.task_4, 80);

            // D 1s 后执行
            taskD.postUIDelay(1000);

            //任务A 期望在任务C D 与 事件vv 完成后，立即执行
            taskA.dependOn(taskC.getTaskId(), taskD.getTaskId(), R.id.event_vv).executeSync();


            // B 期望再任务D完成与事件发生后，立即执行；或者等待 var 时间后执行。
            taskB.dependOn(taskD.getTaskId(), R.id.event_vv)
                    .orDelay(var)
                    .executeSync();


            //C 期望再事件发生的时候，立即执行
            taskC.dependOn(R.id.event_vv)
                    .executeSync();


            //D 期望再事件发生的时候，立即执行
            taskD.dependOn(R.id.event_vv).executeSync();

            // 在1s 后，触发任务执行
            new Task() {

                @Override
                public void doTask() {
                    loge("DemoApp event is triggered !!!! ");
                    TM.triggerEvent(R.id.event_vv);

                }
            }.postAsyncDelay(1000);

            p++;

        }

    }


    /**
     * TestParallelTask
     */
    private void testPara() {
        final Task taskA = getTask("A", 150);
        final Task taskB = getTask("B", 100);
        final Task taskC = getTask("C", 150);
        final Task taskD = getTask("D", 80);

        new ParallelTask()
                .addSubTask(taskA)
                .addSubTask(taskB)
                .addSubTask(taskC)
                .addSubTask(taskD)
                .execute();

        log("DemoApp run tst papra end");
    }

    /**
     * 测试 AB 是否在C 完成后，能立即执行
     * 1) AB 需要在C 完成后才执行
     * 2）AB 并行执行。AB 其中之一在C 当前线程运行。
     */
    private void testTaskSync() {
        Task taskA = getTask("A", 80);
        Task taskB = getTask("B", 190);
        Task taskC = getTask("C");
        taskC.postAsync();
        taskA.dependOn(taskC).executeSync();
        taskB.dependOn(taskC).executeSync();
    }

    private void testParaM() {

        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();
        getTask(2000).postAsync();

        int p = 0;
        SumChecker checker = new SumChecker();
        while (p < 20) {
            Task taskA = getTask("A", 1).register(checker);
            Task taskB = getTask("B", time()).register(checker);
            Task taskC = getTask("C", 790).register(checker);
            Task taskD = getTask("D", 1000).register(checker);
            Task taskE = getTask("E", time()).register(checker);
            Task taskF = getTask("F", 70).register(checker);


            new ParallelTask()
                    .addSubTask(taskA)
                    .addSubTask(taskB)
                    .addSubTask(taskC)
                    .addSubTask(taskD)
                    .addSubTask(taskE)
                    .addSubTask(taskF)
                    .execute();

            checker.verriyfy();
            Log.d("TM_PARA ", "DONE ----");

            p++;
        }

    }


}

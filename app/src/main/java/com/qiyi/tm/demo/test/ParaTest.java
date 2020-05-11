package com.qiyi.tm.demo.test;

import android.util.Log;

import com.qiyi.tm.demo.SumChecker;

import org.qiyi.basecore.taskmanager.ParallelTask;
import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.other.TMLog;

/**
 * 用于验证并发任务是否会多次执行，绘制没有执行的问题。
 */
public class ParaTest extends Test implements Test.RunCallback {
    private final String TAG = "ParaTest";

    @Override
    public void doTest() {


//        testA();
//        testParaM();
//        testPam2();
        testParam3();

    }


    private void testA() {
        // 在子线程进行500次并发任务执行
        new Task() {

            @Override
            public void doTask() {
                int p = 0;
                while (p < 500) {
                    testCount();
                    p++;
                }
            }
        }.postAsync();
    }

    private void testCount() {

        final StateCheck sumChecker = new StateCheck(12);

        ParallelTask parallelTask = new ParallelTask() {

        }.addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(1);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 1);


            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(2);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 2);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(3);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());

                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(4);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 3);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(5);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 4);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(6);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 5);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(7);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 6);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(8);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 7);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(9);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 8);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(10);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 9);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(11);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 10);
            }
        }).addSubTask(new Runnable() {
            @Override
            public void run() {
                sumChecker.add(0);
                TMLog.e(TAG, "[[[[" + Thread.currentThread().getPriority());
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                TMLog.e(TAG, Thread.currentThread().getPriority() + "task run " + 11);
            }
        });
        parallelTask.execute();

        sumChecker.checkValids();
        Log.d(TAG, "data is valid");
    }


    /**
     * 事件依赖，并发执行。
     */
    private void testPam2() {
        int p = 0;
        while (p < 1) {
//            getTask(time()).postAsync();
//            getTask(10).postAsync();
            int id = TM.genNewEventId();
            getTask("A", time(), this).dependOn(id).executeSync();
            getTask("B", time(), this).dependOn(id).executeSyncUI();
            getTask("C", time(), this).dependOn(id).executeSync();
            getTask("D", time(), this).dependOn(id).executeSyncUI();
            getTask("E", time(), this).dependOn(id).executeSync();
            getTask("F", time(), this).dependOn(id).executeSyncUI();
            getTask("G", time(), this).dependOn(id).executeSync();
            getTask("H", time(), this).dependOn(id).executeSync();
            TM.triggerEvent(id);
            Log.e(TAG, ">>>>> on finish >>>>" + cc);
//            getTask(time()).postAsync();
//            TaskManager.getInstance().dumpInfo();
            p++;

        }
    }


    private void testParam3() {

//        TM.setMaxRunningThreadCount(2);
        Task taskA = getTask("A", 20);
        Task taskB = getTask("B", 10);
        Task taskC = getTask("C", 2000);
        Task taskD = getTask("D", time());

        new ParallelTask()
                .addSubTask(taskA)
                .addSubTask(taskB)
                .addSubTask(taskC)
                .addSubTask(taskD)
                .setTimeOut(-1)
                .execute();
        Log.d(TAG, "test param 3 done");

    }

    /**
     * 多次验证并发任务是否能全部执行完成
     */
    private void testParaM() {

        int p = 0;
        while (p < 50) {

            SumChecker checker = new SumChecker();
            getTask(20).postAsync();
            getTask(10).postAsync();
            getTask(time()).postAsync();
            getTask(10).postAsync();
            Task taskA = getTask("A", time()).register(checker);
            Task taskB = getTask("B", time()).register(checker);
            Task taskC = getTask("C", time()).register(checker);
            Task taskD = getTask("D", time()).register(checker);
            Task taskE = getTask("E", time()).register(checker);
            Task taskF = getTask("F", time()).register(checker);
            Task taskG = getTask("G", time()).register(checker);
            Task taskH = getTask("H", time()).register(checker);
            Task taskI = getTask("E", time()).register(checker);
            Task taskJ = getTask("F", time()).register(checker);
            Task taskK = getTask("G", time()).register(checker);
            Task taskL = getTask("H", time()).register(checker);


            new ParallelTask()
                    .addSubTask(taskA)
                    .addSubTask(taskB)
                    .addSubTask(taskC)
                    .addSubTask(taskD)
                    .addSubTask(taskE)
                    .addSubTask(taskF)
                    .addSubTask(taskG)
                    .addSubTask(taskH)
                    .addSubTask(taskI)
                    .addSubTask(taskJ)
                    .addSubTask(taskK)
                    .addSubTask(taskL)
                    .setTimeOut(-1)
                    .execute();

            checker.verriyfy();
            Log.d("DemoApp ", "DONE ----");
            getTask(time()).postAsync();
            getTask(200).postAsync();

            p++;
        }

    }

    int cc;

    @Override
    public synchronized void onRun() {

        cc++;

    }
}

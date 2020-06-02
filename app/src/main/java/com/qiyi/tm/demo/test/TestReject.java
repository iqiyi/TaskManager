package com.qiyi.tm.demo.test;

import android.util.Log;

import com.qiyi.tm.demo.SumChecker;

import org.qiyi.basecore.taskmanager.Task;

/**
 * 验证大量任务并发的时候，是否存在任务不执行的问题
 */
public class TestReject extends Test {

    @Override
    public void doTest() {

        final SumChecker checker = new SumChecker();
        int p = 0;

        Task task = new Task() {
            @Override
            public void doTask() {
                checker.verriyfy();
                Log.e(TAG, "success : all task fnished");
            }
        };

        Task t = null;
        while (p < 2000) {
            t = getTask(time()).register(checker);
            t.postAsync();
            p++;
        }

        //在提交的最后一个任务完成5s 后，执行
        task.delayAfter(5000, t).postAsync();


    }
}

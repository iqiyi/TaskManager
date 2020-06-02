package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.other.TMLog;

/**
 * 测试API : ExecuteNow : 验证在任务繁忙的情况下， 是否能立即得到执行。
 */
public class TestExecuteNow extends Test implements Test.RunCallback {
    int vv = 0;

    @Override
    public void doTest() {
        Task.TaskResultCallback result = new Task.TaskResultCallback() {
            @Override
            public synchronized void onResultCallback(Task task, Object var) {
                vv++;
            }
        };
        int p = 0;
        while (p < 100) {
            if (p % 40 == 0) {
                getTask("green", 100).executeAsyncNow();
                getTask("green", time()).executeAsyncNow();
                getTask("green", 1000).executeAsyncNow();
                getTask("green", 10).executeAsyncNow();
                getTask("green", time()).executeAsyncNow();
            }
            getTask("Normal-" + p, 20 + time(), this).setCallBackOnFinished(result).postAsync();
            p++;
        }
    }

    int cc = 0;

    @Override
    public synchronized void onRun() {
        cc++;
        TMLog.d(TAG, "on run count " + cc);
    }
}

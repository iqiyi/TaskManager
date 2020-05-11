package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;

/**
 * 验证任务是否能被正常取消
 */
public class CancelTest extends Test {

    @Override
    public void doTest() {
        Task task = getTask("0t");
        task.setToken(this);
        task.postUIDelay(1000);

        Task task2 = getTask("2t");
        task2.postUIDelay(1000);
        task2.setToken(this);

        Task task1 = getTask("1t");
        task1.dependOn(task.getTaskId())
                .orDependOn(task2.getTaskId()).postAsync();

        TM.cancelTaskByToken(this);
        TM.cancelTaskById(task.getTaskId());
    }
}

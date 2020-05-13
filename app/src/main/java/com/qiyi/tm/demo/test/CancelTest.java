package com.qiyi.tm.demo.test;

import com.qiyi.tm.demo.R;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.Task;

/**
 * 验证任务是否能被正常取消
 */
public class CancelTest extends Test {

    @Override
    public void doTest() {
        Task task = new Task("CancelTest-task-1", R.id.task_1) {
            @Override
            public void doTask() {
            }
        };

        task.setToken(this);
        task.postUIDelay(1000);

        Task task2 = new Task("CancelTest-task-1", R.id.task_2) {
            @Override
            public void doTask() {
            }
        };
        task2.postUIDelay(1000);
        task2.setToken(this);

        Task task1 = getTask("1t");
        task1.dependOn(task.getTaskId())
                .orDependOn(task2.getTaskId()).postAsync();

        TM.cancelTaskByToken(this);
        TM.cancelTaskById(task.getTaskId());
    }
}

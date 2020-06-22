package org.qiyi.basecore.taskmanager;

import java.util.concurrent.Executor;

public class TMExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
        TM.postAsync(command);
    }
}

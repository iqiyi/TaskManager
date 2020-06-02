package org.qiyi.basecore.taskmanager.threadpool;

public interface IThreadIdleCallback {
    void onIdle(boolean idle);
}

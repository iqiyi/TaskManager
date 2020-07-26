package org.qiyi.basecore.taskmanager.callable.iface;

public interface IAfterCall<T> {
    void onAfterCall(T value);
}

package org.qiyi.basecore.taskmanager.callable.iface;

public interface IPreCall<T> {
    void onPreCall(T value);
}

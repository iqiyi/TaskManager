package org.qiyi.basecore.taskmanager.callable.iface;

public interface CallEach2<K, V> {
    void call(K key, V value);
}

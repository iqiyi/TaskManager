package org.qiyi.basecore.taskmanager.callable.iface;

import org.qiyi.basecore.taskmanager.callable.IterableEachCall;

public interface ShiftTCallEach2<K, V, R> {
    IterableEachCall<R> call(K key, V value);
}

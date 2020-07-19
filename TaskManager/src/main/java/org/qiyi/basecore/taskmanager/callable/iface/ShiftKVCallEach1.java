package org.qiyi.basecore.taskmanager.callable.iface;

import org.qiyi.basecore.taskmanager.callable.MapEachCall;

public interface ShiftKVCallEach1<T, K, V> {
    MapEachCall<K, V> call(T value);
}

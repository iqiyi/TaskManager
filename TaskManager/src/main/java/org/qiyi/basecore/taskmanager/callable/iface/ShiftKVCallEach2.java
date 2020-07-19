package org.qiyi.basecore.taskmanager.callable.iface;

import org.qiyi.basecore.taskmanager.callable.MapEachCall;

public interface ShiftKVCallEach2<K, V, RK, RV> {
    MapEachCall<RK, RV> call(K key, V value);
}

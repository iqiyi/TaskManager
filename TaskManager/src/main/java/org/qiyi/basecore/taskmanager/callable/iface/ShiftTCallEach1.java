package org.qiyi.basecore.taskmanager.callable.iface;

import org.qiyi.basecore.taskmanager.callable.IterableEachCall;

public interface ShiftTCallEach1<T, R> {
    IterableEachCall<R> call(T value);
}

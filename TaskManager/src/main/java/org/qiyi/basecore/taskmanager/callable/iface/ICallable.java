package org.qiyi.basecore.taskmanager.callable.iface;

import org.qiyi.basecore.taskmanager.callable.ObjectCall;

public interface ICallable<T> {
    void call(Object value);

    void callAsync(Object value);

    Object shiftT(ShiftCallT<T, Object > shift);

}

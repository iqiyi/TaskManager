package org.qiyi.basecore.taskmanager.callable.iface;

public interface ICallable<T> {
    void call(Object value);

    void callAsync(Object value);

    Object shiftT(ShiftCallT<T, Object> shift);

}

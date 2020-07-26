package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.callable.iface.CallEachT;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftCallT;

public class ObjectCall<T> extends ShiftT<T> {
    private T mValue;

    public ObjectCall(T value) {
        mValue = value;
    }

    public ObjectCall() {
    }


    @Override
    protected <R> void shiftEach(ShiftT<R> chain, ShiftCallT<T, ? extends ShiftT<R>> each) {
        chain.addNext(each.call(mValue));
        buildPreCall(mValue);
        buildAfterCall(mValue);
    }

    @Override
    protected <K, V> void shiftEach(ShiftKV<K, V> chain, ShiftCallT<T, ? extends ShiftKV<K, V>> each) {
        chain.addNext(each.call(mValue));
        buildPreCall(mValue);
        buildAfterCall(mValue);

    }

    @Override
    protected void callEach(CallEachT<T> call) {

        buildPreCall(mValue);
        buildAfterCall(mValue);

        if (call != null) {
            doPreCall();
            call.call(mValue);
            doAfterCall();
        }

    }

    private void buildPreCall(T var) {
        if (mPreCall != null) {
            PreCall<T> preCall = new PreCall<>(var, mPreCall);
            addPreCall(preCall);
        }
    }

    private void buildAfterCall(T var) {
        if (mAfterCall != null) {
            AfterCall<T> afterCall = new AfterCall<>(var, mAfterCall);
            addAfterCall(afterCall);
        }
    }

}

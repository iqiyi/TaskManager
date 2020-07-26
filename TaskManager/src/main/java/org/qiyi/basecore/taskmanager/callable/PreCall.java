package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.callable.iface.IPreCall;

class PreCall<T> {
    private T mValue;
    private IPreCall<T> mCall;
    PreCall mNext;

    public PreCall(T value, IPreCall<T> call) {
        mValue = value;
        mCall = call;
    }


    void addNext(PreCall next) {
        mNext = next;
    }

    public void call() {
        if (mCall != null) {
            mCall.onPreCall(mValue);
        }
    }

}

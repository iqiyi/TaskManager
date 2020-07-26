package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.callable.iface.IAfterCall;

class AfterCall<T> {
    private T mValue;
    IAfterCall<T> mCall;
    AfterCall mFormer;

    public AfterCall(T value, IAfterCall<T> call) {
        mValue = value;
        mCall = call;
    }

    public void call() {
        if (mCall != null) {
            mCall.onAfterCall(mValue);
        }

    }


    AfterCall addFormer(AfterCall afterCall) {
        mFormer = afterCall;
        return afterCall;
    }

}

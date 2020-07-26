package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.callable.iface.IAfterCall;
import org.qiyi.basecore.taskmanager.callable.iface.IPreCall;

import java.util.LinkedList;

public class Shift<T> {
    LinkedList<PreCall<?>> mPreCalls = new LinkedList<>();
    LinkedList<AfterCall<?>> mAfterCalls = new LinkedList<>();
    protected IPreCall<T> mPreCall;
    protected IAfterCall<T> mAfterCall;
    protected Shift<T> mParent;

    LinkedList<PreCall<?>> getPreCalls() {
        return mPreCalls;
    }

    LinkedList<AfterCall<?>> getAfterCall() {
        return mAfterCalls;
    }

    void doPreCall() {
        if (mPreCalls != null && !mPreCalls.isEmpty()) {
            for (PreCall<?> preCall : mPreCalls) {
                preCall.call();
            }
            mPreCalls.clear();
            mPreCalls = null;
        }
    }

    void doAfterCall() {

        if (mAfterCalls != null && !mAfterCalls.isEmpty()) {
            for (AfterCall<?> afterCall : mAfterCalls) {
                afterCall.call();
            }
            mAfterCalls.clear();
            mAfterCalls = null;
        }
    }


    void setCall(Shift shift) {
        if (shift != null) {
            mAfterCalls = shift.getAfterCall();
            mPreCalls = shift.getPreCalls();
        }
    }

    void addPreCall(PreCall<?> preCall) {
        if (mParent != null) {
            mParent.addPreCall(preCall);
        } else {
            mPreCalls.addLast(preCall);
        }
    }

    void addAfterCall(AfterCall<?> afterCall) {
        if (mParent != null) {
            mParent.addAfterCall(afterCall);
        } else {
            mAfterCalls.addFirst(afterCall);
        }
    }

}

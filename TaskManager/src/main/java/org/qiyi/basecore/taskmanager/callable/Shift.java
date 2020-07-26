package org.qiyi.basecore.taskmanager.callable;

import androidx.annotation.CallSuper;

import org.qiyi.basecore.taskmanager.callable.iface.IAfterCall;
import org.qiyi.basecore.taskmanager.callable.iface.IPreCall;

import java.util.LinkedList;

public class Shift<T> {
    LinkedList<PreCall<?>> mPreCalls = new LinkedList<>();
    LinkedList<AfterCall<?>> mAfterCalls = new LinkedList<>();
    protected IPreCall<T> mPreCall;
    protected IAfterCall<T> mAfterCall;



    LinkedList<PreCall<?>> getPreCalls() {
        return mPreCalls;
    }

    LinkedList<AfterCall<?>> getAfterCall() {
        return mAfterCalls;
    }

    @CallSuper
    void addPreCall(PreCall<?> preCall) {

//        if (mPreCalls == null) {
//            mPreCalls = new LinkedList<>();
//        }
        mPreCalls.addLast(preCall);
    }

    void addAfterCall(AfterCall<?> afterCall) {
//        if(mAfterCalls == null) {
//            mAfterCalls = new LinkedList<>();
//        }
        mAfterCalls.addFirst(afterCall);
    }

    void doPreCall() {
        if (mPreCalls != null) {
            for (PreCall<?> preCall : mPreCalls) {
                preCall.call();
            }
            mPreCalls.clear();
            mPreCalls = null;
        }
    }

    void doAfterCall() {

        if(mAfterCalls != null) {
            for (AfterCall<?> afterCall : mAfterCalls) {
                afterCall.call();
            }
            mAfterCalls.clear();
            mAfterCalls = null;
        }
    }


    public void setCall(Shift shift){
        if(shift != null) {
            mAfterCalls = shift.getAfterCall();
            mPreCalls = shift.getPreCalls();
        }
    }

}

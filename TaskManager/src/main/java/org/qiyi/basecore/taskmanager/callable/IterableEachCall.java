package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.callable.iface.CallEach1;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftKVCallEach1;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftTCallEach1;

import java.util.LinkedList;


public class IterableEachCall<T> implements Runnable {

    protected Iterable<T> mIterable;
    private CallEach1<T> mEach;
    private LinkedList<IterableEachCall<T>> mChildren = new LinkedList<>();

    public IterableEachCall(Iterable<T> iterable) {
        mIterable = iterable;
    }

    IterableEachCall() {

    }

    void addNext(IterableEachCall<T> call) {
        mChildren.addLast(call);
    }

    public void call(CallEach1<T> each) {
        mEach = each;
        run();
    }


    public void callAsync(CallEach1<T> each) {
        mEach = each;
        TM.postAsync(this);
    }

    private void callChildren(CallEach1<T> each) {
        for (IterableEachCall<T> t : mChildren) {
            if (t == null) continue;
            t.call(each);
        }
    }


    /**
     * T:  传入的参数类型
     * R:  返回的参数类型
     *
     * @param each
     * @param <R>
     * @return
     */
    public <R> IterableEachCall<R> shiftT(ShiftTCallEach1<T, R> each) {

        IterableEachCall<R> result = new IterableEachCall<>();
        if (mChildren.isEmpty()) {
            // each should return this type;
            if (mIterable != null) {
                for (T var : mIterable) {
                    result.addNext(each.call(var));
                }
            }
        } else {
            for (IterableEachCall<T> t : mChildren) {
                result.addNext(t.shiftT(each));
            }
        }
        return result;
    }


    public <K, V> MapEachCall<K, V> shiftKV(ShiftKVCallEach1<T, K, V> each) {

        MapEachCall<K, V> result = new MapEachCall<>();
        if (mChildren.isEmpty()) {
            // each should return this type;
            if (mIterable != null) {
                for (T var : mIterable) {
                    result.addNext(each.call(var));
                }
            }
        } else {
            for (IterableEachCall<T> t : mChildren) {
                result.addNext(t.shiftKV(each));
            }
        }
        return result;
    }


    public void callEach() {
        if (mIterable == null) {
            return;
        }

        for (T var : mIterable) {
            mEach.call(var);
        }
    }


    @Override
    public void run() {
        if (mChildren.isEmpty()) {
            callEach();
        } else {
            callChildren(mEach);
        }

    }
}

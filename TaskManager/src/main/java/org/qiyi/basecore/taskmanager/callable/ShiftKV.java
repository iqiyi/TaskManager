/*
 *
 * Copyright (C) 2020 iQIYI (www.iqiyi.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.callable.iface.CallEachKV;
import org.qiyi.basecore.taskmanager.callable.iface.IAfterCall;
import org.qiyi.basecore.taskmanager.callable.iface.IPreCall;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftCallKV;

import java.util.HashMap;
import java.util.LinkedList;

public abstract class ShiftKV<K, V> extends Shift<HashMap.Entry<K, V>> {


    private LinkedList<ShiftKV<K, V>> mChildren = new LinkedList<>();
    private CallEachKV<K, V> mEach;


    public ShiftKV() {
    }

    void addNext(ShiftKV<K, V> shift) {
        if (shift != null) {
            mChildren.addLast(shift);
            shift.mParent = this;
        }
    }


    public final void call(CallEachKV<K, V> value) {
        mEach = value;
        run();
    }

    public final void callAsync(CallEachKV<K, V> value) {
        mEach = value;

        new Task() {

            @Override
            public void doTask() {
                run();
            }
        }.postAsync();
    }


    private void run() {

        if (mPreCall != null || mAfterCall != null) {
            // prepare build call
            doCallEach(null);
            mPreCall = null;
            mAfterCall = null;
        }
        if (mEach != null) {
            doPreCall();
            doCallEach(mEach);
            doAfterCall();
        }


    }

    private void doCallEach(CallEachKV<K, V> call){
        if (mChildren.isEmpty()) {
            callEach(call);
        } else {
            for (ShiftKV<K, V> var : mChildren) {
                var.call(call);
            }
        }
    }

    public final <RK, RV> MapEachCall<RK, RV> shiftKV(ShiftCallKV<K, V, MapEachCall<RK, RV>> each2) {

        MapEachCall<RK, RV> mapEachCall = new MapEachCall<>();
        mapEachCall.setCall(this);

        if (mChildren.isEmpty()) {
            shiftEach(mapEachCall, each2);
        } else {
            for (ShiftKV<K, V> var : mChildren) {
                var.preCall(mPreCall);
                var.afterCall(mAfterCall);
                mapEachCall.addNext(var.shiftKV(each2));
            }
        }
        return mapEachCall;
    }

    public final <R> IterableEachCall<R> shiftT(ShiftCallKV<K, V, IterableEachCall<R>> each2) {

        IterableEachCall<R> iterableEachCall = new IterableEachCall<>();
        iterableEachCall.setCall(this);

        if (mChildren.isEmpty()) {
            shiftEach(iterableEachCall, each2);
        } else {

            for (ShiftKV<K, V> var : mChildren) {
                var.preCall(mPreCall);
                var.afterCall(mAfterCall);
                iterableEachCall.addNext(var.shiftT(each2));
            }
        }
        return iterableEachCall;
    }


    public ShiftKV<K, V> preCall(IPreCall<HashMap.Entry<K, V>> preCall) {
        mPreCall = preCall;
        return this;
    }

    public ShiftKV<K, V> afterCall(IAfterCall<HashMap.Entry<K, V>> afterCall) {
        mAfterCall = afterCall;
        return this;
    }


    protected abstract <RK, RV> void shiftEach(ShiftKV<RK, RV> chain, ShiftCallKV<K, V, ? extends ShiftKV<RK, RV>> each);

    protected abstract <T> void shiftEach(ShiftT<T> chain, ShiftCallKV<K, V, ? extends ShiftT<T>> each);

    protected abstract void callEach(CallEachKV<K, V> each);


}

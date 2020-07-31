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

import org.qiyi.basecore.taskmanager.callable.iface.CallEachT;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftCallT;


public class IterableEachCall<T> extends ShiftT<T> {

    protected Iterable<T> mIterable;

    public IterableEachCall(Iterable<T> iterable) {
        mIterable = iterable;
    }


    public IterableEachCall() {
    }

    @Override
    protected <R> void shiftEach(ShiftT<R> chain, ShiftCallT<T, ? extends ShiftT<R>> each) {
        if (mIterable != null) {
            for (T var : mIterable) {
                if (each != null) {
                    chain.addNext(each.call(var));
                }
                buildPreCall(var);
                buildAfterCall(var);
            }
        }
    }

    @Override
    protected <K, V> void shiftEach(ShiftKV<K, V> chain, ShiftCallT<T, ? extends ShiftKV<K, V>> each) {
        if (mIterable != null) {
            for (T var : mIterable) {
                if (each != null) {
                    chain.addNext(each.call(var));
                }
                buildPreCall(var);
                buildAfterCall(var);
            }
        }
    }

    @Override
    protected void callEach(CallEachT<T> call) {
        if (mIterable != null) {
            for (T var : mIterable) {
                buildPreCall(var);
                buildAfterCall(var);

                if (call != null) {
                    call.call(var);
                }

            }
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

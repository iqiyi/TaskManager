package org.qiyi.basecore.taskmanager.callable;

import org.qiyi.basecore.taskmanager.TM;
import org.qiyi.basecore.taskmanager.callable.iface.CallEach2;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftKVCallEach2;
import org.qiyi.basecore.taskmanager.callable.iface.ShiftTCallEach2;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public final class MapEachCall<K, V> implements Runnable {

    private Map<K, V> mMap;
    private CallEach2<K, V> mEach;
    private LinkedList<MapEachCall<K, V>> mChildren = new LinkedList<>();

    public MapEachCall(Map<K, V> map) {
        mMap = map;
    }

    MapEachCall() {

    }

    public void call(CallEach2<K, V> each) {
        mEach = each;
        run();
        return;
    }

    public void callAsync(CallEach2<K, V> each) {
        mEach = each;
        TM.postAsync(this);
    }


    public <RK, RV> MapEachCall<RK, RV> shiftKV(ShiftKVCallEach2<K, V, RK, RV> each2) {

        MapEachCall<RK, RV> mapEachCall = new MapEachCall<>();

        if (mChildren.isEmpty()) {

            if (mMap != null && each2 != null) {
                Iterator<Map.Entry<K, V>> iterable = mMap.entrySet().iterator();
                while (iterable.hasNext()) {
                    Map.Entry<K, V> var = iterable.next();
                    mapEachCall.addNext(each2.call(var.getKey(), var.getValue()));
                }
            }

        } else {

            for (MapEachCall<K, V> var : mChildren) {
                mapEachCall.addNext(var.shiftKV(each2));
            }
        }
        return mapEachCall;
    }

    public <R> IterableEachCall<R> shiftT(ShiftTCallEach2<K, V, R> each2) {

        IterableEachCall<R> iterableEachCall = new IterableEachCall<>();

        if (mChildren.isEmpty()) {

            Iterator<Map.Entry<K, V>> iterable = mMap.entrySet().iterator();
            while (iterable.hasNext()) {
                Map.Entry<K, V> var = iterable.next();
                iterableEachCall.addNext(each2.call(var.getKey(), var.getValue()));
            }
        } else {

            for (MapEachCall<K, V> var : mChildren) {
                iterableEachCall.addNext(var.shiftT(each2));
            }
        }
        return iterableEachCall;
    }


    void addNext(MapEachCall<K, V> shift) {
        mChildren.addLast(shift);
    }

    private void callEach(CallEach2<K, V> each) {
        if (mMap != null && each != null) {
            Iterator<Map.Entry<K, V>> iterable = mMap.entrySet().iterator();
            while (iterable.hasNext()) {
                Map.Entry<K, V> var = iterable.next();
                each.call(var.getKey(), var.getValue());
            }
        }
    }

    @Override
    public void run() {
        if (mChildren.isEmpty()) {
            callEach(mEach);
        } else {
            for (MapEachCall<K, V> var : mChildren) {
                var.call(mEach);
            }
        }

    }
}

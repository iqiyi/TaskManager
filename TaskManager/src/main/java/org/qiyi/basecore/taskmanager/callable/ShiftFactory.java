package org.qiyi.basecore.taskmanager.callable;

import java.util.Map;

public class ShiftFactory {
    public static <K, V> MapEachCall<K, V> forEach(Map<K, V> map) {
        return new MapEachCall<>(map);
    }

    public static <T> IterableEachCall<T> forEach(Iterable<T> iterable) {
        return new IterableEachCall<>(iterable);
    }

    public static <T> ArrayEachCall<T> forEach(T[] array) {
        return new ArrayEachCall<>(array);
    }
}

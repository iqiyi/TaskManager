package org.qiyi.basecore.taskmanager.callable;

import java.util.Arrays;

public final class ArrayEachCall<T> extends IterableEachCall<T> {

    public ArrayEachCall(T[] array) {
        mIterable = Arrays.asList(array);
    }

}

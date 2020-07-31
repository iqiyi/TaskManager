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

import java.util.ArrayList;
import java.util.Map;

public class ShiftFactory {
    public static <K, V> MapEachCall<K, V> create(Map<K, V> map) {
        return new MapEachCall<>(map);
    }

    public static <T> IterableEachCall<T> create(Iterable<T> iterable) {
        return new IterableEachCall<>(iterable);
    }

    public static <T> ArrayEachCall<T> create(T[] array) {
        return new ArrayEachCall<>(array);
    }

    public static <T> ObjectCall<T> create(T value) {
        return new ObjectCall<>(value);
    }

    public static IterableEachCall<Integer> create(int[] array) {
        if (array != null) {
            int count = array.length;
            ArrayList<Integer> list = new ArrayList<>(count);
            for (int i : array) {
                list.add(i);
            }
            return new IterableEachCall<>(list);
        }
        return null;
    }

    public static IterableEachCall<Float> create(float[] array) {
        if (array != null) {
            int count = array.length;
            ArrayList<Float> list = new ArrayList<>(count);
            for (float i : array) {
                list.add(i);
            }
            return new IterableEachCall<>(list);
        }
        return null;
    }

    public static IterableEachCall<Double> create(double[] array) {
        if (array != null) {
            int count = array.length;
            ArrayList<Double> list = new ArrayList<>(count);
            for (double i : array) {
                list.add(i);
            }
            return new IterableEachCall<>(list);
        }
        return null;
    }


    public static<T> ObjectCall<T> just(T value) {
        return new ObjectCall<>(value);
    }


}

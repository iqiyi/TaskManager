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
package org.qiyi.basecore.taskmanager.pool;

import android.util.SparseArray;

import java.util.LinkedList;

/**
 * 延时清理对象复用池，在一段时间后不使用对象了，那么对象将被自动清理
 */
public class ObjectPool {
    private static String TAG = "TM_ObjectPool";

    /**
     * for each list cache max 80 item
     */
    private final static int MAX_CACHED_SIZE = 50;
    private final static SparseArray<Queue> map = new SparseArray<>();
    private static volatile boolean enabled;

    // need sync :  可能再扩容的时候，出现问题
    @SuppressWarnings("unchecked")
    public static void recycle(RecycleObject obj) {

        if (!enabled) {
            return;
        }

        if (obj != null) {
            Class cls = obj.getClass();
            int id = System.identityHashCode(cls);
            Queue queue;
            synchronized (map) {
                queue = map.get(id);
                if (queue == null) {
                    queue = new Queue(cls);
                    map.put(id, queue);
                }
            }

            // must recycle first  then put , or else will be used by others
            obj.recycle();
            queue.put(obj);

        }
    }

    /**
     * 不能是基础类型
     *
     * @param target
     * @param <T>
     * @return :
     */
    @SuppressWarnings("unchecked")
    public static <T extends RecycleObject> T obtain(Class<T> target) {
        if (!enabled) {
            return null;
        }
        int id = System.identityHashCode(target);
        Queue<T> queue;
        synchronized (map) {
            queue = map.get(id);
            if (queue == null) {
                return null;
            }
        }
        return queue.poll();
    }


    public static boolean cleanUp() {

        if (!enabled) {
            return false;
        }

        LinkedList<Queue> queues = new LinkedList<>();
        synchronized (map) {
            int size = map.size();
            for (int i = 0; i < size; i++) {
                queues.addLast(map.valueAt(i));
            }
        }
        boolean rs = false;
        for (Queue queue : queues) {
            rs |= queue.clean();
        }
        return rs;
    }


    /**
     * 动态长度队列。 如果长时间没有访问，持有对象将逐步回收；
     */
    static class Queue<T> {
        LinkedList<T> list;
        int classId;
        int size;// 初始化默认持有8个对象
        int hitCount;
        int missCount;

        public Queue(Class target) {
            classId = System.identityHashCode(target);
            list = new LinkedList<T>();
            size = 8;
        }

        public T poll() {
            if (list == null) {
                synchronized (this) {
                    if (list == null) {
                        list = new LinkedList<T>();
                        return null;
                    }
                }
            }
            T var;
            synchronized (this) {
                var = list.poll();
            }

            if (var != null) {
                hitCount++;
            } else {
                missCount++;
            }
            return var;
        }

        public void put(T object) {
            synchronized (this) {
                if (list == null) {
                    list = new LinkedList<>();
                }

                if (list.size() < size) {
                    list.addLast(object);
                }
            }
        }

        // > 1 < MAX_CACHED_SIZE
        private void grade() {
            int var = size;

            if (missCount == 0) {
                // shrink
                size = var - (var >> 2);
            } else {
                float rate = 1F* hitCount / missCount;
                if (rate < 0.5f) {
                    // enlarge
                    size = var << 1;
                } else if (rate < 1f) {
                    // 1.25
                    size += (var >> 2);
                } else if (rate > 6f) {
                    size = var - (var >> 2);
                } else if (rate > 10f) {
                    // try shrink
                    size = var >> 1;
                } else { // [1 , 3]
                    if (missCount < 50) {
                        // do nothing
                        if (hitCount < size) {
                            //shrink
                            size = var - (var >> 2);
                        }
                    } else if (missCount < 500) {
                        size += (var >> 2);
                    } else if (missCount < 1500) {
                        size += var >> 1;
                    } else {
                        size = var << 1;
                    }

                }
            }

            if (size > MAX_CACHED_SIZE) {
                size = MAX_CACHED_SIZE;
            } else if (size < 2) {
                size = 2;
            }
        }

        // resize when clean
        public boolean clean() {
            if (list != null) {
                int sz = list.size();
                grade();
                hitCount = 0;
                missCount = 0;
                if (sz > size) {
                    // trim
                    synchronized (this) {
                        while (list.size() > size) {
                            list.pollFirst();
                        }
                    }

                    return true;
                }
            }
            return false;
        }
    }


    public static void enableDataPool(boolean enable) {
        enabled = enable;
    }
}

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
package org.qiyi.basecore.taskmanager.provider;

import android.util.SparseArray;

import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.other.TMLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @ Warning : Need more test!!!
 * A class used to get data cross modules .
 * Sample :
 * Data Provider implementation:
 * new DataProvider(2){
 *
 *             @Override
 *             protected Object onQuery(int id, Object... msg) {
 *                 return null;
 *             }
 *
 *             @Override
 *             protected boolean onDispatch(int id, Object... msg) {
 *                 return false;
 *             }
 *         }.install();
 *
 *
 * User :
 *  case 1: data dispatch:
 *         DataProvider.of(1).dispatch(2, data);
 *  case 2: Data queue:
 *         DataProvider.of(1).query()
 *  case 3: Data observer:
 *         DataProvider.of(1).observe();
 */
public abstract class DataProvider implements IProvider {
    private int providerId;
    private static final String TAG = "TM_DataProvider";
    private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    private static SparseArray<DataProvider> providers = new SparseArray<>();
    private final LinkedList<DataNotifier> queue = new LinkedList<>();
    private static HashMap<Class, Object> typeMatcher;

    static {
        typeMatcher = new HashMap<>(8, 1f);
        typeMatcher.put(int.class, 0);
//        typeMatcher.put(Integer.class, 0);
        typeMatcher.put(boolean.class, false);
//        typeMatcher.put(Boolean.class, false);
        typeMatcher.put(float.class, 0f);
//        typeMatcher.put(Float.class, 0f);
        typeMatcher.put(double.class, 0d);
//        typeMatcher.put(Double.class, 0d);
        typeMatcher.put(long.class, 0L);
//        typeMatcher.put(Long.class, 0L);
        typeMatcher.put(byte.class, (byte) 0);
//        typeMatcher.put(Byte.class, (byte) 0);
        typeMatcher.put(short.class, (short) 0);
//        typeMatcher.put(Short.class, (short) 0);
        typeMatcher.put(char.class, (char) 0);
//        typeMatcher.put(Character.class, (char) 0);
    }


    public static IProvider of(int id) {
        if (id == 0) {
            return DefaultProvider.get();
        }
        DataProvider provider = findProvider(id);
        return provider == null ? EmptyProvider.get() : provider;
    }

    public static DataProvider obtain() {
        return DefaultProvider.get();
    }

    public static DataProvider of(Object identifier) {

        if (identifier == null) {
            TMLog.e(TAG, "get provider with null identifier");
            return DefaultProvider.get();
        }

        DataProvider provider = findProvider(System.identityHashCode(identifier));
        return provider == null ? EmptyProvider.get() : provider;
    }


    public void install() {
        registerProvider(this);
    }

    public void unInstall() {
        unRegister(this);
    }


    /**
     * may crash if required for Long.class and then cast to int ;
     *
     * @param target
     * @param msg
     * @param <T>
     * @return
     */
    public <T> T getSingleMessage(Class<T> target, Object... msg) {
        if (msg != null && msg.length > 0) {
            Object var = msg[0];
            if (var != null) {
                if (target.isAssignableFrom(var.getClass())) {
                    return (T) var;
                }
            }
            // return null or 0
            if (target.isPrimitive()) {
                return (T) typeMatcher.get(target);
            }
        }
        return null;
    }


    private static DataProvider findProvider(int id) {
        readLock.lock();
        try {
            return providers.get(id);
        } finally {
            readLock.unlock();
        }
    }

    private static void unRegister(DataProvider provider) {
        writeLock.lock();
        try {
            providers.remove(provider.providerId);
        } finally {
            writeLock.unlock();
        }

    }

    static void registerProvider(DataProvider provider) {
        writeLock.lock();
        try {
            if (providers.indexOfKey(provider.providerId) < 0) {
                providers.put(provider.providerId, provider);
            } else if (TMLog.isDebug()) {
                throw new IllegalStateException(provider + " is already registered");
            } else {
                TMLog.e(TAG, provider + " is already registered");
            }
        } finally {
            writeLock.unlock();
        }

    }

    public DataProvider(int id) {
        providerId = id;
    }

    public DataProvider(String id) {
        providerId = System.identityHashCode(id);
    }


    /**
     * Call by other modules , this provider should return data by the specified id.
     * @param id
     * @param msg
     * @return
     */
    protected abstract Object onQuery(int id, Object... msg);

    /**
     * Call by User :  DataProvider.of(id).dispatch(dataId, mgs...):
     * data will be dispatched first to all observers, the to the data provider.
     * @param id
     * @param msg
     * @return
     */
    protected abstract boolean onDispatch(int id, Object... msg);

    public <T> T query(int id, Class<T> target, Object... msg) {
        Object object = onQuery(id, msg);
        if (object != null) {
            if (target.isAssignableFrom(object.getClass())) {
                return (T) object;
            }
        }
        // return null or 0
        if (target.isPrimitive()) {
            return (T) typeMatcher.get(target);
        }
        return null;
    }

    /**
     * 分发数据，而不存储数据
     *
     * @param id
     * @param data
     */
    public void dispatch(int id, Object... data) {
        if (!queue.isEmpty()) {
            DataNotifier notifiers[];
            synchronized (queue) {
                notifiers = new DataNotifier[queue.size()];
                queue.toArray(notifiers);
            }
            for (DataNotifier notifier : notifiers) {
                if (notifier.dispatch(id, data)) {
                    return;
                }
            }

        }
        onDispatch(id, data);
    }


    /**
     * @param notifier
     * @param ids      : 关注的任务事件; if ids is null : all event will be transmitted
     */
    public void observe(DataNotifier notifier, Object token, int... ids) {

        notifier.token = System.identityHashCode(token);
        synchronized (queue) {
            notifier.watchedIds(ids);
            queue.add(notifier);
        }
    }

    /**
     * @param token
     * @param ids   : 注册自己感兴趣的事件id
     */
    public void unObserve(Object token, int... ids) {
        int id = System.identityHashCode(token);
        synchronized (queue) {
            Iterator<DataNotifier> iterator = queue.iterator();
            while (iterator.hasNext()) {
                DataNotifier notifier = iterator.next();
                if (notifier.token == id && notifier.unwatchIds(ids)) {
                    iterator.remove();
                }
            }
        }

    }


    public abstract static class DataNotifier {
        int token;
        int ids[];
        int idCount;

        public abstract boolean onDataDispatch(int id, Object... dara);


        boolean dispatch(int id, Object... data) {
            if (idCount == 0) {
                return onDataDispatch(id, data);
            } else {
                for (int i : ids) {
                    if (i == id) {
                        if (onDataDispatch(id, data)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        void watchedIds(int... ids) {
            this.ids = ids;
            idCount = ids == null ? 0 : ids.length;
            if (ids != null && TMLog.isDebug() && TaskManager.isDebugCrashEnabled()) {
                for (int i : ids) {
                    if (i < 0) {
                        throw new IllegalStateException("registered ids of DataProvider must be > 0");
                    }
                }
            }
        }

        boolean unwatchIds(int... ids) {
            int origins[] = this.ids;
            if (idCount > 0 && ids != null && ids.length > 0) {
                int lens = origins.length;
                for (int i : ids) {
                    for (int j = 0; j < lens; j++) {
                        if (origins[j] == i) {
                            origins[j] = -1;
                            idCount--;
                        }
                    }

                }
            }
            return idCount == 0;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || (obj instanceof DataNotifier && ((DataNotifier) obj).token == token);
        }

        @Override
        public int hashCode() {
            return token;
        }
    }


}

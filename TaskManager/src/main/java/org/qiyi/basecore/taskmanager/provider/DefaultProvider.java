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

import java.util.TreeMap;

/**
 * Default implementation of data provider;
 * Kept as singleton. Used for put & get data Application wide.
 */
final class DefaultProvider extends DataProvider {

    private static DefaultProvider provider = new DefaultProvider();
    TreeMap<Integer, Object> map = new TreeMap<>();

    private DefaultProvider() {
        super(0);
        DataProvider.registerProvider(this);
    }

    @Override
    protected Object onQuery(int id, Object... msg) {
        return map.get(id);
    }

    @Override
    protected boolean onDispatch(int id, Object... msg) {
        Object var = msg;
        if (msg != null) {
            if (msg.length == 1) {
                var = msg[0];
            } else if (msg.length == 0) {
                var = null;
            }
        }
        map.put(id, var);
        return true;
    }

    public void install() {
        throw new IllegalStateException("no need to call install for Default data provider");
    }

    public void unInstall() {
        throw new IllegalStateException("Default data provider can't be uninstalled");
    }

    public static DefaultProvider get() {
        return provider;
    }

}

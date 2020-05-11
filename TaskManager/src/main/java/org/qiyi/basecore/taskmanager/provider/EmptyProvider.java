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

public class EmptyProvider extends DataProvider {
    private static EmptyProvider provider = new EmptyProvider();

    private EmptyProvider() {
        super(-1);
    }

    @Override
    protected Object onQuery(int id, Object... msg) {
        return null;
    }

    @Override
    protected boolean onDispatch(int id, Object... msg) {
        return false;
    }

    public void install() {
        // do nothing
    }

    public void unInstall() {
        // do nothing
    }

    public static DataProvider get() {
        return provider;
    }
}

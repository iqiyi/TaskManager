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
package org.qiyi.basecore.taskmanager;

public class RunnableTask extends Task {


    Runnable mRunnable;

    public RunnableTask(Runnable runnable) {
        super();
        mRunnable = runnable;
    }

    public RunnableTask(Runnable runnable, String name) {
        super(name);
        mRunnable = runnable;
    }

    public RunnableTask(Runnable runnable, String name, int id) {
        super(name, id);
        mRunnable = runnable;
    }

    @Override
    public void doTask() {
        mRunnable.run();
    }
}

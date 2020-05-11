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

import android.os.Looper;

public enum RunningThread {

    /**
     * 主线程
     */
    UI_THREAD,
    /**
     * 主线程 : 要求任务在主线程执行， 当有任务依赖的时候，最后的依赖任务的任务完成后，
     * 如果当前是主线程： 就直接执行
     * 如果当前是子线程就：post 到主线程执行。
     */
    UI_THREAD_SYNC,

    /**
     * 后台线程
     */
    BACKGROUND_THREAD

    /**
     * 同步执行：在当前线程执行，或者在
     */
    , BACKGROUND_THREAD_SYNC;


    public boolean isRunningInUIThread() {
        return (this == UI_THREAD || UI_THREAD_SYNC == this);
    }


    /**
     * 如果期望在主线程执行，那么当前线程需要是主线程
     *
     * @return
     */
    public boolean isRunningThreadCorrect() {
        return (Looper.myLooper() == Looper.getMainLooper()) == isRunningInUIThread();
    }

}

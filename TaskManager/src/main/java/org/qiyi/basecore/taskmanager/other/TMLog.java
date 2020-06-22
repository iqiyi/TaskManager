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
package org.qiyi.basecore.taskmanager.other;

import org.qiyi.basecore.taskmanager.deliver.ITracker;

public class TMLog {
    static ITracker loger;
    public final static int LOG_D = 1;
    public final static int LOG_E = 2;

    /**
     * DEBUG类型log，打印不确定性个数参数,加入logBuffer
     *
     * @param LOG_CLASS_NAME
     * @param msg
     */
    public static void d(String LOG_CLASS_NAME, Object... msg) {
        if (loger != null) {
            loger.track(LOG_D, LOG_CLASS_NAME, msg);
            return;
        }
        if (msg != null && msg.length > 0) {
            for (Object var : msg) {
                android.util.Log.d(LOG_CLASS_NAME, var.toString());
            }
        }
    }

    public static boolean isDebug() {
        return loger != null ? loger.isDebug() : false;
    }

    public static void log(String LOG_CLASS_NAME, Object msg) {
        if (loger != null) {
            loger.track(LOG_CLASS_NAME, msg);
            return;
        }

        if (msg != null) {
            android.util.Log.d(LOG_CLASS_NAME, msg.toString());
        }
    }

    /**
     * DEBUG类型log，打印不确定性个数参数,加入logBuffer
     *
     * @param LOG_CLASS_NAME
     * @param msg
     */
    public static void e(String LOG_CLASS_NAME, Object... msg) {

        if (loger != null) {
            loger.track(LOG_E, LOG_CLASS_NAME, msg);
            return;
        }
        if (msg != null && msg.length > 0) {
            for (Object var : msg) {
                android.util.Log.e(LOG_CLASS_NAME, var.toString());
            }
        }

    }

    public static void setLogger(ITracker definedLogger) {
        loger = definedLogger;
    }

}

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

import android.os.SystemClock;

public class TimeUtil {
    static String TAG = "TM_TimeUtil";
    static ThreadLocal<ND> local = new ThreadLocal<>();

    public static void setTag(String var) {
        TAG = var;
    }

    public static void logd(String msg) {

        ND nd = local.get();
        if (nd == null) {
            local.set(new ND());
            TMLog.d(TAG, msg);
        } else {
            nd.logd(msg);
        }
    }

    public static void loge(String msg) {
        ND nd = local.get();
        if (nd == null) {
            local.set(new ND());
            TMLog.d(TAG, msg);
        } else {
            nd.logd(msg);
        }
    }

    static class ND {
        long s1;
        long s2;
        long ds1;

        public ND() {
            s1 = System.currentTimeMillis();
            s2 = SystemClock.currentThreadTimeMillis();
            ds1 = s1;
        }


        private void appendInfo(StringBuilder builder) {
            builder.append("threadT: " + (SystemClock.currentThreadTimeMillis() - s2));
            builder.append(" systemT:  " + (System.currentTimeMillis() - s1));
            builder.append(" gap:st: " + (System.currentTimeMillis() - ds1));
            builder.append(' ');
        }

        public void logd(String msg) {
            StringBuilder builder = new StringBuilder();
            appendInfo(builder);
            builder.append(msg);
            TMLog.d(TAG, builder.toString());
            ds1 = System.currentTimeMillis();
        }

        public void loeg(String msg) {
            StringBuilder builder = new StringBuilder();
            appendInfo(builder);
            builder.append(msg);
            TMLog.e(TAG, builder.toString());
            ds1 = System.currentTimeMillis();
        }


    }
}

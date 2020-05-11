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


public class LogUtils {

    // 返回這個調用的上一個調用
    public static String getTMCallInfo(String extra, String names) {
        Throwable throwable = new Throwable();
        StackTraceElement traceElement[] = throwable.getStackTrace();
        if (traceElement != null) {
            StringBuilder log = new StringBuilder();
            StackTraceElement lastElement = null;
            for (StackTraceElement element : traceElement) {
                if (String.valueOf(element).contains(names)) {
                    lastElement = element;
                    continue;
                }
                if (String.valueOf(element).startsWith("android.")
                        || String.valueOf(element).startsWith("java.")
                        || String.valueOf(element).startsWith("com.android.")) {
                    continue;
                }
                log.append(element).append('\n');
            }
            log.insert(0, '\n');
            log.insert(0, lastElement);
            log.insert(0, " ");
            log.insert(0, extra);
            return log.toString();
        }
        return "";
    }
}

package com.qiyi.tm.demo;

import org.qiyi.basecore.taskmanager.other.TMLog;

/**
 * used to check sum :
 * if task is finished as intend to
 */
public class SumChecker {
    private static final String TAG = "SumChecker";
    int SUM = 0;
    int sum = 0;

    public SumChecker() {

    }

    public void count() {
        SUM++;
    }

    public synchronized void increase() {
        sum++;
    }

    public void verriyfy() {
        if (SUM != sum) {
            throw new IllegalStateException(SUM + " nnot same " + sum);
        } else {
            TMLog.e("DemoApp", SUM + " same " + sum);
        }
    }


}

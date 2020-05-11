package com.qiyi.tm.demo.test;

import org.qiyi.basecore.taskmanager.TM;

public class StateCheck {
    private int state;
    private int dataTrue;
    private StateChange callback;
    private boolean defaultState;

    public StateCheck(int size) {
        state = 0;
        dataTrue = (1 << size) - 1;
    }

    public StateCheck(int size, boolean dft) {
        this(size);
        defaultState = dft;
    }

    public synchronized boolean setState(int index, boolean enable) {
        if (enable) {
            state |= (1 << index);
        } else {
            state = state & (~(1 << index));
        }
        boolean rs = state == dataTrue;
        if (callback != null && rs != defaultState) {
            callback.onStateChange(rs);
        }
        defaultState = rs;
        return defaultState;
    }

    public boolean isValid() {
        return state == dataTrue;
    }

    public void checkValids() {
        TM.crashIf(state != dataTrue, "some task may not run!!! " + Integer.toBinaryString(state));
    }


    public void setStateChangeCallback(StateChange change) {
        callback = change;
    }

    public interface StateChange {
        public void onStateChange(boolean enable);
    }


    public boolean getState(int index) {
        return (state & (1 << index)) > 0;
    }

    public void add(int index) {

        if (getState(index)) {
            throw new IllegalStateException("this task already done " + index);
        }
        setState(index, true);
    }

}

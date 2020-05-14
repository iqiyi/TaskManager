package com.qiyi.tm.demo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.qiyi.basecore.taskmanager.Task;
import org.qiyi.basecore.taskmanager.TaskManager;
import org.qiyi.basecore.taskmanager.deliver.ITracker;
import org.qiyi.basecore.taskmanager.iface.ITaskManagerConfig;
import org.qiyi.basecore.taskmanager.iface.ITaskStateListener;
import org.qiyi.basecore.taskmanager.other.TMLog;

public class DemoApp extends Application {
    private static final String TAG = "TM_TaskManager";

    @Override
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        initTaskManager();
    }


    @Override
    public void onCreate() {
        super.onCreate();
//        new LensInitTask(this).executeSync();
    }

    /**
     * TM config is optional;
     */
    private void initTaskManager() {
        TaskManager.config()
                .enableFullLog(false)
                .setLogTracker(createTracker())
                .enableObjectReuse(true)
                .setDefaultTimeOut(3000)
                .enableMemoryCleanUp(true)
                .setIdleTaskOffset(100)
                .setThreadPoolStrategy(ITaskManagerConfig.STRATEGY_THREAD_GROUP)
                .initTaskManager(this);
        registerCallback();
    }


    private ITracker createTracker() {
        return new ITracker() {
            @Override
            public void track(int level, String tag, Object... msg) {
                logMessage(level == TMLog.LOG_E, tag, msg);
            }

            @Override
            public void track(Object... messages) {
                logMessage(false, TAG, messages);
            }

            @Override
            public void trackCritical(Object... messages) {
                logMessage(true, TAG, messages);
            }

            @Override
            public void printDump() {

            }

            @Override
            public void deliver(int type) {

            }

            // set true : so that will crash ,when some unoproperate use
            @Override
            public boolean isDebug() {
                // return debug mode setting
                return true;
            }
        };
    }

    private void logMessage(boolean loge, String tag, Object... msg) {
        if (msg != null && msg.length > 0) {
            if (msg.length == 1) {
                if (loge) {
                    Log.e(tag, msg[0].toString());
                } else {
                    Log.d(tag, msg[0].toString());
                }
            } else {
                StringBuilder builder = new StringBuilder();
                for (Object o : msg) {
                    builder.append(o.toString());
                    builder.append(" ");
                }

                if (loge) {
                    Log.e(tag, builder.toString());
                } else {
                    Log.d(tag, builder.toString());
                }

            }
        }
    }

    //optional
    private void registerCallback() {
        TaskManager.getInstance().registerTaskStateListener(new ITaskStateListener() {
            @Override
            public void onTaskStateChange(Task task, int state) {
                if (state == ITaskStateListener.ON_START) {
                    Log.d(TAG, "started <<<<<<< " + task);
                } else if (state == ITaskStateListener.ON_FINISHED) {
                    Log.d(TAG, "               Finished !!!!!!" + task);
                } else {
                    Log.e(TAG, "onTaskStateChange Canceled: " + task);
                }
            }
        });

    }

}

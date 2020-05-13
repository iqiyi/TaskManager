package com.qiyi.tm.demo;

import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.Gravity;
import android.widget.TextView;

import com.qiyi.tm.demo.test.AsyncTest;
import com.qiyi.tm.demo.test.CancelTest;
import com.qiyi.tm.demo.test.DependantAfterTest;
import com.qiyi.tm.demo.test.DependantTest;
import com.qiyi.tm.demo.test.EventTaskTest;
import com.qiyi.tm.demo.test.IdleTest;
import com.qiyi.tm.demo.test.MultiSyncTest;
import com.qiyi.tm.demo.test.NeedSyncTest;
import com.qiyi.tm.demo.test.OrdependTest;
import com.qiyi.tm.demo.test.ParaTest;
import com.qiyi.tm.demo.test.TaskResultTest;
import com.qiyi.tm.demo.test.Test;
import com.qiyi.tm.demo.test.TestExecuteNow;
import com.qiyi.tm.demo.test.TestReject;
import com.qiyi.tm.demo.test.TestSerial;
import com.qiyi.tm.demo.test.TriggerEventTest;

public class MainActivity extends AppCompatActivity {
    final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textView = new TextView(this);
        textView.setBackgroundColor(Color.CYAN);
        textView.setTextSize(25);
        textView.setGravity(Gravity.CENTER);
        textView.setText("TaskManager Demo");
        setContentView(textView);
        createTest(15).doTest();
    }

    private Test createTest(int id) {
        switch (id) {
            case 1:
                //test to post a task to run in background thread
                return new AsyncTest();
            case 2:
                //test to cancel a task by id or token
                return new CancelTest();
            case 3:
                //test for api: DependantAfterTest
                return new DependantAfterTest();
            case 4:
                //test for task dependency relationship
                return new DependantTest();
            case 5:
                return new EventTaskTest();
            case 6:
                return new IdleTest();
            case 7:
                return new MultiSyncTest();
            case 8:
                return new NeedSyncTest();
            case 9:
                return new OrdependTest();
            case 10:
                return new ParaTest();
            case 11:
                return new TestExecuteNow();
            case 12:
                return new TaskResultTest();
            case 13:
                return new TestReject();
            case 14:
                return new TestSerial();
            case 15:
                return new TriggerEventTest();
            default:
                return new AsyncTest();
        }


    }

}

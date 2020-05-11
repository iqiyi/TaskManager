package com.qiyi.tm.demo.test;

import android.graphics.Point;

import org.qiyi.basecore.taskmanager.other.TMLog;
import org.qiyi.basecore.taskmanager.provider.DataProvider;

public class DataProviderTest extends Test {

    private String TAG = "DataProviderTest";

    @Override
    public void doTest() {
        new DataProvider(21) {
            @Override
            protected Object onQuery(int id, Object... msg) {
                return null;
            }

            @Override
            protected boolean onDispatch(int id, Object... msg) {
                return false;
            }
        }.install();

        testGet();
    }


    private void testGet() {
        Point pf = DataProvider.of(21).query(1, Point.class, new Point(3, 6));
        int a = DataProvider.of(21).query(2, int.class);
        boolean b = DataProvider.of(21).query(3, boolean.class);
        byte c = DataProvider.of(21).query(4, byte.class);
        Double d = DataProvider.of(21).query(5, Double.class);
        TMLog.d(TAG, "data is : " + a + " " + b + " " + c + " " + d);
    }

    private void testObserve() {

        DataProvider.of(21).observe(new DataProvider.DataNotifier() {
            @Override
            public boolean onDataDispatch(int id, Object... dara) {
                return false;
            }
        }, this);

    }
}

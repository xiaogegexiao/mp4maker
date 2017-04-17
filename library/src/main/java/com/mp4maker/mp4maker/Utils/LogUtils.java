package com.mp4maker.mp4maker.Utils;

import android.util.Log;

import com.mp4maker.mp4maker.BuildConfig;

/**
 * Created by xiaomei on 23/01/2017.
 */

public class LogUtils {
    private static final int MAX_LOG_TAG_LENGTH = 23;

    public static String makeLogTag(Class cls) {
        return makeLogTag(cls.getSimpleName());
    }

    public static String makeLogTag(String str) {
        if (str.length() > MAX_LOG_TAG_LENGTH) {
            return str.substring(0, MAX_LOG_TAG_LENGTH - 1);
        }

        return str;
    }

    public static void D(String TAG, String LOG) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG);
        }
    }

    public static void E(String TAG, String LOG) {
        Log.e(TAG, LOG);
    }
    public static void V(String TAG, String LOG) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, LOG);
        }
    }
    public static void I(String TAG, String LOG) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, LOG);
        }
    }
}

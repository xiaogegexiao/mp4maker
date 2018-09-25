package com.mp4maker.test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.mp4maker.mp4maker.Mp4Maker;
import com.mp4maker.mp4maker.Utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements Mp4Maker.LoadListener, Mp4Maker.Mp4MakeListener {

    public static final String TAG = LogUtils.makeLogTag(MainActivity.class);

    private static final int REQUEST_WRITE_EXTERNAL_PERMISSION = 1;
    private Mp4Maker mMp4Maker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        List<String> imageUrls = new ArrayList<>();
        File testImages = new File("/sdcard/test-images");
        String[] imageNames = testImages.list();
        if (imageNames != null) {
            for (String imageName : imageNames) {
                imageUrls.add(testImages.getAbsolutePath() + File.separator + imageName);
            }
        }

        mMp4Maker = new Mp4Maker
                .Builder(getApplicationContext())
                .loadListener(this)
                .makeListener(this)
                .imageUrls(imageUrls)
                .name("test")
                .build();

        mMp4Maker.start();
    }

    @Override
    public void onLoadStart() {
        LogUtils.D(TAG, "load start");
    }

    @Override
    public void onLoadAmount(int amount) {
        LogUtils.D(TAG, "load " + amount + " snapshots");
    }

    @Override
    public void onLoadEnd() {
        LogUtils.D(TAG, "load end");
    }

    @Override
    public void onMakeStart() {
        LogUtils.D(TAG, "make start");
    }

    @Override
    public void onProcessedAmount(int amount) {
        LogUtils.D(TAG, "make " + amount + " snapshots");
    }

    @Override
    public void onMakeEnd(String mp4Path) {
        LogUtils.D(TAG, "make end, file saved as " + mp4Path);
    }

    @Override
    public void onPermissionRequired(String permission) {
        switch (permission) {
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{permission},
                        REQUEST_WRITE_EXTERNAL_PERMISSION);
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    mMp4Maker.start();
                } else {
                    Toast.makeText(this, "write external permission denied", Toast.LENGTH_SHORT).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}

package com.mp4maker.test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
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
        List<Uri> imageUrls = new ArrayList<>();
        File testImages = new File("/sdcard/test-images");
        String[] imageNames = testImages.list();
        if (imageNames != null) {
            for (String imageName : imageNames) {
                imageUrls.add(Uri.fromFile(new File(testImages.getAbsolutePath() + File.separator + imageName)));
            }
        }

        String[] netImages = new String[]{
                "https://www.easonmusicstore.com/webshaper/pcm/gallery/lg/8618935b6031c4f2ca613b60eea0c9881439613914-lg.jpg",
                "https://www.easonmusicstore.com/webshaper/pcm/gallery/lg/818a8cfab8416a57ad015301ba88bee01439613967-lg.jpg",
                "https://www.easonmusicstore.com/webshaper/pcm/gallery/lg/ab0aa6e47747200c7e44b3d1fadc72911430037070-lg.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Classical_Pianist_Di_Xiao_11.jpg/1200px-Classical_Pianist_Di_Xiao_11.jpg",
                "https://ae01.alicdn.com/kf/HTB1aTIsLVXXXXaGXFXXq6xXFXXXU/Professional-Purple-Bamboo-Flute-Xiao-Instrument-Chinese-Shakuhachi-China-classic-traditional-music-instrument.jpg",
                "https://www.redmusicshop.com/image/cache/data/musical_instrument/xiao/xiao_short_p_1_2-700x500.jpg"
        };
        for (String netImage : netImages) {
            imageUrls.add(Uri.parse(netImage));
        }

        try {
            mMp4Maker = new Mp4Maker
                    .Builder(getApplicationContext())
                    .size(360, 480)
                    .frameRate(5)
                    .loadListener(this)
                    .makeListener(this)
                    .imageUrls(imageUrls)
                    .name("test")
                    .build();
            mMp4Maker.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

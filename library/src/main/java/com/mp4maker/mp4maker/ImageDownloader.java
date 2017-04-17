package com.mp4maker.mp4maker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.Downloader;

import java.io.File;
import java.io.IOException;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

/**
 * Created by xiaomei on 21/01/2017.
 */

public class ImageDownloader {
    private static final String PICASSO_CACHE = "picasso-cache";

    private OkHttpClient mOkHttpClient;
    private OkHttp3Downloader mOkHttp3Downloader;
    private File mCacheDir;
    private Cache mCache;

    public OkHttp3Downloader getDownloader() {
        return mOkHttp3Downloader;
    }

    private static File defaultCacheDir(Context context) {
        File cache = new File(context.getApplicationContext().getCacheDir(), PICASSO_CACHE);
        if (!cache.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
        }
        return cache;
    }

    public ImageDownloader(Context context, long maxSize) {
        mCacheDir = defaultCacheDir(context);
        mCache = new Cache(mCacheDir, maxSize);
        mOkHttpClient = new OkHttpClient.Builder()
                .cache(mCache)
                .build();
        mOkHttp3Downloader = new OkHttp3Downloader(mOkHttpClient);
    }

    public boolean isCached(String url) {
        try {
            Downloader.Response response = mOkHttp3Downloader.load(Uri.parse(url), 1 << 2);
            return response.getContentLength() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public Bitmap getCachedBitmap(String url) {
        Bitmap bitmap = null;
        try {
            Downloader.Response response = mOkHttp3Downloader.load(Uri.parse(url), 1 << 2);
            bitmap = BitmapFactory.decodeStream(response.getInputStream());
        } catch (IOException e) {
            // Ignore
        }

        return bitmap;
    }

    public void clearCache() {
        try {
            mCache.evictAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

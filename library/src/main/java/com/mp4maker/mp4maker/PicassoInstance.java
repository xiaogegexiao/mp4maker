package com.mp4maker.mp4maker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;

import okhttp3.Cache;
import okhttp3.OkHttpClient;

/**
 * Created by xiaomei on 21/01/2017.
 */

public class PicassoInstance {

    public static PicassoInstance getInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        }
        synchronized (PicassoInstance.class) {
            if (sInstance == null) {
                sInstance = new PicassoInstance(context);
            }
        }
        return sInstance;
    }

    private static final String PICASSO_CACHE = "picasso-cache";

    private static PicassoInstance sInstance;
    private Picasso picasso;
    private Cache mCache;
    private OkHttp3Downloader mOkHttp3Downloader;

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

    private PicassoInstance(Context context) {
        mCache = new Cache(defaultCacheDir(context), Integer.MAX_VALUE);
        mOkHttp3Downloader = new OkHttp3Downloader(new OkHttpClient.Builder()
                .cache(mCache)
                .build());

        picasso = new Picasso.Builder(context)
                .downloader(mOkHttp3Downloader)
                .memoryCache(new LruCache(context))
                .indicatorsEnabled(BuildConfig.DEBUG)
                .build();
    }

    public Picasso getPicasso() {
        return picasso;
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

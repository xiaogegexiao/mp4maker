package com.mp4maker.mp4maker.injectmodules;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.mp4maker.mp4maker.BuildConfig;
import com.mp4maker.mp4maker.ImageDownloader;
import com.mp4maker.mp4maker.Mp4Maker;
import com.mp4maker.mp4maker.cts.VideoMaker;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Created by xiaomei on 21/01/2017.
 */

@Module(
        injects = {
                Mp4Maker.class
        },
        library = true,
        complete = false
)

public class DaggerModule {

    private static final long DISK_CACHE_SIZE = 100 * 1024 * 1024;

    private Context mCtx;

    public DaggerModule(Context context) {
        this.mCtx = context;
    }

    @Provides
    @Singleton
    LruCache provideImageCache() {
        return new LruCache(mCtx);
    }

    @Provides
    @Singleton
    ImageDownloader provideImageDownloader() {
        return new ImageDownloader(mCtx, DISK_CACHE_SIZE);
    }

    @Provides
    @Singleton
    Picasso providePicasso(ImageDownloader downloader, LruCache cache) {
        Picasso.Builder builder = new Picasso.Builder(mCtx)
                .downloader(downloader.getDownloader())
                .memoryCache(cache)
                .indicatorsEnabled(BuildConfig.DEBUG);
        return builder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Provides
    @Singleton
    VideoMaker provideVideoMaker(Picasso picasso) {
        return new VideoMaker(mCtx, picasso);
    }
}

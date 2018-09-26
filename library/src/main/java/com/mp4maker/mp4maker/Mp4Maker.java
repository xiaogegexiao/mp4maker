package com.mp4maker.mp4maker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.mp4maker.mp4maker.cts.VideoMaker;
import com.mp4maker.mp4maker.models.VideoMakeEvent;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by xiaomei on 20/12/2016.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Mp4Maker {
    private static final String TAG = Mp4Maker.class.getSimpleName();

    private Context mContext;
    private final LoadListener loadListener;
    private final String videoName;
    private final int width;
    private final int height;
    private final int frameRate;
    private final Mp4MakeListener makeListener;
    private final VideoMaker mVideoMaker;
    private final List<Uri> imageUrls;
    private final Picasso mPicasso;

    Mp4Maker(Context context, String videoName, int width, int height, int frameRate, LoadListener loadListener, Mp4MakeListener makeListener, List<Uri> imageUrls) {
        this.makeListener = makeListener;
        this.imageUrls = imageUrls;
        this.videoName = videoName;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.loadListener = loadListener;
        this.mContext = context;
        this.mPicasso = PicassoInstance.getInstance(context).getPicasso();
        this.mVideoMaker = new VideoMaker(context, mPicasso);
    }

    public static class Builder {
        private final Context context;
        private int width = -1;
        private int height = -1;
        private int frameRate = -1;
        private Mp4Maker.LoadListener loadListener;
        private Mp4Maker.Mp4MakeListener mp4MakeListener;
        private List<Uri> imageUrls;
        private String videoName;

        /**
         * Start building a new {@link Picasso} instance.
         */
        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder frameRate(int frameRate) {
            this.frameRate = frameRate;
            return this;
        }

        public Builder imageUrls(List<Uri> imageUrls) {
            this.imageUrls = imageUrls;
            return this;
        }

        public Builder loadListener(LoadListener loadListener) {
            this.loadListener = loadListener;
            return this;
        }

        public Builder makeListener(Mp4MakeListener listener) {
            this.mp4MakeListener = listener;
            return this;
        }

        public Builder name(String name) {
            this.videoName = name;
            return this;
        }

        public Mp4Maker build() throws Exception{
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("invalid video width and height value (" + width + ", " + height + "). You need to call size to set the width and height");
            }
            if (frameRate <= 0) {
                throw new IllegalArgumentException("invalid video frameRate value (" + frameRate + "). You need to call frameRate to set the frame rate");
            }
            return new Mp4Maker(context, videoName, width, height, frameRate, loadListener, mp4MakeListener, imageUrls);
        }
    }

    public interface LoadListener {
        void onLoadStart();

        void onLoadAmount(int total, int currentIndex, int amount);

        void onLoadEnd();
    }

    public interface Mp4MakeListener {
        void onProcessStart();

        void onProcessedAmount(int amount);

        void onProcessEnd(String mp4Path);

        /**
         * permission required for making mp4.
         * <p>
         *   Manifest.permission.WRITE_EXTERNAL_STORAGE will be required when trying to write mp4 file to sdcard
         * </p>
         * <p>
         *   Manifest.permission.WRITE_EXTERNAL_STORAGE will be required when trying to write mp4 file to sdcard
         * </p>
         *
         * @param permission
         */
        void onPermissionRequired(String permission);
    }

    public void start() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            makeListener.onPermissionRequired(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            makeListener.onPermissionRequired(Manifest.permission.READ_EXTERNAL_STORAGE);
            return;
        }
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            makeListener.onPermissionRequired(Manifest.permission.INTERNET);
            return;
        }
        Disposable disposable = Observable.fromIterable(imageUrls)
                .subscribeOn(Schedulers.io())
                .flatMap(new Function<Uri, ObservableSource<Bitmap>>() {
                    @Override
                    public ObservableSource<Bitmap> apply(final Uri sUri) throws Exception {
                        return Observable.create(new ObservableOnSubscribe<Bitmap>() {
                            @Override
                            public void subscribe(final ObservableEmitter<Bitmap> emitter) throws Exception {
                                try {
                                    Bitmap bm = mPicasso.load(sUri).resize(width, height).get();
                                    emitter.onNext(bm);
                                    emitter.onComplete();
                                } catch (Exception e) {
                                    Log.e(TAG, "failed to decode bitmap " + sUri, e);
                                    emitter.onComplete();
                                }
                            }
                        });
                    }
                })
                .collectInto(new ArrayList<Bitmap>(), new BiConsumer<ArrayList<Bitmap>, Bitmap>() {
                    @Override
                    public void accept(ArrayList<Bitmap> bms, Bitmap bitmap) throws Exception {
                        bms.add(bitmap);
                    }
                })
                .subscribe(new Consumer<List<Bitmap>>() {
                    @Override
                    public void accept(List<Bitmap> bitmaps) throws Exception {
                        try {
                            mVideoMaker.encodeDecodeVideoFromSnapshotListToBuffer(width, height, frameRate, bitmaps, videoName, new VideoMaker.VideoMakingListener() {

                                @Override
                                public void onVideoProcessingStart() {
                                    if (makeListener != null) {
                                        makeListener.onProcessStart();
                                    }
                                }

                                @Override
                                public void onVideoProcessing(int maxSnapshotNum, int currentSnapshotNum) {
                                    if (makeListener != null) {
                                        makeListener.onProcessedAmount(currentSnapshotNum);
                                    }
                                }

                                @Override
                                public void onVideoCreated(String videoPath) {
                                    if (makeListener != null) {
                                        makeListener.onProcessEnd(videoPath);
                                    }
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                    }
                });
    }
}

package com.mp4maker.mp4maker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.mp4maker.mp4maker.cts.VideoMaker;
import com.mp4maker.mp4maker.injectmodules.DaggerModule;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.ObjectGraph;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
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
    private static Mp4Maker mInstance;
    private final LoadListener loadListener;
    private final String videoName;
    private final Mp4MakeListener makeListener;
    private final List<String> imageUrls;
    private final ObjectGraph mp4MakerGraph;

    @Inject
    Picasso mPicasso;

    @Inject
    ImageDownloader mImageDownloader;

    @Inject
    VideoMaker mVideoMaker;

    Mp4Maker(Context mContext, String videoName, LoadListener loadListener, Mp4MakeListener makeListener, List<String> imageUrls) {
        this.makeListener = makeListener;
        this.imageUrls = imageUrls;
        this.videoName = videoName;
        this.loadListener = loadListener;
        this.mContext = mContext;
        mp4MakerGraph = ObjectGraph.create(getModules().toArray());
        mp4MakerGraph.inject(this);
    }

    public static Mp4Maker getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new Mp4Maker.Builder(context).build();
        }
        return mInstance;
    }

    public static class Builder {
        private final Context context;
        private Mp4Maker.LoadListener loadListener;
        private Mp4Maker.Mp4MakeListener mp4MakeListener;
        private List<String> imageUrls;
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

        public Builder imageUrls(List<String> imageUrls) {
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

        public Mp4Maker build() {
            return new Mp4Maker(context, videoName, loadListener, mp4MakeListener, imageUrls);
        }
    }

    public interface LoadListener {
        void onLoadStart();

        void onLoadAmount(int amount);

        void onLoadEnd();
    }

    public interface Mp4MakeListener {
        void onMakeStart();

        void onProcessedAmount(int amount);

        void onMakeEnd(String mp4Path);

        void onPermissionRequired(String permission);
    }

    public void start() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            makeListener.onPermissionRequired(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        Disposable disposable = Observable.fromIterable(imageUrls)
                .subscribeOn(Schedulers.io())
                .flatMap(new Function<String, ObservableSource<Bitmap>>() {
                    @Override
                    public ObservableSource<Bitmap> apply(final String s) throws Exception {
                        return Observable.create(new ObservableOnSubscribe<Bitmap>() {
                            @Override
                            public void subscribe(final ObservableEmitter<Bitmap> emitter) throws Exception {
                                try {
                                    Bitmap bm = mPicasso.load(new File(s)).get();
                                    emitter.onNext(bm);
                                    emitter.onComplete();
                                } catch (Exception e) {
                                    Log.e(TAG, "failed to decode bitmap " + s);
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
                            mVideoMaker.encodeDecodeVideoFromSnapshotListToBuffer(480, 800, 1, bitmaps, videoName, new VideoMaker.VideoMakingListener() {

                                @Override
                                public void onVideoProcessing(int maxSnapshotNum, int currentSnapshotNum) {
                                    if (makeListener != null) {
                                        makeListener.onProcessedAmount(currentSnapshotNum);
                                    }
                                }

                                @Override
                                public void onVideoCreated(String videoPath) {
                                    if (makeListener != null) {
                                        makeListener.onMakeEnd(videoPath);
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

//        final int[] downloadedSnashotNum = new int[1];
//        Observable.fromIterable(imageUrls).flatMap(new Function<String, Observable<Boolean>>() {
//            @Override
//            public Observable<Boolean> apply(final String imageUrl) {
//                return Observable.create(new ObservableOnSubscribe<Boolean>() {
//                    @Override
//                    public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
//                        boolean isCached = mImageDownloader.isCached(imageUrl);
//                        if (isCached) {
//                            emitter.onNext(true);
//                            emitter.onComplete();
//                        } else {
//                            try {
//                                mPicasso.load(imageUrl).get();
//                                emitter.onNext(true);
//                            } catch (IOException e) {
//                                emitter.onNext(false);
//                            }
//                            emitter.onComplete();
//                        }
//                    }
//                }).subscribeOn(Schedulers.io());
//            }
//        }).map(new Function<Boolean, Boolean>() {
//            @Override
//            public Boolean apply(Boolean aBoolean) {
//                downloadedSnashotNum[0]++;
//                if (loadListener != null) {
//                    if (downloadedSnashotNum[0] == 1) {
//                        loadListener.onLoadStart();
//                    } else if (downloadedSnashotNum[0] == imageUrls.size() - 1) {
//                        loadListener.onLoadEnd();
//                    } else {
//                        loadListener.onLoadAmount(downloadedSnashotNum[0]);
//                    }
//                }
//                return aBoolean;
//            }
//        }).toList().toObservable().map(new Function<List<Boolean>, List<Boolean>>() {
//            @Override
//            public List<Boolean> apply(List<Boolean> booleanList) {
//                try {
//                    mVideoMaker.encodeDecodeVideoFromSnapshotListToBuffer(imageUrls, booleanList, videoName, new VideoMaker.VideoMakingListener() {
//
//                        @Override
//                        public void onVideoProcessing(int maxSnapshotNum, int currentSnapshotNum) {
//                            if (makeListener != null) {
//                                makeListener.onProcessedAmount(currentSnapshotNum);
//                            }
//                        }
//
//                        @Override
//                        public void onVideoCreated(String videoPath) {
//                            if (makeListener != null) {
//                                makeListener.onMakeEnd(videoPath);
//                            }
//                        }
//                    });
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                return booleanList;
//            }
//        }).subscribe(new Consumer<List<Boolean>>() {
//            @Override
//            public void accept(List<Boolean> booleen) throws Exception {
//
//            }
//        }, new Consumer<Throwable>() {
//            @Override
//            public void accept(Throwable throwable) throws Exception {
//
//            }
//        }, new Action() {
//            @Override
//            public void run() throws Exception {
//
//            }
//        });
    }

    /**
     * A list of modules to use for the application graph. Subclasses can override this method to
     * provide additional modules provided they call {@code super.getModules()}.
     */
    protected List<Object> getModules() {
        return Arrays.<Object>asList(new DaggerModule(mContext));
    }
}

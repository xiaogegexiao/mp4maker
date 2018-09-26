package com.mp4maker.mp4maker.cts;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Type;
import android.util.Log;

import com.mp4maker.mp4maker.R;
import com.mp4maker.mp4maker.Utils.BitmapUtils;
import com.mp4maker.mp4maker.Utils.FileUtils;
import com.mp4maker.mp4maker.Utils.LogUtils;
import com.squareup.picasso.Picasso;
import com.xiaogegexiao.mp4maker.ScriptC_RGBToYUV;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static com.mp4maker.mp4maker.Utils.Utils.assertTrue;
import static com.mp4maker.mp4maker.Utils.Utils.fail;

/**
 * Created by xiaomei on 23/11/2016.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoMaker {

    private static final String TAG = LogUtils.makeLogTag(VideoMaker.class);
    private static final boolean VERBOSE = true;           // lots of logging
    private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE_FOR_OVER_50_FRAMES = 10;               // 5fps
    private static final int FRAME_RATE_FOR_LESS_50_FRAMES = 5;               // 5fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    // size of a frame, in pixels
    private int mWidth = -1;
    private int mHeight = -1;
    // bit rate, in bits per second
    private int mBitRate = -1;
    // frame rate, frames per second
    private int mFrameRate = 0;
    // largest color component delta seen (i.e. actual vs. expected)
    private int mLargestColorDelta;

    private List<Bitmap> mBitmapList;
    private int mSnapshotNum;
    private Context mContext;
    private Picasso mPicasso;
    private VideoMakingListener mVideoMakingListener;
    private boolean mInterrupted = false;

    private Allocation mRgbAllocation;
    private Allocation mYuvAllocation;
    private RenderScript rs;
    private ScriptC_RGBToYUV mScriptC;

    public VideoMaker(Context context, Picasso picasso) {
        this.mContext = context;
        this.mPicasso = picasso;
        rs = RenderScript.create(mContext);
        mScriptC = new ScriptC_RGBToYUV(rs);

    }

    public interface VideoMakingListener {
        void onVideoProcessingStart();

        void onVideoProcessing(int maxSnapshotNum, int currentSnapshotNum);

        void onVideoCreated(String videoPath);
    }

    /**
     * Tests streaming of AVC video through the encoder and decoder.  Data is encoded from
     * a series of byte[] buffers and decoded into ByteBuffers.  The output is checked for
     * validity.
     */
    public void encodeDecodeVideoFromSnapshotListToBuffer(
            int width,
            int height,
            int frameRate,
            List<Bitmap> bitmapList,
            String videoName,
            VideoMakingListener videoMakingListener) throws Exception {
        mInterrupted = false;
        mBitmapList = bitmapList;
        mVideoMakingListener = videoMakingListener;
        mSnapshotNum = bitmapList.size();
        setParameters(
                width,
                height,
                1800000,
                frameRate <= 0 ?
                        (mSnapshotNum > 50 ?
                                FRAME_RATE_FOR_OVER_50_FRAMES :
                                FRAME_RATE_FOR_LESS_50_FRAMES) : frameRate);
        encodeVideoFromBuffer(false, videoName);
    }

    /**
     * Sets the desired frame size and bit rate.
     */
    private void setParameters(int width, int height, int bitRate, int frameRate) {
        if ((width % 16) != 0 || (height % 16) != 0) {
            Log.w(TAG, "WARNING: width or height not multiple of 16");
        }
        mWidth = width + width % 2;
        mHeight = height + height % 2;
        mBitRate = bitRate;
        mFrameRate = frameRate;
    }

    public void interrupt() {
        this.mInterrupted = true;
    }

    /**
     * Tests encoding and subsequently decoding video from frames generated into a buffer.
     * <p>
     * We encode several frames of a video test pattern using MediaCodec, then decode the
     * output with MediaCodec and do some simple checks.
     * <p>
     * See http://b.android.com/37769 for a discussion of input format pitfalls.
     */
    private void encodeVideoFromBuffer(boolean toSurface, String videoName) throws Exception {
        MediaCodec encoder = null;
        mLargestColorDelta = -1;
        try {
            MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
            if (codecInfo == null) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
                return;
            }
            if (VERBOSE) Log.d(TAG, "found codec: " + codecInfo.getName());
            int colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
            if (VERBOSE) Log.d(TAG, "found colorFormat: " + colorFormat);
            // We avoid the device-specific limitations on width and height by using values that
            // are multiples of 16, which all tested devices seem to be able to handle.
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            if (VERBOSE) Log.d(TAG, "format: " + format);
            // Create a MediaCodec for the desired codec, then configure it as an encoder with
            // our desired properties.
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            // Create a MediaCodec for the decoder, just based on the MIME type.  The various
            // format details will be passed through the csd-0 meta-data later on.
            doEncodeVideoFromBuffer(encoder, videoName);
        } finally {
            if (VERBOSE) Log.d(TAG, "releasing codecs");
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            Log.i(TAG, "Largest color delta: " + mLargestColorDelta);
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static void iterateCodecInfo(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            LogUtils.D(TAG, "display codec info = " + codecInfo.getName() + " for mimetype " + mimeType);
        }
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        fail("couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

    /**
     * Does the actual work for encoding frames from buffers of byte[].
     */
    private void doEncodeVideoFromBuffer(MediaCodec encoder, String videoName) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int generateIndex = 0;
        int checkIndex = 0;
        // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
        // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
        // of algebra and assuming that stride==width and sliceHeight==height yields:
        byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
        // Just out of curiosity.
        long encodedSize = 0;
        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        MediaMuxer mediaMuxer;
        int trackIndex;
        boolean muxerStarted;
        String fileName = new File(OUTPUT_DIR, videoName + ".mp4").toString();
        try {
            boolean fileExist = FileUtils.fileExsit(fileName) || FileUtils.dirExsit(fileName);
            if (fileExist) {
                FileUtils.deleteFileOrDir(fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaMuxer = new MediaMuxer(fileName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }
        trackIndex = -1;
        muxerStarted = false;
        // Loop until the output side is done.
        boolean inputDone = false;
        boolean encoderDone = false;
        while (!encoderDone && !mInterrupted) {
            if (VERBOSE) Log.d(TAG, "loop");
            // If we're not done submitting frames, generate a new one and submit it.  By
            // doing this on every loop we're working to ensure that the encoder always has
            // work to do.
            //
            // We don't really want a timeout here, but sometimes there's a delay opening
            // the encoder device, so a short timeout can keep us from spinning hard.
            if (!inputDone) {
                int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (VERBOSE) Log.d(TAG, "inputBufIndex=" + inputBufIndex);
                if (inputBufIndex >= 0) {
                    long ptsUsec = computePresentationTime(generateIndex, mFrameRate);
                    if (generateIndex == mSnapshotNum) {
                        // Send an empty frame with the end-of-stream flag set.  If we set EOS
                        // on a frame with data, that frame data will be ignored, and the
                        // output will be short one frame.

                        encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE)
                            Log.d(TAG, "sent input EOS (with zero-length frame) ptsUsec:" + ptsUsec);
                        if (mVideoMakingListener != null) {
                            mVideoMakingListener.onVideoCreated(fileName);
                        }
                    } else {
                        if (mVideoMakingListener != null) {
                            if (generateIndex == 0) {
                                mVideoMakingListener.onVideoProcessingStart();
                            }
                            mVideoMakingListener.onVideoProcessing(mSnapshotNum, generateIndex);
                        }
                        Bitmap bm = mBitmapList.get(generateIndex);
                        bm = BitmapUtils.addWaterMark(bm, mContext.getResources().getDrawable(R.mipmap.ic_launcher));
                        if (bm != null) {
//                            long startTime = System.nanoTime();
                            generateFrame(bm, frameData);
//                            Log.d(TAG, "per frame rgb to yuv time " + (System.nanoTime() - startTime) / (1e+6) + " millisecs");
                            bm = null;
                        }
//                        generateFrame(generateIndex, encoderColorFormat, frameData);
                        ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                        // the buffer should be sized to hold one full frame
                        assertTrue(inputBuf.capacity() >= frameData.length);
                        inputBuf.clear();
                        inputBuf.put(frameData);
                        encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);
                        if (VERBOSE) Log.d(TAG, "submitted frame " + generateIndex + " to enc");
                    }
                    generateIndex++;
                } else {
                    // either all in use, or we timed out during initial setup
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }
            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // Once we get EOS from the encoder, we don't need to do this anymore.
            if (!encoderDone) {
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                LogUtils.D(TAG, "info dequeue time " + info.presentationTimeUs);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE)
                        Log.d(TAG, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
                    trackIndex = mediaMuxer.addTrack(newFormat);
                    mediaMuxer.start();
                    muxerStarted = true;
                } else if (encoderStatus < 0) {
                    fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        fail("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    if (info.size != 0) {
                        if (!muxerStarted) {
                            throw new RuntimeException("muxer hasn't started");
                        }
                        // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        encodedSize += info.size;
                        mediaMuxer.writeSampleData(trackIndex, encodedData, info);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Codec config info.  Only expected on first packet.  One way to
                        // handle this is to manually stuff the data into the MediaFormat
                        // and pass that to configure().  We do that here to exercise the API.
                        MediaFormat format =
                                MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                        format.setByteBuffer("csd-0", encodedData);
                        if (VERBOSE) Log.d(TAG, "decoder configured (" + info.size + " bytes)");
                    } else {
                        // Get a decoder input buffer, blocking until it's available.

                        encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                        if (VERBOSE) Log.d(TAG, "passed " + info.size + " bytes to decoder"
                                + (encoderDone ? " (EOS)" : "") + ", presentationTime:" + info.presentationTimeUs);
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            }
        }
        mRgbAllocation = null;
        mYuvAllocation = null;
        if (VERBOSE) Log.d(TAG, "decoded " + checkIndex + " frames at "
                + mWidth + "x" + mHeight + ": enc=" + encodedSize);
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
        if (mInterrupted) {
            try {
                FileUtils.deleteFileOrDir(fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void generateFrame(Bitmap bitmap, byte[] frameData) {
        getNV21(bitmap.getWidth(), bitmap.getHeight(), bitmap, frameData);
//        getYV12(bitmap.getWidth(), bitmap.getHeight(), bitmap, frameData);
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex, int frameRate) {
        return 132 + frameIndex * 1000000 / frameRate;
    }

    void getNV21(int inputWidth, int inputHeight, Bitmap scaled, byte[] frameData) {

//        inputWidth = inputWidth - inputWidth % 2;
//        inputHeight = inputHeight - inputHeight % 2;

//        int[] argb = new int[inputWidth * inputHeight];
//        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
////        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
//        encodeYUV420SPToNV21(frameData, argb, inputWidth, inputHeight);
////        return yuv;


        inputWidth = inputWidth + inputWidth % 2;
        inputHeight = inputHeight + inputHeight % 2;

        if (mRgbAllocation == null) {
            mRgbAllocation = Allocation.createFromBitmap(rs, scaled);
        } else {
            mRgbAllocation.copyFrom(scaled);
        }
        if (mYuvAllocation == null) {
            mYuvAllocation = Allocation.createTyped(
                    rs,
                    Type.createX(rs, Element.U8(rs), frameData.length));
        }
        mScriptC.set_frameWidth(inputWidth);
        mScriptC.set_frameHeight(inputHeight);
        mScriptC.set_frameSize(inputWidth * inputHeight);
        mScriptC.set_gYUVFrame(mYuvAllocation);
        mScriptC.forEach_rgb2yuvFrames(mRgbAllocation);
        mYuvAllocation.copyTo(frameData);
    }

    void getYV12(int inputWidth, int inputHeight, Bitmap scaled, byte[] frameData) {
        int[] argb = new int[inputWidth * inputHeight];
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
//        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420ToYV12(frameData, argb, inputWidth, inputHeight);
//        return yuv;
    }

    void encodeYUV420ToYV12(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                R = (argb[index] & 0xff000000) >>> 24;
                G = (argb[index] & 0xff0000) >> 16;
                B = (argb[index] & 0xff00) >> 8;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                V = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128; // Previously U
                U = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128; // Previously V

                // NV21 has a plane of Y and interleaved planes of VU each
                // sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the
                // sampling is every other
                // pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0
                        : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0
                            : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0
                            : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    void encodeYUV420SPToNV21(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                B = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                R = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each
                // sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the
                // sampling is every other
                // pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0
                        : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0
                            : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0
                            : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }
}

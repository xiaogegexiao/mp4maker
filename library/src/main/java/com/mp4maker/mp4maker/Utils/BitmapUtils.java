package com.mp4maker.mp4maker.Utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * Created by xiaomei on 23/01/2017.
 */

public class BitmapUtils {
    public static Bitmap addWaterMark(Bitmap src, Drawable watermark) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap result = Bitmap.createBitmap(w, h, src.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(src, 0, 0, null);

        int margin = (int)(40f * (float) w / 1280f);
        int markerWidth = (int)(245f * (float) w / 1280f);
        int markerHeight = (int)((float) markerWidth * 43f / 180f);
        watermark.setBounds(w - markerWidth - margin, h - markerHeight - margin, w - margin, h - margin);
        watermark.draw(canvas);
        return result;
    }
}

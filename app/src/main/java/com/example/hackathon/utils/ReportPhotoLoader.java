package com.example.hackathon.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

public final class ReportPhotoLoader {

    private ReportPhotoLoader() { }

    public static Bitmap load(String path, int maxSide) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        if (largest > maxSide && maxSide > 0) {
            while (largest / sample > maxSide) {
                sample *= 2;
            }
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, opts);
    }
}

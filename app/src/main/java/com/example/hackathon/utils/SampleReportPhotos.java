package com.example.hackathon.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.example.hackathon.R;

import java.io.OutputStream;

/**
 * Copies built-in report sample photos into the device Photos / Gallery
 * so users can pick them via the Gallery button.
 */
public final class SampleReportPhotos {

    private static final String PREFS = "sample_report_photos";
    private static final String KEY_SEEDED = "seeded_v2";
    private static final String ALBUM = "Dislocator";

    private static final int[] DRAWABLES = {
            R.drawable.sample_report_elevator,
            R.drawable.sample_report_stairs,
            R.drawable.sample_report_corridor
    };

    private static final String[] NAMES = {
            "dislocator_elevator.jpg",
            "dislocator_stairs.jpg",
            "dislocator_corridor.jpg"
    };

    private SampleReportPhotos() { }

    public static void ensureInGallery(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }

        boolean allOk = true;
        for (int i = 0; i < DRAWABLES.length; i++) {
            if (!saveToGallery(context, DRAWABLES[i], NAMES[i])) {
                allOk = false;
            }
        }
        if (allOk) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply();
        }
    }

    private static boolean saveToGallery(Context context, int drawableRes, String displayName) {
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawableRes);
        if (bitmap == null) {
            return false;
        }

        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri itemUri = null;
        try {
            itemUri = resolver.insert(collection, values);
            if (itemUri == null) {
                return false;
            }
            try (OutputStream out = resolver.openOutputStream(itemUri)) {
                if (out == null) {
                    return false;
                }
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    return false;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(itemUri, values, null, null);
            }
            return true;
        } catch (Exception e) {
            if (itemUri != null) {
                try {
                    resolver.delete(itemUri, null, null);
                } catch (Exception ignored) {
                    // ignore cleanup failure
                }
            }
            return false;
        }
    }
}

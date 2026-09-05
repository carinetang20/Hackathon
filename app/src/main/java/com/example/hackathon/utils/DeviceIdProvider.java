package com.example.hackathon.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * Generates a random anonymous ID once per device install and persists it,
 * so we can tell "reports I submitted" apart from other users' reports
 * once everything lives in a shared Firestore collection.
 */
public class DeviceIdProvider {

    private static final String PREFS = "device_identity";
    private static final String KEY_DEVICE_ID = "device_id";

    private static String cachedId;

    public static synchronized String getDeviceId(Context context) {
        if (cachedId != null) {
            return cachedId;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        cachedId = id;
        return id;
    }
}
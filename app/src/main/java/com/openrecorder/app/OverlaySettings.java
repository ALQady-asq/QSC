// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class OverlaySettings {
    static final int CAMERA_OFF = 0;
    static final int CAMERA_FRONT = 1;
    static final int CAMERA_REAR = 2;
    static final int AUDIO_AAC = 0;
    static final int AUDIO_OPUS = 1;
    static final int AUDIO_PCM = 2;

    private final SharedPreferences preferences;

    OverlaySettings(Context context) {
        preferences = context.getSharedPreferences("capture_extras", Context.MODE_PRIVATE);
    }

    boolean controls() {
        return preferences.getBoolean("controls", true);
    }

    void setControls(boolean enabled) {
        preferences.edit().putBoolean("controls", enabled).apply();
    }

    int camera() {
        return Math.max(CAMERA_OFF, Math.min(CAMERA_REAR, preferences.getInt("camera", CAMERA_OFF)));
    }

    void setCamera(int camera) {
        preferences.edit().putInt("camera", camera).apply();
    }

    record CameraLayout(float x, float y, int size) {}

    CameraLayout cameraLayout(boolean landscape) {
        String prefix = landscape ? "landscape_" : "portrait_";
        int corner = Math.max(0, Math.min(3, preferences.getInt("camera_position", 3)));
        return new CameraLayout(
                preferences.getFloat(prefix + "camera_x", (corner & 1) == 0 ? 0f : 1f),
                preferences.getFloat(prefix + "camera_y", corner < 2 ? 0f : 1f),
                preferences.getInt(prefix + "camera_size", 23));
    }

    void setCameraLayout(boolean landscape, CameraLayout layout) {
        String prefix = landscape ? "landscape_" : "portrait_";
        preferences.edit()
                .putFloat(prefix + "camera_x", Math.max(0f, Math.min(1f, layout.x())))
                .putFloat(prefix + "camera_y", Math.max(0f, Math.min(1f, layout.y())))
                .putInt(prefix + "camera_size", Math.max(12, Math.min(55, layout.size())))
                .apply();
    }

    void addListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void removeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    boolean privateControls() {
        return preferences.getBoolean("private_controls", false);
    }

    void setPrivateControls(boolean enabled) {
        preferences.edit().putBoolean("private_controls", enabled).apply();
    }

    boolean privateCamera() {
        return preferences.getBoolean("private_camera", false);
    }

    void setPrivateCamera(boolean enabled) {
        preferences.edit().putBoolean("private_camera", enabled).apply();
    }

    boolean originalCamera() {
        return preferences.getBoolean("original_camera", false);
    }

    void setOriginalCamera(boolean enabled) {
        preferences.edit().putBoolean("original_camera", enabled).apply();
    }

    int audioEncoding() {
        return Math.max(AUDIO_AAC, Math.min(AUDIO_PCM, preferences.getInt("audio_encoding", AUDIO_AAC)));
    }

    void setAudioEncoding(int encoding) {
        preferences.edit().putInt("audio_encoding", encoding).apply();
    }

    Intent captureIntent(MediaProjectionManager manager) {
        if (camera() != CAMERA_OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        }
        return manager.createScreenCaptureIntent();
    }

    static void openPermission(Activity activity) {
        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", activity.getPackageName(), null));
        try {
            activity.startActivity(permission);
        } catch (android.content.ActivityNotFoundException error) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (android.content.ActivityNotFoundException ignored) {
                android.widget.Toast.makeText(activity, R.string.overlay_permission_unavailable,
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }
}

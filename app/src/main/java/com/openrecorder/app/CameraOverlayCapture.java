// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/** Owns the CameraX use cases and the optional camera-only file for a recording. */
final class CameraOverlayCapture implements LifecycleOwner {
    private static final String TAG = "CameraOverlayCapture";

    private final Context context;
    private final PreviewView view;
    private final CameraSelector selector;
    private final boolean saveOriginal;
    private final String baseName;
    private final Executor main;
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

    private ProcessCameraProvider provider;
    private Preview preview;
    private VideoCapture<Recorder> video;
    private Recording recording;
    private boolean closed;
    private boolean keepFile;
    private boolean paused;

    CameraOverlayCapture(Context context, PreviewView view, int facing,
            boolean saveOriginal, String baseName) {
        this.context = context.getApplicationContext();
        this.view = view;
        this.saveOriginal = saveOriginal;
        this.baseName = baseName;
        main = ContextCompat.getMainExecutor(context);
        selector = facing == OverlaySettings.CAMERA_FRONT
                ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
    }

    @Override
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    void open() {
        lifecycle.setCurrentState(Lifecycle.State.CREATED);
        ListenableFuture<ProcessCameraProvider> pending = ProcessCameraProvider.getInstance(context);
        pending.addListener(() -> {
            if (closed) return;
            try {
                provider = pending.get();
                Display display = view.getDisplay();
                int rotation = display == null ? Surface.ROTATION_0 : display.getRotation();
                preview = new Preview.Builder().setTargetRotation(rotation).build();
                preview.setSurfaceProvider(view.getSurfaceProvider());
                lifecycle.setCurrentState(Lifecycle.State.RESUMED);
                if (saveOriginal && startOriginal(rotation)) return;
                provider.bindToLifecycle(this, selector, preview);
            } catch (Exception error) {
                Log.w(TAG, "Camera could not be opened", error);
                view.setVisibility(android.view.View.GONE);
                close(false);
                notifyUser(R.string.camera_unavailable);
            }
        }, main);
    }

    void updatePreviewRotation() {
        Display display = view.getDisplay();
        if (preview != null && display != null) {
            preview.setTargetRotation(display.getRotation());
        }
    }

    private boolean startOriginal(int rotation) {
        try {
            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build();
            video = new VideoCapture.Builder<>(recorder).setTargetRotation(rotation).build();
            provider.bindToLifecycle(this, selector, preview, video);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, baseName + "_camera.mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Open Recorder");
            MediaStoreOutputOptions output = new MediaStoreOutputOptions.Builder(
                    context.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(values)
                    .build();
            recording = recorder.prepareRecording(context, output).start(main, this::onVideoEvent);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Camera-only recording unavailable", error);
            if (video != null) provider.unbind(video);
            video = null;
            notifyUser(R.string.camera_original_unavailable);
            return false;
        }
    }

    private void onVideoEvent(VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Start && paused && recording != null) {
            recording.pause();
        }
        if (!(event instanceof VideoRecordEvent.Finalize)) return;
        VideoRecordEvent.Finalize result = (VideoRecordEvent.Finalize) event;
        recording = null;
        if (result.hasError() || (closed && !keepFile)) {
            Uri uri = result.getOutputResults().getOutputUri();
            if (!Uri.EMPTY.equals(uri)) {
                try {
                    context.getContentResolver().delete(uri, null, null);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Could not remove incomplete camera video", error);
                }
            }
        }
        if (closed) {
            destroyLifecycle();
        } else if (result.hasError()) {
            Log.w(TAG, "Camera-only encoder stopped", result.getCause());
            if (video != null) provider.unbind(video);
            video = null;
            notifyUser(R.string.camera_original_unavailable);
        }
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (recording == null) return;
        if (paused) recording.pause();
        else recording.resume();
    }

    void close(boolean save) {
        if (closed) return;
        closed = true;
        keepFile = save;
        if (recording != null) {
            recording.stop();
            // CameraX finalizes asynchronously. Release the camera if the callback is delayed.
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::destroyLifecycle, 5_000L);
        } else {
            destroyLifecycle();
        }
    }

    private void destroyLifecycle() {
        if (lifecycle.getCurrentState() != Lifecycle.State.DESTROYED) {
            if (lifecycle.getCurrentState() == Lifecycle.State.INITIALIZED) {
                lifecycle.setCurrentState(Lifecycle.State.CREATED);
            }
            lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
        }
    }

    private void notifyUser(int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}

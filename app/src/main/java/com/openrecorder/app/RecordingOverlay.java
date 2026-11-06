// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.camera.view.PreviewView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owns the two optional windows for one recording, on the main thread. */
final class RecordingOverlay {
    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LongSupplier elapsed;
    private final OverlaySettings settings;
    private final boolean privateControls;
    private final boolean privateCamera;
    private final Runnable applyCameraLayout = this::restoreCameraLayout;
    private final SharedPreferences.OnSharedPreferenceChangeListener cameraLayoutListener =
            (preferences, key) -> {
                if (key != null && (key.startsWith("portrait_camera_")
                        || key.startsWith("landscape_camera_"))) {
                    main.removeCallbacks(applyCameraLayout);
                    main.post(applyCameraLayout);
                }
            };
    private LinearLayout controls;
    private TextView timer;
    private TextView pauseButton;
    private View stopButton;
    private TextView microphoneButton;
    private boolean microphoneMuted;
    private PreviewView cameraView;
    private CameraOverlayCapture camera;
    private WindowManager.LayoutParams cameraLayout;
    private WindowManager.LayoutParams controlsLayout;
    private Rect cameraArea;
    private boolean landscape;
    private float cameraSize;
    private boolean paused;
    private long lastDisplayedSecond = -1;
    private final Runnable autoCollapse = () -> setCollapsed(true);
    private boolean collapsed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (timer == null) return;
            long seconds = elapsed.getAsLong() / 1_000L;
            if (seconds != lastDisplayedSecond) {
                lastDisplayedSecond = seconds;
                String time = seconds >= 3600
                        ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                        : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
                timer.setText(time);
            }
            main.postDelayed(this, 250);
        }
    };

    RecordingOverlay(Context context, OverlaySettings settings, LongSupplier elapsed) {
        this.settings = settings;
        privateControls = settings.privateControls();
        privateCamera = settings.privateCamera();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            DisplayManager displays = context.getSystemService(DisplayManager.class);
            Display display = displays.getDisplay(Display.DEFAULT_DISPLAY);
            this.context = context.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        } else {
            this.context = context;
        }
        this.elapsed = elapsed;
        windows = this.context.getSystemService(WindowManager.class);
    }

    void showControls(Runnable pauseOrResume, Runnable stop, Consumer<Boolean> mute) {
        controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(4), dp(3), dp(4), dp(3));
        controls.setBackground(background(0xee102d36, 26));
        pauseButton = button("Ⅱ", R.string.overlay_pause, 0xff23505a, pauseOrResume);
        timer = new TextView(context);
        timer.setTextColor(Color.WHITE);
        timer.setTextSize(13);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(dp(8), 0, dp(8), 0);
        timer.setOnClickListener(view -> toggleCollapsed());
        timer.setContentDescription(context.getString(R.string.overlay_collapse));
        stopButton = button("■", R.string.overlay_stop, 0xffbf2639, stop);
        controls.addView(pauseButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (mute != null) {
            microphoneButton = button("MIC", R.string.overlay_mute, 0xff23505a, () -> {
                microphoneMuted = !microphoneMuted;
                microphoneButton.setText(microphoneMuted ? "×MIC" : "MIC");
                microphoneButton.setContentDescription(context.getString(
                        microphoneMuted ? R.string.overlay_unmute : R.string.overlay_mute));
                mute.accept(microphoneMuted);
            });
            microphoneButton.setTextSize(10);
            controls.addView(microphoneButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        controls.addView(timer, new LinearLayout.LayoutParams(-2, dp(40)));
        controls.addView(stopButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        controlsLayout = layout(-2, -2, Gravity.TOP | Gravity.RIGHT);
        controlsLayout.y = dp(4);
        setWindowVisible(null, controlsLayout, !privateControls);
        setCollapsed(true);
        try {
            windows.addView(controls, controlsLayout);
            tick.run();
        } catch (RuntimeException error) {
            Log.w("RecordingOverlay", "Could not show recording controls", error);
            controls = null;
            timer = null;
        }
    }

    void showCamera(int facing, boolean saveOriginal, String baseName) {
        cameraView = new PreviewView(context);
        cameraView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraLayout = layout(0, 0, Gravity.TOP | Gravity.LEFT);
        restoreCameraLayout();
        setWindowVisible(null, cameraLayout, !privateCamera);
        cameraView.setContentDescription(context.getString(R.string.camera_label));
        enableCameraGestures();
        camera = new CameraOverlayCapture(context, cameraView, facing, saveOriginal, baseName);
        try {
            windows.addView(cameraView, cameraLayout);
            settings.addListener(cameraLayoutListener);
            camera.open();
        } catch (RuntimeException error) {
            Log.w("RecordingOverlay", "Could not show camera window", error);
            camera.close(false);
            settings.removeListener(cameraLayoutListener);
            removeView(cameraView);
            camera = null;
            cameraView = null;
            android.widget.Toast.makeText(context, R.string.camera_unavailable,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    void restoreCameraLayout() {
        if (cameraView == null) return;
        cameraArea = bounds();
        landscape = cameraArea.width() >= cameraArea.height();
        OverlaySettings.CameraLayout saved = settings.cameraLayout(landscape);
        cameraSize = saved.size();
        resizeCamera();
        cameraLayout.x = dp(8) + Math.round(saved.x() * travelX());
        cameraLayout.y = dp(8) + Math.round(saved.y() * travelY());
        refreshCamera();
    }

    private void enableCameraGestures() {
        ScaleGestureDetector resize = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        int centerX = cameraLayout.x + cameraLayout.width / 2;
                        int centerY = cameraLayout.y + cameraLayout.height / 2;
                        cameraSize = Math.max(12f, Math.min(55f, cameraSize * detector.getScaleFactor()));
                        resizeCamera();
                        cameraLayout.x = centerX - cameraLayout.width / 2;
                        cameraLayout.y = centerY - cameraLayout.height / 2;
                        refreshCamera();
                        return true;
                    }
                });
        cameraView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX;
            private float lastY;
            private boolean multiTouch;

            @Override public boolean onTouch(View view, MotionEvent event) {
                resize.onTouchEvent(event);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        multiTouch = false;
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        multiTouch = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!multiTouch && !resize.isInProgress()) {
                            cameraLayout.x += Math.round(event.getRawX() - lastX);
                            cameraLayout.y += Math.round(event.getRawY() - lastY);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                            refreshCamera();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        settings.setCameraLayout(landscape, new OverlaySettings.CameraLayout(
                                (float) (cameraLayout.x - dp(8)) / travelX(),
                                (float) (cameraLayout.y - dp(8)) / travelY(),
                                Math.round(cameraSize)));
                        view.performClick();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void resizeCamera() {
        int shortEdge = Math.min(cameraArea.width(), cameraArea.height());
        cameraLayout.width = Math.round(shortEdge * cameraSize / 100f);
        cameraLayout.height = cameraLayout.width * 9 / 16;
    }

    private void refreshCamera() {
        int margin = dp(8);
        cameraLayout.x = Math.max(margin, Math.min(cameraLayout.x, margin + travelX()));
        cameraLayout.y = Math.max(margin, Math.min(cameraLayout.y, margin + travelY()));
        if (cameraView.isAttachedToWindow()) windows.updateViewLayout(cameraView, cameraLayout);
    }

    private int travelX() {
        return Math.max(1, cameraArea.width() - cameraLayout.width - dp(16));
    }

    private int travelY() {
        return Math.max(1, cameraArea.height() - cameraLayout.height - dp(16));
    }

    boolean hidePrivateWindows() {
        if (privateControls && controls != null) setWindowVisible(controls, controlsLayout, false);
        if (privateCamera && cameraView != null) setWindowVisible(cameraView, cameraLayout, false);
        return (privateControls && controls != null) || (privateCamera && cameraView != null);
    }

    private void setWindowVisible(View view, WindowManager.LayoutParams layout, boolean visible) {
        if (layout == null) return;
        layout.alpha = visible ? 1f : 0f;
        if (visible) layout.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else layout.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (view != null && view.isAttachedToWindow()) windows.updateViewLayout(view, layout);
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (privateControls && controls != null) setWindowVisible(controls, controlsLayout, paused);
        if (privateCamera && cameraView != null) setWindowVisible(cameraView, cameraLayout, paused);
        if (paused && controls != null) setCollapsed(false);
        if (pauseButton != null) {
            pauseButton.setText(paused ? "▶" : "Ⅱ");
            pauseButton.setContentDescription(context.getString(
                    paused ? R.string.overlay_resume : R.string.overlay_pause));
        }
        if (camera != null) camera.setPaused(paused);
        if (!paused && controls != null && !collapsed) scheduleCollapse();
    }

    void close(boolean saveCamera) {
        main.removeCallbacks(tick);
        main.removeCallbacks(autoCollapse);
        main.removeCallbacks(applyCameraLayout);
        settings.removeListener(cameraLayoutListener);
        if (camera != null) camera.close(saveCamera);
        removeView(cameraView);
        removeView(controls);
        camera = null;
        cameraView = null;
        controls = null;
        timer = null;
    }

    private void removeView(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            windows.removeView(view);
        } catch (IllegalArgumentException error) {
            Log.w("RecordingOverlay", "Overlay was already detached", error);
        }
    }

    private void toggleCollapsed() {
        setCollapsed(!collapsed);
    }

    private void setCollapsed(boolean collapsed) {
        if (controls == null) return;
        this.collapsed = collapsed;
        pauseButton.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (microphoneButton != null) {
            microphoneButton.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        timer.setContentDescription(context.getString(
                collapsed ? R.string.overlay_expand : R.string.overlay_collapse));
        timer.setBackground(collapsed ? background(0xffbf2639, 22) : null);
        controlsLayout.gravity = Gravity.TOP
                | (collapsed ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        controlsLayout.x = collapsed ? dp(12) : 0;
        if (controls.isAttachedToWindow()) windows.updateViewLayout(controls, controlsLayout);
        scheduleCollapse();
    }

    private void scheduleCollapse() {
        main.removeCallbacks(autoCollapse);
        if (!collapsed && !paused) main.postDelayed(autoCollapse, 7_000L);
    }

    private TextView button(String text, int description, int color, Runnable action) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackground(background(color, 22));
        view.setContentDescription(context.getString(description));
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private WindowManager.LayoutParams layout(int width, int height, int gravity) {
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        layout.gravity = gravity;
        return layout;
    }

    private Rect bounds() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics metrics = windows.getCurrentWindowMetrics();
            Rect area = new Rect(metrics.getBounds());
            android.graphics.Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            area.inset(bars.left, bars.top, bars.right, bars.bottom);
            return area;
        }
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windows.getDefaultDisplay().getMetrics(metrics);
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

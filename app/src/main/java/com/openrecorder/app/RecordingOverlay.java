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
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
    private FrameLayout controls;
    private LinearLayout capsule;
    private LinearLayout card;
    private TextView capsuleTimer;
    private TextView cardTimer;
    private TextView cardStatus;
    private TextView pauseButton;
    private boolean microphoneMuted;
    private PreviewView cameraView;
    private CameraOverlayCapture camera;
    private WindowManager.LayoutParams cameraLayout;
    private WindowManager.LayoutParams controlsLayout;
    private Rect cameraArea;
    private boolean landscape;
    private float cameraSize;
    private long lastDisplayedSecond = -1;
    private boolean collapsed;
    private boolean paused;
    private boolean statusBarVisible = true;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (controls == null) return;
            long seconds = elapsed.getAsLong() / 1_000L;
            if (seconds != lastDisplayedSecond) {
                lastDisplayedSecond = seconds;
                String time = seconds >= 3600
                        ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                        : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
                capsuleTimer.setText(time);
                cardTimer.setText(time);
            }
            main.postDelayed(this, 1_000L - elapsed.getAsLong() % 1_000L);
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
        controls = new FrameLayout(context);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controls.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE && !collapsed) {
                setCollapsed(true);
            }
            return false;
        });

        capsule = new LinearLayout(context);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setBackground(background(0xffc83f32, 20));
        capsule.setPadding(dp(8), 0, dp(3), 0);
        capsuleTimer = text("00:00", 14, Color.WHITE);
        capsuleTimer.setContentDescription(context.getString(R.string.overlay_expand));
        capsuleTimer.setOnClickListener(view -> setCollapsed(false));
        capsule.addView(capsuleTimer, new LinearLayout.LayoutParams(dp(49), dp(36)));
        capsule.addView(stopButton(stop, Color.WHITE),
                new LinearLayout.LayoutParams(dp(32), dp(36)));

        card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(6), dp(12), dp(6));
        card.setBackground(background(0xff242527, 18));
        card.addView(stopButton(stop, 0xffff5147), controlSize());
        if (mute != null) {
            ImageButton microphone = new ImageButton(context);
            microphone.setImageResource(android.R.drawable.ic_btn_speak_now);
            microphone.setColorFilter(Color.BLACK);
            microphone.setBackground(background(0xffe4e4e4, 20));
            microphone.setContentDescription(context.getString(R.string.overlay_mute));
            microphone.setOnClickListener(view -> {
                microphoneMuted = !microphoneMuted;
                microphone.setAlpha(microphoneMuted ? 0.55f : 1f);
                microphone.setContentDescription(context.getString(
                        microphoneMuted ? R.string.overlay_unmute : R.string.overlay_mute));
                mute.accept(microphoneMuted);
            });
            card.addView(microphone, controlSize());
        }
        pauseButton = button("Ⅱ", R.string.overlay_pause, 0xff414244, pauseOrResume);
        card.addView(pauseButton, controlSize());
        card.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));

        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setPadding(dp(8), 0, dp(4), 0);
        status.setContentDescription(context.getString(R.string.overlay_collapse));
        status.setOnClickListener(view -> setCollapsed(true));
        cardTimer = text("00:00", 14, Color.WHITE);
        cardStatus = text(context.getString(R.string.notification_recording_title), 12, 0xffc9c9c9);
        cardStatus.setSingleLine(true);
        cardStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.addView(cardTimer);
        status.addView(cardStatus);
        card.addView(status, new LinearLayout.LayoutParams(-2, dp(44)));
        controls.addView(capsule, new FrameLayout.LayoutParams(-2, -2));
        controls.addView(card, new FrameLayout.LayoutParams(-1, -2));
        controlsLayout = layout(-2, -2, Gravity.TOP | Gravity.RIGHT);
        updateControlsVisibility();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            controls.setOnApplyWindowInsetsListener((view, insets) -> {
                boolean visible = insets.isVisible(WindowInsets.Type.statusBars());
                if (statusBarVisible != visible) {
                    statusBarVisible = visible;
                    updateControlsVisibility();
                    if (!visible) setCollapsed(true);
                }
                return view.onApplyWindowInsets(insets);
            });
        }
        setCollapsed(true);
        try {
            windows.addView(controls, controlsLayout);
            tick.run();
        } catch (RuntimeException error) {
            Log.w("RecordingOverlay", "Could not show recording controls", error);
            controls = null;
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
        landscape = context.getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if (camera != null) camera.updatePreviewRotation();
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
        int longSide = Math.round(shortEdge * cameraSize / 100f);
        int shortSide = Math.round(longSide / OverlaySettings.CAMERA_ASPECT_RATIO);
        cameraLayout.width = landscape ? longSide : shortSide;
        cameraLayout.height = landscape ? shortSide : longSide;
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

    private void updateControlsVisibility() {
        setWindowVisible(controls, controlsLayout, statusBarVisible && (!privateControls || paused));
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (controls != null) updateControlsVisibility();
        if (privateCamera && cameraView != null) setWindowVisible(cameraView, cameraLayout, paused);
        if (paused && controls != null) setCollapsed(false);
        if (cardStatus != null) {
            cardStatus.setText(paused ? R.string.overlay_status_paused
                    : R.string.notification_recording_title);
        }
        if (pauseButton != null) {
            pauseButton.setText(paused ? "▶" : "Ⅱ");
            pauseButton.setContentDescription(context.getString(
                    paused ? R.string.overlay_resume : R.string.overlay_pause));
        }
        if (camera != null) camera.setPaused(paused);
    }

    void close(boolean saveCamera) {
        main.removeCallbacks(tick);
        main.removeCallbacks(applyCameraLayout);
        settings.removeListener(cameraLayoutListener);
        if (camera != null) camera.close(saveCamera);
        removeView(cameraView);
        removeView(controls);
        camera = null;
        cameraView = null;
        controls = null;
    }

    private void removeView(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            windows.removeView(view);
        } catch (IllegalArgumentException error) {
            Log.w("RecordingOverlay", "Overlay was already detached", error);
        }
    }

    private void setCollapsed(boolean collapsed) {
        if (controls == null) return;
        this.collapsed = collapsed;
        capsule.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        card.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        controlsLayout.gravity = Gravity.TOP | (collapsed ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        controlsLayout.x = collapsed ? dp(12) : 0;
        controlsLayout.y = collapsed ? dp(3) : dp(7);
        Rect available = bounds();
        int width = available.width();
        float cardFraction = width >= available.height() ? 0.66f : 0.72f;
        int cardWidth = Math.min(width - dp(16), Math.max(dp(290), Math.round(width * cardFraction)));
        controlsLayout.width = collapsed ? -2 : cardWidth;
        if (collapsed) {
            controlsLayout.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        } else {
            controlsLayout.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        if (controls.isAttachedToWindow()) windows.updateViewLayout(controls, controlsLayout);
    }

    void onConfigurationChanged() {
        setCollapsed(collapsed);
        restoreCameraLayout();
    }

    private LinearLayout.LayoutParams controlSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(40), dp(40));
        size.rightMargin = dp(5);
        return size;
    }

    private TextView text(String label, int size, int color) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView stopButton(Runnable action, int color) {
        TextView view = button("■", R.string.overlay_stop, Color.TRANSPARENT, action);
        view.setTextColor(color);
        return view;
    }

    private TextView button(String label, int description, int color, Runnable action) {
        TextView view = text(label, 16, Color.WHITE);
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

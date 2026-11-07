// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app;

import android.view.SurfaceControl;
import android.view.View;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Method;

/**
 * Excludes only our controls window, including its children, from display mirroring.
 * This is a non-SDK API: callers must handle OEMs that do not provide it. Do not
 * substitute FLAG_SECURE, which would black out the controls' rectangle instead.
 */
final class OverlayCaptureExclusion {
    private Method getViewRoot;
    private Method getSurface;
    private Method skipScreenshot;
    private Method applySynchronously;
    private Method getLayerId;
    private final SurfaceExclusionState exclusionState = new SurfaceExclusionState();

    void apply(View controls) throws ReflectiveOperationException {
        if (getViewRoot == null) {
            getViewRoot = method(View.class, "getViewRootImpl");
            skipScreenshot = method(SurfaceControl.Transaction.class,
                    "setSkipScreenshot", SurfaceControl.class, boolean.class);
            applySynchronously = method(SurfaceControl.Transaction.class,
                    "apply", boolean.class);
            try {
                getLayerId = method(SurfaceControl.class, "getLayerId");
            } catch (NoSuchMethodException ignored) {
                // Keep the previous per-draw behavior on OEMs without a stable layer identifier.
            }
        }
        Object root = getViewRoot.invoke(controls);
        if (root == null) throw new IllegalStateException("Controls window is not attached");
        if (getSurface == null) {
            getSurface = method(root.getClass(), "getSurfaceControl");
        }
        SurfaceControl surface = (SurfaceControl) getSurface.invoke(root);
        if (surface == null || !surface.isValid()) {
            throw new IllegalStateException("Controls surface is unavailable");
        }
        int layerId = -1;
        if (getLayerId != null) {
            try {
                layerId = (Integer) getLayerId.invoke(surface);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                getLayerId = null;
            }
        }
        if (!exclusionState.needsApply(layerId)) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            skipScreenshot.invoke(transaction, surface, true);
            // Complete before allowing a frame or making the initially transparent window visible.
            applySynchronously.invoke(transaction, true);
            exclusionState.applied(layerId);
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = HiddenApiBypass.getDeclaredMethod(owner, name, parameters);
        method.setAccessible(true);
        return method;
    }
}

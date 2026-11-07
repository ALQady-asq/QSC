# Contributing to QSC

The source and test workflow are public so contributors can continue the project here or maintain a fork. Open an issue to discuss ongoing maintenance or a substantial change. A fork can build and publish independently under the terms in [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Build and test

Use JDK 21. Install Android SDK Platform 37.0 and Build Tools 36.0.0 with Android Studio or `sdkmanager`.

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
./gradlew :app:testDebugUnitTest :app:assembleDevelopment
adb install -r app/build/outputs/apk/development/app-development.apk
```

The development APK uses the application ID `com.openrecorder.app.matepad.test`. Release builds use `com.openrecorder.app.matepad` and are unsigned when built with `./gradlew :app:assembleRelease`. For an independently signed release, choose your own application ID and signing key. Android requires the existing certificate to update an installed release under the same ID.

## Where to start

| Area | Files in `app/src/main` |
|---|---|
| Capture session and saving | `ScreenRecorder.java`, `RecordingService.java`, `RecordingTimeline.java` |
| Video quality, timing and fallback | `VideoTrackRecorder.java`, `VideoEncodingProfile.java`, `VideoFrameRatePolicy.java` |
| Audio and MP4 | `InternalAudioRecorder.java`, `RecordingMuxer.java` |
| Camera and controls | `CameraOverlayCapture.java`, `RecordingOverlay.java`, `OverlaySettings.java` |
| Tablet settings and camera layout | `SettingsScreen.kt`, `CameraLayoutEditor.kt`, `MainActivity.kt` |

The capture pipeline uses MediaProjection and MediaCodec. CameraX drives camera previews and camera-only recordings. The platform muxer handles AAC. Media3 handles MP4 output with Opus or PCM.

## Useful follow-up work

- Compare native-resolution frame pacing during scrolling and annotation on physical devices. Record the codec, target frame rate, actual output rate, and largest frame gap.
- Test whether the camera overlay and an original camera file can run at the same time at different screen encoder settings. Check both lenses, rotation, pause and resume, and cleanup after cancellation.
- Verify the floating bar, tile, and pause-only overlays on additional Android and HarmonyOS versions. Capture exclusion uses the non-SDK `SurfaceControl.Transaction.setSkipScreenshot` on our controls window, via Android Hidden API Bypass 6.2. Do not replace this with `FLAG_SECURE`, which produced a black rectangle on the tested MatePad. The bar starts transparent and untouchable until exclusion is applied before its first draw; the flag is reapplied before subsequent draws in case relayout recreated the surface. API failures fall back to pause-only controls with a toast. OEMs may silently ignore the flag: only inspecting a recording confirms exclusion. The Redmi Turbo 4 Pro / HyperOS 3.0 and MatePad 11.5s / HarmonyOS 4.2 remain physical-device test targets, not verified devices.
- Add Arabic UI translations and test right-to-left layouts at different tablet window widths.

## Reports and changes

For a recording bug, include the app version, device model, system version, resolution, target FPS, video and audio codecs, and the steps to reproduce it. If possible, include stream metadata from `ffprobe -hide_banner -i sample.mp4`. A short clip with synthetic content is sufficient to demonstrate a visual issue without sharing personal recordings or notifications.

For changes to capture, record a short clip on a physical device and confirm it plays and saves correctly after pause and resume. Run the unit tests and describe what you tested in the pull request. Preserve upstream credit and keep signing material and personal test clips outside the repository.

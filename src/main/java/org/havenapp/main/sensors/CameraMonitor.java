package org.havenapp.main.sensors;

import android.content.Context;
import android.graphics.ImageFormat;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import org.havenapp.main.PowerPolicy;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.Utils;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.sensors.motion.LuminanceMotionDetector;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service-owned camera pipeline (Phase 3). Runs entirely inside {@code MonitorService}
 * with no preview surface: an {@link ImageAnalysis} stream feeds a luma-diff motion
 * detector, and on motion an {@link ImageCapture} still (and optionally a
 * {@link VideoCapture} clip) is written to the session media directory. The camera is
 * bound to the service {@link LifecycleOwner}, so {@link SensingCoordinator} can drop it
 * entirely in the idle tier just by calling {@link #stop()}.
 *
 * All CameraX calls here must run on the main thread.
 */
public class CameraMonitor {

    private static final String TAG = "CameraMonitor";
    private static final int ANALYSIS_W = 640;
    private static final int ANALYSIS_H = 480;
    /** minimum gap between saved stills */
    private static final long CAPTURE_DEBOUNCE_MS = 3000;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final SensorTriggerSink sink;
    private final PreferenceManager prefs;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;

    private final LuminanceMotionDetector detector = new LuminanceMotionDetector();
    private int[] lastLuma;
    private long lastCaptureAt;
    private long lastAnalyzedAt;
    private volatile long minFrameIntervalMs = 200; // updated from PowerPolicy
    private volatile boolean allowVideo = true;
    private boolean started;
    private boolean analyzerLogged;

    public CameraMonitor(Context context, LifecycleOwner lifecycleOwner, SensorTriggerSink sink) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.sink = sink;
        this.prefs = new PreferenceManager(this.context);
        detector.setThreshold(prefs.getCameraSensitivity());
    }

    /** @return true if the user has a lens selected for monitoring. */
    public boolean isEnabled() {
        return prefs.getCameraActivation() && lensSelector() != null;
    }

    /** Re-read PowerPolicy knobs (fps throttle, video gate). Safe to call any time. */
    public void applyPowerPolicy() {
        PowerPolicy.Tier tier = PowerPolicy.current(context);
        int fps = Math.max(1, PowerPolicy.cameraAnalysisFps(tier));
        minFrameIntervalMs = 1000L / fps;
        allowVideo = PowerPolicy.allowVideoCapture(tier);
    }

    /** Must be called on the main thread. */
    public void start() {
        if (started || !isEnabled()) return;
        started = true;
        applyPowerPolicy();
        detector.setThreshold(prefs.getCameraSensitivity());

        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "camera provider unavailable", e);
                started = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /** Must be called on the main thread. */
    public void stop() {
        started = false;
        analyzerLogged = false;
        finishRecording();
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
                Log.i(TAG, "camera unbound");
            } catch (Exception ignored) {
            }
        }
        lastLuma = null;
    }

    /* --------------------------------------------------------------------- internals */

    private CameraSelector lensSelector() {
        String c = prefs.getCamera();
        if (PreferenceManager.BACK.equals(c) || "1".equals(c)) return CameraSelector.DEFAULT_BACK_CAMERA;
        if (PreferenceManager.NONE.equals(c) || "2".equals(c)) return null;
        return CameraSelector.DEFAULT_FRONT_CAMERA;
    }

    private void bindUseCases() {
        if (cameraProvider == null || !started) return;
        CameraSelector selector = lensSelector();
        if (selector == null) return;

        ResolutionSelector res = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(ANALYSIS_W, ANALYSIS_H),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setResolutionSelector(res)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyze);

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        boolean video = prefs.getVideoMonitoringActive() && allowVideo;
        try {
            cameraProvider.unbindAll();
            if (video) {
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD,
                                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis, imageCapture, videoCapture);
            } else {
                videoCapture = null;
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis, imageCapture);
            }
            Log.i(TAG, "camera bound (video=" + video + ")");
        } catch (Exception e) {
            Log.e(TAG, "bindToLifecycle failed", e);
            started = false;
        }
    }

    private void analyze(@NonNull ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) return;

            // fps throttle: CameraX delivers ~30fps; only process every minFrameIntervalMs.
            long now = SystemClock.elapsedRealtime();
            if (now - lastAnalyzedAt < minFrameIntervalMs) return;
            lastAnalyzedAt = now;

            int w = image.getWidth();
            int h = image.getHeight();
            int[] luma = extractLuma(image, w, h);

            if (!analyzerLogged) {
                analyzerLogged = true;
                Log.i(TAG, "analyzer running @ " + w + "x" + h + " threshold=" + prefs.getCameraSensitivity());
            }

            if (lastLuma != null && lastLuma.length == luma.length) {
                if (detector.detectMotion(lastLuma, luma, w, h) != null) {
                    Log.i(TAG, "camera motion detected");
                    onMotion();
                }
            }
            lastLuma = luma;
        } catch (Exception e) {
            Log.w(TAG, "analyze failed", e);
        } finally {
            image.close();
        }
    }

    private static int[] extractLuma(ImageProxy image, int w, int h) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int[] out = new int[w * h];
        byte[] row = new byte[rowStride];
        int o = 0;
        for (int y = 0; y < h; y++) {
            buf.position(y * rowStride);
            int len = Math.min(rowStride, buf.remaining());
            buf.get(row, 0, len);
            for (int x = 0; x < w; x++) {
                out[o++] = row[x * pixelStride] & 0xFF;
            }
        }
        return out;
    }

    private void onMotion() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastCaptureAt < CAPTURE_DEBOUNCE_MS) return;
        lastCaptureAt = now;
        ContextCompat.getMainExecutor(context).execute(this::captureStill);
    }

    private File sessionDir() {
        File dir = new File(context.getExternalFilesDir(null), prefs.getDefaultMediaStoragePath());
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private void captureStill() {
        if (imageCapture == null) return;
        String ts = new SimpleDateFormat(Utils.DATE_TIME_PATTERN, Locale.getDefault()).format(new Date());
        File out = new File(sessionDir(), ts + ".detected.jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(out).build();
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (sink != null) sink.onSensorTrigger(EventTrigger.CAMERA, out.getAbsolutePath());
                        maybeRecordClip();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "takePicture failed", e);
                    }
                });
    }

    private void maybeRecordClip() {
        if (videoCapture == null || activeRecording != null) return;
        String ts = new SimpleDateFormat(Utils.DATE_TIME_PATTERN, Locale.getDefault()).format(new Date());
        File out = new File(sessionDir(), ts + ".mp4");
        FileOutputOptions fileOpts = new FileOutputOptions.Builder(out).build();
        try {
            activeRecording = videoCapture.getOutput()
                    .prepareRecording(context, fileOpts)
                    .start(ContextCompat.getMainExecutor(context), event -> {
                        if (event instanceof VideoRecordEvent.Finalize) {
                            activeRecording = null;
                            if (sink != null) {
                                sink.onSensorTrigger(EventTrigger.CAMERA_VIDEO, out.getAbsolutePath());
                            }
                        }
                    });
            long clipMs = Math.max(3, prefs.getMonitoringTime()) * 1000L;
            ContextCompat.getMainExecutor(context).execute(() ->
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this::finishRecording, clipMs));
        } catch (Exception e) {
            Log.e(TAG, "video record failed", e);
            activeRecording = null;
        }
    }

    private void finishRecording() {
        if (activeRecording != null) {
            try {
                activeRecording.stop();
            } catch (Exception ignored) {
            }
            activeRecording = null;
        }
    }

    @Nullable
    ImageCapture getImageCapture() {
        return imageCapture;
    }
}

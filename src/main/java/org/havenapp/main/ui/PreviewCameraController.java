package org.havenapp.main.ui;

import android.content.Context;
import android.graphics.ImageFormat;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.sensors.motion.LuminanceMotionDetector;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX-backed live preview for the aiming / sensitivity screens
 * ({@link CameraFragment} inside {@code CameraConfigureActivity} and the pre-arm view in
 * {@code MonitorActivity}). Preview only - no capture, no file writes; the actual
 * monitoring pipeline is {@code sensors.CameraMonitor} inside the service.
 */
public class PreviewCameraController {

    public interface PreviewMotionListener {
        void onMotion(int percentChanged);
    }

    private static final String TAG = "PreviewCameraController";
    private static final int ANALYSIS_W = 640;
    private static final int ANALYSIS_H = 480;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final PreviewMotionListener listener;
    private final PreferenceManager prefs;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final LuminanceMotionDetector detector = new LuminanceMotionDetector();

    private ProcessCameraProvider cameraProvider;
    private int[] lastLuma;
    private boolean started;

    public PreviewCameraController(Context context, LifecycleOwner owner,
                                   PreviewView previewView, PreviewMotionListener listener) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = owner;
        this.previewView = previewView;
        this.listener = listener;
        this.prefs = new PreferenceManager(this.context);
        detector.setThreshold(prefs.getCameraSensitivity());
    }

    public void setSensitivity(int value) {
        detector.setThreshold(value);
    }

    /** Re-read the lens preference and rebind. */
    public void updateCamera() {
        if (started) {
            stop();
            start();
        }
    }

    public void start() {
        if (started) return;
        started = true;
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bind();
            } catch (Exception e) {
                Log.e(TAG, "camera provider unavailable", e);
                started = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void stop() {
        started = false;
        lastLuma = null;
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {
            }
        }
    }

    private CameraSelector lensSelector() {
        String c = prefs.getCamera();
        if (PreferenceManager.BACK.equals(c) || "1".equals(c)) return CameraSelector.DEFAULT_BACK_CAMERA;
        return CameraSelector.DEFAULT_FRONT_CAMERA;
    }

    private void bind() {
        if (cameraProvider == null || !started) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

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

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(lifecycleOwner, lensSelector(), preview, analysis);
        } catch (Exception e) {
            Log.e(TAG, "bindToLifecycle failed", e);
            started = false;
        }
    }

    private void analyze(@NonNull ImageProxy image) {
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) return;
            int w = image.getWidth();
            int h = image.getHeight();
            int[] luma = extractLuma(image, w, h);
            if (lastLuma != null && lastLuma.length == luma.length && listener != null) {
                List<Integer> changed = detector.detectMotion(lastLuma, luma, w, h);
                int pct = changed == null ? 0 : (int) ((changed.size() / (float) luma.length) * 100);
                ContextCompat.getMainExecutor(context).execute(() -> listener.onMotion(pct));
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
}

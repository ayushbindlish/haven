package org.havenapp.main.pairing;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.util.Size;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.havenapp.main.R;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

/** Parent device: scan a child's pairing QR with the camera. */
public class ParentScanActivity extends AppCompatActivity {

    private ProcessCameraProvider provider;
    private boolean handled;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PreviewView pv = new PreviewView(this);
        FrameLayout root = new FrameLayout(this);
        root.addView(pv);
        setContentView(root);
        setTitle(R.string.parent_scan_title);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }
        startCamera(pv);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] pm, @NonNull int[] gr) {
        super.onRequestPermissionsResult(rc, pm, gr);
        if (gr.length > 0 && gr[0] == PackageManager.PERMISSION_GRANTED) {
            recreate(); // onCreate re-runs, now with permission -> starts the camera
        } else {
            finish();
        }
    }

    private void startCamera(PreviewView pv) {
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> f =
                ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try {
                provider = f.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(pv.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                                .build())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();
                analysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::scan);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void scan(@NonNull ImageProxy image) {
        try {
            if (handled || image.getFormat() != ImageFormat.YUV_420_888) return;
            int w = image.getWidth(), h = image.getHeight();
            ImageProxy.PlaneProxy p = image.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int rowStride = p.getRowStride();
            byte[] luma = new byte[w * h];
            byte[] row = new byte[rowStride];
            int o = 0;
            for (int y = 0; y < h; y++) {
                buf.position(y * rowStride);
                buf.get(row, 0, Math.min(rowStride, buf.remaining()));
                System.arraycopy(row, 0, luma, o, w);
                o += w;
            }
            String text = QrCodec.decodeLuma(luma, w, h);
            PairingPayload payload = PairingPayload.decode(text);
            if (payload != null && !handled) {
                handled = true;
                new PairedStore(this).addOrReplace(payload);
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.parent_paired, payload.name),
                            Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, ParentDashboardActivity.class));
                    finish();
                });
            }
        } catch (Exception ignored) {
        } finally {
            image.close();
        }
    }

    @Override
    protected void onDestroy() {
        if (provider != null) provider.unbindAll();
        super.onDestroy();
    }
}

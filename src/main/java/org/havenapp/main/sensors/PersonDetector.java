package org.havenapp.main.sensors;

import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

/**
 * Thin wrapper over ML Kit on-device image labelling used only to answer "is there a
 * person in this frame?". ML Kit picks NNAPI / GPU internally when the device supports it.
 */
public final class PersonDetector {

    private static final String TAG = "PersonDetector";
    private static final float MIN_CONFIDENCE = 0.60f;

    public interface Result {
        void onResult(boolean personPresent);
    }

    private final ImageLabeler labeler;

    public PersonDetector() {
        labeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build());
    }

    /** Runs async; {@code image} must stay valid until the callback (caller closes it after). */
    public void detect(@NonNull Image image, int rotationDegrees, @NonNull Result cb) {
        try {
            InputImage input = InputImage.fromMediaImage(image, rotationDegrees);
            labeler.process(input)
                    .addOnSuccessListener(labels -> {
                        boolean person = false;
                        for (com.google.mlkit.vision.label.ImageLabel l : labels) {
                            String t = l.getText();
                            if ("Person".equalsIgnoreCase(t) || "People".equalsIgnoreCase(t)) {
                                person = true;
                                break;
                            }
                        }
                        cb.onResult(person);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "labeling failed", e);
                        cb.onResult(true); // fail-open: don't suppress a real event
                    });
        } catch (Exception e) {
            Log.w(TAG, "detect error", e);
            cb.onResult(true);
        }
    }

    public void close() {
        try {
            labeler.close();
        } catch (Exception ignored) {
        }
    }
}

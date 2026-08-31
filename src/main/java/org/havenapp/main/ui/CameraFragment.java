/*
 * Copyright (c) 2017 Nathanial Freitas / Guardian Project
 *  * Licensed under the GPLv3 license.
 *
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */
package org.havenapp.main.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.havenapp.main.PreferenceManager;
import org.havenapp.main.R;
import org.havenapp.main.model.EventTrigger;
import org.havenapp.main.service.MonitorService;

/**
 * Live camera preview + motion read-out for the aiming / sensitivity screens. The real
 * monitoring pipeline runs headless in {@link MonitorService} (see
 * {@code sensors.CameraMonitor}); this fragment only shows a preview while the user is
 * setting things up, and steps aside once the service takes the camera.
 */
public final class CameraFragment extends Fragment {

    private PreviewCameraController controller;
    private PreviewView previewView;
    private PreferenceManager prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PreferenceManager(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.camera_fragment, container, false);
        previewView = view.findViewById(R.id.camera_view);
        return view;
    }

    public void setMotionSensitivity(int threshold) {
        if (controller != null) controller.setSensitivity(threshold);
    }

    public void updateCamera() {
        if (controller != null) controller.updateCamera();
    }

    public void stopCamera() {
        if (controller != null) controller.stop();
    }

    public void initCamera() {
        // Once monitoring is live the service owns the camera; don't open a second session.
        if (MonitorService.getInstance() != null && MonitorService.getInstance().isRunning()) {
            stopCamera();
            return;
        }
        if (!prefs.getCameraActivation() || previewView == null || getActivity() == null) {
            return;
        }
        if (controller == null) {
            controller = new PreviewCameraController(getActivity(), getViewLifecycleOwner(),
                    previewView, this::onPreviewMotion);
        }
        controller.setSensitivity(prefs.getCameraSensitivity());
        controller.start();
    }

    private void onPreviewMotion(int percentChanged) {
        if (isDetached() || getActivity() == null) return;
        Intent i = new Intent("event");
        i.putExtra("type", EventTrigger.CAMERA);
        i.putExtra("detected", percentChanged > 0);
        i.putExtra("changed", percentChanged);
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(i);
    }

    @Override
    public void onResume() {
        super.onResume();
        initCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCamera();
    }

    @Override
    public void onDestroyView() {
        stopCamera();
        controller = null;
        previewView = null;
        super.onDestroyView();
    }
}

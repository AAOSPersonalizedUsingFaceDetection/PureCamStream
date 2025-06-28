package com.example.purecamstream;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.os.*;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Camera2PreviewOnly";
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final Size TARGET_RESOLUTION = new Size(640, 480);

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;

    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private boolean isCameraOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        } else {
            startBackgroundThread();
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    openCamera();
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
            });
        }
    }

    private void openCamera() {
        Log.d(TAG, "Attempting to open camera");
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.i(TAG, "Camera found: " + cameraId + ", facing: " + facing);

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        isCameraOpen = true;
                        startPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        isCameraOpen = false;
                        camera.close();
                        if (cameraDevice == camera) cameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        isCameraOpen = false;
                        camera.close();
                        if (cameraDevice == camera) cameraDevice = null;
                    }
                }, backgroundHandler);
                break;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access failed", e);
        }
    }

    private void startPreview() {
        Log.d(TAG, "Starting preview");
        try {
            if (surfaceHolder.getSurface() == null || !surfaceHolder.getSurface().isValid()) {
                Log.e(TAG, "Surface is not valid");
                return;
            }

            Surface previewSurface = surfaceHolder.getSurface();

            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);

            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 30));
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                                Log.d(TAG, "Preview started successfully");
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture request failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Preview start failed", e);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Background thread stop failed", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (!isCameraOpen && surfaceHolder.getSurface().isValid()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            isCameraOpen = false;
        } finally {
            stopBackgroundThread();
            super.onPause();
        }
    }
}

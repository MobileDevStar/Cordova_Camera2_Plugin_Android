package com.appnovation.ppw_camera;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.graphics.Rect;
import java.util.ArrayList;

import java.io.IOException;
import java.util.List;

public class PPWCameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    public static final String TAG = "PPWCameraPreview";

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private int mCamFacing;
    private boolean mFocusMutex = false;

    //scale properties
    private float mScaleFactor = 1.f;
    private ScaleGestureDetector mScaleDetector;

    public PPWCameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        Camera.CameraInfo cam = new Camera.CameraInfo();
        mCamFacing = cam.facing;
        mFocusMutex = true;
    }

    public void clearCamera() {
       if (mCamera != null) {
            if (mHolder != null) {
                mHolder.removeCallback(this);
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
       }
    }

    private Camera getCamInstance() {
        if (mCamera != null) {
            return mCamera;
        }
        mCamera = PPWCameraActivity.getCameraInstance();
        return mCamera;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera = getCamInstance();
            Camera.Parameters p = mCamera.getParameters();
            List<String> supportedFocusModes = p.getSupportedFocusModes();
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            mCamera.setParameters(p);
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            PPWCameraActivity.mTakePictureMutex = true;
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void changeOrientation(int orientation) {
        try {
            mCamera.stopPreview();
            mCamera.setDisplayOrientation(orientation);
            mCamera.startPreview();
        } catch (Exception e) {/**/}
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        clearCamera();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mHolder.getSurface() == null){
          return;
        }
        try {
            mCamera.stopPreview();
        } catch (Exception e){
        }
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            PPWCameraActivity.mTakePictureMutex = true;
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private Rect focusArea(float x, float y) {
        Rect touchRect = new Rect(
            (int)(x - 100), 
            (int)(y - 100), 
            (int)(x + 100), 
            (int)(y + 100));

        final Rect targetFocusRect = new Rect(
            touchRect.left * 2000/this.getWidth() - 1000,
            touchRect.top * 2000/this.getHeight() - 1000,
            touchRect.right * 2000/this.getWidth() - 1000,
            touchRect.bottom * 2000/this.getHeight() - 1000);

        return targetFocusRect;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        if(event.getAction() == MotionEvent.ACTION_DOWN && event.getPointerCount()==1 && mFocusMutex) {
            mFocusMutex = false;
            try {
                mCamera.cancelAutoFocus();
                Rect focusRect = focusArea(event.getX(), event.getY());
                List<Camera.Area> focusList = new ArrayList<Camera.Area>();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    Camera.Area focusArea = new Camera.Area(focusRect, 1000);
                    focusList.add(focusArea);
                    Camera.Parameters p = mCamera.getParameters();
                    if (p.getMaxNumFocusAreas() > 0) {
                        p.setFocusAreas(focusList);
                    }
                    if (p.getMaxNumMeteringAreas() > 0) {
                        p.setMeteringAreas(focusList);
                    }

                    mCamera.setParameters(p);
                }
                
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Camera.Parameters params = camera.getParameters();
                        if (params.getFlashMode() != null && params.getFlashMode().compareToIgnoreCase(Camera.Parameters.FLASH_MODE_TORCH) == 0) {
                            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF); //turn flash on/off to fix disabling itself on focus
                            camera.setParameters(params);
                            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                            camera.setParameters(params);
                        }
                        if (success) {
                            camera.cancelAutoFocus();
                        }
                        mFocusMutex = true;
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Tap focus error: " + e.getMessage());
                mFocusMutex = true;
            }
        }
        return true;
    }

    public class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            Camera.Parameters p = mCamera.getParameters();
            if (p.isZoomSupported()) {
                mScaleFactor *= detector.getScaleFactor();
                int maxZoom = p.getMaxZoom();
                int nextZoom = (int)((mScaleFactor - 1)*maxZoom);
                if ( nextZoom > maxZoom ) {
                    nextZoom = p.getMaxZoom();
                    mScaleFactor = 2;
                }
                if ( nextZoom < 0 ) {
                    nextZoom = 0;
                    mScaleFactor = 1;
                }
                p.setZoom(nextZoom);
                mCamera.setParameters(p);
            }
            mFocusMutex = true;
            return true;
        }
    }
}
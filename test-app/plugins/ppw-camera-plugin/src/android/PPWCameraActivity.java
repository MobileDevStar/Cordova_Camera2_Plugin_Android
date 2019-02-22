package com.appnovation.ppw_camera;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import org.apache.sanselan.Sanselan;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;
import android.graphics.Paint.Style;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;
import android.media.ThumbnailUtils;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.Display;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.location.LocationListener;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.lang.Runnable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PPWCameraActivity extends Activity {

    private static PPWCameraActivity mInstance = null;

    public static final String TAG = "PPWCameraActivity";
    private static final String SECRET_KEY = "password";

    private static final String FLASH_NAME_AUTO = "auto";
    private static final String FLASH_NAME_TORCH = "torch";
    private static final String FLASH_NAME_ON = "on";
    private static final String FLASH_NAME_OFF = "off";

    private final int ORIENTATION_DETECTION_THRESHOLD = 40;

    private static Camera mCamera;
    private PPWCameraPreview mPreview;
    private static HashMap<String,String> mDataOutput;
    private int mPhotoWidth;
    private int mPhotoHeight;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private String mEncodingType;
    private int mQuality;
    private int mThumbnail;
    private boolean mBackNotify;
    private String mFlashType = null;
    private int mDateFontSize;
    private String mDateFormat;
    private boolean mShutterSoundOff;
    private JSONObject mOptions;

    ImageButton thumbButton;
    ImageButton imageViewButton;

    public static boolean mTakePictureMutex = false;
    public boolean mInit = false;

    private String mConfirmErrorMessage;
    private float mConfirmationTimeInterval;
    private Handler mConfirmationTimer = new Handler();

    //GPS Data
    private Location mLocation;
    private LocationManager mLocationMgr;

    // rotation handler
    private OrientationEventListener mOrientationListener;
    private boolean mIsOrientationLandscapeRight = false;

    public static PPWCameraActivity getInstance() {
        return mInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mCamera = null;
            mPreview.clearCamera(); // release the camera immediately to fix pause crash
        }

        if (mOrientationListener != null)
            mOrientationListener.disable();

        mInstance = null;
        mInit = false;
        if (mLocationMgr != null) {
            mLocationMgr.removeUpdates(mLocationListener);
            mLocationMgr = null;
            mLocation = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mInstance = this;
        init();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mBackNotify) {
            PPWCamera.sendError("back button clicked",2,PPWCamera.openCameraCallbackContext);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        /* since onKeyDown is called many times, if the key is held down, we do our actions in onKeyUp
           because this gets called once only.
        */
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            takePicture();
        }

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // This prevents volume from changing if the user presses any of the volume keys
        // we also don't block events for any other keys than the two volume buttons
        return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP;
    }

    public void confirmCamera() {
        mConfirmationTimer.removeCallbacks(showConfirmErrorPopup);
    }

    private Runnable showConfirmErrorPopup = new Runnable() {
       @Override
       public void run() {
          new AlertDialog.Builder(PPWCameraActivity.this)
                            .setTitle("Error")
                            .setMessage(mConfirmErrorMessage)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setCancelable(false)
                            .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    PPWCameraActivity.this.finish();
                                }
                            })
                            .show();
       }
    };

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            Log.d(TAG, "Location update: " + location.toString());
            mLocation = location;
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };

    public void init() {
        if (mInit) {
            return;
        }
        mInit = true; //don't initialize more than once

        setContentView(getR("layout","activity_ppw_camera"));

        mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= (270 - ORIENTATION_DETECTION_THRESHOLD) && orientation <= (270 + ORIENTATION_DETECTION_THRESHOLD)) {
                    if (mIsOrientationLandscapeRight) {
                        if (mPreview != null)
                            mPreview.changeOrientation(0);

                        mIsOrientationLandscapeRight = false;
                    }
                } else if (orientation >= (90 - ORIENTATION_DETECTION_THRESHOLD) && orientation <= (90 + ORIENTATION_DETECTION_THRESHOLD)) {
                    if (!mIsOrientationLandscapeRight) {
                        if (mPreview != null)
                            mPreview.changeOrientation(180);
                        mIsOrientationLandscapeRight = true;
                    }
                }
            }
        };

        // start listener for orientation changes
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }

        //init gps
        mLocationMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!mLocationMgr.isProviderEnabled( LocationManager.GPS_PROVIDER ) &&
                !mLocationMgr.isProviderEnabled( LocationManager.NETWORK_PROVIDER )) {
            new AlertDialog.Builder(PPWCameraActivity.this)
                            .setTitle("Error")
                            .setMessage("Location data is not available")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setCancelable(true)
                            .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                    // startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                }
                            })
                            .show();
        }
        mLocationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
        mLocationMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, mLocationListener);
        mLocation = mLocationMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (mLocation == null) {
            mLocation = mLocationMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        //output custom data
        mDataOutput = new HashMap<String,String>();

        //icon font face
        Typeface font = Typeface.createFromAsset(getAssets(), "flaticon_ppw_camera.ttf");

        //create a cropped border
        class CroppedCameraPreview extends FrameLayout {
            private SurfaceView cameraPreview;
            private int actualHeight = 0;
            private int actualWidth = 0;
            private int frameWidth = 0;
            private int frameHeight = 0;
            public CroppedCameraPreview( Context context, SurfaceView view) {
                super( context );
                cameraPreview = view;
                actualWidth = context.getResources().getDisplayMetrics().widthPixels;
                actualHeight = context.getResources().getDisplayMetrics().heightPixels;
                frameWidth = actualWidth;
                frameHeight = actualHeight;
                try
                {
                    WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
                    Display display = windowManager.getDefaultDisplay();

                    // includes window decorations (statusbar bar/menu bar)
                    if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17) {
                        actualWidth = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                        actualHeight = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
                    }

                    // includes window decorations (statusbar bar/menu bar)
                    if (Build.VERSION.SDK_INT >= 17) {
                        Point realSize = new android.graphics.Point();
                        Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                        actualWidth = realSize.x;
                        actualHeight = realSize.y;
                    }

                } catch (Exception ignored)
                {
                }
            }

            @Override
            protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
                int height = actualHeight;
                int width = actualWidth;
                float actualRatio = ((float)actualWidth)/actualHeight;
                float ratio = ((float)mPreviewWidth)/mPreviewHeight;
                if (actualRatio > ratio) {
                    width = (int) (height * ratio);
                } else {
                    height = (int) (width / ratio);
                }
                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout( boolean changed, int l, int t, int r, int b) {
                if (cameraPreview != null) {
                    int height = b-t;
                    int width = r-l;
                    Size previewSize = getCameraInstance().getParameters().getPreviewSize();
                    float previewRatio = ((float)previewSize.width)/previewSize.height;
                    float ratio = ((float)width)/height;
                    if (previewRatio > ratio) {
                        width = (int)(height * previewRatio);
                    } else {
                        height = (int)(width / previewRatio);
                    }
                    final float deltaFraction = 0.5f;
                    int deltaWidth = (int)(((width - (r-l))*deltaFraction) + (actualWidth-frameWidth));
                    int deltaHeight = (int)(((height - (b-t))*deltaFraction) + (actualHeight-frameHeight));
                    cameraPreview.layout (-deltaWidth,-deltaHeight,width-deltaWidth,height-deltaHeight);
                }
            }
        }

        // Create our Preview view and set it as the content of our activity.
        mPreview = new PPWCameraPreview(this, getCameraInstance());
        FrameLayout preview = (FrameLayout) findViewById(getR("id","frame_camera_preview"));
        CroppedCameraPreview croppedPreview = new CroppedCameraPreview(this,mPreview);
        preview.addView(croppedPreview);
        croppedPreview.addView(mPreview);

        int initialOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
        if (initialOrientation == Surface.ROTATION_270) {
            mPreview.changeOrientation(180);
        }

        // this is the button that handles click events, the actual close button only acts as a visual
        final Button button_exit_tap = (Button) findViewById(getR("id", "button_exit_tap"));
        if (button_exit_tap != null) {
            button_exit_tap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                    if (mBackNotify) {
                        PPWCamera.sendError("close button clicked", 1, PPWCamera.openCameraCallbackContext);
                    }
                }
            });
        }

        // Add a listener to the Close button
        final Button closeButton = (Button) findViewById(getR("id","button_exit"));
        closeButton.setTypeface(font);
        closeButton.setText(getR("string","close_icon"));

        //add a listener to the imageview button
        imageViewButton = (ImageButton) findViewById(getR("id","button_imageView"));
        imageViewButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    imageViewButton.setVisibility(View.INVISIBLE);
                }
            }
        );

        // Add a listener to the thumbnail button
        thumbButton = (ImageButton) findViewById(getR("id","button_thumbnail"));
        thumbButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    imageViewButton.setVisibility(View.VISIBLE);
                }
            }
        );

        // Add a listener to the Capture button
        Button captureButton = (Button) findViewById(getR("id","button_capture"));
        captureButton.setTypeface(font);
        captureButton.setText(getR("string","camera_icon"));
        captureButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePicture();
                }
            }
        );

        mPhotoWidth = 640;
        mPhotoHeight = 480;
        mPreviewWidth = 640;
        mPreviewHeight = 480;
        mEncodingType = "jpg";
        mQuality = 100;
        mThumbnail = 25;
        mBackNotify = false;
        mFlashType = FLASH_NAME_AUTO;
        mConfirmationTimeInterval = 500;
        mConfirmErrorMessage = "Error confirming photo captured";
        mDateFontSize = 20;
        mShutterSoundOff = false;

        //scroll through overlay options
        if (PPWCamera.openCameraJsonArrayArgs != null && PPWCamera.openCameraJsonArrayArgs.length() > 0) {
            Log.d(TAG,""+PPWCamera.openCameraJsonArrayArgs.toString());

            JSONObject options = PPWCamera.openCameraJsonArrayArgs.optJSONObject(0);
            mPhotoWidth = options.optInt("targetWidth", 640);
            mPhotoHeight = options.optInt("targetHeight",480);
            mPreviewWidth = options.optInt("previewWidth", 640);
            mPreviewHeight = options.optInt("previewHeight",480);
            mEncodingType = options.optString("encodingType", "jpg");
            mQuality = options.optInt("quality", 100);
            mThumbnail = options.optInt("thumbnail",25);
            mBackNotify = options.optBoolean("backNotify",false);
            mFlashType = options.optString("flashType",FLASH_NAME_AUTO);
            mConfirmationTimeInterval = options.optInt("confirmTimeInterval",500);
            mConfirmErrorMessage = options.optString("confirmErrorMessage","Error confirming photo captured");
            mDateFontSize = options.optInt("dateFontSize",20);
            mDateFormat = options.optString("dateFormat","");
            mShutterSoundOff = options.optBoolean("shutterSoundOff", false);
            mOptions = options.optJSONObject("options");

            //setup camera for new values
            setupCamera();

            JSONArray overlay = options.optJSONArray("overlay");
            if (overlay != null) {
                for (int i=0;i<overlay.length();++i) {

                    JSONObject item = overlay.optJSONObject(i);
                    String type = item.optString("type");
                    if (type != null) {

                        RelativeLayout layout = (RelativeLayout) findViewById(getR("id","container"));
                        RelativeLayout.LayoutParams rp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        String position = item.optString("position");
                        if (position != null) {
                            if (position.contains("bottom")) {
                                rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                            } else if (position.contains("top")) {
                                rp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                            } else if (position.startsWith("center")) {
                                rp.addRule(RelativeLayout.CENTER_VERTICAL);
                            }
                            if (position.contains("left")) {
                                rp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                            } else if (position.contains("right")) {
                                rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                            } else if (position.endsWith("center")) {
                                rp.addRule(RelativeLayout.CENTER_HORIZONTAL);
                            }
                        }
                        int top = getPixelSP(item.optInt("top", 0));
                        int left = getPixelSP(item.optInt("left", 0));
                        int bottom = getPixelSP(item.optInt("bottom", 0));
                        int right = getPixelSP(item.optInt("right", 0));
                        rp.setMargins(left,top,right,bottom);

                        TextView view = null;
                        if (type.compareToIgnoreCase("text") == 0) {

                            //add a new text view
                            view = new TextView(this);
                            view.setText(item.optString("value", "error"));
                            view.setTextColor(Color.WHITE);
                            view.setShadowLayer(2, -1, 1, Color.BLACK);

                        }
                        else if (type.compareToIgnoreCase("carousel") == 0) {

                            class tagItem {
                                public int count;
                                public JSONArray value;
                                public String id;
                                public String initial;
                            };

                            //add a new button
                            view = new Button(this);
                            tagItem t = new tagItem();
                            t.value = item.optJSONArray("value");
                            t.id = item.optString("id");
                            t.initial = item.optString("initial","");
                            t.count = 0;
                            for(int j=0; j<t.value.length(); ++j) {
                                if (t.initial.compareTo(t.value.optString(j,""))==0) {
                                    t.count = j;
                                }
                            }
                            view.setTag(t);
                            String selected = t.value.optString(t.count,"error");
                            view.setText(selected);
                            view.setTextColor(Color.WHITE);
                            view.setShadowLayer(2, -1, 1, Color.BLACK);
                            Drawable bg = view.getBackground();
                            if (bg != null) {
                                bg.setAlpha(25);
                            }
                            mDataOutput.put(t.id,selected);
                            view.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    tagItem t = (tagItem)v.getTag();
                                    t.count++;
                                    if (t.count >= t.value.length()) {
                                        t.count = 0;
                                    }
                                    String selected = t.value.optString(t.count,"error");
                                    ((TextView)v).setText(selected);
                                    mDataOutput.put(t.id, selected);
                                }
                            });

                        }

                        if (view != null) {
                            view.setTextSize(TypedValue.COMPLEX_UNIT_SP,item.optInt("size",12));
                            view.setLayoutParams(rp);
                            layout.addView(view);
                        }
                    }
                }
            }
        }

        // Add a listener to the flash button
        Camera.Parameters params = getCameraInstance().getParameters();
        final Button flashButton = (Button) findViewById(getR("id","button_flash"));
        flashButton.setTypeface(font);
        final List<String> supportedFlash = params.getSupportedFlashModes();
        if (supportedFlash == null || params.getFlashMode() == null || !supportedFlash.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
            flashButton.setVisibility(View.INVISIBLE); //hide if not supported
        }
        else {
            int defaultColor = Color.WHITE;
            int defaultIcon = getR("string","flash_auto_icon");
            String defaultFlash = Camera.Parameters.FLASH_MODE_AUTO;
            if (mFlashType.compareToIgnoreCase(FLASH_NAME_OFF) == 0 && supportedFlash.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                defaultColor = Color.DKGRAY;
                defaultIcon = getR("string","flash_off_icon");
                defaultFlash = Camera.Parameters.FLASH_MODE_OFF;
            } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_ON) == 0 && supportedFlash.contains(Camera.Parameters.FLASH_MODE_ON)) {
                defaultColor = Color.YELLOW;
                defaultIcon = getR("string","flash_on_icon");
                defaultFlash = Camera.Parameters.FLASH_MODE_ON;
            } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_TORCH) == 0 && supportedFlash.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                defaultColor = Color.GREEN;
                defaultIcon = getR("string","flash_on_icon");
                defaultFlash = Camera.Parameters.FLASH_MODE_TORCH;
            }

            params.setFlashMode(defaultFlash);
            flashButton.setText(defaultIcon);
            getCameraInstance().setParameters(params);

            flashButton.setTextColor(defaultColor);
            GradientDrawable gd = (GradientDrawable)flashButton.getBackground();
            gd.setStroke(getPixelSP(2),defaultColor);
            ((GradientDrawable)closeButton.getBackground()).setStroke(getPixelSP(2),Color.WHITE);

            flashButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Camera.Parameters params = getCameraInstance().getParameters();
                        String currentFlash = params.getFlashMode();
                        Log.d(TAG,"current flash "+currentFlash);
                        String nextFlash = supportedFlash.get(0);
                        if (currentFlash.compareTo(Camera.Parameters.FLASH_MODE_AUTO) == 0) {
                            if (supportedFlash.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                nextFlash = Camera.Parameters.FLASH_MODE_TORCH;
                            }
                            else {
                                currentFlash = Camera.Parameters.FLASH_MODE_TORCH;
                            }
                        }
                        if (currentFlash.compareTo(Camera.Parameters.FLASH_MODE_TORCH) == 0) {
                            if (supportedFlash.contains(Camera.Parameters.FLASH_MODE_ON)) {
                                nextFlash = Camera.Parameters.FLASH_MODE_ON;
                            }
                            else {
                                currentFlash = Camera.Parameters.FLASH_MODE_ON;
                            }
                        }
                        if (currentFlash.compareTo(Camera.Parameters.FLASH_MODE_ON) == 0) {
                            if (supportedFlash.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                                nextFlash = Camera.Parameters.FLASH_MODE_OFF;
                            }
                            else {
                                currentFlash = Camera.Parameters.FLASH_MODE_OFF;
                            }
                        }
                        if (currentFlash.compareTo(Camera.Parameters.FLASH_MODE_OFF) == 0) {
                            if (supportedFlash.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                                nextFlash = Camera.Parameters.FLASH_MODE_AUTO;
                            }
                            else {
                                currentFlash = Camera.Parameters.FLASH_MODE_AUTO;
                            }
                        }
                        Log.d(TAG,"next flash "+nextFlash);
                        int nextColor = Color.WHITE;
                        int nextIcon = getR("string","flash_auto_icon");
                        mFlashType = FLASH_NAME_AUTO;
                        if (nextFlash.compareTo(Camera.Parameters.FLASH_MODE_OFF) == 0) {
                            nextColor = Color.DKGRAY;
                            nextIcon = getR("string","flash_off_icon");
                            mFlashType = FLASH_NAME_OFF;
                        } else if (nextFlash.compareTo(Camera.Parameters.FLASH_MODE_ON) == 0) {
                            nextColor = Color.YELLOW;
                            nextIcon = getR("string","flash_on_icon");
                            mFlashType = FLASH_NAME_ON;
                        } else if (nextFlash.compareTo(Camera.Parameters.FLASH_MODE_TORCH) == 0) {
                            nextColor = Color.GREEN;
                            nextIcon = getR("string","flash_on_icon");
                            mFlashType = FLASH_NAME_TORCH;
                        }
                        params.setFlashMode(nextFlash);
                        flashButton.setText(nextIcon);
                        getCameraInstance().setParameters(params);

                        //update color
                        flashButton.setTextColor(nextColor);
                        GradientDrawable gd = (GradientDrawable)flashButton.getBackground();
                        gd.setStroke(getPixelSP(2),nextColor);
                        ((GradientDrawable)closeButton.getBackground()).setStroke(getPixelSP(2),Color.WHITE);
                    }
                }
            );
        }
    }

    private void takePicture() {
        // get an image from the camera
        if (mTakePictureMutex) {
            mTakePictureMutex = false;
            Toast.makeText(PPWCameraActivity.this, "Saving...",Toast.LENGTH_SHORT).show();
            try {

                // providing empty shutter callback to Camera.takePicture() plays the sound,
                // while passing `null` disables the sound
                Camera.ShutterCallback camera_shutterCallback = null;
                if (!mShutterSoundOff) {
                    camera_shutterCallback = new Camera.ShutterCallback() {
                        @Override
                        public void onShutter() {}
                    };
                }

                //add updated gps data
                if (mLocation != null) {
                    Camera.Parameters params = getCameraInstance().getParameters();
                    params.removeGpsData();
                    params.setGpsLatitude(mLocation.getLatitude());
                    params.setGpsLongitude(mLocation.getLongitude());
                    params.setGpsAltitude(mLocation.getAltitude());
                    params.setGpsTimestamp((long)(mLocation.getTime()*0.001));
                    params.setGpsProcessingMethod(mLocation.getProvider());
                    getCameraInstance().setParameters(params);
                }
                getCameraInstance().takePicture(camera_shutterCallback, null, mPicture);
            } catch (Exception e) {
                Log.d(TAG,"exception on picture taking "+e.getMessage());
                sendError();
            }
        }
    }

    public int getR(String group, String key) {
        return getApplicationContext().getResources().getIdentifier(key, group, getApplicationContext().getPackageName());
    }

    private int getPixelSP(int pixels) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,pixels,getResources().getDisplayMetrics());
    };

    public static Camera getCameraInstance(){
        if (mCamera!=null) {
            return mCamera;
        }

        Camera c = null;
        try {
            c = Camera.open();
            mCamera = c;
        }
        catch (Exception e){
            Log.e(TAG, "failed to open Camera");
            sendError();
        }
        return c;
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] imageData, Camera camera) {

            final String timeStamp = String.valueOf(System.currentTimeMillis());
            final String FILENAME = timeStamp + "."+mEncodingType;
            final String FILENAME_THUMB = timeStamp + "_thumb."+mEncodingType;
            final String FILENAME_DATA = timeStamp + ".json";

                Log.d(TAG, "here");

            try {
                //check disk space
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long availableBlocks = stat.getAvailableBlocks();
                long availableBytes = blockSize*availableBlocks;
                byte[] imageResize = resizeImage(imageData, mPhotoWidth, mPhotoHeight, true);
                byte[] imageThumb = null;
                if (mThumbnail > 0) {
                    imageThumb = resizeImage(imageResize, (int)(mPhotoWidth*mThumbnail*0.01f), (int)(mPhotoHeight*mThumbnail*0.01f), false);
                }
                int dataLength = imageResize.length;
                if (mThumbnail > 0) {
                    dataLength += imageThumb.length;
                }

                if (availableBytes <= dataLength) {

                    String availSize = Formatter.formatFileSize(PPWCameraActivity.this, availableBytes);

                    new AlertDialog.Builder(PPWCameraActivity.this)
                            .setTitle("Unable to Save - Disk Full")
                            .setMessage("Available space: " + availSize)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                //save if space available
                else {
                    //add meta deta
                    if (mEncodingType.compareToIgnoreCase("png") != 0) {
                        imageResize = addJPEGExifTagsFromSource(imageData, imageResize);
                    }

                    //create new
                    FileOutputStream fos = openFileOutput(FILENAME, Context.MODE_PRIVATE);
                    fos.write(imageResize);
                    fos.close();
                    String imagePath = getFilesDir() + "/" + FILENAME;

                    String imagePathThumb = "";
                    if (imageThumb != null) {
                        fos = openFileOutput(FILENAME_THUMB, Context.MODE_PRIVATE);
                        fos.write(imageThumb);
                        fos.close();
                        imagePathThumb = getFilesDir() + "/" + FILENAME_THUMB;
                    }

                    String hash = hmacSha512(bytesToHex(imageResize), SECRET_KEY);

                    JSONObject output = new JSONObject();
                    output.put("imageURI",imagePath);
                    output.put("imageThumbURI",imagePathThumb);
                    output.put("lastModifiedDate",timeStamp);
                    output.put("size",imageResize.length);
                    output.put("type",mEncodingType);
                    output.put("hash",hash);
                    output.put("flashType",mFlashType);
                    output.put("options",mOptions);
                    output.put("jsonURI",FILENAME_DATA);
                    output.put("root","external");

                    if (!mDataOutput.isEmpty()) {
                        JSONObject data = new JSONObject();
                        for (HashMap.Entry<String, String> entry : mDataOutput.entrySet()) {
                            data.put(entry.getKey(),entry.getValue());
                        }
                        output.put("data",data);
                    }

                    fos = openFileOutput(FILENAME_DATA, Context.MODE_PRIVATE);
                    fos.write(output.toString().getBytes("utf-8"));
                    fos.close();

                    //update thumbnail
                    if (mThumbnail > 0) {
                        //setup image view
                        Bitmap image = BitmapFactory.decodeByteArray(imageResize, 0, imageResize.length);
                        imageViewButton.setImageBitmap(image);
                        imageViewButton.setVisibility(View.INVISIBLE);

                        int radius = (int)(mPhotoWidth*(mThumbnail*0.01f));
                        if (mPhotoHeight > mPhotoWidth) {
                            radius = (int)(mPhotoHeight*(mThumbnail*0.01f));
                        }

                        Bitmap thumb = ThumbnailUtils.extractThumbnail(image, radius, radius);
                        thumbButton.setImageBitmap(getCircleBitmap(thumb));
                        thumbButton.setVisibility(View.VISIBLE);
                    }

                    Log.d(TAG, output.toString());
                    PluginResult result = new PluginResult(PluginResult.Status.OK, output);
                    result.setKeepCallback(true);
                    PPWCamera.openCameraCallbackContext.sendPluginResult(result);

                    //start timer to check for confirmation
                    mConfirmationTimer.removeCallbacks(showConfirmErrorPopup);
                    mConfirmationTimer.postDelayed(showConfirmErrorPopup, (long)mConfirmationTimeInterval);
                }

            } catch (Exception e) {
                Log.d(TAG, "File not found Error: " + e.getMessage());
                sendError();
            }
            camera.cancelAutoFocus();
            camera.stopPreview();
            camera.startPreview();
            PPWCameraActivity.mTakePictureMutex = true;
        }
    };

    private static void sendError() {
        PPWCamera.sendError("camera error",0,PPWCamera.openCameraCallbackContext);
    }

    /*
     * Camera modifier
     */
    protected Size determineBestSize(List<Camera.Size> sizes, int width, int height) {
        for (Size currentSize : sizes) {
            if (currentSize.width <= width && currentSize.height <= height) {
                return currentSize;
            }
        }

        return sizes.get(0);
    }

    private Size determineBestPreviewSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPreviewSizes(), mPreviewWidth, mPreviewHeight);
    }

    private Size determineBestPictureSize(Camera.Parameters parameters) {
        return determineBestSize(parameters.getSupportedPictureSizes(), mPhotoWidth, mPhotoHeight);
    }

    public void setupCamera() {
        Camera.Parameters parameters = getCameraInstance().getParameters();
        setHiddenParameter(parameters, "zsl-values", "zsl", "on");
        Size bestPreviewSize = determineBestPreviewSize(parameters);
        Size bestPictureSize = determineBestPictureSize(parameters);
        parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
        parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);
        getCameraInstance().setParameters(parameters);
    }

    /*
     * Image modifiers
     */
    byte[] resizeImage(byte[] input, int width, int height, boolean shouldTimeStamp) {

        //down scale and crop image
        Bitmap original = BitmapFactory.decodeByteArray(input, 0, input.length);
        Bitmap resized = ThumbnailUtils.extractThumbnail(original, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        Bitmap dest = resized;

        // add rotation
        if (mIsOrientationLandscapeRight) {
            Matrix mat = new Matrix();
            mat.postRotate(180);
            resized = Bitmap.createBitmap(resized, 0, 0, width, height, mat, true);
        }

        //add timestamp to photo
        if (shouldTimeStamp) {
            dest = Bitmap.createBitmap(resized.getWidth(), resized.getHeight(), Bitmap.Config.ARGB_8888);
            SimpleDateFormat sdf = new SimpleDateFormat(mDateFormat);
            final String dateTime = sdf.format(new Date()); // reading local time in the system
            final Canvas canvas = new Canvas(dest);
            final Paint fillPaint = new Paint();
            fillPaint.setTextSize(mDateFontSize*(((float)width)/mPhotoWidth));
            fillPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            fillPaint.setColor(Color.YELLOW);
            fillPaint.setStyle(Style.FILL);
            final Paint strokePaint = new Paint(fillPaint);
            strokePaint.setTextSize(mDateFontSize*(((float)width)/mPhotoWidth));
            strokePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            strokePaint.setColor(Color.DKGRAY);
            strokePaint.setStyle(Style.STROKE);
            strokePaint.setStrokeWidth(2);
            canvas.drawBitmap(resized, 0f, 0f, null);
            final float dateHeight = fillPaint.measureText("yY");
            final float dateWidth = fillPaint.measureText(dateTime, 0, dateTime.length());
            final float datePadding = 10;
            canvas.drawText(dateTime, width-dateWidth-datePadding, height-datePadding, strokePaint);
            canvas.drawText(dateTime, width-dateWidth-datePadding, height-datePadding, fillPaint);
        }

        //set encoding format
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        if (mEncodingType.compareToIgnoreCase("png") == 0) {
            dest.compress(Bitmap.CompressFormat.PNG, mQuality, blob);
        } else {
            dest.compress(Bitmap.CompressFormat.JPEG, mQuality, blob);
        }
        resized.recycle();

        return blob.toByteArray();
    }

    Bitmap getCircleBitmap(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.RED);
        canvas.drawOval(new RectF(rect), paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        bitmap.recycle();

        return output;
    }

    /*
     * HMAC SHA-512 encoding
     */
    private static String hmacSha512(String value, String key)
            throws UnsupportedEncodingException, NoSuchAlgorithmException,
            InvalidKeyException {
        String type = "HmacSHA512";
        SecretKeySpec secret = new SecretKeySpec(key.getBytes(), type);
        Mac mac = Mac.getInstance(type);
        mac.init(secret);
        byte[] bytes = mac.doFinal(value.getBytes());
        return bytesToHex(bytes);
    }

    private final static char[] hexArray = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /*
     * Exif tagging
     */
    private byte[] addJPEGExifTagsFromSource(byte[] sourceMetaData, byte[] destImageData) {
        try {
            TiffOutputSet outputSet = null;
            JpegImageMetadata metadata = (JpegImageMetadata)Sanselan.getMetadata(sourceMetaData);
            if (null != metadata) {
                TiffImageMetadata exif = metadata.getExif();
                if (null != exif) {
                    outputSet = exif.getOutputSet();

                    List<?> dirs = outputSet.getDirectories();
                    for (int i = 0; i < dirs.size(); i++) {
                        try {
                            TiffOutputDirectory dir = (TiffOutputDirectory) dirs.get(i);
                            dir.setJpegImageData(null);
                            dir.setTiffImageData(null);
                        } catch (Exception e) {
                            Log.d(TAG,"Exception on removing thumbnail image: "+e.getMessage());
                            sendError();
                        }
                    }
                }
            }

            if (null != outputSet) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ExifRewriter ER = new ExifRewriter();
                ER.updateExifMetadataLossless(destImageData, bos, outputSet);
                return bos.toByteArray();
            }
        } catch (Exception e) {
            Log.d(TAG,"Exception on exif adding: "+e.getMessage());
            sendError();
        }
        return destImageData;
    }

    private static void setHiddenParameter(Camera.Parameters params, String values_key, String key, String value) {
        if (params.get(key) == null) {
            return;
        }

        String possible_values_str = params.get(values_key);
        if (possible_values_str == null) {
            return;
        }

        String[] possible_values = possible_values_str.split(",");
        for (String possible : possible_values) {
            if (possible.equals(value)) {
                params.set(key, value);
                return;
            }
        }
    }
}

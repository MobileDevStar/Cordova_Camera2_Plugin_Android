package com.appnovation.ppw_camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.cordova.PluginResult;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@TargetApi(21)
public class PPWCameraActivity2 extends Activity {

    private static PPWCameraActivity2 mInstance = null;

    public static final String TAG = "PPWCameraActivity2";

    private static final String SECRET_KEY = "password";

    private static final String FLASH_NAME_AUTO = "auto";
    private static final String FLASH_NAME_TORCH = "torch";
    private static final String FLASH_NAME_ON = "on";
    private static final String FLASH_NAME_OFF = "off";

    private final int ORIENTATION_DETECTION_THRESHOLD = 40;

    private static HashMap<String, String> mDataOutput;
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
    Button flashButton;
    Button closeButton;

    public static boolean mTakePictureMutex = false;
    public boolean mInit = false;

    private String mConfirmErrorMessage;
    private float mConfirmationTimeInterval;
    private Handler mConfirmationTimer = new Handler();

    //GPS Data
    private Location mLocation;
    private LocationManager mLocationMgr;

    CameraCharacteristics mCharacteristics;
    // rotation handler
    private OrientationEventListener mOrientationListener;
    private boolean mIsOrientationLandscapeRight = false;

    private JSONObject callbackOutput;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    /**
     * Tag for the {@link Log}.
     */
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //Zooming
    public float finger_spacing = 0;
    public float zoom_level = 1f;
    public float maximumZoomLevel;
    public Rect zoom;


    public static PPWCameraActivity2 getInstance() {
        return mInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        init();
    }

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


    private void init() {
        if (mInit) {
            return;
        }
        mInit = true;

        setContentView(getR("layout", "activity_ppw_camera2"));
        mTextureView = (AutoFitTextureView) findViewById(getR("id", "frame_camera_preview"));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PPWCameraActivity2.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        //startBackgroundThread();


        mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= (270 - ORIENTATION_DETECTION_THRESHOLD) && orientation <= (270 + ORIENTATION_DETECTION_THRESHOLD)) {
                    if (mIsOrientationLandscapeRight) {
                        changeOrientation(0);
                        /*if (mPreview != null)
                            mPreview.changeOrientation(0);*/

                        mIsOrientationLandscapeRight = false;
                    }
                } else if (orientation >= (90 - ORIENTATION_DETECTION_THRESHOLD) && orientation <= (90 + ORIENTATION_DETECTION_THRESHOLD)) {
                    if (!mIsOrientationLandscapeRight) {
                        changeOrientation(180);
                        /*if (mPreview != null)
                            mPreview.changeOrientation(180);*/
                        mIsOrientationLandscapeRight = true;
                    }
                }
                // Toast.makeText(getApplicationContext(), new Integer(orientation).toString(), Toast.LENGTH_SHORT).show();
            }
        };

        // start listener for orientation changes
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }

        //init gps
        mLocationMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !mLocationMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            new AlertDialog.Builder(PPWCameraActivity2.this)
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
        mDataOutput = new HashMap<String, String>();

        //icon font face
        Typeface font = Typeface.createFromAsset(getAssets(), "flaticon_ppw_camera.ttf");

        /*

        // Create our Preview view and set it as the content of our activity.
        mPreview = new PPWCameraPreview(this, getCameraInstance());
        FrameLayout preview = (FrameLayout) findViewById(getR("id","frame_camera_preview"));
        CroppedCameraPreview croppedPreview = new CroppedCameraPreview(this,mPreview);
        preview.addView(croppedPreview);
        croppedPreview.addView(mPreview);
*/
        int initialOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
        if (initialOrientation == Surface.ROTATION_270) {
            changeOrientation(180);
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
                    } else {
                        if (callbackOutput != null) {
                            PPWCamera.openCameraCallbackContext.success(callbackOutput);
                        }
                    }
                }
            });
        }

        // Add a listener to the Close button
        closeButton = (Button) findViewById(getR("id", "button_exit"));
        closeButton.setTypeface(font);
        closeButton.setText(getR("string", "close_icon"));

        //add a listener to the imageview button
        imageViewButton = (ImageButton) findViewById(getR("id", "button_imageView"));
        imageViewButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        imageViewButton.setVisibility(View.INVISIBLE);
                    }
                }
        );

        // Add a listener to the thumbnail button
        thumbButton = (ImageButton) findViewById(getR("id", "button_thumbnail"));
        thumbButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        imageViewButton.setVisibility(View.VISIBLE);
                    }
                }
        );

        // Add a listener to the Capture button
        Button captureButton = (Button) findViewById(getR("id", "button_capture"));
        captureButton.setTypeface(font);
        captureButton.setText(getR("string", "camera_icon"));
        captureButton.setOnClickListener(new View.OnClickListener() {
                                             @Override
                                             public void onClick(View v) {
                                                 takePicture();
                                             }
                                         }
        );

        flashButton = (Button) findViewById(getR("id", "button_flash"));
        flashButton.setTypeface(font);


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
            Log.d(TAG, "" + PPWCamera.openCameraJsonArrayArgs.toString());

            JSONObject options = PPWCamera.openCameraJsonArrayArgs.optJSONObject(0);
            mPhotoWidth = options.optInt("targetWidth", 640);
            mPhotoHeight = options.optInt("targetHeight", 480);
            mPreviewWidth = options.optInt("previewWidth", 640);
            mPreviewHeight = options.optInt("previewHeight", 480);
            mEncodingType = options.optString("encodingType", "jpg");
            mQuality = options.optInt("quality", 100);
            mThumbnail = options.optInt("thumbnail", 25);
            mBackNotify = options.optBoolean("backNotify", false);
            mFlashType = options.optString("flashType", FLASH_NAME_AUTO);
            mConfirmationTimeInterval = options.optInt("confirmTimeInterval", 500);
            mConfirmErrorMessage = options.optString("confirmErrorMessage", "Error confirming photo captured");
            mDateFontSize = options.optInt("dateFontSize", 20);
            mDateFormat = options.optString("dateFormat", "");
            mShutterSoundOff = options.optBoolean("shutterSoundOff", false);
            mOptions = options.optJSONObject("options");

            JSONArray overlay = options.optJSONArray("overlay");
            if (overlay != null) {
                for (int i = 0; i < overlay.length(); ++i) {

                    JSONObject item = overlay.optJSONObject(i);
                    String type = item.optString("type");
                    if (type != null) {

                        RelativeLayout layout = (RelativeLayout) findViewById(getR("id", "container"));
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
                        rp.setMargins(left, top, right, bottom);

                        TextView view = null;
                        if (type.compareToIgnoreCase("text") == 0) {

                            //add a new text view
                            view = new TextView(this);
                            view.setText(item.optString("value", "error"));
                            view.setTextColor(Color.WHITE);
                            view.setShadowLayer(2, -1, 1, Color.BLACK);

                        } else if (type.compareToIgnoreCase("carousel") == 0) {

                            class tagItem {
                                public int count;
                                public JSONArray value;
                                public String id;
                                public String initial;
                            }
                            ;

                            //add a new button
                            view = new Button(this);
                            tagItem t = new tagItem();
                            t.value = item.optJSONArray("value");
                            t.id = item.optString("id");
                            t.initial = item.optString("initial", "");
                            t.count = 0;
                            for (int j = 0; j < t.value.length(); ++j) {
                                if (t.initial.compareTo(t.value.optString(j, "")) == 0) {
                                    t.count = j;
                                }
                            }
                            view.setTag(t);
                            String selected = t.value.optString(t.count, "error");
                            view.setText(selected);
                            view.setTextColor(Color.WHITE);
                            view.setShadowLayer(2, -1, 1, Color.BLACK);
                            Drawable bg = view.getBackground();
                            if (bg != null) {
                                bg.setAlpha(25);
                            }
                            mDataOutput.put(t.id, selected);
                            view.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    tagItem t = (tagItem) v.getTag();
                                    t.count++;
                                    if (t.count >= t.value.length()) {
                                        t.count = 0;
                                    }
                                    String selected = t.value.optString(t.count, "error");
                                    ((TextView) v).setText(selected);
                                    mDataOutput.put(t.id, selected);
                                }
                            });

                        }

                        if (view != null) {
                            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.optInt("size", 12));
                            view.setLayoutParams(rp);
                            layout.addView(view);
                        }
                    }
                }
            }
        }
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            new ImageSaver().execute(reader.acquireNextImage());
        }
    };

    private static void sendError() {
        PPWCamera.sendError("camera error", 0, PPWCamera.openCameraCallbackContext);
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
            fillPaint.setTextSize(mDateFontSize * (((float) width) / mPhotoWidth));
            fillPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            fillPaint.setColor(Color.YELLOW);
            fillPaint.setStyle(Paint.Style.FILL);
            final Paint strokePaint = new Paint(fillPaint);
            strokePaint.setTextSize(mDateFontSize * (((float) width) / mPhotoWidth));
            strokePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            strokePaint.setColor(Color.DKGRAY);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2);
            canvas.drawBitmap(resized, 0f, 0f, null);
            final float dateHeight = fillPaint.measureText("yY");
            final float dateWidth = fillPaint.measureText(dateTime, 0, dateTime.length());
            final float datePadding = 10;
            canvas.drawText(dateTime, width - dateWidth - datePadding, height - datePadding, strokePaint);
            canvas.drawText(dateTime, width - dateWidth - datePadding, height - datePadding, fillPaint);
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
            JpegImageMetadata metadata = (JpegImageMetadata) Sanselan.getMetadata(sourceMetaData);
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
                            Log.d(TAG, "Exception on removing thumbnail image: " + e.getMessage());
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
            Log.d(TAG, "Exception on exif adding: " + e.getMessage());
            sendError();
        }
        return destImageData;
    }

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });

    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                   int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mInstance = this;
        zoom = null;

        init();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        /*if (mTextureView.isAvailable()) {
            showToast(Integer.toString(mTextureView.getWidth()));
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }*/
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();

        if (mOrientationListener != null)
            mOrientationListener.disable();

        mInstance = null;
        mInit = false;

        if (mLocationMgr != null) {
            mLocationMgr.removeUpdates(mLocationListener);
            mLocationMgr = null;
            mLocation = null;
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mBackNotify) {
            PPWCamera.sendError("back button clicked", 2, PPWCamera.openCameraCallbackContext);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (mCharacteristics == null)
                return false;
            //CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            //CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            float maxZoom = (mCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;

            Rect m = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int action = event.getAction();
            float current_finger_spacing;

            if (event.getPointerCount() > 1) {
                // Multi touch logic
                current_finger_spacing = getFingerSpacing(event);

                if (finger_spacing != 0) {
                    if (current_finger_spacing > finger_spacing && maxZoom > zoom_level) {
                        zoom_level++;

                    } else if (current_finger_spacing < finger_spacing && zoom_level > 1) {
                        zoom_level--;

                    }
                    int minW = (int) (m.width() / maxZoom);
                    int minH = (int) (m.height() / maxZoom);
                    int difW = m.width() - minW;
                    int difH = m.height() - minH;
                    int cropW = difW / 100 * (int) zoom_level;
                    int cropH = difH / 100 * (int) zoom_level;
                    cropW -= cropW & 3;
                    cropH -= cropH & 3;
                    zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);
                    mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }
                finger_spacing = current_finger_spacing;
            } else {
                if (action == MotionEvent.ACTION_UP) {
                    //single touch logic
                }
            }

            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                        null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (NullPointerException ex) {
                ex.printStackTrace();
            }
        } catch (Exception e) {
            throw new RuntimeException("can not access camera.", e);
        }

        return true;
    }

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {

                mCharacteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = mCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                maximumZoomLevel = (mCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));

                //Zoom
          /*      if (zoom != null && mPreviewRequestBuilder != null) {
                    mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
                }*/

                StreamConfigurationMap map = mCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {

                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {

                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = mCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                if (!mFlashSupported) {
                    flashButton.setVisibility(View.INVISIBLE); //hide if not supported
                } else {
                    int defaultColor = Color.WHITE;
                    int defaultIcon = getR("string", "flash_auto_icon");
                    if (mFlashType.compareToIgnoreCase(FLASH_NAME_OFF) == 0) {
                        defaultColor = Color.DKGRAY;
                        defaultIcon = getR("string", "flash_off_icon");
                    } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_ON) == 0) {
                        defaultColor = Color.YELLOW;
                        defaultIcon = getR("string", "flash_on_icon");
                    } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_TORCH) == 0) {
                        defaultColor = Color.GREEN;
                        defaultIcon = getR("string", "flash_on_icon");
                    }

                    flashButton.setText(defaultIcon);
                    flashButton.setTextColor(defaultColor);
                    GradientDrawable gd = (GradientDrawable) flashButton.getBackground();
                    gd.setStroke(getPixelSP(2), defaultColor);
                    ((GradientDrawable) closeButton.getBackground()).setStroke(getPixelSP(2), Color.WHITE);

                    flashButton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    String currentFlash = mFlashType;
                                    Log.d(TAG, "current flash " + currentFlash);

                                    int nextColor = Color.WHITE;
                                    int nextIcon = getR("string", "flash_auto_icon");
                                    mFlashType = FLASH_NAME_AUTO;
                                    if (currentFlash.compareToIgnoreCase(FLASH_NAME_AUTO) == 0) {
                                        nextColor = Color.GREEN;
                                        nextIcon = getR("string", "flash_on_icon");
                                        mFlashType = FLASH_NAME_TORCH;
                                    } else if (currentFlash.compareToIgnoreCase(FLASH_NAME_TORCH) == 0) {
                                        nextColor = Color.YELLOW;
                                        nextIcon = getR("string", "flash_on_icon");
                                        mFlashType = FLASH_NAME_ON;
                                    } else if (currentFlash.compareToIgnoreCase(FLASH_NAME_ON) == 0) {
                                        nextColor = Color.DKGRAY;
                                        nextIcon = getR("string", "flash_off_icon");
                                        mFlashType = FLASH_NAME_OFF;
                                    }

                                    Log.d(TAG, "next flash " + mFlashType);

                                    flashButton.setText(nextIcon);
                                    //update color
                                    flashButton.setTextColor(nextColor);
                                    GradientDrawable gd = (GradientDrawable) flashButton.getBackground();
                                    gd.setStroke(getPixelSP(2), nextColor);
                                    ((GradientDrawable) closeButton.getBackground()).setStroke(getPixelSP(2), Color.WHITE);

                                    if (mPreviewRequestBuilder != null) {
                                        try {
                                            //  mCaptureSession.stopRepeating();

                                            setFlashMode(mPreviewRequestBuilder);

                                            mPreviewRequest = mPreviewRequestBuilder.build();
                                            mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                                    mCaptureCallback, mBackgroundHandler);
                                        } catch (CameraAccessException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                }
                            }
                    );
                }


                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            /*Camera2BasicFragment.ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(this, FRAGMENT_DIALOG);*/
        }
    }

    /**
     * Opens the camera specified by .
     */
    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PPWCameraActivity2.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //mOrientationListener.disable();
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            //mOrientationListener.enable();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                //Zoom
                                // if (zoom != null) mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

                                // Flash is automatically enabled when necessary.
                                setFlashMode(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            if (mIsOrientationLandscapeRight)
                matrix.postRotate(180, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        // if (mTakePictureMutex) {
        //     mTakePictureMutex = false;
        lockFocus();
        //  }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            //mPreviewRequestBuilder.set(CaptureRequest)
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.

            //Zoom
           /* if (zoom != null && mPreviewRequestBuilder != null) {
                mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            }*/

            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.

            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            //Zoom
            if (zoom != null) {
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            }

            //CaptureRequest.JPEG_GPS_LOCATION.
            //add updated gps data
            if (mLocation != null) {
                captureBuilder.set(CaptureRequest.JPEG_GPS_LOCATION, mLocation);
            }

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setFlashMode(captureBuilder);

            showToast("Saving... ");
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    //showToast("Saved... ");
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void changeOrientation(int orientation) {
        if (mPreviewRequestBuilder != null) {
            try {
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int viewWidth = mTextureView.getWidth();
                int viewHeight = mTextureView.getHeight();
                Matrix matrix = new Matrix();
                RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
                RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
                float centerX = viewRect.centerX();
                float centerY = viewRect.centerY();
                if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                    bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                    float scale = Math.max(
                            (float) viewHeight / mPreviewSize.getHeight(),
                            (float) viewWidth / mPreviewSize.getWidth());
                    matrix.postScale(scale, scale, centerX, centerY);
                    matrix.postRotate(90 * rotation, centerX, centerY);
                } else if (Surface.ROTATION_180 == rotation) {
                    if (mIsOrientationLandscapeRight)
                        matrix.postRotate(180, centerX, centerY);
                }

                mTextureView.setTransform(matrix);

                // mCaptureSession.stopRepeating();
                mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                        orientation);
                mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mBackgroundHandler);


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setFlashMode(mPreviewRequestBuilder);

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            if (zoom != null) mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public int getR(String group, String key) {
        return getApplicationContext().getResources().getIdentifier(key, group, getApplicationContext().getPackageName());
    }

    private int getPixelSP(int pixels) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, pixels, getResources().getDisplayMetrics());
    }

    ;

    private void setFlashMode(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            if (mFlashType.compareToIgnoreCase(FLASH_NAME_AUTO) == 0) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
            } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_OFF) == 0) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
            } else if (mFlashType.compareToIgnoreCase(FLASH_NAME_ON) == 0) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF);
            } else { //Torch
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON);
                requestBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
            }
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private class ImageSaver extends AsyncTask<Image, Void, Bitmap> {

        Image mTargetImage = null;

        @Override
        protected Bitmap doInBackground(Image... params) {
            mTargetImage = params[0];

            ByteBuffer buffer = mTargetImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            final String timeStamp = String.valueOf(System.currentTimeMillis());
            final String FILENAME = timeStamp + "." + mEncodingType;
            final String FILENAME_THUMB = timeStamp + "_thumb." + mEncodingType;
            final String FILENAME_DATA = timeStamp + ".json";

            Log.d(PPWCameraActivity2.TAG, "here");

            try {
                //check disk space
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long availableBlocks = stat.getAvailableBlocks();
                long availableBytes = blockSize * availableBlocks;
                byte[] imageResize = resizeImage(bytes, mPhotoWidth, mPhotoHeight, true);
                byte[] imageThumb = null;
                if (mThumbnail > 0) {
                    imageThumb = resizeImage(imageResize, (int) (mPhotoWidth * mThumbnail * 0.01f), (int) (mPhotoHeight * mThumbnail * 0.01f), false);
                }
                int dataLength = imageResize.length;
                if (mThumbnail > 0) {
                    dataLength += imageThumb.length;
                }

                if (availableBytes <= dataLength) {

                    String availSize = Formatter.formatFileSize(PPWCameraActivity2.this, availableBytes);

                    new AlertDialog.Builder(PPWCameraActivity2.this)
                            .setTitle("Unable to Save - Disk Full")
                            .setMessage("Available space: " + availSize)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }

                //save if space available
                else {
                    //add meta deta
                    if (mEncodingType.compareToIgnoreCase("png") != 0) {
                        imageResize = addJPEGExifTagsFromSource(bytes, imageResize);
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

                    callbackOutput = new JSONObject();
                    callbackOutput.put("imageURI", imagePath);
                    callbackOutput.put("imageThumbURI", imagePathThumb);
                    callbackOutput.put("lastModifiedDate", timeStamp);
                    callbackOutput.put("size", imageResize.length);
                    callbackOutput.put("type", mEncodingType);
                    callbackOutput.put("hash", hash);
                    callbackOutput.put("flashType", mFlashType);
                    callbackOutput.put("options", mOptions);
                    callbackOutput.put("jsonURI", FILENAME_DATA);
                    callbackOutput.put("root", "external");

                    if (!mDataOutput.isEmpty()) {
                        JSONObject data = new JSONObject();
                        for (HashMap.Entry<String, String> entry : mDataOutput.entrySet()) {
                            data.put(entry.getKey(), entry.getValue());
                        }
                        callbackOutput.put("data", data);
                    }

                    fos = openFileOutput(FILENAME_DATA, Context.MODE_PRIVATE);
                    fos.write(callbackOutput.toString().getBytes("utf-8"));
                    fos.close();

                    //update thumbnail
                    if (mThumbnail > 0) {
                        //setup image view
                        Bitmap image = BitmapFactory.decodeByteArray(imageResize, 0, imageResize.length);
                        return image;
                    }

                    Log.d(TAG, callbackOutput.toString());
                    // PluginResult result = new PluginResult(PluginResult.Status.OK, output);
                    // result.setKeepCallback(true);
                    // PPWCamera.openCameraCallbackContext.sendPluginResult(result);
/*
                    //start timer to check for confirmation
                    mConfirmationTimer.removeCallbacks(showConfirmErrorPopup);
                    mConfirmationTimer.postDelayed(showConfirmErrorPopup, (long)mConfirmationTimeInterval);*/
                }

            } catch (Exception e) {
                Log.d(TAG, "File not found Error: " + e.getMessage());
                sendError();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap image) {
            if (image != null) {

                imageViewButton.setImageBitmap(image);
                imageViewButton.setVisibility(View.INVISIBLE);

                int radius = (int) (mPhotoWidth * (mThumbnail * 0.01f));
                if (mPhotoHeight > mPhotoWidth) {
                    radius = (int) (mPhotoHeight * (mThumbnail * 0.01f));
                }

                Bitmap thumb = ThumbnailUtils.extractThumbnail(image, radius, radius);
                thumbButton.setImageBitmap(getCircleBitmap(thumb));
                thumbButton.setVisibility(View.VISIBLE);
            }
            //  mTakePictureMutex = true;
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }
    }

    class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}



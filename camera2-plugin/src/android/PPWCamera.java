package com.appnovation.ppw_camera;

import java.io.File;
import java.io.Serializable;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.widget.Toast;
import android.util.Log;
import android.content.ContentValues;
import android.database.Cursor;

public class PPWCamera extends CordovaPlugin {

    private static final String TAG = "PPWCameraPlugin";

    public static final String ACTION_OPEN_CAMERA = "openCamera";
    public static final String ACTION_CLOSE_CAMERA = "closeCamera";
    public static final String ACTION_CONFIRM_CAMERA = "confirmCamera";

    public static CallbackContext openCameraCallbackContext;
    public static JSONArray openCameraJsonArrayArgs;

    public static final int UPVERSION_SDK_INT = 21;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) {

        if (!checkCameraHardware(cordova.getActivity().getApplicationContext())) {
            sendError("camera not found", 0, callback);
            return false;
        }

        if (Build.VERSION.SDK_INT < UPVERSION_SDK_INT) {
            return init1(action, args, callback);
        } else {
            return init2(action, args, callback);
        }
    }

    private boolean init1(String action, JSONArray args, CallbackContext callback) {
        if (action.compareToIgnoreCase(ACTION_CONFIRM_CAMERA) == 0) {
            PPWCameraActivity a = PPWCameraActivity.getInstance();
            if (a != null) {
                a.confirmCamera();
                return true;
            }
            sendError("Camera could not be confirmed. Camera activity is not available", 0, callback);
            return false;
        }

        if (action.compareToIgnoreCase(ACTION_CLOSE_CAMERA) == 0) {
            PPWCameraActivity a = PPWCameraActivity.getInstance();
            if (a != null) {
                a.finish();
                return true;
            }
            sendError("Camera could not be closed. Camera activity is not available", 0, callback);
            return false;

        }

        if (action.compareToIgnoreCase(ACTION_OPEN_CAMERA) != 0) {
            sendError("invalid command", 0, callback);
            return false;
        }

        //singleton check
        PPWCameraActivity a = PPWCameraActivity.getInstance();
        if (a != null) {
            sendError("Another camera instance already exists", 0, callback);
            return false;
        }

        openCameraCallbackContext = callback;
        openCameraJsonArrayArgs = args;

        try {
            if (this.cordova != null) {
                this.cordova.setActivityResultCallback(this);
                Intent i = new Intent(this.cordova.getActivity(), PPWCameraActivity.class);
                this.cordova.getActivity().startActivity(i);
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            sendError(e.getMessage(), 0, callback);
            return false;
        }
        return true;
    }

    private boolean init2(String action, JSONArray args, CallbackContext callback) {
        if (action.compareToIgnoreCase(ACTION_CONFIRM_CAMERA) == 0) {
            PPWCameraActivity2 a = PPWCameraActivity2.getInstance();
            if (a != null) {
                a.confirmCamera();
                return true;
            }
            sendError("Camera could not be confirmed. Camera activity is not available", 0, callback);
            return false;
        }

        if (action.compareToIgnoreCase(ACTION_CLOSE_CAMERA) == 0) {
            PPWCameraActivity2 a = PPWCameraActivity2.getInstance();
            if (a != null) {
                a.finish();
                return true;
            }
            sendError("Camera could not be closed. Camera activity is not available", 0, callback);
            return false;

        }

        if (action.compareToIgnoreCase(ACTION_OPEN_CAMERA) != 0) {
            sendError("invalid command", 0, callback);
            return false;
        }

        //singleton check
        PPWCameraActivity2 a = PPWCameraActivity2.getInstance();
        if (a != null) {
            sendError("Another camera instance already exists", 0, callback);
            return false;
        }

        openCameraCallbackContext = callback;
        openCameraJsonArrayArgs = args;

        try {
            if (this.cordova != null) {
                this.cordova.setActivityResultCallback(this);
                Intent i = new Intent(this.cordova.getActivity(), PPWCameraActivity2.class);
                this.cordova.getActivity().startActivity(i);
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            sendError(e.getMessage(), 0, callback);
            return false;
        }
        return true;
    }


    public static void sendError(String msg, int code, CallbackContext callback) {
        try {
            JSONObject output = new JSONObject();
            output.put("code", code);
            output.put("message", msg);
            callback.error(output);
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT < UPVERSION_SDK_INT){
                PPWCameraActivity a = PPWCameraActivity.getInstance();
                if (a == null)
                    return;

                new AlertDialog.Builder(a)
                        .setTitle("Error")
                        .setMessage("There was an error.  Please restart the app.")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setCancelable(false)
                        .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                PPWCameraActivity a = PPWCameraActivity.getInstance();
                                if (a != null) {
                                    a.finish(); //close activity
                                }
                            }
                        })
                        .show();
            } else {
                PPWCameraActivity2 a = PPWCameraActivity2.getInstance();
                if (a == null)
                    return;

                new AlertDialog.Builder(a)
                        .setTitle("Error")
                        .setMessage("There was an error.  Please restart the app.")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setCancelable(false)
                        .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                PPWCameraActivity2 a = PPWCameraActivity2.getInstance();
                                if (a != null) {
                                    a.finish(); //close activity
                                }
                            }
                        })
                        .show();
            }
        }
    }

    /**
     * Check if this device has a camera
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }
}
/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.content.Context;
import android.content.Intent;

import android.app.Activity;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.telephony.TelephonyManager;
import java.math.BigInteger;
import java.security.MessageDigest;

import java.io.FileInputStream;

import java.util.StringTokenizer;

import android.content.SharedPreferences;

import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import android.content.res.Resources;

import android.provider.Settings;

import android.util.Log;

/**
 * Globals; uses singleton pattern to ensure that all members exist only
 * once. Created and destroyed when the app is started/stopped.
 **/
public class Globals
{
  /***************************************************************************
   * Private constants
   **/
  private static final String     LTAG = "Globals";

  // Maxim number of items we want in the image cache.
  private static final int        IMAGE_CACHE_MAX = 200;

  // Defaults for the client ID.
  private static final String     CLIENT_ID_PREFIX  = "android-";
  private static final String     CLIENT_ID_UNKNOWN = "unknown-id";
  private static final String     HARDWARE_UNKNOWN  = "unknown-hardware";

  // Time interval to expect location updates in, in milliseconds. This is only
  // a guideline, and location updates may occur more or less often than that.
  // The 10 second interval is more or less picked at random; there's the
  // assumption that people won't move fast enough for a higher update frequency
  // to become important.
  private static final int        LOCATION_UPDATE_PERIOD      = 10 * 1000;

  // Resolution for location updates, in meters. Again the number is picked
  // based on the assumption that a higher resolution is not useful. What this
  // value means in practice is that we won't receive location updates if the
  // location changed by less than this amount.
  private static final float      LOCATION_UPDATE_RESOLUTION  = 10;

  // Directory prefix for the data directory.
  private static final String     DATA_DIR_PREFIX             = "boo_data";

  /***************************************************************************
   * Public constants
   **/
  // Preferences
  public static final String      PREFERENCES_NAME    = "fm.audioboo.app";

  public static final String      PREF_API_KEY        = "api.key";
  public static final String      PREF_API_SECRET     = "api.secret";

  public static final String      PREF_USE_LOCATION   = "settings.use_location";

  // Reusable dialog IDs. The ones defined here start from 10000.
  public static final int         DIALOG_GPS_SETTINGS = 10000;


  /***************************************************************************
   * Singleton data
   **/
  private static Globals  sInstance;


  /***************************************************************************
   * Private instance data
   **/
  // Generated at startup; the algorithm always produces the same string for the
  // same device.
  private String            mClientID;

  // Current location provider. Used to determine e.g. if the user needs to be
  // asked to enable location providers.
  private String            mLocationProvider;

  // Location listener. Used to continuously update location information.
  private LocationListener  mLocationListener;

  // Base path, prepended before mRelativeFilePath. It's on the external
  // storage and includes the file bundle.
  private String            mBasePath;



  /***************************************************************************
   * Public instance data
   **/
  public Context            mContext;
  public API                mAPI;
  public ImageCache         mImageCache;
  public BooPlayer          mPlayer;

  // Location information, updated regularly if the appropriate settings are
  // switched on.
  public Location           mLocation;


  /***************************************************************************
   * Singleton implementation
   **/
  public static void create(Context context)
  {
    if (null == sInstance) {
      sInstance = new Globals(context);
    }
  }



  public static void destroy(Context context)
  {
    if (null == sInstance) {
      return;
    }
    if (context != sInstance.mContext) {
      return;
    }

    sInstance.release();
    sInstance = null;
  }



  public static Globals get()
  {
    return sInstance;
  }



  /***************************************************************************
   * Implementation
   **/
  private Globals(Context context)
  {
    mContext = context;

    mAPI = new API();
    mImageCache = new ImageCache(mContext, IMAGE_CACHE_MAX);
    mPlayer = new BooPlayer(mContext);
    mPlayer.start();
  }



  private void release()
  {
    mAPI = null;
    mImageCache = null;

    mPlayer.mShouldRun = false;
    mPlayer.interrupt();
    mPlayer = null;
  }



  /**
   * Generates (and returns) mClientID
   **/
  public String getClientID()
  {
    if (null != mClientID) {
      return mClientID;
    }

    TelephonyManager tman = (TelephonyManager) mContext.getSystemService(
        Context.TELEPHONY_SERVICE);
    String deviceId = tman.getDeviceId();

    try {
      // Let's be nice to our users and not send out their device ID in plain
      // text.
      MessageDigest m = MessageDigest.getInstance("SHA-1");
      m.update((CLIENT_ID_PREFIX + getHardwareString() + deviceId).getBytes());
      String digest = new BigInteger(1, m.digest()).toString(16);
      while (digest.length() < 32) {
        digest = "0" + digest;
      }
      mClientID = CLIENT_ID_PREFIX + digest;

    } catch (java.security.NoSuchAlgorithmException ex) {
      Log.e(LTAG, "ERROR: could not determine client ID: " + ex.getMessage());
      // We'll have to set the client ID to something to avoid errors further
      // down the line.
      mClientID = CLIENT_ID_PREFIX + CLIENT_ID_UNKNOWN;
    }

    return mClientID;
  }



  /**
   * Constructs a hardware identifier string from /proc/cpuinfo and returns it.
   **/
  public String getHardwareString()
  {
    // This should uniquely identify the hardware, without including information
    // about the system software. The fields DISPLAY, FINGERPRINT, etc.
    // all include information about the SDK.
    return String.format("%s:%s:%s:%s",
        Build.BOARD, Build.BRAND, Build.DEVICE, Build.MODEL);
  }



  /**
   * Returns the App's preferences
   **/
  public SharedPreferences getPrefs()
  {
    return mContext.getSharedPreferences(PREFERENCES_NAME,
        mContext.MODE_PRIVATE);
  }



  /**
   * Starts listening for updates to the device's location, if that is
   * requested in the preferencs.
   *
   * Returns true on success, false on failure. If no location updates are
   * requested as per the app's preferences, that is counted as instant
   * success.
   **/
  public boolean startLocationUpdates()
  {
    // Read preferences.
    SharedPreferences prefs = getPrefs();
    boolean use_location = prefs.getBoolean(PREF_USE_LOCATION, false);
    if (!use_location) {
      // If we're not supposed to use location info, then we'll assume success.
      return true;
    }

    // Determine location provider, if that hasn't happened yet.
    LocationManager lm = (LocationManager) mContext.getSystemService(
        Context.LOCATION_SERVICE);

    // Determine the location provider to use. We prefer GPS, but NETWORK
    // is acceptable.
    mLocationProvider = null;
    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      mLocationProvider = LocationManager.GPS_PROVIDER;
    }
    else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
      mLocationProvider = LocationManager.NETWORK_PROVIDER;
    }

    if (null == mLocationProvider) {
      Log.w(LTAG, "Location updates requested, but no location provider enabled.");
      return false;
    }
    Log.i(LTAG, "Using location provider: " + mLocationProvider);

    // Start listening to location updates. All we do in the listener is null
    // mLocation if there's any chance it might become stale, and set it when
    // we receive new Location information.
    mLocationListener = new LocationListener()
    {
      public void onLocationChanged(Location location)
      {
        mLocation = location;
      }


      public void onProviderDisabled(String provider)
      {
        mLocation = null;
      }


      public void onProviderEnabled(String provider)
      {
        mLocation = null;
      }


      public void onStatusChanged(String provider, int status, Bundle extras)
      {
        if (status == LocationProvider.OUT_OF_SERVICE) {
          mLocation = null;
        }
      }
    };

    lm.requestLocationUpdates(mLocationProvider, LOCATION_UPDATE_PERIOD,
        LOCATION_UPDATE_RESOLUTION, mLocationListener);

    return true;
  }



  /**
   * Stops listeneing to updates to the devie's location.
   **/
  public void stopLocationUpdates()
  {
    if (null == mLocationListener) {
      return;
    }

    LocationManager lm = (LocationManager) mContext.getSystemService(
        Context.LOCATION_SERVICE);
    lm.removeUpdates(mLocationListener);

    mLocationListener = null;
  }



  /**
   * Creates Dialog instances for the Dialog IDs defined above. Dialogs belong
   * to the Activity passed as the first parameter.
   **/
  public Dialog createDialog(final Activity activity, int id)
  {
    Dialog dialog = null;

    Resources res = activity.getResources();

    switch (id) {
      case DIALOG_GPS_SETTINGS:
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(res.getString(R.string.gps_settings_message))
          .setCancelable(false)
          .setPositiveButton(res.getString(R.string.gps_settings_start),
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                  activity.startActivity(i);
                }
              })
          .setNegativeButton(res.getString(R.string.gps_settings_cancel),
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  dialog.cancel();
                }
              });
        dialog = builder.create();
        break;
    }

    return dialog;
  }



  /**
   * Returns the base path for the app's data.
   **/
  public String getBasePath()
  {
    if (null == mBasePath) {
      mBasePath = mContext.getDir(DATA_DIR_PREFIX, Context.MODE_PRIVATE).getPath();
      // TODO Maybe use external storage?
      // String base = Environment.getExternalStorageDirectory().getPath();
      // base += File.separator + "data" + File.separator + getPackageName();
      // mBasePath = base;
    }
    return mBasePath;
  }
}

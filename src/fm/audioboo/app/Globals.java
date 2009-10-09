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

import android.os.Build;
import android.telephony.TelephonyManager;
import java.math.BigInteger;
import java.security.MessageDigest;

import java.io.FileInputStream;

import java.util.StringTokenizer;

import android.content.SharedPreferences;

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

  /***************************************************************************
   * Public constants
   **/
  public static final String      PREFERENCES_NAME  = "fm.audioboo.app";

  public static final String      PREF_API_KEY      = "api.key";
  public static final String      PREF_API_SECRET   = "api.secret";


  /***************************************************************************
   * Singleton data
   **/
  private static Globals  sInstance;


  /***************************************************************************
   * Private instance data
   **/
  // Generated at startup; the algorithm always produces the same string for the
  // same device.
  public String       mClientID;


  /***************************************************************************
   * Public instance data
   **/
  public Context      mContext;
  public API          mAPI;
  public ImageCache   mImageCache;
  public BooPlayer    mPlayer;



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
}

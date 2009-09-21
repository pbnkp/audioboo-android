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


  /***************************************************************************
   * Singleton data
   **/
  private static Globals  sInstance;


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
}

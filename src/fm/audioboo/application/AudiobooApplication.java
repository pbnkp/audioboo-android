/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Application;

import android.util.Log;

/**
 * Process-level object creates and owns singleton objects.
 **/
public class AudiobooApplication extends Application
{
  /***************************************************************************
   * Private constants
   **/
  private static final String     LTAG = "Audioboo";

  /***************************************************************************
   * Public constants
   **/
  @Override
  public void onCreate()
  {
    Log.i(LTAG, "Starting up...");
    Globals.create(this);
    Log.i(LTAG, "Startup complete.");
  }



  @Override
  public void onTerminate()
  {
    Log.i(LTAG, "Shutting down...");
    Globals.destroy(this);
    Log.i(LTAG, "Shutdown complete.");
  }
}

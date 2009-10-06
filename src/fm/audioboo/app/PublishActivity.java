/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.app.Activity;

import android.os.Bundle;

import android.content.res.Configuration;

import android.util.Log;

/**
 * FIXME
 **/
public class PublishActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "PublishActivity";



  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO  = "fm.audioboo.extras.boo";



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.publish);
    setTitle(getResources().getString(R.string.publish_activity_title));
  }



  @Override
  public void onStart()
  {
    super.onStart();
  }



  @Override
  public void onPause()
  {
    super.onPause();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }
}

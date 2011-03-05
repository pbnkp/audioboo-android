/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Activity;

import android.os.Bundle;

import android.content.Intent;

import android.content.res.Configuration;
import android.content.res.TypedArray;

import android.widget.TabHost;

import android.app.Dialog;

import fm.audioboo.data.BooData;

import android.util.Log;

/**
 * FIXME
 **/
public class BooDetailsActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooDetails";


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_DATA = "fm.audioboo.extras.boo-data";



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.boo_details);

    // Grab extras
    BooData data = getIntent().getParcelableExtra(EXTRA_BOO_DATA);
    if (null == data) {
      throw new IllegalArgumentException("Intent needs to define the '"
          + EXTRA_BOO_DATA + "' extra.");
    }

    Boo boo = new Boo(data);
    Log.d(LTAG, "Data: " + data.mTitle);
    Log.d(LTAG, "Boo: " + boo);

    // FIXME
  }



  @Override
  public void onStart()
  {
    super.onStart();

//    // Start listening to location updates, if that's required.
//    if (!Globals.get().startLocationUpdates()) {
//      showDialog(DIALOG_GPS_SETTINGS);
//    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again. XXX We need to ignore this in the parent activity so the child
    // actvities don't get restarted. Ignoring in the child activities is also
    // required.
    super.onConfigurationChanged(config);
  }



  @Override
  public void onStop()
  {
    super.onStop();

//    Globals.get().mPlayer.stopPlaying();
//    Globals.get().stopLocationUpdates();
  }



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;
//
//    switch (id) {
//      case DIALOG_GPS_SETTINGS:
//        dialog = Globals.get().createDialog(this, id);
//        break;
//    }

    return dialog;
  }
}

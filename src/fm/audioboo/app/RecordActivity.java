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

import android.widget.ToggleButton;
import android.widget.CompoundButton;

import android.util.Log;

/**
 * FIXME
 **/
public class RecordActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "RecordActivity";


  /***************************************************************************
   * Data members
   **/
  private FLACRecorder mFlacRecorder;


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
  }



  @Override
  public void onStart()
  {
    super.onStart();

    setContentView(R.layout.record);

    if (null == mFlacRecorder) {
      mFlacRecorder = new FLACRecorder();
      mFlacRecorder.start();
    }

    ToggleButton tb = (ToggleButton) findViewById(R.id.record_button);
    if (null != tb) {
      tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (isChecked) {
            mFlacRecorder.resumeRecording();
          }
          else {
            mFlacRecorder.pauseRecording();
          }
        }
      });
    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



}

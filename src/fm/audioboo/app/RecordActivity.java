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
import android.os.Handler;
import android.os.Message;

import android.content.res.Configuration;

import android.widget.ToggleButton;
import android.widget.CompoundButton;

import fm.audioboo.widget.RecordButton;
import fm.audioboo.widget.SpectralView;

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

  // Limit for the recording time we allow, in seconds.
  private static final int    RECORDING_TIME_LIMIT        = 1200;

  // Base file name for the current recording.
  private static final String RECORDING_BASE_NAME         = "current";

  // Extension for recordings.
  private static final String RECORDING_EXTENSION         = ".flac";


  /***************************************************************************
   * Data members
   **/
  // Recorder instance
  private FLACRecorder  mFlacRecorder;

  // Reference to the record button
  private RecordButton  mRecordButton;

  // Reference to the spectral view
  private SpectralView  mSpectralView;


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
      // Instanciate recorder. TODO need to check whether the file exists,
      // and use a different name.
      String filename = RECORDING_BASE_NAME + RECORDING_EXTENSION;
      mFlacRecorder = new FLACRecorder(this, filename,
        new Handler(new Handler.Callback()
        {
          public boolean handleMessage(Message m)
          {
            switch (m.what) {
              case FLACRecorder.MSG_AMPLITUDES:
                FLACRecorder.Amplitudes amp = (FLACRecorder.Amplitudes) m.obj;
                drawAmplitudes(amp);
                mRecordButton.setProgress((int) (amp.mPosition / 1000));
                break;

              default:
                reportError(m.what);
                break;
            }

            return true;
          }
        }
      ));
      mFlacRecorder.start();
    }

    mRecordButton = (RecordButton) findViewById(R.id.record_button);
    if (null != mRecordButton) {
      mRecordButton.setMax(RECORDING_TIME_LIMIT);

      mRecordButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (isChecked) {
            Log.d(LTAG, "Resume recording!");
            mFlacRecorder.resumeRecording();
            mSpectralView.startAnimation();
          }
          else {
            Log.d(LTAG, "Pause recording!");
            mFlacRecorder.pauseRecording();
            mSpectralView.stopAnimation();
          }
        }
      });
    }

    mSpectralView = (SpectralView) findViewById(R.id.record_spectral_view);
  }



  @Override
  public void onPause()
  {
    super.onPause();
    // FIXME may need changes
    if (null != mFlacRecorder) {
      mFlacRecorder.pauseRecording();
      mFlacRecorder.interrupt();
    }

    if (null != mSpectralView) {
      mSpectralView.stopAnimation();
    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void drawAmplitudes(FLACRecorder.Amplitudes amp)
  {
    mSpectralView.setAmplitudes(amp.mAverage, amp.mPeak);
  }



  private void reportError(int code)
  {
    Log.d(LTAG, "Error: " + code);
  }
}

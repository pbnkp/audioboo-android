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
import android.os.Environment;

import android.content.res.Configuration;

import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.ToggleButton;
import android.widget.CompoundButton;

import fm.audioboo.widget.RecordButton;
import fm.audioboo.widget.SpectralView;
import fm.audioboo.widget.BooPlayerView;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.net.Uri;

import java.util.Date;

import java.io.File;

import android.util.Log;

/**
 * The RecordActivity allows for recording (and playing back) of Boos, and
 * launches UploadAcitivity.
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

  // The record activity essentially represents a Boo, even though it's not
  // filled with all possible bits of information yet. We (re-)create this
  // Boo whenever we reset the recorder.
  private Boo           mBoo;

  // Base path, prepended before mRelativeFilePath. It's on the external
  // storage and includes the file bundle.
  private String        mBasePath;



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
      resetFLACRecorder();
    }


    mRecordButton = (RecordButton) findViewById(R.id.record_button);
    if (null != mRecordButton) {
      mRecordButton.setMax(RECORDING_TIME_LIMIT);

      mRecordButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (isChecked) {
            startRecording();
          }
          else {
            stopRecording();
          }
        }
      });
    }

    mSpectralView = (SpectralView) findViewById(R.id.record_spectral_view);
  }



  private void startRecording()
  {
    Log.d(LTAG, "Resume recording!");
    mFlacRecorder.resumeRecording();
    mSpectralView.startAnimation();

    // Stop playback, regardless where it's been started from.
    Globals.get().mPlayer.stopPlaying();
    stopPlayer();
  }



  private void stopRecording()
  {
    Log.d(LTAG, "Pause recording!");
    mFlacRecorder.pauseRecording();
    mSpectralView.stopAnimation();

    // Every time we stop recording, we really want the current recording
    // duration to be remembered in the Boo we're holding.
    mBoo.mDuration = mFlacRecorder.getDuration();

    // Show player.
    showPlayer();
  }



  @Override
  public void onPause()
  {
    super.onPause();

    // Pause recording in onPause
    if (null != mFlacRecorder) {
      mFlacRecorder.pauseRecording();
      mFlacRecorder.interrupt();
    }

    if (null != mSpectralView) {
      mSpectralView.stopAnimation();
    }

    stopPlayer();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);

    Log.d(LTAG, "RecordActivity: onConfiguratioNChanged");
    // XXX ? maybe that's all that's required to load the landscape
    // layout.
    // FIXME: no, need to attach buttons to actions, and all that.
    // FIXME:
    //  - also need to do the whole shebang if the tab was selected, because the
    //    following would lead to the wrong layout being used:
    //    - switch to another tab
    //    - open or close the keyboard
    //    - switch back
    //  - doesn't need to create data objects, but reinitialize the button view to
    //    update the current recording state, etc
    setContentView(R.layout.record);
  }



  private void drawAmplitudes(FLACRecorder.Amplitudes amp)
  {
    mSpectralView.setAmplitudes(amp.mAverage, amp.mPeak);
  }



  private void showPlayer()
  {
    // Fade in player
    BooPlayerView player = (BooPlayerView) findViewById(R.id.record_player);
    if (null != player) {
      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
      player.startAnimation(animation);
      player.setVisibility(View.VISIBLE);

      Log.d(LTAG, "Playing: " + mBoo);
      // Tell the player to lay the boo we remember.
      player.play(mBoo, false);
    }
  }



  private void stopPlayer()
  {
    // If the player view is showing, fade it out.
    BooPlayerView player = (BooPlayerView) findViewById(R.id.record_player);
    if (null != player && View.VISIBLE == player.getVisibility()) {
      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
      player.startAnimation(animation);

      player.stop();
    }
  }



  private void resetFLACRecorder()
  {
    if (null != mFlacRecorder) {
      mFlacRecorder.mShouldRun = false;
      mFlacRecorder.interrupt();
      mFlacRecorder = null;
    }

    // Instanciate recorder. TODO need to check whether the file exists,
    // and use a different name.
    String filename = getBasePath() + File.separator + RECORDING_BASE_NAME + RECORDING_EXTENSION;
    File f = new File(filename);
    f.getParentFile().mkdirs();

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


    // Also recrete the Boo we're remembering. We can at least set the "recorded
    // at" date, and hijack the mHighMP3Url to point to our flac file.
    mBoo = new Boo();
    mBoo.mRecordedAt = new Date();
    mBoo.mHighMP3Url = Uri.parse(String.format("file://%s", filename));
    Log.d(LTAG, "New boo: " + mBoo);
  }



  private void reportError(int code)
  {
    Log.d(LTAG, "Error: " + code);
    // TODO
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
//    String[] menu_titles = getResources().getStringArray(R.array.shelfoverview_menu_titles);
//    final int[] menu_icons = {
//      android.R.drawable.ic_menu_add,
//      R.drawable.scan,
//      android.R.drawable.ic_menu_search,
//      android.R.drawable.ic_menu_info_details,
//    };
//    for (int i = 0 ; i < menu_titles.length ; ++i) {
//      menu.add(0, i, 0, menu_titles[i]).setIcon(menu_icons[i]);
//// FIXME item.setAlphabeticShortcut(SearchManager.MENU_KEY);
//    }
//    return true;
    menu.add(0, 0, 0, "Will contain upload and reset");
    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    return true;
  }



  private String getBasePath()
  {
    if (null == mBasePath) {
      String base = Environment.getExternalStorageDirectory().getPath();
      base += File.separator + "data" + File.separator + getPackageName();
      mBasePath = base;
    }
    return mBasePath;
  }

}

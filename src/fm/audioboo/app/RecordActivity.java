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

import android.content.Context;
import android.content.Intent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import android.content.res.Configuration;

import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.CompoundButton;
import android.widget.TextView;

import android.widget.Toast;

import fm.audioboo.widget.RecordButton;
import fm.audioboo.widget.SpectralView;
import fm.audioboo.widget.BooPlayerView;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.app.Dialog;
import android.app.AlertDialog;

import android.location.Location;

import android.net.Uri;

import java.util.Date;

import java.io.File;

import android.util.Log;

/**
 * The RecordActivity allows for recording (and playing back) of Boos, and
 * launches PublishAcitivity.
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

  // Options menu IDs
  private static final int  MENU_RESTART                  = 0;
  private static final int  MENU_PUBLISH                  = 1;

  // Dialog IDs.
  private static final int  DIALOG_RECORDING_ERROR        = 0;

  // Vibration duration in msec - half a second seems about right.
  private static final long VIBRATE_DURATION              = 500;


  /***************************************************************************
   * Data members
   **/
  // Recorder instance
  private FLACRecorder  mFlacRecorder;

  // The record activity essentially represents a Boo, even though it's not
  // filled with all possible bits of information yet. We (re-)create this
  // Boo whenever we reset the recorder.
  private Boo           mBoo;
  private boolean       mBooIsNew;


  // Reference to the record button
  private RecordButton  mRecordButton;

  // Reference to the spectral view
  private SpectralView  mSpectralView;

  // Reference to overlay view
  private View          mOverlay;

  // Last error. Used and cleared in onCreateDialog
  private int           mErrorCode = -1;

  // Request code - sent to PublishActivity so it can respond appropriately.
  private int           mRequestCode;

  // For keeping the screen on during recording
  private WakeLock      mWakeLock;


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "AudioBoo Recording");
  }



  @Override
  public void onStart()
  {
    super.onStart();

    // Check whether the recorder file already exists. If it does, we'll not
    // initialize the recorder, but only the player.
    String filename = getRecorderFilename();
    filename += Boo.EXTENSION;
    Boo boo = Boo.constructFromFile(filename);
    if (null != boo && 0.0 != boo.mDuration) {
      mBoo = boo;
      mBooIsNew = false;
    }
    else if (null == mFlacRecorder) {
      resetFLACRecorder();
    }

    initUI();
  }



  private void startRecording()
  {
    if (null == mFlacRecorder) {
      resetFLACRecorder();
    }

    // Force screen to stay on.
    mWakeLock.acquire();

    // Log.d(LTAG, "Resume recording!");
    mFlacRecorder.resumeRecording();
    mSpectralView.startAnimation();

    // Stop playback, regardless where it's been started from.
    Globals.get().mPlayer.stopPlaying();
    stopPlayer();
  }



  private void stopRecording()
  {
    // Clear screen on flag
    mWakeLock.release();

    // Log.d(LTAG, "Pause recording!");
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
      mBoo.mDuration = mFlacRecorder.getDuration();
      mFlacRecorder.pauseRecording();
      mFlacRecorder.interrupt();
    }

    if (null != mSpectralView) {
      mSpectralView.stopAnimation();
    }

    stopPlayer();

    // Write Boo
    String filename = getRecorderFilename();
    filename += Boo.EXTENSION;
    mBoo.writeToFile(filename);

    // Release the lock if we're holding it.
    if (mWakeLock.isHeld()) {
      mWakeLock.release();
    }
  }



  @Override
  public void onResume()
  {
    super.onResume();

    hideOrShowPlayer();
  }



  private void hideOrShowPlayer()
  {
    // Show the player view, if there's a Boo to match.
    if (null != mBoo) {
      // Only show the player if the Boo has a duration. Otherwise there's
      // nothing to play back.
      // Log.d(LTAG, "Boo: " + mBoo);
      if (0.0 != mBoo.mDuration) {
        showPlayer();
      }

      if (!mBooIsNew) {
        mOverlay.setVisibility(View.VISIBLE);
      }
    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void startCountdown()
  {
    View v = findViewById(R.id.record_overlay);
    v.setVisibility(View.VISIBLE);

    TextView tv = (TextView) findViewById(R.id.record_countdown);
    v.setVisibility(View.VISIBLE);

    countDownStep(3);
  }



  private void countDownStep(final int step)
  {
    TextView tv = (TextView) findViewById(R.id.record_countdown);

    // If step has reached zero, hide all countdown-related views and start
    // recording.
    if (0 == step) {
      tv.setVisibility(View.GONE);

      View v = findViewById(R.id.record_overlay);
      v.setVisibility(View.GONE);

      startRecording();
      return;
    }

    // Else display the step..
    tv.setText(String.format("%d", step));

    // ... start a 1 second animation that ends up in this function again ...
    Animation animation = AnimationUtils.loadAnimation(this, R.anim.countdown);
    animation.setAnimationListener(new Animation.AnimationListener() {
      public void onAnimationEnd(Animation animation)
      {
        // When the player finished fading out, stop capturing clicks.
        countDownStep(step - 1);
      }

      public void onAnimationRepeat(Animation animation)
      {
        // pass
      }

      public void onAnimationStart(Animation animation)
      {
        // pass
      }
    });
    tv.startAnimation(animation);

    // ... and vibrate!
    Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    v.vibrate(VIBRATE_DURATION);
  }



  private void initUI()
  {
    // This function is called either from onStart() or from
    // onConfigurationChanged(). Either way, we need to reconstruct the
    // activity's state as it was previously.

    // First of all, we need to set the content view.
    setContentView(R.layout.record);

    // That also implies that we've lost our bindings for the button, etc.
    mRecordButton = (RecordButton) findViewById(R.id.record_button);
    if (null == mRecordButton) {
      Log.e(LTAG, "No record button found!");
      return;
    }
    mRecordButton.setMax(RECORDING_TIME_LIMIT);
    mRecordButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
      {
        if (isChecked) {
          startCountdown();
        }
        else {
          stopRecording();
        }
      }
    });


    mSpectralView = (SpectralView) findViewById(R.id.record_spectral_view);
    if (null == mSpectralView) {
      Log.e(LTAG, "No spectral view found!");
      return;
    }


    mOverlay = findViewById(R.id.record_overlay);
    if (null == mOverlay) {
      Log.e(LTAG, "No overlay view found!");
      return;
    }

    // If we've been recording, set the button/spectral view to the appropriate
    // state.
    if (null != mFlacRecorder) {
      // If we're recording, we'll update everything regularly. If not, we'll
      // want to set the button's progress properly nevertheless.
      if (mFlacRecorder.isRecording()) {
        mSpectralView.startAnimation();
        mRecordButton.setChecked(true);
      }
      else {
        FLACRecorder.Amplitudes amp = mFlacRecorder.getAmplitudes();
        if (null != amp) {
          mRecordButton.setProgress((int) (amp.mPosition / 1000));
        }
      }
    }
  }



  private void updateRecordingState(FLACRecorder.Amplitudes amp)
  {
    if (null == mBoo.mRecordedAt) {
      mBoo.mRecordedAt = new Date();
    }

    mSpectralView.setAmplitudes(amp.mAverage, amp.mPeak);
    mRecordButton.setProgress((int) (amp.mPosition / 1000));

    // If the Boo has no location, but Globals does, update the Boo's location.
    // By doing that here, we'll get the location as early on in the recording
    // process as possible. That should provide the most "accurate" location
    // in terms of the point in time that best represents where the recording
    // was made.
    if (null == mBoo.mLocation) {
      Location loc = Globals.get().mLocation;
      if (null != loc) {
        mBoo.mLocation = new BooLocation(this, Globals.get().mLocation);
      }
    }
  }



  private void showPlayer()
  {
    // Fade in player
    BooPlayerView player = (BooPlayerView) findViewById(R.id.record_player);
    if (null != player) {
      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
      player.startAnimation(animation);
      player.setVisibility(View.VISIBLE);

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

    // Instanciate recorder.
    String filename = getRecorderFilename();

    mFlacRecorder = new FLACRecorder(this, filename,
      new Handler(new Handler.Callback()
      {
        public boolean handleMessage(Message m)
        {
          switch (m.what) {
            case FLACRecorder.MSG_AMPLITUDES:
              FLACRecorder.Amplitudes amp = (FLACRecorder.Amplitudes) m.obj;
              updateRecordingState(amp);
              break;

            case FLACRecorder.MSG_OK:
              // Ignore.
              break;

            default:
              mErrorCode = m.what;
              showDialog(DIALOG_RECORDING_ERROR);
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
    mBoo.mHighMP3Url = Uri.parse(String.format("file://%s", filename));

    // Ensure that no Boo is written here.
    filename += Boo.EXTENSION;
    File f = new File(filename);
    if (f.exists()) {
      f.delete();
    }

    mBooIsNew = true;
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    String[] menu_titles = getResources().getStringArray(R.array.record_menu_titles);
    final int[] menu_icons = {
      android.R.drawable.ic_menu_revert,
      android.R.drawable.ic_menu_share,
    };
    for (int i = 0 ; i < menu_titles.length ; ++i) {
      menu.add(0, i, 0, menu_titles[i]).setIcon(menu_icons[i]);
    }
    return true;
  }



  @Override
  public boolean onPrepareOptionsMenu(Menu menu)
  {
    MenuItem publish = menu.getItem(MENU_PUBLISH);
    publish.setEnabled(0.0 != mBoo.mDuration);
    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case MENU_RESTART:
        resetFLACRecorder();
        initUI();
        break;

      case MENU_PUBLISH:
        Intent i = new Intent(this, PublishActivity.class);
        String filename = getRecorderFilename() + Boo.EXTENSION;
        i.putExtra(PublishActivity.EXTRA_BOO_FILENAME, filename);
        startActivityForResult(i, ++mRequestCode);
        break;

      default:
        Log.e(LTAG, "Unknown menu id: " + item.getItemId());
        return false;
    }

    return true;
  }



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (mRequestCode != requestCode) {
      //Log.d(LTAG, "Ignoring result for requestCode: " + requestCode);
      return;
    }

    // If the activity got cancelled, we'll not do anything. If an error occurred
    // during publishing, that will end up sending the cancel error code, but
    // the publish activity is responsible for displaying errors itself.
    if (Activity.RESULT_CANCELED == resultCode) {
      return;
    }

    // For anything but RESULT_OK, let's log an error - that's unexpected.
    if (Activity.RESULT_OK != resultCode) {
      Log.e(LTAG, "Unexpected result code: " + resultCode + " - " + data);
      return;
    }

    // If the activity sent OK, then we'll reset everything. No need to keep old
    // Boos around.
    resetFLACRecorder();
    initUI();
    hideOrShowPlayer();

    // Toast that we're done.
    Toast.makeText(this, R.string.record_publish_success_toast, Toast.LENGTH_LONG).show();
  }



  private String getRecorderFilename()
  {
    String filename = Globals.get().getBasePath() + File.separator + RECORDING_BASE_NAME + RECORDING_EXTENSION;
    File f = new File(filename);
    f.getParentFile().mkdirs();

    return filename;
  }



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_RECORDING_ERROR:
        {
          String content = getResources().getString(R.string.record_error_message);
          content = String.format(content, mErrorCode);
          mErrorCode = -1;

          AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setMessage(content)
            .setCancelable(false)
            .setPositiveButton(getResources().getString(R.string.record_error_ack), null);
          dialog = builder.create();
        }
        break;
    }

    return dialog;
  }


}

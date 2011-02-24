/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;

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

import fm.audioboo.data.BooLocation;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.app.Dialog;
import android.app.AlertDialog;

import android.location.Location;

import android.net.Uri;

import java.util.Date;

import java.io.File;

import java.util.List;
import java.util.LinkedList;

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
  private BooRecorder   mBooRecorder;

  // The record activity essentially represents a Boo, even though it's not
  // filled with all possible bits of information yet. We (re-)create this
  // Boo whenever we reset the recorder.
  private Boo           mBoo;

  // Reference to the record button
  private RecordButton  mRecordButton;

  // Reference to the spectral view
  private SpectralView  mSpectralView;

  // Last error. Used and cleared in onCreateDialog
  private int           mErrorCode = -1;

  // Request code - sent to PublishActivity so it can respond appropriately.
  private int           mRequestCode;



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
  }



  private void startRecording()
  {
    if (null == mBooRecorder) {
      initBooRecorder();
    }

    // Force screen to stay on.
    mRecordButton.setKeepScreenOn(true);

    // Stop playback, regardless where it's been started from.
    Globals.get().mPlayer.stopPlaying();
    stopPlayer();

    // Log.d(LTAG, "Resume recording!");
    mBooRecorder.start();
    mSpectralView.startAnimation();
  }



  private void stopRecording()
  {
    // Release the lock if we're holding it.
    mRecordButton.setKeepScreenOn(false);

    if (null != mSpectralView) {
      // Log.d(LTAG, "Stop animating.");
      mSpectralView.stopAnimation();
    }

    if (null != mBooRecorder) {
      // Log.d(LTAG, "Pause recording.");
      mBooRecorder.stop();
    }

    // Show player.
    showPlayer();
  }



  @Override
  public void onPause()
  {
    super.onPause();

    stopRecording();
    mRecordButton.setChecked(false);
    stopPlayer();

    // Write Boo
    mBoo.writeToFile();
  }



  @Override
  public void onResume()
  {
    super.onResume();

    Globals.get().getBooManager().rebuildIndex();

    // If no Boo exists, this might be the first start of the Activity. We're
    // best served if we just grab the latest Boo, if any.
    if (null == mBoo) {
      mBoo = Globals.get().getBooManager().getLatestBoo();
    }
    else {
      // If on the other hand we have a boo, we'll try to reload it. If that fails,
      // we still need to reset the boo.
      if (!mBoo.reload()) {
        mBoo = Globals.get().getBooManager().getLatestBoo();
      }
    }

    // We might still end up having no Boo here if there's none on disk, so
    // let's create a new one if that's the case.
    if (null == mBoo) {
      mBoo = Globals.get().getBooManager().createBoo();
      mBoo.writeToFile();
    }

    // The next thing to do is to initialize the BooRecorder.
    initBooRecorder();

    // Last, update the UI accordingly
    initUI();
    hideOrShowPlayer();
  }



  private void hideOrShowPlayer()
  {
    // Show the player view, if there's a Boo to match.
    if (null != mBoo) {
      // Only show the player if the Boo has a duration. Otherwise there's
      // nothing to play back.
      // Log.d(LTAG, "Boo: " + mBoo);
      if (0.0 != mBoo.getDuration()) {
        showPlayer();
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
    // Stop playback, regardless where it's been started from.
    Globals.get().mPlayer.stopPlaying();
    stopPlayer();

    // Start countdown.
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
    double duration = mBoo.getDuration();
    if (duration > 0.0) {
      mRecordButton.setProgress((int) duration);
      mRecordButton.setChecked(true);
      mRecordButton.setChecked(false);
    }
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


    // If we've been recording, set the button/spectral view to the appropriate
    // state.
    if (null != mBooRecorder) {
      // If we're recording, we'll update everything regularly. If not, we'll
      // want to set the button's progress properly nevertheless.
      if (mBooRecorder.isRecording()) {
        mSpectralView.startAnimation();
        mRecordButton.setChecked(true);
      }
      else {
        FLACRecorder.Amplitudes amp = mBooRecorder.getAmplitudes();
        if (null != amp) {
          mRecordButton.setProgress((int) (amp.mPosition / 1000));
        }
      }
    }
  }



  private void updateRecordingState(FLACRecorder.Amplitudes amp)
  {
    if (null == mBoo.mData.mRecordedAt) {
      mBoo.mData.mRecordedAt = new Date();
    }

    mSpectralView.setAmplitudes(amp.mAverage, amp.mPeak);
    mRecordButton.setProgress((int) (amp.mPosition / 1000));

    // If the Boo has no location, but Globals does, update the Boo's location.
    // By doing that here, we'll get the location as early on in the recording
    // process as possible. That should provide the most "accurate" location
    // in terms of the point in time that best represents where the recording
    // was made.
    if (null == mBoo.mData.mLocation) {
      Location loc = Globals.get().mLocation;
      if (null != loc) {
        mBoo.mData.mLocation = new BooLocation(this, Globals.get().mLocation);
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



  private void initBooRecorder()
  {
    if (null != mBooRecorder) {
      mBooRecorder.stop();
      mBooRecorder = null;
    }

    if (null == mBoo) {
      Log.e(LTAG, "Cannot instanciate BooRecorder, Boo does not exist.");
      return;
    }

    // Instanciate recorder.
    mBooRecorder = new BooRecorder(this, mBoo,
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

            case BooRecorder.MSG_END_OF_RECORDING:
              // Alright, let's write that Boo to disk!
              mBoo.writeToFile();
              break;

            default:
              mBooRecorder.stop();
              mErrorCode = m.what;
              showDialog(DIALOG_RECORDING_ERROR);
              break;
          }

          return true;
        }
      }
    ));
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
    publish.setEnabled(0.0 != mBoo.getDuration());
    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case MENU_RESTART:
        mBoo.delete();
        mBoo = Globals.get().getBooManager().createBoo();
        mBoo.writeToFile();

        initBooRecorder();
        initUI();
        hideOrShowPlayer();
        break;

      case MENU_PUBLISH:
        Intent i = new Intent(this, PublishActivity.class);
        mBoo.writeToFile();
        i.putExtra(PublishActivity.EXTRA_BOO_FILENAME, mBoo.mData.mFilename);
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

    // Toast that we're done.
    Toast.makeText(this, R.string.record_publish_success_toast, Toast.LENGTH_LONG).show();
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

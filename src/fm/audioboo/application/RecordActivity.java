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
import android.content.DialogInterface;
import android.content.res.Configuration;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;

import android.media.AudioManager;
import android.media.MediaPlayer;

import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;

import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.Toast;
import android.widget.Button;

import fm.audioboo.widget.RecordButton;
import fm.audioboo.widget.SpectralView;
import fm.audioboo.widget.BooPlayerView;
import fm.audioboo.widget.PieProgressView;

import fm.audioboo.data.BooLocation;
import fm.audioboo.data.DestinationInfo;
import fm.audioboo.data.User;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.app.Dialog;
import android.app.AlertDialog;

import android.location.Location;

import android.net.Uri;

import org.apache.http.NameValuePair;

import java.util.List;
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

  // Dialog IDs.
  private static final int  DIALOG_RECORDING_ERROR        = 0;
  private static final int  DIALOG_DRAFT                  = 1;

  // Vibration duration in msec - half a second seems about right.
  private static final long VIBRATE_DURATION              = 500;

  // Activity codes
  private static final int  ACTIVITY_PUBLISH              = 0;


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_FILENAME = "fm.audioboo.extras.boo-filename";


  /***************************************************************************
   * Data members
   **/
  // Recorder instance
  private BooRecorder     mBooRecorder;
  // Temporary place to store mBoo.getDuration()
  private double          mRecordingOffset;

  // The record activity essentially represents a Boo, even though it's not
  // filled with all possible bits of information yet. We (re-)create this
  // Boo whenever we reset the recorder.
  private DestinationInfo mDestinationInfo;
  private String          mNewTitle;
  private Boo             mBoo;

  // Time limit for recordings; we'll grab that at startup.
  private int             mRecordingTimeLimit;

  // Reference to UI elements
  private RecordButton    mRecordButton;
  private SpectralView    mSpectralView;
  private PieProgressView mPieProgress;
  private TextView        mTextProgress;
  private Button          mRestartButton;
  private Button          mPublishButton;

  // Last error. Used and cleared in onCreateDialog
  private int             mErrorCode = -1;

  // Recording callbacks
  private Handler         mRecordingHandler = new Handler(new Handler.Callback()
  {
    public boolean handleMessage(Message m)
    {
      switch (m.what) {
        case FLACRecorder.MSG_AMPLITUDES:
          FLACRecorder.Amplitudes amp = (FLACRecorder.Amplitudes) m.obj;
          updateRecordingState(amp);
          break;

        case FLACRecorder.MSG_OK:
          // Ignore
          break;

        case BooRecorder.MSG_END_OF_RECORDING:
          updateButtons();
          break;

        default:
          mBooRecorder.stop();
          mErrorCode = m.what;
          showDialog(DIALOG_RECORDING_ERROR);
          break;
      }

      return true;
    }
  });


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.record);

    Intent intent = getIntent();

    // See if we perhaps are launched via ACTION_VIEW;
    Uri dataUri = intent.getData();
    if (null != dataUri) {
      DestinationInfo info = new DestinationInfo();

      List<NameValuePair> params = UriUtils.getQuery(dataUri);
      for (NameValuePair pair : params) {
        String name = pair.getName();
        if (name.equals("destination[stream_id]")) {
          try {
            info.mDestinationId = Integer.valueOf(pair.getValue());
            info.mIsChannel = true;
          } catch (NumberFormatException ex) {
            info.mDestinationId = -1;
          }
        }

        else if (name.equals("destination[recipient_id]")) {
          try {
            info.mDestinationId = Integer.valueOf(pair.getValue());
            info.mIsChannel = false;
          } catch (NumberFormatException ex) {
            info.mDestinationId = -1;
          }
        }

        else if (name.equals("destination[parent_id]")) {
          try {
            info.mInReplyTo = Integer.valueOf(pair.getValue());
          } catch (NumberFormatException ex) {
            info.mInReplyTo = -1;
          }
        }

        else if (name.equals("destination[title]")) {
          mNewTitle = pair.getValue();
        }

        else if (name.equals("destination_name")) {
          info.mDestinationName = pair.getValue();
        }
      }

      if (-1 == info.mDestinationId || null == info.mDestinationName) {
        Toast.makeText(this, R.string.record_invalid_uri, Toast.LENGTH_LONG).show();
        finish();
        return;
      }

      mDestinationInfo = info;
    }

    // If we're not getting a data URI, we're not recording a boo with a
    // destination. We might still be recording a fresh boo, or we might be
    // adding to an existing one. We'll know by whether or not the
    // EXTRA_BOO_FILENAME parameter is given.
    Bundle extras = intent.getExtras();
    if (null != extras) {
      String filename = extras.getString(EXTRA_BOO_FILENAME);
      Boo boo = Boo.constructFromFile(filename);
      if (null == boo) {
        throw new IllegalArgumentException("Boo file '" + filename + "' could "
            + "not be loaded.");
      }
      mBoo = boo;
    }

    if (null == mBoo) {
      mBoo = Globals.get().getBooManager().createBoo();
      mBoo.mData.mTitle = mNewTitle;
    }
    // Log.d(LTAG, "Boo: " + mBoo);

    // Grab recording time limit.
    mRecordingTimeLimit = Globals.get().getRecordingLimit();
  }



  private void writeBoo()
  {
    // We only have destination info if we're creating a new boo anyway.
    if (null != mDestinationInfo) {
      mBoo.mData.mDestinationInfo = mDestinationInfo;
      if (!mDestinationInfo.mIsChannel) {
        mBoo.mData.mIsMessage = true;
      }
    }

    // Always overwrite the user. We're dealing with drafts, after all...
    mBoo.mData.mUser = Globals.get().mAccount;

    mBoo.writeToFile();
  }



  private void startRecording()
  {
    // Stop playback & hide player.
    Globals.get().mPlayer.stop();
    hidePlayer();

    // Force screen to stay on.
    mRecordButton.setKeepScreenOn(true);

    mRecordingOffset = mBoo.getDuration();

    // Log.d(LTAG, "Resume recording!");
    mBooRecorder.start();
    mSpectralView.startAnimation();

    // Update button availability.
    disableSecondaryButtons();
  }



  private void stopRecording()
  {
    // Release the lock if we're holding it.
    mRecordButton.setKeepScreenOn(false);

    // Log.d(LTAG, "Stop animating.");
    mSpectralView.stopAnimation();

    // Log.d(LTAG, "Pause recording.");
    mBooRecorder.stop();

    // Show & initialize player.
    showPlayer();
    Globals.get().mPlayer.play(mBoo, false);
  }



  private void disableSecondaryButtons()
  {
    if (null != mRestartButton) {
      mRestartButton.setEnabled(false);
    }
    if (null != mPublishButton) {
      mPublishButton.setEnabled(false);
    }
  }



  private void updateButtons()
  {
    // Update button availability.
    double duration = mBoo.getDuration();
    if (null != mRestartButton) {
      mRestartButton.setEnabled(duration > 0.0f);
    }
    if (null != mPublishButton) {
      mPublishButton.setEnabled(duration > 0.0f);
    }
    if (null != mRecordButton) {
      mRecordButton.setEnabled(duration < mRecordingTimeLimit);
    }
  }



  @Override
  public void onStop()
  {
    super.onStop();

    // Definitely stop playback. We don't exactly know what Boo was playing
    // before, and we don't really care.
    Globals.get().mPlayer.stop();
  }



  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if (KeyEvent.KEYCODE_BACK == keyCode && 0 == event.getRepeatCount()) {
      onBackPressedManual();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }



  public void onBackPressedManual()
  {
    if (mBoo.getDuration() > 0.0
        || (null != mBooRecorder
          && (mBooRecorder.isRecording()
            || mBooRecorder.getDuration() > 0.0)))
    {
      // Save/discard dialogue.
      mRecordButton.setChecked(false);
      showDialog(DIALOG_DRAFT);
    }
    else {
      deleteAndQuit();
    }
  }


  private void deleteAndQuit()
  {
    mRecordButton.setChecked(false);

    int res = mBoo.delete() ? Activity.RESULT_OK : Activity.RESULT_CANCELED;
    setResult(res);
    finishInternal();
  }



  private void finishInternal()
  {
    // We ensure the freshly deleted boo doesn't get played by queueing the
    // intro boo.
    Globals.get().mPlayer.play(Boo.createIntroBoo(this), false);

    finish();
  }



  @Override
  public void onResume()
  {
    super.onResume();

    reInitialize();
  }



  private void reInitialize()
  {
    // The next thing to do is to initialize the BooRecorder.
    initBooRecorder();

    // Last, update the UI accordingly
    populateViews();

    // Show & initialize player. (also stops playback if something was already
    // playing)
    Globals.get().mPlayer.play(mBoo, false);
    showPlayer();
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
    // Stop playback & hide player.
    Globals.get().mPlayer.stop();
    hidePlayer();

    // Start countdown.
    View v = findViewById(R.id.record_overlay);
    v.setVisibility(View.VISIBLE);

    TextView tv = (TextView) findViewById(R.id.record_countdown);
    v.setVisibility(View.VISIBLE);

    // Play countdown
    MediaPlayer mp = MediaPlayer.create(this, R.raw.countdown);
    mp.start();

    mRecordButton.setEnabled(false);

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

      mRecordButton.setEnabled(true);

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

    // ... and vibrate, if we're in vibrate mode!
    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    if (null != am) {
      if (AudioManager.RINGER_MODE_VIBRATE == am.getRingerMode()) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (null != v) {
          v.vibrate(VIBRATE_DURATION);
        }
      }
    }
  }



  private void populateViews()
  {
    // This function is called either from onStart() or from
    // onConfigurationChanged(). Either way, we need to reconstruct the
    // activity's state as it was previously.

    // That also implies that we've lost our bindings for the button, etc.
    mRecordButton = (RecordButton) findViewById(R.id.record_button);
    if (null == mRecordButton) {
      Log.e(LTAG, "No record button found!");
      return;
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

    mPieProgress = (PieProgressView) findViewById(R.id.record_pie_progress);
    if (null != mPieProgress) {
      mPieProgress.setMax(mRecordingTimeLimit);
      double duration = mBoo.getDuration();
      if (duration > 0.0) {
        mPieProgress.setProgress((int) duration);
      }
    }

    mSpectralView = (SpectralView) findViewById(R.id.record_spectral_view);
    if (null == mSpectralView) {
      Log.e(LTAG, "No spectral view found!");
      return;
    }

    // Set colors depending on whether a destination ID is set or not.
    int gridColor = R.color.record_grid_color;
    int gridBackgroundColor = R.color.record_grid_background;
    int gridBarColor = R.color.record_grid_bar;
    int backgroundColor = R.color.record_background;
    if (null != mDestinationInfo) {
      gridColor = R.color.record_to_grid_color;
      gridBackgroundColor = R.color.record_to_grid_background;
      gridBarColor = R.color.record_to_grid_bar;
      backgroundColor = R.color.record_to_background;
    }

    View view = findViewById(R.id.record_background);
    if (null != view) {
      view.setBackgroundResource(backgroundColor);
    }

    mSpectralView.setBarDrawable(gridBarColor);
    mSpectralView.setGridColor(gridColor);
    mSpectralView.setBackgroundResource(gridBackgroundColor);


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
        if (null != amp && null != mPieProgress) {
          mPieProgress.setProgress((int) (amp.mPosition / 1000f));
        }
      }
    }

    // Show/Hide addressee field.
    TextView text_view = (TextView) findViewById(R.id.record_addressee);
    if (null != text_view) {
      if (null != mDestinationInfo) {
        String addressee = String.format(getResources().getString(R.string.record_addressee),
            mDestinationInfo.mDestinationName);
        text_view.setText(addressee);
      }
      else {
        // Making it invisible messes up the layout, so let's just set an empty
        // text.
        text_view.setText(" ");
      }
    }

    // Set title in the progress views.
    text_view = (TextView) findViewById(R.id.record_title);
    if (null != text_view) {
      if (null == mBoo.mData.mTitle) {
        text_view.setText(R.string.boo_player_new_title);
      }
      else {
        text_view.setText(mBoo.mData.mTitle);
      }
    }

    // Set author in progress views.
    text_view = (TextView) findViewById(R.id.record_author);
    if (null != text_view) {
      if (null != mBoo.mData.mUser) {
        text_view.setText(mBoo.mData.mUser.mUsername);
      }
      else {
        User account = Globals.get().mAccount;
        if (null != account && null != account.mUsername) {
          text_view.setText(account.mUsername);
        }
        else {
          text_view.setText(R.string.boo_player_author_self);
        }
      }
    }

    mTextProgress = (TextView) findViewById(R.id.record_remaining);

    // Other buttons.
    mRestartButton = (Button) findViewById(R.id.record_restart);
    if (null != mRestartButton) {
      mRestartButton.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            mBoo.delete();
            mBoo = Globals.get().getBooManager().createBoo();
            mBoo.mData.mTitle = mNewTitle;
            reInitialize();

            // Update buttons.
            disableSecondaryButtons();
            if (null != mRecordButton) {
              mRecordButton.resetState();
              mRecordButton.setEnabled(true);
            }

            // Reset progress.
            if (null != mPieProgress) {
              mPieProgress.setProgress(0);
            }
          }
      });
    }

    mPublishButton = (Button) findViewById(R.id.record_publish);
    if (null != mPublishButton) {
      mPublishButton.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            writeBoo();
            Intent i = new Intent(RecordActivity.this, PublishActivity.class);
            i.putExtra(PublishActivity.EXTRA_BOO_FILENAME, mBoo.mData.mFilename);
            startActivityForResult(i, ACTIVITY_PUBLISH);
          }
      });
    }

    // Disable buttons if there's nothing to do with them yet.
    updateButtons();
  }



  private void updateRecordingState(FLACRecorder.Amplitudes amp)
  {
    int position = (int) (mRecordingOffset + (amp.mPosition / 1000f));

    // Update UI
    mSpectralView.setAmplitudes(amp.mAverage, amp.mPeak);
    if (null != mPieProgress) {
      mPieProgress.setProgress(position);
    }

    double remaining = mRecordingTimeLimit - position;
    if (null != mTextProgress) {
      int min = (int) (remaining / 60);
      int sec = ((int) remaining) % 60;
      mTextProgress.setText(String.format("%02d:%02d", min, sec));
    }

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

    if (null == mBoo.mData.mRecordedAt) {
      mBoo.mData.mRecordedAt = new Date();
    }

    // We may have reason to stop recording here.
    if (position >= mRecordingTimeLimit) {
      stopRecording();
    }
  }



  private void showPlayer()
  {
    ViewAnimator anim = (ViewAnimator) findViewById(R.id.record_player_flipper);
    if (null != anim) {
      anim.setDisplayedChild(0);
    }

    View player = findViewById(R.id.record_player);
    player.setEnabled(mBoo.getDuration() > 0.0f);
  }



  private void hidePlayer()
  {
    ViewAnimator anim = (ViewAnimator) findViewById(R.id.record_player_flipper);
    if (null != anim) {
      anim.setDisplayedChild(1);
    }
  }



  private void initBooRecorder()
  {
    if (null != mBooRecorder) {
      mBooRecorder.stop();
      mBooRecorder = null;
    }

    // Instanciate recorder.
    mBooRecorder = new BooRecorder(this, mBoo, mRecordingHandler);
  }



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    switch (resultCode) {
      case Activity.RESULT_CANCELED:
        // Nothing to do.
        break;

      case Activity.RESULT_OK: // Stuff has been changed
        mBoo.reload();
        reInitialize();
        break;

      case PublishActivity.RESULT_PUBLISHED:
        setResult(PublishActivity.RESULT_PUBLISHED);
        finishInternal();
        break;

      default:
        Log.e(LTAG, "Unexpected result code: " + resultCode + " - " + data);
        break;
    }
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
            .setPositiveButton(R.string.record_error_ack, null);
          dialog = builder.create();
        }
        break;

      case DIALOG_DRAFT:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.record_draft_dialog_content)
          .setPositiveButton(R.string.record_draft_dialog_save, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface d, int which)
              {
                writeBoo();
                setResult(Activity.RESULT_OK);
                finishInternal();
              }
          })
          .setNegativeButton(R.string.record_draft_dialog_delete, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface d, int which)
              {
                deleteAndQuit();
              }
          })
        ;
        dialog = builder.create();
        break;
    }

    return dialog;
  }
}

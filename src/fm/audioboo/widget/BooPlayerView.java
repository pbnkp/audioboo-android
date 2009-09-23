/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.content.Context;
import android.util.AttributeSet;

import android.view.View;

import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import android.media.AudioManager;

import fm.audioboo.app.Boo;
import fm.audioboo.app.BooPlayer;
import fm.audioboo.app.Globals;

import fm.audioboo.app.R;

import android.util.Log;

/**
 * Displays a player window, and uses the BooPlayer in Globals to play back
 * Boos.
 **/
public class BooPlayerView extends LinearLayout
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooPlayerView";

  // Multiplier applied to boo playback progress (in seconds) before it's
  // used as max/current in the progress display.
  private static final int  PROGRESS_MULTIPLIER = 5000;

  // The type of stream Boos play in.
  private static final int  STREAM_TYPE         = AudioManager.STREAM_MUSIC;


  /***************************************************************************
   * Public constants
   **/
  // Playback ends either successfully or with an error.
  public static final int END_STATE_SUCCESS = 0;
  public static final int END_STATE_ERROR   = 1;



  /***************************************************************************
   * Informs users that playback ended
   **/
  public static abstract class PlaybackEndListener
  {
    public abstract void onPlaybackEnded(BooPlayerView view, int endState);
  }




  /***************************************************************************
   * Data members
   **/
  // Context
  private Context             mContext;

  // Seekbar instance
  private SeekBar             mSeekBar;

  // Button instance
  private PlayPauseButton     mButton;

  // Title
  private TextView            mTitle;

  // Boo to be played.
  private Boo                 mBoo;

  // Listener
  private PlaybackEndListener mListener;

  // Audio manager - used in more than one place.
  private AudioManager        mAudioManager;


  /***************************************************************************
   * Button Listener
   **/
  private class ButtonListener implements CompoundButton.OnCheckedChangeListener
  {
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
      if (isChecked) {
        onStop(END_STATE_SUCCESS);
      }
      else {
        onStart();
      }
    }
  }



  /***************************************************************************
   * Boo Progress Listener
   **/
  private class BooProgressListener extends BooPlayer.ProgressListener
  {
    public void onProgress(int state, double progress)
    {
      switch (state) {
        case BooPlayer.STATE_PLAYBACK:
          mButton.setIndeterminate(false);
          mButton.setProgress((int) (progress * PROGRESS_MULTIPLIER));
          break;

        case BooPlayer.STATE_BUFFERING:
          mButton.setIndeterminate(true);
          break;

        case BooPlayer.STATE_FINISHED:
          onStop(END_STATE_SUCCESS);
          break;

        case BooPlayer.STATE_ERROR:
          onStop(END_STATE_ERROR);
          break;
      }
    }
  }



  /***************************************************************************
   * Implementation
   **/
  public BooPlayerView(Context context)
  {
    super(context);
    mContext = context;
  }



  public BooPlayerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
  }



  public BooPlayerView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs);
    mContext = context;
  }



  public void setPlaybackEndListener(PlaybackEndListener listener)
  {
    mListener = listener;
  }



  public void play(Boo boo)
  {
    play(boo, true);
  }



  public void play(Boo boo, boolean playImmediately)
  {
    mBoo = boo;

    // Set title
    if (null == mBoo.mTitle) {
      setTitle("New recording"); // FIXME
    }
    else {
      setTitle(mBoo.mTitle);
    }

    // Set the button to a neutral state. startPlaying() will set it to a
    // indeterminate state, and actual playback will mean it'll switch to
    // a playback state.
    mButton.setChecked(true);
    mButton.setIndeterminate(false);
    mButton.setProgress(0);

    // If we're supposed to play back immediately, then do so.
    if (playImmediately) {
      startPlaying();
    }
  }



  private void startPlaying()
  {
    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    Globals.get().mPlayer.setProgressListener(new BooProgressListener());
    Globals.get().mPlayer.play(mBoo);

    // Initialize button state
    mButton.setChecked(false);
    mButton.setIndeterminate(true);
    mButton.setMax((int) (mBoo.mDuration * PROGRESS_MULTIPLIER));
    mButton.setProgress(0);
  }



  public void setTitle(String title)
  {
    if (null != mTitle) {
      mTitle.setText(title);
    }
  }



  public void stop()
  {
    // Set button to a neutral state
    mButton.setChecked(true);
    mButton.setIndeterminate(false);
    mButton.setProgress(0);

    // Stops playback.
    if (null != mBoo) {
      Globals.get().mPlayer.stopPlaying();
    }
  }



  public void pause()
  {
    // Pauses playback.
    if (null != mBoo) {
      Globals.get().mPlayer.pausePlaying();
    }
  }



  public void resume()
  {
    // Resumes playback.
    if (null != mBoo) {
      Globals.get().mPlayer.resumePlaying();
    }
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    // Grab and remember the audio manager
    mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

    LinearLayout content = (LinearLayout) inflate(mContext, R.layout.boo_player, this);

    // Set up seekbar
    mSeekBar = (SeekBar) content.findViewById(R.id.boo_player_volume);
    if (null != mSeekBar) {
      mSeekBar.setMax(mAudioManager.getStreamMaxVolume(STREAM_TYPE));
      mSeekBar.setProgress(mAudioManager.getStreamVolume(STREAM_TYPE));

      mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
        {
          mAudioManager.setStreamVolume(STREAM_TYPE, progress, 0);
        }

        public void onStartTrackingTouch(SeekBar seekBar)
        {
          // Ignore
        }


        public void onStopTrackingTouch(SeekBar seekBar)
        {
          // Ignore
        }
      });
    }


    // Set up play/pause button
    mButton = (PlayPauseButton) content.findViewById(R.id.boo_player_button);
    if (null != mButton) {
      mButton.setChecked(false);
      mButton.setIndeterminate(true);


      mButton.setOnCheckedChangeListener(new ButtonListener());
    }

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);
  }



  private void onStop(int state)
  {
    // isChecked == true is sent once before we've actually started playback;
    // it's best to ignore that.
    if (!Globals.get().mPlayer.hasStarted()) {
      // Log.d(LTAG, "ignore first onStop");
      return;
    }

    // We don't care here whether the button is checked or not, we just
    // stop playback.
    stop();

    // Propagate this event to the user
    if (null != mListener) {
      mListener.onPlaybackEnded(this, state);
    }
  }



  private void onStart()
  {
    startPlaying();
  }
}

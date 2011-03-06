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

package fm.audioboo.widget;

import android.content.Context;
import android.util.AttributeSet;

import android.view.View;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;

import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import android.media.AudioManager;

import android.os.Handler;
import android.os.Message;

import fm.audioboo.application.Pair;
import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;

import fm.audioboo.service.Constants;
import fm.audioboo.service.BooPlayerClient;

import fm.audioboo.application.R;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Displays a player window, and uses the BooPlayer in Globals to play back
 * Boos.
 **/
public class BooPlayerView
       extends LinearLayout
       implements BooPlayerClient.ProgressListener,
                  CompoundButton.OnCheckedChangeListener
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
  private WeakReference<Context>  mContext;

  // Seekbar instance
  private SeekBar                 mSeekBar;

  // Button instance
  private PlayPauseButton         mButton;

  // Title
  private TextView                mTitle;

  // Listener
  private PlaybackEndListener     mListener;

//  // If we've already notified the listener since the last play call, this
//  // will be true and we won't notify the listener again.
//  private boolean                 mEndedSent;

  // Audio manager - used in more than one place.
  private AudioManager            mAudioManager;


  /***************************************************************************
   * Implementation
   **/
  public BooPlayerView(Context context)
  {
    super(context);
    setup(context);
  }



  public BooPlayerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setup(context);
  }



  public BooPlayerView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs);
    setup(context);
  }



  public void setPlaybackEndListener(PlaybackEndListener listener)
  {
    mListener = listener;
  }



  public PlaybackEndListener getPlaybackEndListener()
  {
    return mListener;
  }



  public void play(Boo boo)
  {
    play(boo, true);
  }



  public void play(Boo boo, boolean playImmediately)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    setActive(true);

    // Log.d(LTAG, "view play: " + playImmediately);

    // Playback
    startPlaying(boo, playImmediately);

    // Set title
    updateTitle();
  }



  private void setButtonState(int newState)
  {
    switch (newState) {
      case Constants.STATE_NONE: // Same as STATE_FINISHED
      case Constants.STATE_PREPARING:
      case Constants.STATE_PAUSED:
        mButton.setChecked(true);
        mButton.setIndeterminate(false);
        mButton.setProgress(0);
        break;

      case Constants.STATE_ERROR:
        mButton.setChecked(true);
        mButton.setIndeterminate(false);
        mButton.setProgress(0);

        sendEnded(END_STATE_ERROR);
        break;

      case Constants.STATE_PLAYING:
        mButton.setChecked(false);
        mButton.setIndeterminate(false);
        break;

      case Constants.STATE_BUFFERING:
        mButton.setChecked(false);
        mButton.setIndeterminate(true);
        break;
    }
  }



  private void startPlaying(Boo boo, boolean playImmediately)
  {
    // Log.d(LTAG, "view start");

    // Initialize button state
    setButtonState(Constants.STATE_BUFFERING);

    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    if (null != boo) {
      Globals.get().mPlayer.play(boo, playImmediately);
    }
    else {
      Globals.get().mPlayer.resume();
    }
  }



  public void setTitle(String title)
  {
    if (null != mTitle) {
      mTitle.setText(title);
    }
  }



  /**
   * Makes child elements clickable or not.
   **/
  public void setActive(boolean flag)
  {
    if (null != mButton) {
      mButton.setClickable(flag);
    }
    if (null != mSeekBar) {
      mButton.setClickable(flag);
    }
  }



  public void stop()
  {
    // Log.d(LTAG, "view stop");
    setActive(false);

    // Stops playback.
    Globals.get().mPlayer.stop();

    // Set button to a neutral state
    setButtonState(Constants.STATE_FINISHED);
  }



  public void pause()
  {
    // Log.d(LTAG, "view pause");
    // Pauses playback.
    Globals.get().mPlayer.pause();
  }



  public void resume()
  {
    // Log.d(LTAG, "view resume");
    // Resumes playback.
    Globals.get().mPlayer.resume();
  }



  public boolean isPaused()
  {
    return (Constants.STATE_PAUSED == Globals.get().mPlayer.getState());
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    // Grab and remember the audio manager
    mAudioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

    LinearLayout content = (LinearLayout) inflate(ctx, R.layout.boo_player, this);

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
      setButtonState(Constants.STATE_NONE);
      mButton.setOnCheckedChangeListener(this);
    }

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);

    // Be informed of whatever player state exists.
    Globals.get().mPlayer.setProgressListener(this);

    // If the playback service is playing, initialize title, etc.
    if (Constants.STATE_NONE != Globals.get().mPlayer.getState()) {
      // Initialize button state
      setButtonState(Constants.STATE_BUFFERING);

      // Update title
      updateTitle();
    }
  }



  private void sendEnded(int state)
  {
    if (null != mListener) {
      mListener.onPlaybackEnded(this, state);
    }
  }



  private void setup(Context context)
  {
    mContext = new WeakReference<Context>(context);

    setClickable(true);

    setOnTouchListener(new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event)
        {
          // Log.d(LTAG, "Captured touch.");
          return true;
        }
    });
  }



  private void updateTitle()
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }


    String title = Globals.get().mPlayer.getTitle();
    if (null == title) {
      setTitle(ctx.getResources().getString(R.string.boo_player_new_title));
    }
    else {
      setTitle(title);
    }
  }


  /***************************************************************************
   * BooPlayerClient.ProgressListener implementation
   **/
  public void onProgress(int state, double progress, double total)
  {
    // Update button state
    setButtonState(state);

    // If there's progress, update that, too.
    if (null != mButton && total > 0) {
      mButton.setMax((int) (total * PROGRESS_MULTIPLIER));
      mButton.setProgress((int) (progress * PROGRESS_MULTIPLIER));
    }
  }



  /***************************************************************************
   * CompoundButton.OnCheckedChangeListener implementation
   **/
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
  {
    int state = Globals.get().mPlayer.getState();
    boolean shouldBeChecked = (Constants.STATE_NONE == state)
      || (Constants.STATE_PAUSED == state)
      || (Constants.STATE_ERROR == state);

    // Log.d(LTAG, "Is checked: " + isChecked + " - should be? " + shouldBeChecked);

    if (shouldBeChecked == isChecked) {
      return;
    }

    if (isChecked) {
      stop();
      sendEnded(END_STATE_SUCCESS);
    }
    else {
      if (Constants.STATE_PAUSED == state) {
        startPlaying(null, false);
      }
    }
  }
}

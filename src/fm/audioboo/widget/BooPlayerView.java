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
public class BooPlayerView extends LinearLayout implements Handler.Callback
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

  // Messages used to invoke onStart/onStop on main thread.
  private static final int  MSG_ON_START  = 0;  // Start clicked
  private static final int  MSG_ON_STOP   = 1;  // Stop clicked
  private static final int  MSG_PLAYBACK  = 2;  // Display playback progress
  private static final int  MSG_BUFFERING = 3;  // Display buffering progress
  private static final int  MSG_FINISHED  = 4;  // Revert to initial state.

  // Button states
  private static final int  STATE_NONE      = -1; // Only initial state.
  private static final int  STATE_STOPPED   = 0;  // Shows play button, but no
                                                  // progress/indeterminate
  private static final int  STATE_BUFFERING = 1;  // Shows stop button and
                                                  // indeterminate
  private static final int  STATE_PLAYING   = 2;  // Shows stop button and
                                                  // progress.


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
  private SeekBar             mSeekBar;

  // Button instance
  private PlayPauseButton     mButton;

  // Title
  private TextView            mTitle;

  // Boo to be played.
  private Boo                 mBoo;

  // Listener
  private PlaybackEndListener mListener;

  // If we've already notified the listener since the last play call, this
  // will be true and we won't notify the listener again.
  private boolean             mEndedSent;

  // Audio manager - used in more than one place.
  private AudioManager        mAudioManager;

  // Expected button state
  private int                 mButtonState = STATE_NONE;


  /***************************************************************************
   * Button Listener
   **/
  private class ButtonListener implements CompoundButton.OnCheckedChangeListener
  {
    private Handler mHandler;

    public ButtonListener(Handler.Callback callback)
    {
      mHandler = new Handler(callback);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
      boolean shouldBeChecked = (STATE_STOPPED == mButtonState);
      if (shouldBeChecked == isChecked) {
        return;
      }

      if (isChecked) {
        mHandler.obtainMessage(MSG_ON_STOP, END_STATE_SUCCESS).sendToTarget();
      }
      else {
        mHandler.obtainMessage(MSG_ON_START).sendToTarget();
      }
    }
  }



  /***************************************************************************
   * Boo Progress Listener
   **/
  private class BooProgressListener extends BooPlayerClient.ProgressListener
  {
    private Handler mHandler;

    public BooProgressListener(Handler.Callback callback)
    {
      mHandler = new Handler(callback);
    }


    public void onProgress(int state, double progress)
    {
      switch (state) {
        case Constants.STATE_PLAYING:
          mHandler.obtainMessage(MSG_PLAYBACK, new Double(progress)).sendToTarget();
          break;

        case Constants.STATE_BUFFERING:
          mHandler.obtainMessage(MSG_BUFFERING).sendToTarget();
          break;

        case Constants.STATE_FINISHED:
          mHandler.obtainMessage(MSG_FINISHED, END_STATE_SUCCESS).sendToTarget();
          break;

        case Constants.STATE_ERROR:
          mHandler.obtainMessage(MSG_FINISHED, END_STATE_ERROR).sendToTarget();
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
    mBoo = boo;

    // Set title
    if (null == mBoo.mData.mTitle) {
      setTitle(ctx.getResources().getString(R.string.boo_player_new_title));
    }
    else {
      setTitle(mBoo.mData.mTitle);
    }

    // Set the button to a neutral state. startPlaying() will set it to a
    // indeterminate state, and actual playback will mean it'll switch to
    // a playback state.
    setButtonState(STATE_STOPPED);

    // If we're supposed to play back immediately, then do so.
    if (playImmediately) {
      startPlaying();
    }
  }



  private void setButtonState(int newState)
  {
    // Log.d(LTAG, String.format("Got button state %d, switching to %d", mButtonState, newState));
    if (mButtonState == newState) {
      // Bail. No state change.
      return;
    }
    mButtonState = newState;

    switch (newState) {
      case STATE_BUFFERING:
        mButton.setChecked(false);
        mButton.setIndeterminate(true);
        if (null != mBoo && 0.0 != mBoo.getDuration()) {
          mButton.setMax((int) (mBoo.getDuration() * PROGRESS_MULTIPLIER));
        }
        break;

      case STATE_PLAYING:
        mButton.setChecked(false);
        mButton.setIndeterminate(false);
        break;

      case STATE_STOPPED:
      default:
        mButton.setChecked(true);
        mButton.setIndeterminate(false);
        mButton.setProgress(0);
        break;
    }
  }



  private void startPlaying()
  {
    // Log.d(LTAG, "view start");
    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    // FIXME Globals.get().mPlayer.setProgressListener(new BooProgressListener(this));
    Globals.get().mPlayer.play(mBoo);

    // Initialize button state
    setButtonState(STATE_BUFFERING);

    // Reset flag whether an ended message was sent or not.
    mEndedSent = false;
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
    if (null != mBoo) {
      Globals.get().mPlayer.stop();
    }

    // Set button to a neutral state
    setButtonState(STATE_STOPPED);
  }



  public void pause()
  {
    // Log.d(LTAG, "view pause");
    // Pauses playback.
    if (null != mBoo) {
      Globals.get().mPlayer.pause();
    }
  }



  public void resume()
  {
    // Log.d(LTAG, "view resume");
    // Resumes playback.
    if (null != mBoo) {
      Globals.get().mPlayer.resume();
    }
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
      setButtonState(STATE_STOPPED);

      mButton.setOnCheckedChangeListener(new ButtonListener(this));
    }

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);
  }



  public boolean handleMessage(Message msg)
  {
    // Log.d(LTAG, "Got message: " + msg.what);
    switch (msg.what) {
      case MSG_ON_START:
        startPlaying();
        break;

      case MSG_ON_STOP:
        stop();
        sendEnded(msg.arg1);
        break;

      case MSG_PLAYBACK:
        setButtonState(STATE_PLAYING);
        Double d = (Double) msg.obj;
        mButton.setProgress((int) (d * PROGRESS_MULTIPLIER));
        break;

      case MSG_BUFFERING:
        setButtonState(STATE_BUFFERING);
        break;

      case MSG_FINISHED:
        setButtonState(STATE_STOPPED);
        sendEnded(msg.arg1);
        break;

      default:
        Log.e(LTAG, "Unknown message id: " + msg.what);
        return false;
    }

    return true;
  }



  private void sendEnded(int state)
  {
    if (null != mListener && !mEndedSent) {
      mEndedSent = true;
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
}

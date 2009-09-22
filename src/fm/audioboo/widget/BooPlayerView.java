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

  // Boo to be played.
  private Boo                 mBoo;

  // Listener
  private PlaybackEndListener mListener;

  // Audio manager - used in more than one place.
  private AudioManager        mAudioManager;


  /***************************************************************************
   * Boo Progress Listener
   **/
  private class BooProgressListener extends BooPlayer.ProgressListener
                                    implements CompoundButton.OnCheckedChangeListener
  {
    BooProgressListener()
    {
      super();

      // Install handler for listening to the toggle.
      mButton.setOnCheckedChangeListener(this);
    }


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
          onPlaybackEnded(END_STATE_SUCCESS);
          break;

        case BooPlayer.STATE_ERROR:
          onPlaybackEnded(END_STATE_ERROR);
          break;
      }
    }


    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
      onPlaybackEnded(END_STATE_SUCCESS);
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
    mBoo = boo;

    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    Globals.get().mPlayer.setProgressListener(new BooProgressListener());
    Globals.get().mPlayer.play(mBoo);

    // Initialize button state
    mButton.setChecked(false);
    mButton.setIndeterminate(true);
    mButton.setMax((int) (boo.mDuration * PROGRESS_MULTIPLIER));
    mButton.setProgress(0);

  }



  public void stop()
  {
    // Stops playback.
    Globals.get().mPlayer.stopPlaying();
  }



  public void pause()
  {
    // Pauses playback.
    Globals.get().mPlayer.pausePlaying();
  }



  public void resume()
  {
    // Pauses playback.
    Globals.get().mPlayer.resumePlaying();
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
    }
  }



  private void onPlaybackEnded(int state)
  {
    // We don't care here whether the button is checked or not, we just
    // stop playback.
    Globals.get().mPlayer.stopPlaying();

    // Propagate this event to the user
    if (null != mListener) {
      mListener.onPlaybackEnded(this, state);
    }
  }
}

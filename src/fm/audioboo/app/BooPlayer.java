/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.content.Context;

import android.media.MediaPlayer;

import java.util.TimerTask;
import java.util.Timer;

import android.util.Log;

/**
 * Plays Boos. Abstracts out all the differences between streaming MP3s from the
 * web and playing local FLAC files.
 * TODO FLAC files not (yet) supported.
 **/
public class BooPlayer extends Thread
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooPlayer";

  // Sleep time, if the thread's not woken.
  private static final int SLEEP_TIME           = 60 * 1000;

  // Interval at which we notify the user of playback progress (msec)
  private static final int TIMER_TASK_INTERVAL  = 500;

  /***************************************************************************
   * Public constants
   **/
  // Three states: either buffering, or playing, or finished. If the state is
  // STATE_PLAYBACK or STATE_FINISHED, then ProgressListener's second
  // parameter (below) contains the current playback position.
  public static final int STATE_BUFFERING = 0;
  public static final int STATE_PLAYBACK  = 1;
  public static final int STATE_FINISHED  = 2;
  public static final int STATE_ERROR     = 3;


  /***************************************************************************
   * Listener to state changes
   **/
  public static abstract class ProgressListener
  {
    public abstract void onProgress(int state, double progress);
  }



  /***************************************************************************
   * Public data
   **/
  // Flag that keeps the thread running when true.
  public boolean mShouldRun;


  /***************************************************************************
   * Private data
   **/
  // Context in which this object was created
  private Context               mContext;

  // Boo that's currently being played
  private Object                mBooLock = new Object();
  private Boo                   mBoo;

  // Player for MP3 Boos streamed from the Web.
  private MediaPlayer           mMediaPlayer;

  // Listener for state changes
  private ProgressListener      mListener;

  // Tick timer, for tracking progress
  private Timer                 mTimer;
  private TimerTask             mTimerTask;
  private boolean               mPaused;

  // Used for progress tracking
  private double                mPlaybackProgress;
  private long                  mTimestamp;

  /***************************************************************************
   * Implementation
   **/
  public BooPlayer(Context context)
  {
    mContext = context;
  }



  public void play(Boo boo)
  {
    synchronized (mBooLock)
    {
      mBoo = boo;
    }
    interrupt();
  }



  public void stopPlaying()
  {
    play(null);
  }



  public void pausePlaying()
  {
    if (null != mMediaPlayer) {
      mMediaPlayer.pause();
    }
    mPaused = true;
  }



  public void resumePlaying()
  {
    if (null != mMediaPlayer) {
      mMediaPlayer.start();
    }
    mPaused = false;
  }



  public void setProgressListener(ProgressListener listener)
  {
    mListener = listener;
  }



  public void run()
  {
    mShouldRun = true;

    Boo playingBoo = null;
    while (mShouldRun)
    {
      try {
        // Grab the Boo to be played off the list.
        Boo currentBoo = null;
        synchronized (mBooLock)
        {
          currentBoo = mBoo;
        }

        if (currentBoo != playingBoo) {
          if (null != playingBoo) {
            stopPlaying(playingBoo);
          }

          playingBoo = currentBoo;

          if (null != playingBoo) {
            startPlaying(playingBoo);
          }
        }

        // Fall asleep.
        sleep(SLEEP_TIME);
      } catch (InterruptedException ex) {
        // pass
      }
    }

    // Right, make sure to finish playback.
    play(null);
  }



  private void stopPlaying(Boo boo)
  {
    // Log.d(LTAG, "Stop playing: " + boo);
    if (null != mMediaPlayer) {
      mMediaPlayer.stop();
      mMediaPlayer.release();
      mMediaPlayer = null;
    }
    if (null != mTimer) {
      mTimer.cancel();
      mTimer = null;
      mTimerTask = null;
      mPaused = true;
    }
  }



  private void startPlaying(Boo boo)
  {
    // Log.d(LTAG, "Start playing: " + boo.mHighMP3Url);
    mMediaPlayer = new MediaPlayer();

    // Attach listeners to the player, for propagating state up to the users of this
    // class.
    mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
      public void onBufferingUpdate(MediaPlayer mp, int percent)
      {
        if (null == mListener) {
          return;
        }

        if (mp.isPlaying()) {
          mListener.onProgress(STATE_PLAYBACK, mPlaybackProgress);
        }
        else {
          mListener.onProgress(STATE_BUFFERING, 0f);
        }
      }
    });

    mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      public void onPrepared(MediaPlayer mp)
      {
        if (null == mListener) {
          return;
        }

        mListener.onProgress(STATE_PLAYBACK, mPlaybackProgress);

        // If we made it here, then we'll start a tick timer for sending
        // continuous progress to the users of this class.
        mPlaybackProgress = 0f;
        mTimestamp = System.currentTimeMillis();

        try {
          mTimer = new Timer();
          mTimerTask = new TimerTask()
          {
            public void run()
            {
              onTimer();
            }
          };
          mPaused = false;
          mTimer.scheduleAtFixedRate(mTimerTask, 0, TIMER_TASK_INTERVAL);
        } catch (java.lang.IllegalStateException ex) {
          Log.e(LTAG, "Could not start timer: " + ex);
          // Ignore.
        }
      }
    });

    mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      public void onCompletion(MediaPlayer mp)
      {
        if (null == mListener) {
          return;
        }

        mListener.onProgress(STATE_FINISHED, mPlaybackProgress);
      }
    });

    mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
      public boolean onError(MediaPlayer mp, int what, int extra)
      {
        if (null == mListener) {
          return false;
        }

        mListener.onProgress(STATE_ERROR, 0f);
        return true;
      }
    });

    // Now try playing back!
    try {
      mMediaPlayer.setDataSource(mContext, boo.mHighMP3Url);
      mMediaPlayer.prepare();

      mMediaPlayer.start();
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Error playing back '" + boo.mHighMP3Url + "': " + ex);
      mMediaPlayer.release();
      mMediaPlayer = null;
      return;
    }

  }



  private void onTimer()
  {
    long current = System.currentTimeMillis();
    long diff = current - mTimestamp;
    mTimestamp = current;

    if (mPaused) {
      return;
    }

    // Log.d(LTAG, "timestamp: " + mTimestamp);
    // Log.d(LTAG, "diff: " + diff);
    // Log.d(LTAG, "progress: " + mPlaybackProgress);
    mPlaybackProgress += (double) diff / 1000.0;

    if (null != mListener) {
      mListener.onProgress(STATE_PLAYBACK, mPlaybackProgress);
    }
  }
}

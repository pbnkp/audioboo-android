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

import android.util.Log;

/**
 * Plays Boos.
 **/
public class BooPlayer extends Thread
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooPlayer";

  // Sleep time, if the thread's not woken.
  private static final int SLEEP_TIME = 60 * 1000;


  /***************************************************************************
   * Public constants
   **/
  // Three states: either buffering, or playing, or finished
  public static final int STATE_BUFFERING = 0;
  public static final int STATE_PLAYBACK  = 1;
  public static final int STATE_FINISHED  = 2;


  /***************************************************************************
   * Listener to state changes
   **/
  public static abstract class OnStateChangeListener
  {
    public abstract void onStateChanged(int state, float progress);
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
  private OnStateChangeListener mListener;

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



  public void setOnStateChangeListener(OnStateChangeListener listener)
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
  }



  private void stopPlaying(Boo boo)
  {
    Log.d(LTAG, "Stop playing: " + boo);
    if (null != mMediaPlayer) {
      mMediaPlayer.stop();
      mMediaPlayer.release();
      mMediaPlayer = null;
    }
  }



  private void startPlaying(Boo boo)
  {
    Log.d(LTAG, "Start playing: " + boo.mHighMP3Url);
    mMediaPlayer = new MediaPlayer();

    mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
      public void onBufferingUpdate(MediaPlayer mp, int percent)
      {
        if (null == mListener) {
          return;
        }

        if (100 == percent) {
          mListener.onStateChanged(STATE_PLAYBACK, 0f); // FIXME
        }
        else {
          mListener.onStateChanged(STATE_BUFFERING, 0f);
        }
      }
    });

    mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
      public void onPrepared(MediaPlayer mp)
      {
        if (null == mListener) {
          return;
        }

        mListener.onStateChanged(STATE_PLAYBACK, 0f); // FIXME
      }
    });


    mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      public void onCompletion(MediaPlayer mp)
      {
        if (null == mListener) {
          return;
        }

        mListener.onStateChanged(STATE_FINISHED, 0f); // FIXME
      }
    });


    try {
      mMediaPlayer.setDataSource(mContext, boo.mHighMP3Url);
      mMediaPlayer.prepare();
      mMediaPlayer.start();
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Error playing back '" + boo.mHighMP3Url + "': " + ex);
      mMediaPlayer.release();
      mMediaPlayer = null;
    }
  }
}

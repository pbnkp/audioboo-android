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
   * Base class for a tiny class hierarchy that lets us abstract some of the
   * logic differences between playing back MP3s (remote) and FLACs (local)
   * into subclasses. XXX Note that for simplicity, we always assume FLAC
   * files to be local; MP3s on the other hand are streamed from any URI the
   * underlying API can handle.
   **/
  private abstract class PlayerBase
  {
    // Pauses/resumes playback. Can only be called after start() has been
    // called.
    abstract void pause();
    abstract void resume();

    // Sets up the player and starts playback.
    abstract void start(Boo boo);

    // Stops playback and also releases player resources; after this call
    // pause()/resume() won't work any longer.
    abstract void stop();
  }


  /***************************************************************************
   * Player for MP3 files; since it can play more than MP3s through the
   * underlying Android API, we'll call it API player.
   **/
  private class APIPlayer extends PlayerBase
  {
    // Player API
    private MediaPlayer mMediaPlayer;


    void pause()
    {
      if (null != mMediaPlayer) {
        mMediaPlayer.pause();
      }
    }



    void resume()
    {
      if (null != mMediaPlayer) {
        mMediaPlayer.start();
      }
    }



    void start(Boo boo)
    {
      // Log.d(LTAG, "Start playing: " + boo.mHighMP3Url);
      mMediaPlayer = new MediaPlayer();

      // Attach listeners to the player, for propagating state up to the users of this
      // class.
      mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
          if (mp.isPlaying()) {
            sendStatePlayback();
          }
          else {
            sendStateBuffering();
          }
        }
      });

      mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp)
        {
          mp.start();
          startPlaybackState();
        }
      });

      mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp)
        {
          sendStateEnded();
        }
      });

      mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
          sendStateError();
          return true;
        }
      });

      // Now try playing back!
      try {
        // Log.d(LTAG, "setting data source");
        mMediaPlayer.setDataSource(mContext, boo.mHighMP3Url);
        mMediaPlayer.prepareAsync();

      } catch (java.io.IOException ex) {
        Log.e(LTAG, "Error playing back '" + boo.mHighMP3Url + "': " + ex);
        mMediaPlayer.release();
        mMediaPlayer = null;
        return;
      }
    }



    void stop()
    {
      // Log.d(LTAG, "Stop playing: " + boo);
      if (null != mMediaPlayer) {
        if (mMediaPlayer.isPlaying()) {
          mMediaPlayer.stop();
        }
        mMediaPlayer.release();
        mMediaPlayer = null;
      }
    }
  }



  /***************************************************************************
   * Player for FLAC files, which are assumed to be local
   **/
  private class FLACPlayerWrapper extends PlayerBase
  {
    // Player instance
    private FLACPlayer  mFlacPlayer;


    void pause()
    {
      if (null != mFlacPlayer) {
        mFlacPlayer.pausePlayback();
      }
    }



    void resume()
    {
      if (null != mFlacPlayer) {
        mFlacPlayer.resumePlayback();
      }
    }



    void start(Boo boo)
    {
      String filename = boo.mHighMP3Url.getPath();
      mFlacPlayer = new FLACPlayer(mContext, filename);

      mFlacPlayer.setListener(new FLACPlayer.PlayerListener() {
        public void onError()
        {
          sendStateError();
        }


        public void onFinished()
        {
          sendStateEnded();
        }
      });

      mFlacPlayer.start();

      // There's no buffering for local files, so we'll
      // immediately enter playback state.
      startPlaybackState();
    }



    void stop()
    {
      mFlacPlayer.mShouldRun = false;
      mFlacPlayer.interrupt();
      mFlacPlayer = null;
    }
  }



  /***************************************************************************
   * Private data
   **/
  // Context in which this object was created
  private Context               mContext;

  // Boo that's currently being played
  private Object                mBooLock = new Object();
  private Boo                   mBoo;

  // Player instance.
  private PlayerBase            mPlayer;

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
    if (null != mPlayer) {
      mPlayer.pause();
    }
    mPaused = true;
  }



  public void resumePlaying()
  {
    if (null != mPlayer) {
      mPlayer.resume();
    }
    mPaused = false;
  }



  public boolean hasStarted()
  {
    return (mPlayer != null);
  }



  public void setProgressListener(ProgressListener listener)
  {
    mListener = listener;
  }



  /**
   * Thread's run function.
   **/
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
        //Log.d(LTAG, "Should play: " + currentBoo);
        //Log.d(LTAG, "Playing: " + playingBoo);

        if (currentBoo != playingBoo) {
          if (null != playingBoo) {
            stopPlayingInternal();
          }

          playingBoo = currentBoo;

          if (null != playingBoo) {
            startPlayingInternal(playingBoo);
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



  /**
   * Used in the run() function; stops playback and tears down the player
   * instance.
   **/
  private void stopPlayingInternal()
  {
    if (null != mPlayer) {
      mPlayer.stop();
      mPlayer = null;
    }

    if (null != mTimer) {
      mTimer.cancel();
      mTimer = null;
      mTimerTask = null;
      mPaused = true;
    }
  }



  /**
   * Used in the run() function; sets up the player instance and starts
   * playback.
   **/
  private void startPlayingInternal(Boo boo)
  {
    // Examine the Boo's Uri. From that we determine what player to instanciate.
    String path = boo.mHighMP3Url.getPath();
    int ext_sep = path.lastIndexOf(".");
    String ext = path.substring(ext_sep).toLowerCase();

    if (ext.equals(".flac")) {
      // Start FLAC player.
      mPlayer = new FLACPlayerWrapper();
    }
    else {
      // Handle everything else via the APIPlayer
      mPlayer = new APIPlayer();
    }

    // Now we can use the base API to start playback.
    mPlayer.start(boo);
  }



  /**
   * Switches the progress listener to playback state, and starts the timer
   * that'll inform the listener on a regular basis that progress is being made.
   **/
  private void startPlaybackState()
  {
    //Log.d(LTAG, "Starting playback state.");

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
      sendStateError();
    }
  }



  /**
   * Notifies the listener that playback has ended.
   **/
  private void sendStateEnded()
  {
    // Log.d(LTAG, "Send end state");

    if (null == mListener) {
      return;
    }

    mListener.onProgress(STATE_FINISHED, mPlaybackProgress);
  }



  /**
   * Notifies the listener that the player is in buffering state.
   **/
  private void sendStateBuffering()
  {
    //Log.d(LTAG, "Send buffering state");

    if (null == mListener) {
      return;
    }

    mListener.onProgress(STATE_BUFFERING, 0f);
  }



  /**
   * Notifies the listener that the player is playing back; this is really
   * only used after a sendStateBuffering() has been sent. Under normal
   * conditions, onTimer() below sends updates.
   **/
  private void sendStatePlayback()
  {
    //Log.d(LTAG, "Send playback state");

    if (null == mListener) {
      return;
    }

    mListener.onProgress(STATE_PLAYBACK, mPlaybackProgress);
  }



  /**
   * Sends an error state to the listener.
   **/
  private void sendStateError()
  {
    //Log.d(LTAG, "Send error state");

    if (null == mListener) {
      return;
    }

    mListener.onProgress(STATE_ERROR, 0f);
  }



  /**
   * Invoked periodically; tracks progress and notifies the listener
   * accordingly.
   **/
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

    sendStatePlayback();
  }
}

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

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Plays Boos. Abstracts out all the differences between streaming MP3s from the
 * web and playing local FLAC files.
 **/
public class BooPlayer extends Thread
{
  /***************************************************************************
   * Public constants
   **/
  // The player state machine is complicated by the way the Android API's
  // MediaPlayer changes states. Suffice to say that while the player is
  // preparing, no other action can be taken immediately.
  // We solve that problem not by blocking until the player has finished
  // preparing, but by introducing a pending state. BooPlayer's functions
  // only set the pending state; once the player has finished preparing,
  // that pending state is entered.
  //
  // Some of these states are pseudo-states in that they do not affect
  // state transitions. In particular, STATE_ERROR and STATE_NONE are both
  // states from which the same transitions can be made. The same applies to
  // STATE_PLAYING and STATE_BUFFERING.
  //
  // Those states exist because they are of interest to the ProgressListener.
  // The ProgressListener will be sent the following states as they are entered
  // - STATE_PLAYING
  // - STATE_BUFFERING
  // - STATE_FINISHED aka STATE_NONE
  // - STATE_ERROR
  public static final int STATE_NONE            = 0;
  public static final int STATE_PREPARING       = 1;
  public static final int STATE_PAUSED          = 2;
  public static final int STATE_PLAYING         = 3;

  public static final int STATE_FINISHED        = STATE_NONE;
  public static final int STATE_BUFFERING       = 6;
  public static final int STATE_ERROR           = 7;


  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG              = "BooPlayer";

  // Sleep time, if the thread's not woken.
  private static final int SLEEP_TIME           = 60 * 1000;

  // Interval at which we notify the user of playback progress (msec)
  private static final int TIMER_TASK_INTERVAL  = 500;

  // State machine transitions.
  private static final int T_ERROR              = -3;
  private static final int T_IGNORE             = -2;
  private static final int T_NONE               = -1;
  private static final int T_PREPARE            = 0;
  private static final int T_RESUME             = 1;
  private static final int T_STOP               = 2;
  private static final int T_PAUSE              = 3;
  private static final int T_RESET              = 4;
  private static final int T_START              = 5;

  // Decision matrix - how to get from one state to the other.
  // XXX The indices correspond to values of the first four STATE_ constants,
  //     so don't change the constant values.
  // Read row index (first) as the current state, column index (second) as
  // the desired state.
  private static final int STATE_DECISION_MATRIX[][] = {
    { T_NONE,   T_PREPARE,  T_PREPARE,  T_START,  },
    { T_IGNORE, T_NONE,     T_IGNORE,   T_IGNORE, },
    { T_STOP,   T_RESET,    T_NONE,     T_RESUME, },
    { T_STOP,   T_RESET,    T_PAUSE,    T_NONE,   },
  };

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
    // Sets up the player.
    abstract boolean prepare(Boo boo);

    // Pauses/resumes playback. Can only be called after start() has been
    // called.
    abstract void pause();
    abstract void resume();

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


    public boolean prepare(Boo boo)
    {
      Context ctx = mContext.get();
      if (null == ctx) {
        Log.e(LTAG, "Context is dead, won't play.");
        return false;
      }

      // Prepare player
      mMediaPlayer = MediaPlayer.create(ctx, boo.mHighMP3Url);
      if (null == mMediaPlayer) {
        Log.e(LTAG, "Could not start playback of URI: " + boo.mHighMP3Url);
        return false;
      }

      // Attach listeners to the player, for propagating state up to the users of this
      // class.
      mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent)
        {
          // If the player is playing/buffering, and that changed, we want to
          // switch to the opposite state. If the player is not playing, we don't.
          int state = STATE_NONE;
          if (mp.isPlaying()) {
            state = STATE_PLAYING;
          }
          else {
            state = STATE_BUFFERING;
          }

          boolean doInterrupt = false;
          synchronized (mLock)
          {
            // Strictly speaking, the mState != state check happens in the run()
            // function, but why interrupt more than we really need?
            if (mState != state
              && ((STATE_PLAYING == mState)
                || (STATE_BUFFERING == mState)))
            {
              mPendingState = state;
              doInterrupt = true;
            }
          }
          if (doInterrupt) {
            interrupt();
          }
        }
      });

      mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp)
        {
          stopPlaying();
        }
      });


      mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int what, int extra)
        {
          synchronized (mLock)
          {
            mPendingState = STATE_ERROR;
          }
          interrupt();
          return true;
        }
      });

      return true;
    }



    public void pause()
    {
      mMediaPlayer.pause();
    }



    public void resume()
    {
      mMediaPlayer.start();
    }



    public void stop()
    {
      mMediaPlayer.stop();
      mMediaPlayer.release();
      mMediaPlayer = null;
    }
  }



  /***************************************************************************
   * Player for FLAC files, which are assumed to be local
   **/
  private class FLACPlayerWrapper extends PlayerBase
  {
    // Player instance
    private FLACPlayer  mFlacPlayer;


    public boolean prepare(Boo boo)
    {
      Context ctx = mContext.get();
      if (null == ctx) {
        Log.e(LTAG, "Context is dead, won't play.");
        return false;
      }

      // Flatten audio file before we can start playback. This call will return
      // quickly if the file is already flattend, and will block while flattening.
      boo.flattenAudio();

      // Start playback
      String filename = boo.mHighMP3Url.getPath();
      mFlacPlayer = new FLACPlayer(ctx, filename);

      mFlacPlayer.setListener(new FLACPlayer.PlayerListener() {
        public void onError()
        {
          synchronized (mLock)
          {
            mPendingState = STATE_ERROR;
          }
          interrupt();
        }


        public void onFinished()
        {
          stopPlaying();
        }
      });

      mFlacPlayer.start();

      return true;
    }



    public void pause()
    {
      mFlacPlayer.pausePlayback();
    }



    public void resume()
    {
      mFlacPlayer.resumePlayback();
    }



    public void stop()
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
  private WeakReference<Context>  mContext;

  // Lock.
  private Object                mLock = new Object();

  // Player instance.
  private PlayerBase            mPlayer;

  // Player for MP3 Boos streamed from the Web.
  private MediaPlayer           mMediaPlayer;

  // Listener for state changes
  private ProgressListener      mListener;

  // Tick timer, for tracking progress
  private Timer                 mTimer;
  private TimerTask             mTimerTask;

  // Internal player state
  private int                   mState = STATE_NONE;
  private int                   mPendingState = STATE_NONE;
  private boolean               mResetState;

  // Boo that's currently being played
  private Boo                   mBoo;

  // Used for progress tracking
  private double                mPlaybackProgress;
  private long                  mTimestamp;

  /***************************************************************************
   * Implementation
   **/
  public BooPlayer(Context context)
  {
    mContext = new WeakReference<Context>(context);
  }



  /**
   * Prepares the internal player with the given Boo. Starts playback
   * immediately.
   **/
  public void play(Boo boo)
  {
    synchronized (mLock)
    {
      if (null != mBoo && mBoo != boo) {
        mResetState = true;
      }
      mBoo = boo;
      mPendingState = STATE_PLAYING;
    }
    interrupt();
  }



  /**
   * Ends playback. Playback cannot be resumed after this function is called.
   **/
  public void stopPlaying()
  {
    // Log.d(LTAG, "stop from outside");
    synchronized (mLock)
    {
      mPendingState = STATE_NONE;
    }
    interrupt();
  }



  /**
   * Pauses playback. Playback can be resumed.
   **/
  public void pausePlaying()
  {
    synchronized (mLock)
    {
      mPendingState = STATE_PAUSED;
    }
    interrupt();
  }



  public void resumePlaying()
  {
    synchronized (mLock)
    {
      mPendingState = STATE_PLAYING;
    }
    interrupt();
  }



  public int getPlaybackState()
  {
    synchronized (mLock)
    {
      return mState;
    }
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
    mResetState = false;

    Boo playingBoo = null;
    while (mShouldRun)
    {
      try {
        // Figure out the action to take from here. This needs to be done under lock
        // so relevant data won't change under our noses.
        Boo currentBoo = null;
        int currentState = STATE_ERROR;
        int pendingState = STATE_NONE;
        int action = T_NONE;

        boolean reset = false;

        synchronized (mLock)
        {
          currentBoo = mBoo;
          currentState = mState;
          pendingState = mPendingState;

          // If the current and pending Boo don't match, then we need to reset
          // the state machine. Then we'll go from there.
          reset = mResetState;
          if (mResetState) {
            mResetState = false;
            currentState = STATE_NONE;
          }

          // If the next state is to be an error state, let's not bother with
          // trying to find out what to do next - we want to stop.
          if (STATE_ERROR == pendingState) {
            action = T_STOP;
          }
          else {
            action = STATE_DECISION_MATRIX[normalizeState(currentState)][normalizeState(pendingState)];
          }

          // We also set the new state here. This is primarily done because
          // STATE_PREPARING should be set as soon as possible, but it doesn't
          // hurt for the others either.
          // Strictly speaking, we're inviting a race here: once mLock has been
          // released, the thread could be interrupted before the appropriate action
          // to effect the state change can be taken. That, however, does not matter
          // because the next time the decision matrix is consulted, this "lost"
          // state is recovered.
          // By setting the pendingState (in most cases) to be identical to mState,
          // we effectively achieve that the next interrupt() should result in
          // T_NONE.
          switch (action) {
            case T_IGNORE:
            case T_NONE:
              break;

            case T_PREPARE:
            case T_RESET:
              mState = mPendingState = STATE_PREPARING;
              break;

            case T_RESUME:
              mState = mPendingState = STATE_PLAYING;
              break;

            case T_STOP:
              mState = mPendingState = STATE_NONE;
              break;

            case T_PAUSE:
              mState = mPendingState = STATE_PAUSED;
              break;

            case T_START:
              // For this action only, we set a new pending state. Once
              // preparing has finished, interrupt() will be invoked.
              mState = STATE_PREPARING;
              mPendingState = STATE_PLAYING;
              break;

            default:
              Log.e(LTAG, "Unknown action: " + action);
              pendingState = STATE_ERROR;
              mShouldRun = false;
              continue;
          }
        }
        // Log.d(LTAG, String.format("State change: %d -> %d : %d", currentState, pendingState, action));

        // If we need to reset the state machine, let's do so now.
        if (reset) {
          stopInternal(false);
        }

        // If the pending state is an error state, also send an error state
        // to listeners.
        if (STATE_ERROR == pendingState) {
          sendStateError();
        }

        // Now perform the appropriate action to attain the new state.
        performAction(action, currentBoo);

        // Sleep until the next interrupt occurs.
        sleep(SLEEP_TIME);
      } catch (InterruptedException ex) {
        // pass
      }
    }

    // Finally we have to transition to an ended state.
    int action = T_NONE;
    synchronized (mLock)
    {
      action = STATE_DECISION_MATRIX[normalizeState(mState)][STATE_NONE];
      mState = mPendingState = STATE_NONE;
    }
    performAction(action, null);
  }



  private void performAction(int action, Boo boo)
  {
    switch (action) {
      case T_IGNORE:
      case T_NONE:
        // T_IGNORE and T_NONE are technically different actions: in T_NONE
        // the pending and current state are identical, whereas T_IGNORE
        // simply ignores the state change for now. Either way, we do nothing
        // here.
        break;

      case T_PREPARE:
      case T_START:
        // We need to prepare the player. For that, boo needs to be non-null.
        prepareInternal(boo);
        break;

      case T_RESUME:
        resumeInternal();
        break;

      case T_STOP:
        stopInternal(true);
        break;

      case T_PAUSE:
        pauseInternal();
        break;

      case T_RESET:
        // A reset is identical to stop followed by prepare. The boo needs
        // to be non-null.
        stopInternal(false);
        prepareInternal(boo);
        break;

      default:
        Log.e(LTAG, "Unknown action: " + action);
        break;
    }
  }



  /**
   * Helper function. "Normalizes" a state value insofar as it factors out semi-
   * states.
   **/
  private int normalizeState(int state)
  {
    if (STATE_BUFFERING == state) {
      return STATE_PLAYING;
    }
    if (STATE_ERROR == state) {
      return STATE_NONE;
    }
    return state;
  }





  private void prepareInternal(Boo boo)
  {
    if (null == boo) {
      Log.e(LTAG, "Prepare without boo!");
      synchronized (mLock)
      {
        mPendingState = STATE_ERROR;
      }
      interrupt();
      return;
    }

    sendStateBuffering();

    // Local Boos are treated via the FLACPlayerWrapper.
    if (boo.isLocal()) {
      mPlayer = new FLACPlayerWrapper();
    }
    else {
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
    }

    // Now we can use the base API to start playback.
    boolean result = mPlayer.prepare(boo);
    synchronized (mLock)
    {
      if (result) {
        // Once that returns, we set the current state to PAUSED. While we were in
        // PREPARING state, no other state changes could be effected, so that's safe.
        // We also interrupt() again, to let the thread figure out if there's a
        // pending state after this.
        mState = STATE_PAUSED;
      }
      else {
        mPendingState = STATE_ERROR;
      }
    }
    interrupt();
  }



  private void pauseInternal()
  {
    mPlayer.pause();
    sendStateBuffering();
  }



  private void resumeInternal()
  {
    mPlayer.resume();

    if (null == mTimer) {
      startPlaybackState();
    }
    else {
      sendStatePlayback();
    }
  }



  private void stopInternal(boolean sendState)
  {
    if (null != mPlayer) {
      mPlayer.stop();
      mPlayer = null;
    }

    if (null != mTimer) {
      mTimer.cancel();
      mTimer = null;
      mTimerTask = null;
    }

    synchronized (mLock)
    {
      mBoo = null;
    }

    // Log.d(LTAG, "sending State ended: " + sendState);
    if (sendState) {
      sendStateEnded();
    }
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

    // If we made it here, then we'll start a tick timer for sending
    // continuous progress to the users of this class.
    mPlaybackProgress = 0f;
    mTimestamp = System.currentTimeMillis();

    mListener.onProgress(STATE_PLAYING, mPlaybackProgress);

    try {
      mTimer = new Timer();
      mTimerTask = new TimerTask()
      {
        public void run()
        {
          onTimer();
        }
      };
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

    mListener.onProgress(STATE_PLAYING, mPlaybackProgress);
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

    synchronized (mLock)
    {
      if (STATE_PLAYING != mState) {
        return;
      }
    }

    // Log.d(LTAG, "timestamp: " + mTimestamp);
    // Log.d(LTAG, "diff: " + diff);
    // Log.d(LTAG, "progress: " + mPlaybackProgress);
    mPlaybackProgress += (double) diff / 1000.0;

    sendStatePlayback();
  }
}

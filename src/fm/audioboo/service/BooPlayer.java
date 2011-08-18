/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * Copyright (C) 2010,2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.content.Context;

import java.util.TimerTask;
import java.util.Timer;

import java.lang.ref.WeakReference;

import fm.audioboo.data.PlayerState;
import fm.audioboo.application.Boo;

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
  private static final String LTAG              = "BooPlayer";

  // Sleep time, if the thread's not woken.
  private static final int SLEEP_TIME_LONG      = 60 * 1000;
  // Sleep time for retrying an action
  private static final int SLEEP_TIME_SHORT     = 251;

  // Interval at which we notify the user of playback progress (msec)
  private static final int TIMER_TASK_INTERVAL  = 500;

  // State machine transitions.
  private static final int T_ERROR              = -2;
  private static final int T_NONE               = -1;
  private static final int T_PREPARE            = 0;
  private static final int T_RESUME             = 1;
  private static final int T_STOP               = 2;
  private static final int T_PAUSE              = 3;
  private static final int T_RESET              = 4;

  // Decision matrix - how to get from one state to the other.
  // XXX The indices correspond to values of the first four STATE_ constants,
  //     so don't change the constant values.
  // Read row index (first) as the current state, column index (second) as
  // the desired state.
  private static final int STATE_DECISION_MATRIX[][] = {
    { T_NONE,   T_PREPARE,  T_PREPARE,  T_PREPARE,  T_STOP, },
    { T_NONE,   T_NONE,     T_NONE,     T_NONE,     T_NONE, },
    { T_STOP,   T_RESET,    T_NONE,     T_RESUME,   T_STOP, },
    { T_STOP,   T_RESET,    T_PAUSE,    T_NONE,     T_STOP, },
    { T_STOP,   T_RESET,    T_RESET,    T_RESET,    T_NONE, },
  };


  /***************************************************************************
   * Listens to whether or not the player is active/inactive.
   **/
  public static interface ActivityListener
  {
    public void updateActivity(boolean active);
  }


  /***************************************************************************
   * Public data
   **/
  // Flag that keeps the thread running when true.
  public volatile boolean mShouldRun;



  /***************************************************************************
   * Private data
   **/
  // Context in which this object was created
  private WeakReference<Context>  mContext;

  // Lock.
  private Object                mLock         = new Object();

  // Player instance.
  private volatile PlayerBase   mPlayer;

  // Tick timer, for tracking progress
  private volatile Timer        mTimer;

  // Internal player state
  private volatile int          mState        = Constants.STATE_NONE;
  private volatile int          mTargetState  = Constants.STATE_NONE;
  private volatile boolean      mResetState;

  // Boo that's currently being played
  private volatile Boo          mBoo;

  // Used for seeking
  private double                mSeekTo = -1;

  // Activity listener.
  private WeakReference<ActivityListener> mListener;


  /***************************************************************************
   * Public Interface
   **/
  public BooPlayer(Context ctx)
  {
    mContext = new WeakReference<Context>(ctx);
  }



  public BooPlayer(Context ctx, ActivityListener listener)
  {
    mContext = new WeakReference<Context>(ctx);
    mListener = new WeakReference<ActivityListener>(listener);
  }



  public Context getContext()
  {
    return mContext.get();
  }



  public Object getLock()
  {
    return mLock;
  }



  public Boo getBoo()
  {
    synchronized (mLock) {
      return mBoo;
    }
  }



  /**
   * Prepares the internal player with the given Boo. Starts playback
   * immediately.
   **/
  public void play(Boo boo, boolean playImmediately)
  {
    //Log.d(LTAG, "Asked to play: " + boo + " / " + playImmediately);
    //Thread.dumpStack();
    synchronized (mLock)
    {
      if (null == boo || null == boo.mData) {
        mBoo = null;
        mTargetState = Constants.STATE_ERROR;
      }
      else {
        mBoo = boo;
        mTargetState = playImmediately ? Constants.STATE_PLAYING : Constants.STATE_PAUSED;
      }
      mResetState = true;
    }
    interrupt();
  }



  /**
   * Seek to given position. Must be >= 0 and < getDuration()
   **/
  public void seekTo(double position)
  {
    synchronized (mLock)
    {
      mSeekTo = position;
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
      mTargetState = Constants.STATE_FINISHED;
    }
    interrupt();
  }



  /**
   * Pauses playback. Playback can be resumed.
   **/
  public void pausePlaying()
  {
    // Log.d(LTAG, "pause playing?");
    synchronized (mLock)
    {
      mTargetState = Constants.STATE_PAUSED;
    }
    interrupt();
  }



  public void resumePlaying()
  {
    synchronized (mLock)
    {
      mTargetState = Constants.STATE_PLAYING;
    }
    interrupt();
  }



  public PlayerState getPlayerState()
  {
    PlayerState s = new PlayerState();

    synchronized (mLock) {
      s.mState = mState;
      if (null == mPlayer) {
        s.mProgress = 0f;
      }
      else {
        s.mProgress = mPlayer.getPosition() / 1000f;
      }

      if (null != mBoo && null != mBoo.mData) {
        s.mTotal = mBoo.getDuration();
        s.mBooId = mBoo.mData.mId;
        s.mBooTitle = mBoo.mData.mTitle;
        s.mBooUsername = null == mBoo.mData.mUser ? null : mBoo.mData.mUser.mUsername;
        s.mBooIsMessage = mBoo.mData.mIsMessage;
        s.mBooIsLocal = mBoo.isLocal();
      }
    }

    return s;
  }



  /**
   * Used internally, but safe to use from the outside.
   **/
  public void setErrorState()
  {
    // Log.d(LTAG, "setting error state");
    synchronized (mLock) {
      mTargetState = Constants.STATE_ERROR;
    }
    interrupt();
  }



  /**
   * Flip STATE_PLAYING to STATE_BUFFERING and vice versa. Only has an effect
   * if the given state is either one of the two, the current state is either
   * one of the two, and the current and given states differ.
   **/
  public void flipBufferingState(int state)
  {
    if (Constants.STATE_PLAYING != state
        && Constants.STATE_BUFFERING != state)
    {
      Log.e(LTAG, "Invalid parameter for flipBufferingState: " + state);
      return;
    }

    boolean doInterrupt = false;
    synchronized (mLock) {
      if (Constants.STATE_PLAYING != mState
          && Constants.STATE_BUFFERING != mState)
      {
        return;
      }

      if (mState != state) {
        mState = state;
        doInterrupt = true;
      }
    }
    if (doInterrupt) {
      interrupt();
    }
  }



  /***************************************************************************
   * Private implementation
   **/

  /**
   * XXX Used internally, don't use from the outside. IF the current state is
   * STATE_PREPARING, advances the state into STATE_PAUSED.
   **/
  public void prepareSucceeded()
  {
    synchronized (mLock) {
      prepareSucceededUnlocked();
    }
    interrupt();
  }

  public void prepareSucceededUnlocked()
  {
    if (Constants.STATE_PREPARING != mState) {
      return;
    }

    mState = Constants.STATE_PAUSED;
  }



  /**
   * Thread's run function.
   **/
  public void run()
  {
    mShouldRun = true;
    mResetState = false;

    while (mShouldRun)
    {
      try {
        // The result of the action performed will influence sleep time.
        boolean sleep_long;

        synchronized (mLock)
        {
          // 1. Get current state
          Boo currentBoo = mBoo;
          int currentState = mState;
          int targetState = mTargetState;
          double seekTo = mSeekTo;
          // Log.d(LTAG, "#1 currentBoo: " + currentBoo + " - currentState: " + currentState + " - targetState: " + targetState);

          // 2. If we're supposed to reset the state machine, let's do so now.
          //    We also set the current state to STATE_NONE so that the rest
          //    of the state machine can function normally.
          if (mResetState) {
            stopUnlocked();
            mResetState = false;

            // If the target state is the error state, we'll also pre-empt any
            // further processing in this loop, and skip right to the next
            // iteration. Note that this leaves the pending state in the
            // error condition, too, meaning we can only exit the error state
            // through a reset from the outside, i.e. a call to play().
            if (Constants.STATE_ERROR == targetState) {
              mState = Constants.STATE_ERROR;
              continue;
            }

            // If the pending state is anything else, we'll assume that the
            // current state is STATE_NONE, for simplicity's sake.
            currentState = Constants.STATE_NONE;
          }

          // Log.d(LTAG, "#2 currentBoo: " + currentBoo + " - currentState: " + currentState + " - targetState: " + targetState);

          // 3. Figure out the action that might take us to the target state
          //    (this doesn't have to happen immediately.)
          //    If the current state is STATE_ERROR, we'll ignore this part and
          //    don't do anything. That forces the caller to use play() to reset
          //    the state machine.
          int action = T_NONE;
          if (Constants.STATE_ERROR == currentState) {
            action = T_NONE;
          }
          else {
            action = STATE_DECISION_MATRIX[normalizeState(currentState)][normalizeState(targetState)];
          }
          // Log.d(LTAG, "#3 currentBoo: " + currentBoo + " - currentState: " + currentState + " - targetState: " + targetState + " - action: " + action);

          // 4. Perform the action. Note that all action functions will
          //    change mState if
          //    a) They successfully changed state (which not every function must
          //       do), or
          //    b) They encountered an error.
          sleep_long = performAction(action, currentState, targetState, currentBoo, seekTo);
        }

        // Now we're back out of the lock, we'll sleep.
        if (sleep_long) {
          // Sleep until the next interrupt occurs.
          sleep(SLEEP_TIME_LONG);
        }
        else {
          // Sleep for a short time, then try again.
          sleep(SLEEP_TIME_SHORT);
        }
      } catch (InterruptedException ex) {
        // pass
      }
    }

    // Finally we have to transition to an ended state.
    synchronized (mLock)
    {
      int action = STATE_DECISION_MATRIX[normalizeState(mState)][Constants.STATE_NONE];
      performAction(action, mState, Constants.STATE_NONE, null, -1f);
    }
  }



  private boolean performAction(int action, int state, int targetState, Boo boo,
      double seekTo)
  {
    boolean sleep_long = true;

    switch (action) {
      case T_NONE:
        // In most cases, do nothing. We're pretty much waiting for stuff to happen.
        // However, if the current state is PLAYING, and we've been asked to seek,
        // we can do so.
        if (Constants.STATE_PLAYING == state && seekTo > 0) {
          sleep_long = seekInternal(seekTo);
        }
        break;

      case T_PREPARE:
        // We need to prepare the player. For that, boo needs to be non-null.
        prepareInternal(boo);
        sleep_long = false;
        break;

      case T_RESUME:
        sleep_long = resumeInternal();
        break;

      case T_STOP:
        stopUnlocked();
        mState = targetState;
        break;

      case T_PAUSE:
        pauseInternal();
        break;

      case T_RESET:
        stopUnlocked();
        prepareInternal(boo);
        sleep_long = false;
        break;

      default:
        Log.e(LTAG, "Unknown action: " + action);
        break;
    }

    return sleep_long;
  }



  /**
   * Helper function. "Normalizes" a state value insofar as it factors out semi-
   * states.
   **/
  private int normalizeState(int state)
  {
    switch (state) {
      case Constants.STATE_BUFFERING:
        return Constants.STATE_PLAYING;

      case Constants.STATE_ERROR:
        return Constants.STATE_NONE;

      default:
        return state;
    }
  }




  private void prepareInternal(Boo boo)
  {
    // Log.d(LTAG, "prepare internal: " + boo);
    if (null == boo || null == boo.mData) {
      Log.e(LTAG, "Prepare without boo!");
      mState = Constants.STATE_ERROR;
      return;
    }
    mState = Constants.STATE_PREPARING;

    // Local Boos are treated via the FLACPlayerWrapper.
    if (boo.isIntro()) {
      // Intro needs the API player.
      mPlayer = new APIPlayer(this);
    }
    else if (boo.isLocal()) {
      mPlayer = new FLACPlayerWrapper(this);
    }
    else if (boo.isRemote()) {
      // Handle everything else via the APIPlayer
      mPlayer = new APIPlayer(this);
    }
    else {
      // Not sure what to do here, exactly.
      Log.e(LTAG, "Boo " + boo + " appears to be neither local nor remote. Huh.");
      mState = Constants.STATE_ERROR;
      return;
    }

    // Now we can use the base API to start playback.
    boolean result = mPlayer.prepare(boo);
    if (!result) {
      mState = Constants.STATE_ERROR;
    }
  }



  private void pauseInternal()
  {
    mPlayer.pause();
    mState = Constants.STATE_PAUSED;

    if (null != mTimer) {
      onTimer(false);
      mTimer.cancel();
      mTimer = null;
    }
  }



  private boolean resumeInternal()
  {
    // Log.d(LTAG, "resume internal");
    if (mPlayer.resume()) {
      // Log.d(LTAG, "resume succeeded");
      mState = Constants.STATE_PLAYING;
      return resumeCountingProgress();
    }
    // Log.d(LTAG, "resume failed");

    mState = Constants.STATE_ERROR;
    return false;
  }



  private boolean seekInternal(double position)
  {
    mPlayer.seekTo((long) (position * 1000));
    return true;
  }



  private void stopUnlocked()
  {
    if (null != mPlayer) {
      mPlayer.stop();
      mPlayer = null;
    }

    if (null != mTimer) {
      onTimer(false);
      mTimer.cancel();
      mTimer = null;
    }
  }



  /**
   * Switches the progress listener to playback state, and starts the timer
   * that'll inform the listener on a regular basis that progress is being made.
   **/
  private boolean resumeCountingProgress()
  {
    //Log.d(LTAG, "Starting playback state.");

    try {
      mTimer = new Timer();
      TimerTask task = new TimerTask()
      {
        public void run()
        {
          onTimer(true);
        }
      };
      mTimer.scheduleAtFixedRate(task, 0, TIMER_TASK_INTERVAL);
    } catch (java.lang.IllegalStateException ex) {
      Log.e(LTAG, "Could not start timer: " + ex);
      mState = Constants.STATE_ERROR;
      return false;
    }

    return true;
  }



  /**
   * Invoked periodically; tracks progress and notifies the listener
   * accordingly.
   **/
  private void onTimer(boolean fromTimer)
  {
    // Notify listener, if it exists.
    if (null == mListener) {
      return;
    }
    ActivityListener listener = mListener.get();
    if (null == listener) {
      return;
    }
    // Since the timer is only running when stuff is active, the
    // fromTimer flag already tells us whether the player is active
    // or not.
    listener.updateActivity(fromTimer);
  }



  /**
   * Helper; get duration if a Boo is set
   **/
  public double getDuration()
  {
    synchronized (mLock)
    {
      if (null != mBoo) {
        return mBoo.getDuration();
      }
    }
    return 0f;
  }
}

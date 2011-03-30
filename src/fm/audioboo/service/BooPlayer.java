/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
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

import fm.audioboo.data.PersistentPlaybackState; // FIXME PlayerState
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
    { T_IGNORE, T_NONE,     T_IGNORE,   T_RESUME, },
    { T_STOP,   T_RESET,    T_NONE,     T_RESUME, },
    { T_STOP,   T_RESET,    T_PAUSE,    T_NONE,   },
  };


  /***************************************************************************
   * Listener to state changes
   **/
  public static interface ProgressListener
  {
    public void onProgress(PlayerState state);
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
  private WeakReference<Context>  mContext;

  // Lock.
  private Object                mLock         = new Object();

  // Player instance.
  private volatile PlayerBase   mPlayer;

  // Listener for state changes
  private ProgressListener      mListener;

  // Tick timer, for tracking progress
  private volatile Timer        mTimer;

  // Internal player state
  private volatile int          mState        = Constants.STATE_NONE;
  private volatile int          mPendingState = Constants.STATE_NONE;
  private volatile boolean      mResetState;

  // Boo that's currently being played
  private volatile Boo          mBoo;

  // Used for progress tracking
  private double                mPlaybackProgress;
  private long                  mTimestamp;



  /***************************************************************************
   * Implementation
   **/
  public BooPlayer(Context ctx)
  {
    mContext = new WeakReference<Context>(ctx);
  }



  public Context getContext()
  {
    return mContext.get();
  }



  public Object getLock()
  {
    return mLock;
  }



  /**
   * Set progress listener
   **/
  void setProgressListener(ProgressListener listener)
  {
    mListener = listener;
  }



  /**
   * Prepares the internal player with the given Boo. Starts playback
   * immediately.
   **/
  public void play(Boo boo, boolean playImmediately)
  {
    // Log.d(LTAG, "Asked to play: " + boo + " / " + playImmediately);
    synchronized (mLock)
    {
      if (null == boo || null == boo.mData) {
        mBoo = null;
        mPendingState = Constants.STATE_NONE;
      }
      else {
        mBoo = boo;
        mPendingState = playImmediately ? Constants.STATE_PLAYING : Constants.STATE_PAUSED;
      }
      mResetState = true;
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
      mPendingState = Constants.STATE_NONE;
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
      mPendingState = Constants.STATE_PAUSED;
    }
    interrupt();
  }



  public void resumePlaying()
  {
    synchronized (mLock)
    {
      mPendingState = Constants.STATE_PLAYING;
    }
    interrupt();
  }



  public int getPlaybackState()
  {
    synchronized (mLock)
    {
      return getPlaybackStateUnlocked();
    }
  }



  public PlayerState getPlayerState()
  {
    PlayerState s = new PlayerState();

    synchronized (mLock) {
      s.mState = mState;
      s.mProgress = mPlaybackProgress;
      s.mTotal = getDuration();
      s.mBooId = getBooIdInternal();
      s.mBooTitle = getTitleInternal();
      s.mBooUsername = getUsernameInternal();
      s.mBooIsMessage = getIsMessageInternal();
    }

    return s;
  }



  private String getTitleInternal()
  {
    if (null == mBoo || null == mBoo.mData) {
      return null;
    }
    return mBoo.mData.mTitle;
  }



  private String getUsernameInternal()
  {
    if (null == mBoo || null == mBoo.mData || null == mBoo.mData.mUser) {
      return null;
    }
    return mBoo.mData.mUser.mUsername;
  }



  private int getBooIdInternal()
  {
    if (null == mBoo || null == mBoo.mData) {
      return -1;
    }
    return mBoo.mData.mId;
  }



  private boolean getIsMessageInternal()
  {
    if (null == mBoo || null == mBoo.mData) {
      return false;
    }
    return mBoo.mData.mIsMessage;
  }



  public PersistentPlaybackState getPersistentState()
  {
    synchronized (mLock)
    {
      if (null == mBoo || null == mBoo.mData) {
        return null;
      }
      PersistentPlaybackState state = new PersistentPlaybackState();
      state.mBooData = mBoo.mData;
      state.mState = mState;
      state.mProgress = mPlaybackProgress;
      return state;
    }
  }



  public void setPendingState(int state)
  {
    synchronized (mLock)
    {
      setPendingStateUnlocked(state);
    }
  }



  public int getPlaybackStateUnlocked()
  {
    return mState;
  }



  public void setPendingStateUnlocked(int state)
  {
    mPendingState = state;
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
        // Figure out the action to take from here. This needs to be done under lock
        // so relevant data won't change under our noses.
        Boo currentBoo = null;
        int currentState = Constants.STATE_ERROR;
        int pendingState = Constants.STATE_NONE;
        int action = T_NONE;

        boolean reset = false;

        synchronized (mLock)
        {
          currentBoo = mBoo;
          currentState = mState;
          pendingState = mPendingState;
          reset = mResetState;
          if (reset) {
            mResetState = false;
            currentState = Constants.STATE_NONE;
          }

          // Log.d(LTAG, "#1 currentBoo: " + currentBoo + " - currentState: " + currentState + " - pendingState: " + pendingState);
          // Log.d(LTAG, "Reset? " + reset);

          // If the next state is to be an error state, let's not bother with
          // trying to find out what to do next - we want to stop.
          if (Constants.STATE_ERROR == pendingState) {
            action = T_STOP;
          }
          else {
            action = STATE_DECISION_MATRIX[normalizeState(currentState)][normalizeState(pendingState)];
          }
          // Log.d(LTAG, "#1 Current: " + currentState + " Pending: " + pendingState + " Action: " + action);

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
              mState = mPendingState = Constants.STATE_PREPARING;
              break;

            case T_RESUME:
              // State and pending state are determined by whether the resume
              // action succeeds.
              break;

            case T_STOP:
              mState = mPendingState = Constants.STATE_NONE;
              break;

            case T_PAUSE:
              mState = mPendingState = Constants.STATE_PAUSED;
              break;

            case T_START:
              // For this action only, we set a new pending state. Once
              // preparing has finished, interrupt() will be invoked.
              mState = Constants.STATE_PREPARING;
              mPendingState = Constants.STATE_PLAYING;
              break;

            default:
              Log.e(LTAG, "Unknown action: " + action);
              pendingState = Constants.STATE_ERROR;
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
        if (Constants.STATE_ERROR == pendingState) {
          sendState(Constants.STATE_ERROR);
        }

        // Now perform the appropriate action to attain the new state.
        boolean ares = performAction(action, currentBoo);
        // Log.d(LTAG, "#2 Current: " + currentState + " Pending: " + pendingState + " Action: " + action);

        if (ares) {
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
    int action = T_NONE;
    synchronized (mLock)
    {
      action = STATE_DECISION_MATRIX[normalizeState(mState)][Constants.STATE_NONE];
      mState = mPendingState = Constants.STATE_NONE;
    }
    performAction(action, null);
  }



  private boolean performAction(int action, Boo boo)
  {
    boolean result = true;

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
        result = resumeInternal();
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

    return result;
  }



  /**
   * Helper function. "Normalizes" a state value insofar as it factors out semi-
   * states.
   **/
  private int normalizeState(int state)
  {
    if (Constants.STATE_BUFFERING == state) {
      return Constants.STATE_PLAYING;
    }
    if (Constants.STATE_ERROR == state) {
      return Constants.STATE_NONE;
    }
    return state;
  }




  private void prepareInternal(Boo boo)
  {
    if (null == boo || null == boo.mData) {
      Log.e(LTAG, "Prepare without boo!");
      synchronized (mLock)
      {
        mPendingState = Constants.STATE_ERROR;
      }
      interrupt();
      return;
    }

    sendState(Constants.STATE_PREPARING);

    // Local Boos are treated via the FLACPlayerWrapper.
    synchronized (mLock) {
      if (boo.isLocal()) {
        mPlayer = new FLACPlayerWrapper(this);
      }
      else if (boo.isRemote()) {
        // Handle everything else via the APIPlayer
        mPlayer = new APIPlayer(this);
      }
      else {
        // Not sure what to do here, exactly.
        Log.e(LTAG, "Boo " + boo + " appears to be neither local nor remote. Huh.");
        return;
      }

      // Now we can use the base API to start playback.
      boolean result = mPlayer.prepare(boo);
      if (result) {
        // Once that returns, we set the current state to PAUSED. While we were in
        // PREPARING state, no other state changes could be effected, so that's safe.
        // We also interrupt() again, to let the thread figure out if there's a
        // pending state after this.
        mState = Constants.STATE_PAUSED;
      }
      else {
        mPendingState = Constants.STATE_ERROR;
      }
    }
    interrupt();
  }



  private void pauseInternal()
  {
    synchronized (mLock) {
      mPlayer.pause();
    }

    sendState(Constants.STATE_PAUSED);
  }



  private boolean resumeInternal()
  {
    synchronized (mLock) {
      if (mPlayer.resume()) {
        mState = mPendingState = Constants.STATE_PLAYING;
      }
      else {
        return false;
      }
    }

    if (null == mTimer) {
      startPlaybackState();
    }
    else {
      sendState(Constants.STATE_PLAYING);
    }

    return true;
  }



  private void stopInternal(boolean sendState)
  {
    // Log.d(LTAG, "Stop internal: " + sendState);
    synchronized (mLock) {
      if (null != mPlayer) {
        mPlayer.stop();
        mPlayer = null;
      }

      if (null != mTimer) {
        mTimer.cancel();
        mTimer = null;
      }
    }

    if (sendState) {
      // Log.d(LTAG, "sending State ended: " + sendState);
      sendState(Constants.STATE_FINISHED);
      mBoo = null; // Reset *after* sending end state.
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
    sendState(Constants.STATE_PLAYING);

    try {
      mTimer = new Timer();
      TimerTask task = new TimerTask()
      {
        public void run()
        {
          onTimer();
        }
      };
      mTimer.scheduleAtFixedRate(task, 0, TIMER_TASK_INTERVAL);
    } catch (java.lang.IllegalStateException ex) {
      Log.e(LTAG, "Could not start timer: " + ex);
      sendState(Constants.STATE_ERROR);
    }
  }



  private void sendState(int state)
  {
    if (null == mListener) {
      return;
    }

    // Overwrite numeric state.
    PlayerState s = getPlayerState();
    s.mState = state;

    mListener.onProgress(s);
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

    int state = -1;
    synchronized (mLock)
    {
      state = mState;
    }


    if (state == Constants.STATE_PLAYING) {
      // Log.d(LTAG, "timestamp: " + mTimestamp);
      // Log.d(LTAG, "diff: " + diff);
      // Log.d(LTAG, "progress: " + mPlaybackProgress);
      mPlaybackProgress += (double) diff / 1000.0;
    }

    sendState(state);
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

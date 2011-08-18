/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.net.Uri;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.os.Handler;
import android.os.SystemClock;
import android.os.IBinder;
import android.os.RemoteException;

import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;
import fm.audioboo.data.PlayerState;

import java.lang.ref.WeakReference;

import java.util.List;
import java.util.LinkedList;

import android.util.Log;

/**
 * Client class for talking to the AudiobooService's BooPlaybackService
 **/
public class BooPlayerClient
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "BooPlayerClient";

  // Delay for updating the progress bar (msec).
  private static final int  PROGRESS_UPDATE_MS  = 331;


  /***************************************************************************
   * Progress listener
   **/
  public static interface ProgressListener
  {
    public void onProgress(PlayerState state);
  }



  /***************************************************************************
   * Implement BindListener to receive a BooPlayerClient instance that's
   * bound to IBooPlaybackService.
   **/
  public interface BindListener
  {
    public void onBound(BooPlayerClient client);
  }


  /***************************************************************************
   * Private data
   **/
  private WeakReference<Context>        mContext;
  private WeakReference<BindListener>   mBindListener;

  // Service stub
  private volatile IBooPlaybackService  mStub = null;

  // Progress polling
  private long                    mNextUpdate;
  private Handler                 mHandler = new Handler();
  private Runnable                mUpdateTask = new Runnable()
  {
    public void run()
    {
      // Log.d(LTAG, "[" + this + "]: update");
      PlayerState state = null;
      BooPlayerClient client = Globals.get().mPlayer;
      if (null != client) {
        state = client.getState();
      }
      sendState(state);

      mNextUpdate += PROGRESS_UPDATE_MS;
      // Log.d(LTAG, "#2 Posting at: " + mNextUpdate);
      mHandler.postAtTime(this, mNextUpdate);
    }
  };


  // Progress listener
  private Object                        mListenerLock = new Object();
  private List<WeakReference<ProgressListener>>  mListeners = new LinkedList<WeakReference<ProgressListener>>();

  // Service connection
  private ServiceConnection             mConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service)
    {
      mStub = IBooPlaybackService.Stub.asInterface(service);

      // Notify bind success.
      BindListener listener = mBindListener.get();
      if (null != listener) {
        listener.onBound(BooPlayerClient.this);
      }
    }


    public void onServiceDisconnected(ComponentName className)
    {
      mStub = null;
    }
  };




  /***************************************************************************
   * Management functions
   **/

  /**
   * Returns true if binding was successful, false otherwise. Shortly after a
   * successful bind, BindListener's onBound() method will be called with
   * a BooPlayerClient instance through which you can communicate with the
   * service.
   **/
  public static boolean bindService(Context context, BindListener listener)
  {
    BooPlayerClient bpc = new BooPlayerClient(context, listener);
    return bpc.bindService();
  }



  /**
   * Always unbind each BooPlayerClient object.
   **/
  public void unbindService()
  {
    if (null == mStub) {
      return;
    }

    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    ctx.unbindService(mConnection);
  }



  private BooPlayerClient(Context context, BindListener listener)
  {
    mContext = new WeakReference<Context>(context);
    mBindListener = new WeakReference<BindListener>(listener);
  }



  private boolean bindService()
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      Log.e(LTAG, "No context to bind with service with.");
      return false;
    }
    return ctx.bindService(
        new Intent(IBooPlaybackService.class.getName()),
        mConnection, Context.BIND_AUTO_CREATE);
  }



  public void addProgressListener(ProgressListener listener)
  {
    synchronized (mListenerLock) {
      int oldSize = mListeners.size();

      List<WeakReference<ProgressListener>> toRemove = new LinkedList<WeakReference<ProgressListener>>();

      boolean add = true;
      for (WeakReference<ProgressListener> ref : mListeners) {
        if (ref.get() == listener) {
          add = false;
        }
        else if (null == ref.get()) {
          toRemove.add(ref);
        }
      }

      if (add) {
        // Log.d(LTAG, "Adding listener: " + listener);
        mListeners.add(new WeakReference<ProgressListener>(listener));
      }

      mListeners.removeAll(toRemove);

      decideOnPolling(oldSize);
    }
  }



  public void removeProgressListener(ProgressListener listener)
  {
    synchronized (mListenerLock) {
      int oldSize = mListeners.size();

      List<WeakReference<ProgressListener>> toRemove = new LinkedList<WeakReference<ProgressListener>>();

      for (WeakReference<ProgressListener> ref : mListeners) {
        if (ref.get() == listener) {
          // Log.d(LTAG, "Removing listener: " + listener);
          toRemove.add(ref);
        }
        else if (null == ref.get()) {
          toRemove.add(ref);
        }
      }

      mListeners.removeAll(toRemove);

      decideOnPolling(oldSize);
    }
  }



  private void sendState(PlayerState state)
  {
    List<ProgressListener> targets = new LinkedList<ProgressListener>();

    synchronized (mListenerLock) {
      List<WeakReference<ProgressListener>> toRemove = new LinkedList<WeakReference<ProgressListener>>();

      for (WeakReference<ProgressListener> ref : mListeners) {
        ProgressListener listener = ref.get();
        if (null != listener) {
          targets.add(listener);
        }
        else {
          toRemove.add(ref);
        }
      }

      mListeners.removeAll(toRemove);
    }


    // Log.d(LTAG, "Dispatching " + state + " to " + targets.size());
    for (ProgressListener listener : targets) {
      // Log.d(LTAG, "Dispatching to listener: " + listener);
      listener.onProgress(state);
    }
  }



  private void decideOnPolling(int oldSize)
  {
    // Decisions are made on the basis of whether or not there were listeners
    // before and there are listeners now; the number of listeners doesn't
    // matter.
    boolean oldHasListeners = oldSize > 0;
    boolean curHasListeners = mListeners.size() > 0;

    // If that hasn't chnaged, we don't need to do anything.
    if (oldHasListeners == curHasListeners) {
      return;
    }

    // Otherwise we need to go by the current contents.
    mHandler.removeCallbacks(mUpdateTask);
    if (curHasListeners) {
      mNextUpdate = SystemClock.uptimeMillis() + PROGRESS_UPDATE_MS;
      mHandler.postAtTime(mUpdateTask, mNextUpdate);
    }
  }



  /***************************************************************************
   * BooPlayer implementation
   **/
  public void play(Boo boo, boolean playImmediately)
  {
    // The first thing we do is send a "buffering" state. The first event from
    // the service can be delayed up to 5 seconds, in which time we may not show
    // any progress at all.
    PlayerState state = new PlayerState();
    state.mState = (playImmediately ? Constants.STATE_BUFFERING : Constants.STATE_PREPARING);
    state.mProgress = 0f;
    state.mTotal = boo.getDuration();
    state.mBooId = boo.mData.mId;
    state.mBooTitle = boo.mData.mTitle;
    state.mBooUsername = (null == boo.mData.mUser ? null : boo.mData.mUser.mUsername);
    sendState(state);

    // Before sending stuff off to the service, make sure the mp3 uri (if it
    // exists) is absolute.
    if (null != boo.mData.mHighMP3Url) {
      Uri uri = Globals.get().mAPI.makeAbsoluteUri(boo.mData.mHighMP3Url);
      uri = Globals.get().mAPI.signUri(uri);
      if (null != uri) {
        boo.mData.mHighMP3Url = uri;
      }
    }

    try {
      mStub.play(boo.mData, playImmediately);
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
  }



  public void stop()
  {
    try {
      mStub.stop();
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
  }



  public void pause()
  {
    try {
      mStub.pause();
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
  }



  public void resume()
  {
    try {
      mStub.resume();
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
  }



  public void seekTo(double position)
  {
    try {
      mStub.seekTo(position);
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
  }



  public PlayerState getState()
  {
    try {
      return mStub.getState();
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
    return null;
  }
}

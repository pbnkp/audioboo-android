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
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.os.IBinder;
import android.os.RemoteException;

import fm.audioboo.application.Boo;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Client class for talking to the AudibooService's BooPlaybackService
 **/
public class BooPlayerClient
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "BooPlayerClient";


  /***************************************************************************
   * Progress listener
   **/
  public static interface ProgressListener
  {
    public void onProgress(int state, double progress, double total);
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

  // Progress listener
  private ProgressListener              mListener;
  private BroadcastReceiver             mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      if (intent.getAction().equals(Constants.EVENT_PROGRESS)) {
        int state = intent.getIntExtra(Constants.PROGRESS_STATE, 0);
        double progress = intent.getDoubleExtra(Constants.PROGRESS_PROGRESS, 0f);
        double total = intent.getDoubleExtra(Constants.PROGRESS_TOTAL, 0f);
        if (null != mListener) {
          mListener.onProgress(state, progress, total);
        }
      }
    }
  };

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

      // Register a broadcast receiver for state updates
      Context ctx = mContext.get();
      if (null == ctx) {
        return;
      }
      ctx.registerReceiver(mReceiver, new IntentFilter(Constants.EVENT_PROGRESS));
    }


    public void onServiceDisconnected(ComponentName className)
    {
      mStub = null;

      Context ctx = mContext.get();
      if (null == ctx) {
        return;
      }
      ctx.registerReceiver(null, new IntentFilter(Constants.EVENT_PROGRESS));
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



  public void setProgressListener(ProgressListener listener)
  {
    mListener = listener;
  }



  /***************************************************************************
   * BooPlayer implementation
   **/
  public void play(Boo boo)
  {
    try {
      mStub.play(boo.mData);
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



  public int getState()
  {
    try {
      return mStub.getState();
    } catch (RemoteException ex) {
      Log.e(LTAG, "Exception " + ex.getMessage());
    }
    return Constants.STATE_NONE;
  }
}

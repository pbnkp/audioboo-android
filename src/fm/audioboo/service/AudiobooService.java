/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.app.Service;

import android.os.IBinder;
import android.os.Parcelable;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;

import fm.audioboo.data.BooData;
import fm.audioboo.application.Boo;

import fm.audioboo.application.BooDetailsActivity;

import fm.audioboo.application.R;

import android.util.Log;


/**
 * Service implementing IAudiobooService
 **/
public class AudiobooService
       extends Service
       implements BooPlayer.ProgressListener
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "AudiobooService";

  // Notification IDs
  private static final int  NOTIFICATION_PLAYING_BACK    = 0;


  /***************************************************************************
   * Private data
   **/
  private BooPlayer   mPlayer;


  /***************************************************************************
   * Service implementation
   **/
  @Override
  public IBinder onBind(Intent intent)
  {
    if (IBooPlaybackService.class.getName().equals(intent.getAction())) {
      return mBinder;
    }
    return null;
  }



  @Override
  public void onCreate()
  {
    if (null == mPlayer) {
      mPlayer = new BooPlayer(this);
      mPlayer.setProgressListener(this);
      mPlayer.start();
    }
  }



  @Override
  public void onDestroy()
  {
    if (null != mPlayer) {
      mPlayer.mShouldRun = false;
      mPlayer.interrupt();
      mPlayer = null;
    }
  }


  /***************************************************************************
   * ProgressListener implementation
   **/
  public void onProgress(int state, double progress, double total)
  {
    // Log.d(LTAG, "Got state 1: " + state + " - " + System.currentTimeMillis());
    Intent i = new Intent(Constants.EVENT_PROGRESS);
    i.putExtra(Constants.PROGRESS_STATE, state);
    i.putExtra(Constants.PROGRESS_PROGRESS, progress);
    i.putExtra(Constants.PROGRESS_TOTAL, total);
    sendBroadcast(i);
  }



  /***************************************************************************
   * IBooPlaybackService implementation
   **/
  private final IBooPlaybackService.Stub mBinder = new IBooPlaybackService.Stub()
  {
    public void play(BooData boo, boolean playImmediately)
    {
      mPlayer.play(new Boo(boo), playImmediately);

      // Create notification
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

      // Create intent for notification clicks. We want to open the Boo's detail
      // view.
      Intent intent = new Intent(AudiobooService.this, BooDetailsActivity.class);
      intent.putExtra(BooDetailsActivity.EXTRA_BOO_DATA, (Parcelable) boo);
      PendingIntent contentIntent = PendingIntent.getActivity(AudiobooService.this, 0, intent, 0);

      // Create Notification
      Resources res = getResources();
      String title = (null != boo.mTitle) ? boo.mTitle : res.getString(R.string.notification_default_title);
      String username = (null != boo.mUser && null != boo.mUser.mUsername) ? boo.mUser.mUsername : res.getString(R.string.notification_unknown_user);
      String text = String.format(res.getString(R.string.notification_text), username);
      Notification notification = new Notification(R.drawable.notification,
          null, System.currentTimeMillis());
      notification.setLatestEventInfo(AudiobooService.this, title, text, contentIntent);

      // Set ongoing and no-clear flags to ensure the notification stays until
      // cleared by this service.
      notification.flags |= Notification.FLAG_ONGOING_EVENT;
      notification.flags |= Notification.FLAG_NO_CLEAR;

      // Install notification
      nm.notify(NOTIFICATION_PLAYING_BACK, notification);
    }



    public void stop()
    {
      mPlayer.stopPlaying();

      // Cancel notification
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      nm.cancel(NOTIFICATION_PLAYING_BACK);
    }



    public void pause()
    {
      mPlayer.pausePlaying();
    }



    public void resume()
    {
      mPlayer.resumePlaying();
    }



    public int getState()
    {
      return mPlayer.getPlaybackState();
    }



    public String getTitle()
    {
      return mPlayer.getTitle();
    }



    public String getUsername()
    {
      return mPlayer.getUsername();
    }



    public double getDuration()
    {
      return mPlayer.getDuration();
    }


  };
}


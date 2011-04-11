/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.app.Service;

import android.os.IBinder;
import android.os.Parcelable;
import android.os.Environment;

import android.net.Uri;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import fm.audioboo.data.BooData;
import fm.audioboo.data.PlayerState;

import fm.audioboo.application.Boo;
import fm.audioboo.application.UriUtils;
import fm.audioboo.application.BooDetailsActivity;

import fm.audioboo.application.R;

import android.util.Log;


/**
 * Service implementing IAudiobooService
 **/
public class AudiobooService
       extends Service
       implements BooPlayer.ActivityListener
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "AudiobooService";


  /***************************************************************************
   * Private data
   **/
  private BooPlayer     mPlayer;
  private UploadManager mUploader;


  /***************************************************************************
   * Service implementation
   **/
  @Override
  public IBinder onBind(Intent intent)
  {
    if (IBooPlaybackService.class.getName().equals(intent.getAction())) {
      return mPlaybackServiceBinder;
    }
    else if (IUploadService.class.getName().equals(intent.getAction())) {
      return mUploadServiceBinder;
    }
    return null;
  }



  @Override
  public void onCreate()
  {
    if (null == mPlayer) {
      mPlayer = new BooPlayer(this, this);
      mPlayer.start();

      // Initialize player.
      Boo boo = Boo.constructFromFile(getStateFilename());
      if (null == boo) {
        boo = Boo.createIntroBoo(this);
      }
      mPlayer.play(boo, false);
    }

    if (null == mUploader) {
      mUploader = new UploadManager(this);
    }
  }



  @Override
  public void onDestroy()
  {
    if (null != mPlayer) {
      // Stop player
      cancelPlaybackNotification();
      mPlayer.mShouldRun = false;
      mPlayer.interrupt();
      mPlayer = null;
    }

    if (null != mUploader) {
      mUploader.stop();
      mUploader = null;
    }
  }



  private String getStateFilename()
  {
    String path = Environment.getExternalStorageDirectory().getPath();
    path += File.separator + "data" + File.separator + getPackageName() + File.separator + "state";
    return path;
  }



  private void showPlaybackNotification()
  {
    PlayerState state = mPlayer.getPlayerState();
    if (null == state) {
      return;
    }

    // Create notification
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    // Create intent for notification clicks. We want to open the Boo's detail
    // view.
    Intent intent = new Intent(Intent.ACTION_VIEW, UriUtils.createDetailsUri(
          state.mBooId, state.mBooIsMessage));
    PendingIntent contentIntent = PendingIntent.getActivity(AudiobooService.this, 0, intent, 0);

    // Create Notification
    Resources res = getResources();
    String title = (null != state.mBooTitle) ? state.mBooTitle : res.getString(R.string.notification_default_title);
    String username = (null != state.mBooUsername) ? state.mBooUsername : res.getString(R.string.notification_unknown_user);
    String text = String.format(res.getString(R.string.notification_text), username);
    Notification notification = new Notification(R.drawable.notification,
        null, System.currentTimeMillis());
    notification.setLatestEventInfo(AudiobooService.this, title, text, contentIntent);

    // Set ongoing and no-clear flags to ensure the notification stays until
    // cleared by this service.
    notification.flags |= Notification.FLAG_ONGOING_EVENT;
    notification.flags |= Notification.FLAG_NO_CLEAR;

    // Install notification
    nm.notify(Constants.NOTIFICATION_PLAYING_BACK, notification);

  }



  private void cancelPlaybackNotification()
  {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(Constants.NOTIFICATION_PLAYING_BACK);
  }



  /***************************************************************************
   * IBooPlaybackService implementation
   **/
  private final IBooPlaybackService.Stub mPlaybackServiceBinder = new IBooPlaybackService.Stub()
  {
    public void play(BooData boo, boolean playImmediately)
    {
      Boo b = new Boo(boo);
      b.writeToFile(getStateFilename());

      mPlayer.play(b, playImmediately);
    }



    public void stop()
    {
      mPlayer.stopPlaying();
      cancelPlaybackNotification();
    }



    public void pause()
    {
      mPlayer.pausePlaying();
      cancelPlaybackNotification();
    }



    public void resume()
    {
      mPlayer.resumePlaying();
    }



    public PlayerState getState()
    {
      return mPlayer.getPlayerState();
    }
  };



  /***************************************************************************
   * IUploadService implementation
   **/
  private final IUploadService.Stub mUploadServiceBinder = new IUploadService.Stub()
  {
    public void processQueue()
    {
      // Log.d(LTAG, "Processing upload queue");
      mUploader.processQueue();
    }
  };



  /***************************************************************************
   * BooPlayer.ActivityListener implementation
   **/
  public void updateActivity(boolean active)
  {
    if (active) {
      showPlaybackNotification();
    }
    else {
      cancelPlaybackNotification();
    }
  }

}


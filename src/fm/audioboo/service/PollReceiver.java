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

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;

import android.os.Handler;
import android.os.Message;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;

import fm.audioboo.application.API;
import fm.audioboo.application.Boo;
import fm.audioboo.application.BooList;
import fm.audioboo.application.Globals;
import fm.audioboo.application.MessagesActivity;
import fm.audioboo.application.R;

import java.util.Date;

import android.util.Log;


/**
 * Receiver for poll Intents. Waits for Globals to connect to the service,
 * then uses the IPollerService interface to poll for messages.
 **/
public class PollReceiver extends BroadcastReceiver
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG              = "PollReceiver";



  /***************************************************************************
   * Public constants
   **/
  // Action
  public static final String ACTION_POLL_MESSAGES = "fm.audioboo.actions.POLL_MESSAGES";


  /***************************************************************************
   * Private data
   **/
  private Context mContext = null;

  private Handler mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          onReceiveBoos((BooList) msg.obj);
          return true;
        }
        Log.e(LTAG, "Poller got: " + msg.what);
        return true;
      }
  });



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onReceive(Context context, Intent intent)
  {
    if (!ACTION_POLL_MESSAGES.equals(intent.getAction())) {
      return;
    }

    mContext = context;
    Globals.get().mAPI.fetchBoos(API.BOOS_INBOX, mHandler, 1, 20, new Date());
  }



  private void onReceiveBoos(BooList boos)
  {
    int unread = 0;

    for (Boo boo : boos.mClips) {
      if (!boo.mData.mIsMessage) {
        continue;
      }
      if (boo.mData.mIsRead) {
        continue;
      }
      ++unread;
    }

    if (0 == unread) {
      cancelNotification();
    }
    else {
      showNotification(unread);
    }
  }



  private void cancelNotification()
  {
    NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(Constants.NOTIFICATION_MESSAGES);
  }



  private void showNotification(int unread)
  {
    // Create notification
    NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

    // Create intent for notification clicks. We want to open the Boo's detail
    // view.
    Intent intent = new Intent(mContext, MessagesActivity.class);
    PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);

    // Create Notification
    String title = mContext.getResources().getString(R.string.poll_title);
    String text = String.format(mContext.getResources().getString(R.string.poll_info_format), unread);
    Notification notification = new Notification(R.drawable.notification,
        null, System.currentTimeMillis());
    notification.setLatestEventInfo(mContext, title, text, contentIntent);

    // Set the number on the notification, to show how many messages are unread.
    notification.defaults = Notification.DEFAULT_ALL;
    notification.number = unread;

    // Install notification
    nm.notify(Constants.NOTIFICATION_MESSAGES, notification);
  }
}

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
import android.content.Intent;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;

import android.os.Handler;
import android.os.Message;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

import java.lang.ref.WeakReference;

import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;
import fm.audioboo.application.API;
import fm.audioboo.application.MyBoosActivity;
import fm.audioboo.application.MessagesActivity;
import fm.audioboo.application.R;

import fm.audioboo.data.UploadInfo;

import android.util.Log;

/**
 * Uploads Boos/Messages in the upload queue. It's not very clever that way, but
 * it does chunked uploading and can report which Boo/Message is uploaded and by
 * how much.
 **/
public class UploadManager
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG              = "UploadManager";

  // Sleep time, if the thread's not woken.
  private static final int SLEEP_TIME_LONG      = 60 * 1000;

  // Notification IDs (FIXME put in the same place as other NOTIFICATION_
  // constants).
  private static final int  NOTIFICATION_UPLOADING  = 1;


  /***************************************************************************
   * Upload result
   **/
  public static class UploadResult
  {
    public int      id;
    public String   contentType;
    public int      size;
    public int      received;
    public int      outstanding;
    public boolean  complete;
    public String   filename;
  }



  /***************************************************************************
   * Queue thread
   **/
  private class QueueThread extends Thread
  {
    public volatile boolean mShouldRun = true;


    public void run()
    {
      while (mShouldRun)
      {
        try {
          // And when we're done, sleep. We'll get interrupted if the app
          // thinks we need to do stuff.
          sleep(SLEEP_TIME_LONG);

          Boo boo = null;

          // If there's no current boo, get one.
          synchronized (mUploadLock) {
            if (null == mBooUpload) {
              List<Boo> uploads = new LinkedList<Boo>();
              uploads.addAll(Globals.get().getBooManager().getBooUploads());
              uploads.addAll(Globals.get().getBooManager().getMessageUploads());
              Collections.sort(uploads, Boo.RECORDING_DATE_COMPARATOR);

              if (!uploads.isEmpty()) {
                mBooUpload = uploads.get(0);
              }
            }

            boo = mBooUpload;
          }

          // If there's still no current boo, sleep again.
          if (null == boo) {
            clearNotification();
            continue;
          }
          setNotification(boo);

          // Loop until processNextStage returns false. It'll return false in
          // most cases.
          while (processNextStage(boo, null)) {
            Log.d(LTAG, "Still processing: " + boo);
          }

        } catch (InterruptedException ex) {
          // pass
        }
      }
    }
  }



  /***************************************************************************
   * Private data
   **/
  // Context in which this object was created
  private WeakReference<Context>  mContext;

  // Upload that's currently processed.
  private Object                  mUploadLock = new Object();
  private Boo                     mBooUpload;

  // Current chunk upload size, and timestamp for last upload request begin;
  // the chunk size is going to be recalculated based on that timestamp.
  private QueueThread             mThread;
  private int                     mChunkSize      = Constants.MIN_UPLOAD_CHUNK_SIZE;
  private long                    mUploadStarted  = -1;

  private Handler                 mHandler        = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          UploadResult res = (UploadResult) msg.obj;
          Boo boo = null;
          synchronized (mUploadLock) {
            boo = mBooUpload;
          }
          if (null == boo) {
            Log.e(LTAG, "Something is wrong. Got upload result, but no current upload to process.");
            return true;
          }
          processNextStage(boo, res);
          return true;
        }

        // Consume message, but don't do anything. The next time the thread wakes
        // up, it'll try the same thing again.
        Log.e(LTAG, "Upload result is: " + msg.what);
        return true;
      }
  });


  /***************************************************************************
   * Public Interface
   **/
  public UploadManager(Context ctx)
  {
    mContext = new WeakReference<Context>(ctx);
    mThread = new QueueThread();
    mThread.start();
  }



  public void stop()
  {
    mThread.mShouldRun = false;
    mThread.interrupt();
  }



  public void processQueue()
  {
    // Really just need to interrupt the main run loop, that's all.
    mThread.interrupt();
  }



  /**
   * Returns true if the main thread is supposed to call this function again
   * immediately, false otherwise.
   **/
  private boolean processNextStage(Boo boo, UploadResult res)
  {
    if (null == boo || null == boo.mData || null == boo.mData.mUploadInfo) {
      Log.e(LTAG, "Can't process null upload: " + boo);
      return false;
    }

    // Process timestamps first, to determine new chunk size.
    if (-1 != mUploadStarted) {
      long diff = System.currentTimeMillis() - mUploadStarted;

      if (diff > Constants.MAX_UPLOAD_TIME) {
        mChunkSize /= 1.5;
      }
      else if (diff < Constants.MIN_UPLOAD_TIME) {
        mChunkSize *= 1.5;
      }

      if (mChunkSize < Constants.MIN_UPLOAD_CHUNK_SIZE) {
        mChunkSize = Constants.MIN_UPLOAD_CHUNK_SIZE;
      }
      else if (mChunkSize > Constants.MAX_UPLOAD_CHUNK_SIZE) {
        mChunkSize = Constants.MAX_UPLOAD_CHUNK_SIZE;
      }

      mUploadStarted = -1;
    }
    Log.d(LTAG, "Chunk size is: " + mChunkSize);

    // Delegate to chunk-specific function
    boolean ret = false;
    switch (boo.mData.mUploadInfo.mUploadStage) {
      case UploadInfo.UPLOAD_STAGE_AUDIO:
        ret = processAudioStage(boo, res);
        break;

      case UploadInfo.UPLOAD_STAGE_IMAGE:
        ret = processImageStage(boo, res);
        break;

      case UploadInfo.UPLOAD_STAGE_METADATA:
        ret = processMetadataStage(boo, res);
        break;

      default:
        Log.e(LTAG, "Invalid processing stage: " + boo.mData.mUploadInfo.mUploadStage);
        break;
    }

    return ret;
  }



  private boolean processAudioStage(Boo boo, UploadResult res)
  {
    Log.d(LTAG, "Audio stage: " + boo);

    if (null != res) {
      if (-1 != boo.mData.mUploadInfo.mAudioChunkId
          && boo.mData.mUploadInfo.mAudioChunkId != res.id)
      {
        Log.e(LTAG, "Got response, but the chunk IDs don't match. Ugh.");
        return false;
      }

      // Update metadata
      boo.mData.mUploadInfo.mAudioChunkId = res.id;
      boo.mData.mUploadInfo.mAudioUploaded = res.received;

      if (res.complete) {
        boo.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_IMAGE;
        boo.writeToFile();
        return true;
      }
      boo.writeToFile();
    }

    // Create a new attachment if we don't have an ID yet. Otherwise add to the
    // pre-existing attachment.
    mUploadStarted = System.currentTimeMillis();
    if (-1 == boo.mData.mUploadInfo.mAudioChunkId) {
      boo.flattenAudio();
      Globals.get().mAPI.createAttachment(boo.mData.mHighMP3Url.getPath(), 0,
          mChunkSize, mHandler);
    }
    else {
      Globals.get().mAPI.appendToAttachment(boo.mData.mUploadInfo.mAudioChunkId,
          boo.mData.mHighMP3Url.getPath(), boo.mData.mUploadInfo.mAudioUploaded,
          mChunkSize, mHandler);
    }
    return false;
  }



  private boolean processImageStage(Boo boo, UploadResult res)
  {
    Log.d(LTAG, "Image stage: " + boo);

    if (null != res) {
      if (-1 != boo.mData.mUploadInfo.mImageChunkId
          && boo.mData.mUploadInfo.mImageChunkId != res.id)
      {
        Log.e(LTAG, "Got response, but the chunk IDs don't match. Ugh.");
        return false;
      }

      // Update metadata
      boo.mData.mUploadInfo.mImageChunkId = res.id;
      boo.mData.mUploadInfo.mImageUploaded = res.received;

      if (res.complete) {
        boo.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_METADATA;
        boo.writeToFile();
        return true;
      }
      boo.writeToFile();
    }

    // We might not have an image attachment.
    if (null == boo.mData.mImageUrl) {
      boo.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_METADATA;
      boo.writeToFile();
      return true;
    }

    // Create a new attachment if we don't have an ID yet. Otherwise add to the
    // pre-existing attachment.
    mUploadStarted = System.currentTimeMillis();
    if (-1 == boo.mData.mUploadInfo.mImageChunkId) {
      boo.flattenAudio();
      Globals.get().mAPI.createAttachment(boo.mData.mImageUrl.getPath(), 0,
          mChunkSize, mHandler);
    }
    else {
      Globals.get().mAPI.appendToAttachment(boo.mData.mUploadInfo.mImageChunkId,
          boo.mData.mImageUrl.getPath(), boo.mData.mUploadInfo.mImageUploaded,
          mChunkSize, mHandler);
    }
    return false;
  }



  private boolean processMetadataStage(Boo boo, UploadResult res)
  {
    // TODO
    Log.d(LTAG, "Right, attachments are uploaded. Now upload metadata.");

    // FIXME
    Globals.get().mAPI.uploadBoo(boo, mHandler);

    // Clear the current upload state and go back to sleep.
    // FIXME
    // boo.delete();
    synchronized (mUploadLock) {
      if (boo != mBooUpload) {
        Log.e(LTAG, "Uh, this really shouldn't happen.");
        return false;
      }
      mBooUpload = null;
    }
    return false;
  }



  private void clearNotification()
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(NOTIFICATION_UPLOADING);
  }



  private void setNotification(Boo boo)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    // Create notification
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

    // Create Notification
    Intent intent = null;
    if (boo.mData.mIsMessage) {
      intent = new Intent(ctx, MessagesActivity.class);
      intent.putExtra(MessagesActivity.EXTRA_DISPLAY_MODE, MessagesActivity.DISPLAY_MODE_OUTBOX);
    }
    else {
      intent = new Intent(ctx, MyBoosActivity.class);
    }
    PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, intent, 0);

    String title = boo.mData.mTitle;
    double progress = boo.uploadProgress();
    String prog = ctx.getResources().getString(R.string.upload_progress);
    prog = String.format(prog, progress);

    Notification notification = new Notification(android.R.drawable.stat_sys_upload,
        null, System.currentTimeMillis());
    notification.setLatestEventInfo(ctx, prog, title, contentIntent);

    // Set ongoing and no-clear flags to ensure the notification stays until
    // cleared by this service.
    notification.flags |= Notification.FLAG_ONGOING_EVENT;
    notification.flags |= Notification.FLAG_NO_CLEAR;

    // Install notification
    nm.notify(NOTIFICATION_UPLOADING, notification);
  }
}

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
  private static final int SLEEP_TIME_LONG      = 5 * 60 * 1000;



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

    public String toString()
    {
      return String.format("[UploadResult:%d:%s:%d/%d:%s]", id, contentType,
          received, size, filename);
    }
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
          process();

          // And when we're done, sleep. We'll get interrupted if the app
          // thinks we need to do stuff.
          sleep(SLEEP_TIME_LONG);
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
        UploadResult result = null;
        if (API.ERR_SUCCESS == msg.what) {
          result = (UploadResult) msg.obj;
        }
        process(msg.what, result);
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
   * Run the upload processing loop until we're out of work for the moment.
   * XXX Must be called when the upload lock is held.
   **/
  private void process()
  {
    process(API.ERR_SUCCESS, null);
  }


  private void process(int result, UploadResult res)
  {
    synchronized (mUploadLock)
    {
      // Check for errors.
      if (API.ERR_SUCCESS != result) {
        Log.e(LTAG, "Response code: " + result);
        setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_ERROR);
        mBooUpload = null;
        return;
      }

      // Preprocess; avoid that the result is matched with the wrong Boo.
      if (null == mBooUpload && null != res) {
        Log.e(LTAG, "Result for unknown upload: " + res);
        return;
      }

      // Ensure that there is a current boo, if possible. If the queue is
      // empty, of course, that won't be the case.
      if (null == mBooUpload) {
        // Log.d(LTAG, "Finding uploads...");
        Globals.get().getBooManager().rebuildIndex();
        List<Boo> uploads = new LinkedList<Boo>();
        uploads.addAll(Globals.get().getBooManager().getBooUploads());
        uploads.addAll(Globals.get().getBooManager().getMessageUploads());
        Collections.sort(uploads, Boo.RECORDING_DATE_COMPARATOR);

        if (!uploads.isEmpty()) {
          mBooUpload = uploads.get(0);
        }
      }

      // Empty queue, we're done.
      if (null == mBooUpload) {
        clearUploadingNotification();
        return;
      }

      // Now process stages until we're supposed to stop.
      while (processNextStage(res)) {
        // After the first iteration, any result that might've come in needs to
        // be discarded.
        res = null;
      }
    }
  }



  /**
   * Returns true if the main thread is supposed to call this function again
   * immediately, false otherwise.
   * XXX Must be called when the upload lock is held.
   **/
  private boolean processNextStage(UploadResult res)
  {
    if (null == mBooUpload.mData || null == mBooUpload.mData.mUploadInfo) {
      Log.e(LTAG, "Can't process null upload: " + mBooUpload);

      setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_ERROR);
      mBooUpload = null;
      return false;
    }
    setNotification(mBooUpload, Constants.NOTIFICATION_UPLOADING);

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
    // Log.d(LTAG, "Chunk size is: " + mChunkSize);

    // Delegate to chunk-specific function
    boolean ret = false;
    switch (mBooUpload.mData.mUploadInfo.mUploadStage) {
      case UploadInfo.UPLOAD_STAGE_AUDIO:
        ret = processAudioStage(res);
        break;

      case UploadInfo.UPLOAD_STAGE_IMAGE:
        ret = processImageStage(res);
        break;

      case UploadInfo.UPLOAD_STAGE_METADATA:
        ret = processMetadataStage(res);
        break;

      default:
        Log.e(LTAG, "Invalid processing stage: " + mBooUpload.mData.mUploadInfo.mUploadStage);
        setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_ERROR);
        mBooUpload = null;
        break;
    }

    return ret;
  }



  /**
   * Part of processNextStage()
   * XXX Must be called when the upload lock is held.
   **/
  private boolean processAudioStage(UploadResult res)
  {
    // Log.d(LTAG, "Audio stage: " + mBooUpload);

    if (null != res) {
      if (-1 != mBooUpload.mData.mUploadInfo.mAudioChunkId
          && mBooUpload.mData.mUploadInfo.mAudioChunkId != res.id)
      {
        Log.e(LTAG, "Got response, but the chunk IDs don't match. Ugh.");
        setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_ERROR);
        mBooUpload = null;
        return false;
      }

      // Update metadata
      mBooUpload.mData.mUploadInfo.mAudioChunkId = res.id;
      mBooUpload.mData.mUploadInfo.mAudioUploaded = res.received;
      mBooUpload.mData.mUploadInfo.mUploadError = false;

      if (res.complete || res.outstanding <= 0) {
        mBooUpload.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_IMAGE;
        mBooUpload.writeToFile();
        return true;
      }
      mBooUpload.writeToFile();
    }

    // Create a new attachment if we don't have an ID yet. Otherwise add to the
    // pre-existing attachment.
    mUploadStarted = System.currentTimeMillis();
    if (-1 == mBooUpload.mData.mUploadInfo.mAudioChunkId) {
      mBooUpload.flattenAudio();
      Globals.get().mAPI.createAttachment(mBooUpload.mData.mHighMP3Url.getPath(), 0,
          mChunkSize, mHandler);
    }
    else {
      Globals.get().mAPI.appendToAttachment(mBooUpload.mData.mUploadInfo.mAudioChunkId,
          mBooUpload.mData.mHighMP3Url.getPath(), mBooUpload.mData.mUploadInfo.mAudioUploaded,
          mChunkSize, mHandler);
    }
    return false;
  }



  /**
   * Part of processNextStage()
   * XXX Must be called when the upload lock is held.
   **/
  private boolean processImageStage(UploadResult res)
  {
    // Log.d(LTAG, "Image stage: " + mBooUpload);

    if (null != res) {
      if (-1 != mBooUpload.mData.mUploadInfo.mImageChunkId
          && mBooUpload.mData.mUploadInfo.mImageChunkId != res.id)
      {
        Log.e(LTAG, "Got response, but the chunk IDs don't match. Ugh.");
        setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_ERROR);
        mBooUpload = null;
        return false;
      }

      // Update metadata
      mBooUpload.mData.mUploadInfo.mImageChunkId = res.id;
      mBooUpload.mData.mUploadInfo.mImageUploaded = res.received;
      mBooUpload.mData.mUploadInfo.mUploadError = false;

      if (res.complete || res.outstanding <= 0) {
        mBooUpload.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_METADATA;
        mBooUpload.writeToFile();
        return true;
      }
      mBooUpload.writeToFile();
    }

    // We might not have an image attachment.
    if (null == mBooUpload.mData.mImageUrl) {
      mBooUpload.mData.mUploadInfo.mUploadStage = UploadInfo.UPLOAD_STAGE_METADATA;
      mBooUpload.writeToFile();
      return true;
    }

    // Create a new attachment if we don't have an ID yet. Otherwise add to the
    // pre-existing attachment.
    mUploadStarted = System.currentTimeMillis();
    if (-1 == mBooUpload.mData.mUploadInfo.mImageChunkId) {
      mBooUpload.flattenAudio();
      Globals.get().mAPI.createAttachment(mBooUpload.mData.mImageUrl.getPath(), 0,
          mChunkSize, mHandler);
    }
    else {
      Globals.get().mAPI.appendToAttachment(mBooUpload.mData.mUploadInfo.mImageChunkId,
          mBooUpload.mData.mImageUrl.getPath(), mBooUpload.mData.mUploadInfo.mImageUploaded,
          mChunkSize, mHandler);
    }
    return false;
  }



  /**
   * Part of processNextStage()
   * XXX Must be called when the upload lock is held.
   **/
  private boolean processMetadataStage(UploadResult res)
  {
    // Log.d(LTAG, "metadata stage: " + res);
    if (null != res && res.id > 0) {
      setNotification(mBooUpload, Constants.NOTIFICATION_UPLOAD_DONE);

      mBooUpload.delete();
      mBooUpload = null;
      return false;
    }

    // Try the last phase.
    Globals.get().mAPI.uploadBoo(mBooUpload, mHandler);
    return false;
  }



  /**
   * Clear upload notification
   **/
  private void clearUploadingNotification()
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.cancel(Constants.NOTIFICATION_UPLOADING);
  }



  /**
   * Set upload notification
   **/
  private void setNotification(Boo boo, int notificationType)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    if (null == boo || null == boo.mData) {
      Log.e(LTAG, "Received null boo, ignoring.");
      clearUploadingNotification(); // XXX can't be useful at this point.
      return;
    }

    // Handle notification/upload state.
    switch (notificationType) {
      case Constants.NOTIFICATION_UPLOADING:
        break; // Nothing to do.

      case Constants.NOTIFICATION_UPLOAD_ERROR:
        if (null != boo) {
          if (null != boo.mData && null != boo.mData.mUploadInfo) {
            boo.mData.mUploadInfo.mUploadError = true;
          }
          boo.writeToFile();
        }
        // XXX fall through

      case Constants.NOTIFICATION_UPLOAD_DONE:
        clearUploadingNotification();
        break;

      default:
        break;
    }

    // Create notification
    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

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
    String message = null;
    int drawable = R.drawable.notification;
    switch (notificationType) {
      case Constants.NOTIFICATION_UPLOADING:
        double progress = boo.uploadProgress();
        message = ctx.getResources().getString(R.string.upload_progress);
        message = String.format(message, progress);
        drawable = android.R.drawable.stat_sys_upload;
        break;

      case Constants.NOTIFICATION_UPLOAD_ERROR:
        message = ctx.getResources().getString(R.string.upload_error);
        break;

      case Constants.NOTIFICATION_UPLOAD_DONE:
        message = ctx.getResources().getString(R.string.upload_done);
        break;

      default:
        Log.e(LTAG, "Unreachable line reached.");
        return;
    }

    Notification notification = new Notification(drawable, null,
        System.currentTimeMillis());
    notification.setLatestEventInfo(ctx, message, title, contentIntent);

    // Set ongoing and no-clear flags to ensure the notification stays until
    // cleared by this service.
    if (Constants.NOTIFICATION_UPLOADING == notificationType) {
      notification.flags |= Notification.FLAG_ONGOING_EVENT;
      notification.flags |= Notification.FLAG_NO_CLEAR;
    }
    else {
      notification.flags |= Notification.FLAG_AUTO_CANCEL;
    }

    // Install notification
    nm.notify(notificationType, notification);
  }
}

/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2010,2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.content.Context;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

import fm.audioboo.data.BooData;

/**
 * Records Boos. This class uses FLACRecorder to record individual FLAC files
 * for a Boo.
 *
 * BooRecorder is a leaky abstraction of FLACRecorder; FLACRecorder's message
 * codes are re-used and so is FLACRecorder.Amplitudes.
 **/
public class BooRecorder
{
  /***************************************************************************
   * Public constants
   **/
  // Message ID for end of recording; at this point stats are finalized.
  // XXX Note that the message ID must be at least one higher than the highest
  // FLACRecorder message ID.
  public static final int MSG_END_OF_RECORDING  = FLACRecorder.MSG_AMPLITUDES + 1;


  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooRecorder";



  /***************************************************************************
   * Private data
   **/
  // Context
  private WeakReference<Context>  mContext;

  // Boo to record into. And current Recording
  private Boo                     mBoo;
  private BooData.Recording       mRecording;

  // Handler for messages sent by BooRecorder
  private Handler                 mUpchainHandler;
  // Internal handler to hand to FLACRecorder.
  private Handler                 mInternalHandler;

  // For recording FLAC files.
  private FLACRecorder            mRecorder;

  // Overall recording metadata
  private FLACRecorder.Amplitudes mAmplitudes;
  private FLACRecorder.Amplitudes mLastAmplitudes;


  /***************************************************************************
   * Implementation
   **/
  public BooRecorder(Context context, Boo boo, Handler handler)
  {
    mContext = new WeakReference<Context>(context);
    mBoo = boo;
    mUpchainHandler = handler;
    mInternalHandler = new Handler(new Handler.Callback()
    {
      public boolean handleMessage(Message m)
      {
        switch (m.what) {
          case FLACRecorder.MSG_AMPLITUDES:
            FLACRecorder.Amplitudes amp = (FLACRecorder.Amplitudes) m.obj;

            // Create a copy of the amplitude in mLastAmplitudes; we'll use
            // that when we restart recording to calculate the position
            // within the Boo.
            mLastAmplitudes = new FLACRecorder.Amplitudes(amp);

            if (null != mAmplitudes) {
              amp.mPosition += mAmplitudes.mPosition;
            }
            mUpchainHandler.obtainMessage(FLACRecorder.MSG_AMPLITUDES,
                amp).sendToTarget();
            return true;


          case MSG_END_OF_RECORDING:
            // Update stats - at this point, mLastAmp should really be the last set
            // of amplitudes we got from the recorder.
            if (null == mAmplitudes) {
              mAmplitudes = mLastAmplitudes;
            }
            else {
              mAmplitudes.accumulate(mLastAmplitudes);
            }

            if (null != mRecording && null != mLastAmplitudes) {
              mRecording.mDuration = mLastAmplitudes.mPosition / 1000.0;
              mRecording = null;
            }

            mUpchainHandler.obtainMessage(MSG_END_OF_RECORDING).sendToTarget();
            return true;


          default:
            mUpchainHandler.obtainMessage(m.what, m.obj).sendToTarget();
            return true;
        }
      }
    });
  }



  public void start()
  {
    // Every time we start recording, we create a new recorder instance, and
    // record to a new file.
    // That means if there's still a recorder instance running (shouldn't
    // happen!), we'll kill it.
    if (null != mRecorder) {
      stop();
    }

    // Add a new recording to the Boo.
    mRecording = mBoo.getLastEmptyRecording();

    // Start recording!
    mRecorder = new FLACRecorder(mRecording.mFilename, mInternalHandler);
    mRecorder.start();
    mRecorder.resumeRecording();
  }



  public void stop()
  {
    if (null == mRecorder) {
      // We're done.
      return;
    }

    // Pause recording & kill recorder
    mRecorder.pauseRecording();
    mRecorder.mShouldRun = false;
    mRecorder.interrupt();
    try {
      mRecorder.join();
    } catch (InterruptedException ex) {
      // pass
    }
    mRecorder = null;

    // Post an end-of-recording message; that'll update stats.
    mInternalHandler.obtainMessage(MSG_END_OF_RECORDING).sendToTarget();

  }



  public boolean isRecording()
  {
    if (null == mRecorder) {
      return false;
    }
    return mRecorder.isRecording();
  }



  public double getDuration()
  {
    if (null == mAmplitudes) {
      return 0f;
    }
    return mAmplitudes.mPosition / 1000.0;
  }



  public FLACRecorder.Amplitudes getAmplitudes()
  {
    return mAmplitudes;
  }
}

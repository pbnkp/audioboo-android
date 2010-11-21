/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009,2010 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.content.Context;

import android.os.Handler;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import fm.audioboo.jni.FLACStreamEncoder;

import java.nio.ByteBuffer;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Records a single FLAC file from the microphone. Overwrites the file if it
 * already exists.
 **/
public class FLACRecorder extends Thread
{
  /***************************************************************************
   * Public constants
   **/
  // Message codes - XXX Also see BooRecorder
  public static final int MSG_OK                    = 0;
  public static final int MSG_INVALID_FORMAT        = 1;
  public static final int MSG_HARDWARE_UNAVAILABLE  = 2;
  public static final int MSG_ILLEGAL_ARGUMENT      = 3;
  public static final int MSG_READ_ERROR            = 4;
  public static final int MSG_WRITE_ERROR           = 5;
  public static final int MSG_AMPLITUDES            = 6;

  // Sample rate, channel config, format
  public static final int SAMPLE_RATE    = 22050;
  public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  public static final int FORMAT         = AudioFormat.ENCODING_PCM_16BIT;


  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "FLACRecorder";


  /***************************************************************************
   * Simple class for reporting measured Amplitudes to user of FLACRecorder
   **/
  public static class Amplitudes
  {
    public long   mPosition;
    public float  mPeak;
    public float  mAverage;


    public Amplitudes()
    {
    }


    public Amplitudes(Amplitudes other)
    {
      mPosition = other.mPosition;
      mPeak = other.mPeak;
      mAverage = other.mAverage;
    }


    public String toString()
    {
      return String.format("%dms: %f/%f", mPosition, mAverage, mPeak);
    }


    public void accumulate(Amplitudes other)
    {
      // Position is simple; the overall position is the sum of
      // both positions.
      long oldPos = mPosition;
      mPosition += other.mPosition;

      // The higher peak is the overall peak.
      if (other.mPeak > mPeak) {
        mPeak = other.mPeak;
      }

      // Average is more complicated, because it needs to be weighted
      // on the time it took to calculate it.
      float weightedOld = mAverage * (oldPos / (float) mPosition);
      float weightedNew = other.mAverage * (other.mPosition / (float) mPosition);
      mAverage = weightedOld + weightedNew;
    }
  }


  /***************************************************************************
   * Public data
   **/
  // Flag that keeps the thread running when true.
  public boolean mShouldRun;


  /***************************************************************************
   * Private data
   **/
  // Flag that signals whether the thread should record or ignore PCM data.
  private boolean                 mShouldRecord = false;

  // Stream encoder
  private FLACStreamEncoder       mEncoder;

  // File path for the output file.
  private String                  mPath;

  // Handler to notify at the above report interval
  private Handler                 mHandler;

  // Remember the duration of the recording. This is in msec.
  private double                  mDuration;


  /***************************************************************************
   * Implementation
   **/
  public FLACRecorder(String path, Handler handler)
  {
    mPath = path;
    mHandler = handler;
  }



  public void resumeRecording()
  {
    mShouldRecord = true;
  }



  public void pauseRecording()
  {
    mShouldRecord = false;
  }



  public boolean isRecording()
  {
    return mShouldRun && mShouldRecord;
  }



  public double getDuration()
  {
    // Duration for Boos is normally in secs, and we're remembering msecs here,
    // so we'll need to convert.
    return mDuration / 1000;
  }



  public Amplitudes getAmplitudes()
  {
    if (null == mEncoder) {
      return null;
    }

    Amplitudes amp = new Amplitudes();
    amp.mPosition = (long) mDuration;
    amp.mPeak = mEncoder.getMaxAmplitude();
    amp.mAverage = mEncoder.getAverageAmplitude();

    return amp;
  }



  public static int mapChannelConfig(int channelConfig)
  {
    switch (channelConfig) {
      case AudioFormat.CHANNEL_CONFIGURATION_MONO:
        return 1;

      case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
        return 2;

      default:
        return 0;
    }
  }


  public static int mapFormat(int format)
  {
    switch (format) {
      case AudioFormat.ENCODING_PCM_8BIT:
        return 8;

      case AudioFormat.ENCODING_PCM_16BIT:
        return 16;

      default:
        return 0;
    }
  }



  public void run()
  {
    mShouldRun = true;
    boolean oldShouldRecord = false;

    try {
      // Set up recorder
      int bufsize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG,
          FORMAT);
      if (AudioRecord.ERROR_BAD_VALUE == bufsize) {
        Log.e(LTAG, "Sample rate, channel config or format not supported!");
        mHandler.obtainMessage(MSG_INVALID_FORMAT).sendToTarget();
        return;
      }
      if (AudioRecord.ERROR == bufsize) {
        Log.e(LTAG, "Unable to query hardware!");
        mHandler.obtainMessage(MSG_HARDWARE_UNAVAILABLE).sendToTarget();
        return;
      }

      AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE, CHANNEL_CONFIG, FORMAT, bufsize);

      // Initialize variables for calculating the recording duration.
      int format = mapFormat(FORMAT);
      int channels = mapChannelConfig(CHANNEL_CONFIG);
      int bytesPerSecond = SAMPLE_RATE * (format / 8) * channels;

      // Set up encoder. Create path for the file if it doesn't yet exist.
      mEncoder = new FLACStreamEncoder(mPath, SAMPLE_RATE, channels, format);

      // Start recording loop
      mDuration = 0.0;
      ByteBuffer buffer = ByteBuffer.allocateDirect(bufsize);
      while (mShouldRun) {
        // Toggle recording state, if necessary
        if (mShouldRecord != oldShouldRecord) {
          // State changed! Let's see what we are supposed to do.
          if (mShouldRecord) {
            // Log.d(LTAG, "Start recording!");
            recorder.startRecording();
          }
          else {
            // Log.d(LTAG, "Stop recording!");
            recorder.stop();
            mEncoder.flush();
          }
          oldShouldRecord = mShouldRecord;
        }

        // If we're supposed to be recording, read data.
        if (mShouldRecord) {
          int result = recorder.read(buffer, bufsize);
          switch (result) {
            case AudioRecord.ERROR_INVALID_OPERATION:
              Log.e(LTAG, "Invalid operation.");
              mHandler.obtainMessage(MSG_READ_ERROR).sendToTarget();
              break;

            case AudioRecord.ERROR_BAD_VALUE:
              Log.e(LTAG, "Bad value.");
              mHandler.obtainMessage(MSG_READ_ERROR).sendToTarget();
              break;

            default:
              if (result > 0) {
                // Compute time recorded
                double read_ms = (1000.0 * result) / bytesPerSecond;
                mDuration += read_ms;

                //long start = System.currentTimeMillis();
                int write_result = mEncoder.write(buffer, result);
                if (write_result != result) {
                  Log.e(LTAG, "Attempted to write " + result
                      + " but only wrote " + write_result);
                  mHandler.obtainMessage(MSG_WRITE_ERROR).sendToTarget();
                }
                else {
                  Amplitudes amp = getAmplitudes();
                  mHandler.obtainMessage(MSG_AMPLITUDES, amp).sendToTarget();
                }
                //long end = System.currentTimeMillis();
                //Log.d(LTAG, "Write of " + result + " bytes took " + (end - start) + " msec.");
              }
          }
        }
      }

      recorder.release();
      mEncoder.release();
      mEncoder = null;

    } catch (IllegalArgumentException ex) {
      Log.e(LTAG, "Illegal argument: " + ex.getMessage());
      mHandler.obtainMessage(MSG_ILLEGAL_ARGUMENT, ex.getMessage()).sendToTarget();
    }

    mHandler.obtainMessage(MSG_OK).sendToTarget();
  }



}

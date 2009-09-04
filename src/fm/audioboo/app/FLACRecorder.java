/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioFormat;

import android.os.Environment;

import java.io.File;

import fm.audioboo.jni.FLACStreamEncoder;

import android.util.Log;

/**
 * FIXME
 **/
public class FLACRecorder extends Thread
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "FLACRecorder";

  // Sample rate, channel config, format
  private static final int SAMPLE_RATE    = 22050;
  private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  private static final int FORMAT         = AudioFormat.ENCODING_PCM_16BIT;


  /***************************************************************************
   * Public data
   **/
  public boolean mShouldRun;


  /***************************************************************************
   * Private data
   **/
  private boolean     mShouldRecord;


  /***************************************************************************
   * Implementation
   **/
  public FLACRecorder()
  {
  }


  public void resumeRecording()
  {
    mShouldRecord = true;
  }


  public void pauseRecording()
  {
    mShouldRecord = false;
  }


  private int mapChannelConfig(int channelConfig)
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


  private int mapFormat(int format)
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
    mShouldRecord = false;

    try {
      // Set up recorder
      int bufsize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG,
          FORMAT);
      if (AudioRecord.ERROR_BAD_VALUE == bufsize) {
        Log.e(LTAG, "Sample rate, channel config or format not supported!");
        // FIXME report to caller
        return;
      }
      if (AudioRecord.ERROR == bufsize) {
        Log.e(LTAG, "Unable to query hardware!");
        // FIXME report to caller
        return;
      }

      // Let's be generous with the buffer size... beats underruns
      bufsize *= 2;

      AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE, CHANNEL_CONFIG, FORMAT, bufsize);

      // Set up encoder
      // FIXME path
      String foo = Environment.getExternalStorageDirectory().getPath();
      File f = new File(foo + "/data/fm.audioboo.app");
      f.mkdirs();
      foo += "/data/fm.audioboo.app/asdf.flac";
      FLACStreamEncoder encoder = new FLACStreamEncoder(foo, SAMPLE_RATE,
          mapChannelConfig(CHANNEL_CONFIG), mapFormat(FORMAT));

      // Start recording loop
      byte[] buffer = new byte[bufsize];
      boolean oldShouldRecord = mShouldRecord;
      while (mShouldRun) {
        // Toggle recording state, if necessary
        if (mShouldRecord != oldShouldRecord) {
          // State changed! Let's see what we are supposed to do.
          if (mShouldRecord) {
            Log.d(LTAG, "Start recording!");
            recorder.startRecording();
          }
          else {
            Log.d(LTAG, "Stop recording!");
            recorder.stop();
          }
          oldShouldRecord = mShouldRecord;
        }

        // If we're supposed to be recording, read data.
        if (mShouldRecord) {
          int result = recorder.read(buffer, 0, bufsize);
          switch (result) {
            case AudioRecord.ERROR_INVALID_OPERATION:
              Log.e(LTAG, "INvalid operation");
              break;

            case AudioRecord.ERROR_BAD_VALUE:
              Log.e(LTAG, "Bad value");
              break;

            default:
              if (result > 0) {
                Log.d(LTAG, "Read: " + result);
                int write_result = encoder.write(buffer, result);
                Log.d(LTAG, "Wrote: " + write_result);
              }
          }
        }
      }

      recorder.release();
      encoder.release();

    } catch (IllegalArgumentException ex) {
      Log.e(LTAG, "Illegal argument: " + ex.getMessage());
      // FIXME report to caller
    }
  }
}

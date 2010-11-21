/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.content.Context;

import android.os.Environment;

import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioManager;

import java.io.File;

import fm.audioboo.jni.FLACStreamDecoder;

import java.nio.ByteBuffer;

import android.util.Log;

/**
 * Plays FLAC audio files.
 **/
public class FLACPlayer extends Thread
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "FLACPlayer";

  // Sleep time (in msec) when playback is paused. Will be interrupted, so
  // this can be arbitrarily large.
  private static final int PAUSED_SLEEP_TIME  = 10 * 60 * 1000;


  /***************************************************************************
   * Public data
   **/
  // Flag that keeps the thread running when true.
  public boolean mShouldRun;


  /***************************************************************************
   * Listener that informs the user of errors and end of playback.
   **/
  public static abstract class PlayerListener
  {
    public abstract void onError();
    public abstract void onFinished();
  }


  /***************************************************************************
   * Private data
   **/
  // Context in which this object was created
  private Context           mContext;

  // Stream decoder.
  private FLACStreamDecoder mDecoder;

  // Audio track
  private AudioTrack        mAudioTrack;

  // File path for the output file.
  private String            mPath;

  // Flag; determines whether playback is paused or not.
  private boolean           mPaused;

  // Listener.
  private PlayerListener    mListener;


  /***************************************************************************
   * Implementation
   **/
  public FLACPlayer(Context context, String path)
  {
    mContext = context;
    mPath = path;

    mShouldRun = true;
    mPaused = true;
  }



  public void pausePlayback()
  {
    mPaused = true;
    interrupt();
  }



  public void resumePlayback()
  {
    mPaused = false;
    interrupt();
  }



  public void setListener(PlayerListener listener)
  {
    mListener = listener;
  }



  public void run()
  {
    // Try to initialize the decoder.
    mDecoder = new FLACStreamDecoder(mPath);

    // Map channel config & format
    int sampleRate = mDecoder.sampleRate();
    int channelConfig = mapChannelConfig(mDecoder.channels());
    int format = mapFormat(mDecoder.bitsPerSample());

    // Determine buffer size
    int decoder_bufsize = mDecoder.minBufferSize();
    int playback_bufsize = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
        format);
    int bufsize = Math.max(playback_bufsize, decoder_bufsize);

    // Create AudioTrack.
    try {
      mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
          channelConfig, format, bufsize, AudioTrack.MODE_STREAM);
      mAudioTrack.play();

      ByteBuffer buffer = ByteBuffer.allocateDirect(bufsize);
      byte[] tmpbuf = new byte[bufsize];
      while (mShouldRun) {
        try {
          // If we're paused, just sleep the thread
          if (mPaused) {
            sleep(PAUSED_SLEEP_TIME);
            continue;
          }

          // Otherwise, play back a chunk.
          int read = mDecoder.read(buffer, bufsize);
          if (read <= 0) {
            // We're done with playing back!
            break;
          }

          buffer.rewind();
          buffer.get(tmpbuf, 0, read);
          mAudioTrack.write(tmpbuf, 0, read);
        } catch (InterruptedException ex) {
          // We'll pass through to the next iteration. If mShouldRun has
          // been set to false, the thread will terminate. If mPause has
          // been set to true, we'll sleep in the next interation.
        }
      }

      mAudioTrack.stop();
      mAudioTrack.release();
      mAudioTrack = null;
      mDecoder.release();
      mDecoder = null;

      if (null != mListener) {
        mListener.onFinished();
      }

    } catch (IllegalArgumentException ex) {
      Log.e(LTAG, "Could not initialize AudioTrack.");

      if (null != mListener) {
        mListener.onError();
      }
    }
  }



  private int mapChannelConfig(int channels)
  {
    switch (channels) {
      case 1:
        return AudioFormat.CHANNEL_CONFIGURATION_MONO;

      case 2:
        return AudioFormat.CHANNEL_CONFIGURATION_STEREO;

      default:
        throw new IllegalArgumentException("Only supporting one or two channels!");
    }
  }



  private int mapFormat(int bits_per_sample)
  {
    switch (bits_per_sample) {
      case 8:
        return AudioFormat.ENCODING_PCM_8BIT;

      case 16:
        return AudioFormat.ENCODING_PCM_16BIT;

      default:
        throw new IllegalArgumentException("Only supporting 8 or 16 bit samples!");
    }
  }
}

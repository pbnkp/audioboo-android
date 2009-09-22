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


  /***************************************************************************
   * Public data
   **/
  // Flag that keeps the thread running when true.
  public boolean mShouldRun;


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
  private String            mRelativeFilePath;

  // Base path, prepended before mRelativeFilePath. It's on the external
  // storage and includes the file bundle.
  private String            mBasePath;



  /***************************************************************************
   * Implementation
   **/
  public FLACPlayer(Context context, String relativeFilePath)
  {
    mContext = context;
    mRelativeFilePath = relativeFilePath;
  }



  public void run()
  {
    mShouldRun = true;

    // Try to initialize the decoder.
    String path = getBasePath() + File.separator + mRelativeFilePath;
    mDecoder = new FLACStreamDecoder(path);

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


      byte[] buffer = new byte[bufsize];
      do {
        int read = mDecoder.read(buffer, bufsize);
        if (read <= 0) {
          break;
        }

        mAudioTrack.write(buffer, 0, read);
      } while (true);

      mAudioTrack.stop();
      mAudioTrack.release();
      mAudioTrack = null;
      mDecoder.release();
      mDecoder = null;

    } catch (IllegalArgumentException ex) {
      Log.e(LTAG, "Could not initialize AudioTrack.");
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



  private String getBasePath()
  {
    if (null == mBasePath) {
      String base = Environment.getExternalStorageDirectory().getPath();
      base += File.separator + "data" + File.separator + mContext.getPackageName();
      mBasePath = base;
    }
    return mBasePath;
  }


}

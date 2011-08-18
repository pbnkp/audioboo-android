/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.content.Context;

import fm.audioboo.application.FLACPlayer;
import fm.audioboo.application.Boo;

import android.util.Log;

/**
 * Player for FLAC files, which are presumed to be local.
 **/
public class FLACPlayerWrapper extends PlayerBase
{
  /***************************************************************************
   * Private constants
   **/
  private static String LTAG = "FLACPlayerWrapper";


  /***************************************************************************
   * Private data
   **/
  // Player API
  private FLACPlayer  mFlacPlayer;


  /***************************************************************************
   * Implementation
   **/
  public FLACPlayerWrapper(BooPlayer player)
  {
    super(player);
  }



  public boolean prepare(Boo boo)
  {
    final Context ctx = getContext();
    if (null == ctx) {
      Log.e(LTAG, "Context is dead, won't play.");
      return false;
    }

    final BooPlayer player = mPlayer.get();
    if (null == player) {
      Log.e(LTAG, "BooPlayer is dead, won't play.");
      return false;
    }


    // Flatten audio file before we can start playback. This call will return
    // quickly if the file is already flattend, and will block while flattening.
    boo.flattenAudio();

    // Start playback
    String filename = boo.mData.mHighMP3Url.getPath();
    mFlacPlayer = new FLACPlayer(ctx, filename);

    mFlacPlayer.setListener(new FLACPlayer.PlayerListener() {
      public void onError()
      {
        player.setErrorState();
      }


      public void onFinished()
      {
        player.stopPlaying();
      }
    });

    mFlacPlayer.start();

    player.prepareSucceededUnlocked();
    return true;
  }



  public void pause()
  {
    if (null != mFlacPlayer) {
      mFlacPlayer.pausePlayback();
    }
  }



  public boolean resume()
  {
    if (null != mFlacPlayer) {
      mFlacPlayer.resumePlayback();
      return true;
    }
    return false;
  }



  public void stop()
  {
    if (null != mFlacPlayer) {
      mFlacPlayer.mShouldRun = false;
      mFlacPlayer.interrupt();
      mFlacPlayer = null;
    }
  }



  public void seekTo(long position)
  {
    if (null != mFlacPlayer) {
      mFlacPlayer.seekTo(position);
    }
  }



  public long getPosition()
  {
    if (null != mFlacPlayer) {
      return mFlacPlayer.currentPosition();
    }
    return 0;
  }
}

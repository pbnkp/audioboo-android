/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.content.Context;

import android.media.MediaPlayer;

import fm.audioboo.application.Boo;

import android.util.Log;

/**
 * Player for MP3 files; since it can play more than MP3s through the
 * underlying Android API, we'll call it API player.
 **/
public class APIPlayer extends PlayerBase
{
  /***************************************************************************
   * Private constants
   **/
  private static String LTAG = "APIPlayer";


  /***************************************************************************
   * Private data
   **/
  // Player API
  private MediaPlayer mMediaPlayer;


  /***************************************************************************
   * Implementation
   **/
  public APIPlayer(BooPlayer player)
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

    // Prepare player
    mMediaPlayer = MediaPlayer.create(ctx, boo.mData.mHighMP3Url);
    if (null == mMediaPlayer) {
      Log.e(LTAG, "Could not start playback of URI: " + boo.mData.mHighMP3Url);
      return false;
    }

    // Attach listeners to the player, for propagating state up to the users of this
    // class.
    mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
      public void onBufferingUpdate(MediaPlayer mp, int percent)
      {
        // If the player is playing/buffering, and that changed, we want to
        // switch to the opposite state. If the player is not playing, we don't.
        int state = Constants.STATE_NONE;
        if (mp.isPlaying()) {
          state = Constants.STATE_PLAYING;
        }
        else {
          state = Constants.STATE_BUFFERING;
        }

        boolean doInterrupt = false;
        synchronized (player.getLock())
        {
          // Strictly speaking, the getState() != state check happens in the
          // run() function, but why interrupt more than we really need?
          if (player.getPlaybackStateUnlocked() != state
            && ((Constants.STATE_PLAYING == player.getPlaybackStateUnlocked())
              || (Constants.STATE_BUFFERING == player.getPlaybackStateUnlocked())))
          {
            player.setPendingStateUnlocked(state);
            doInterrupt = true;
          }
        }
        if (doInterrupt) {
          player.interrupt();
        }
      }
    });

    mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      public void onCompletion(MediaPlayer mp)
      {
        player.stopPlaying();
      }
    });


    mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
      public boolean onError(MediaPlayer mp, int what, int extra)
      {
        player.setPendingState(Constants.STATE_ERROR);
        player.interrupt();
        return true;
      }
    });

    return true;
  }



  public void pause()
  {
    if (null == mMediaPlayer) {
      return;
    }
    mMediaPlayer.pause();
  }



  public void resume()
  {
    if (null == mMediaPlayer) {
      return;
    }
    mMediaPlayer.start();
  }



  public void stop()
  {
    if (null == mMediaPlayer) {
      return;
    }
    mMediaPlayer.stop();
    mMediaPlayer.release();
    mMediaPlayer = null;
  }
}


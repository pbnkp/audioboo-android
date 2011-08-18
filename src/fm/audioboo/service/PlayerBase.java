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

import java.lang.ref.WeakReference;

import fm.audioboo.application.Boo;

/**
 * Base class for a tiny class hierarchy that lets us abstract some of the
 * logic differences between playing back MP3s (remote) and FLACs (local)
 * into subclasses. XXX Note that for simplicity, we always assume FLAC
 * files to be local; MP3s on the other hand are streamed from any URI the
 * underlying API can handle.
 **/
abstract class PlayerBase
{
  /***************************************************************************
   * Protected data
   **/
  protected WeakReference<BooPlayer>  mPlayer;


  /***************************************************************************
   * Implementation
   **/
  public PlayerBase(BooPlayer player)
  {
    mPlayer = new WeakReference<BooPlayer>(player);
  }



  public Context getContext()
  {
    BooPlayer player = mPlayer.get();
    if (null == player) {
      return null;
    }
    return player.getContext();
  }


  /***************************************************************************
   * Interface
   **/
  // Sets up the player.
  abstract boolean prepare(Boo boo);

  // Pauses/resumes playback. Can only be called after start() has been
  // called. The resume() may return false if the player is not yet prepared
  // for playback.
  abstract void pause();
  abstract boolean resume();

  // Stops playback and also releases player resources; after this call
  // pause()/resume() won't work any longer.
  abstract void stop();

  // Seek within currently playing Boo.
  abstract void seekTo(long position);
  abstract long getPosition();
}

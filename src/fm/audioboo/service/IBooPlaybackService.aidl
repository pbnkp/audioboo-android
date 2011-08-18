/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import fm.audioboo.data.BooData;
import fm.audioboo.data.PlayerState;


/**
 * Service interface for BooPlaybackService.
 **/
interface IBooPlaybackService
{
  /**
   * Play the boo represented by BooData
   **/
  void play(in BooData boo, boolean playImmediately);

  /**
   * Stop/pause/resume playback.
   **/
  void stop();
  void pause();
  void resume();

  /**
   * Return the title, duration, etc. of the currently played boo, or null/0
   * if no boo is playing.
   **/
  PlayerState getState();

  /**
   * Seek to the given position. Does nothing if no Boo is playing.
   **/
  void seekTo(double position);
}

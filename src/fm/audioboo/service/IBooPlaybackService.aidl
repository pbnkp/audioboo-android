/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import fm.audioboo.data.BooData;


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
   * Return internal playback state; see Constants.STATE_*
   **/
  int getState();

  /**
   * Return the title, duration, etc. of the currently played boo, or null/0
   * if no boo is playing.
   **/
  String getTitle();
  String getUsername();
  double getDuration();
}

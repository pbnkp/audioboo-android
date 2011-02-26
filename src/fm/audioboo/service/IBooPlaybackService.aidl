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
  void play(in BooData boo);

  void stop();
  void pause();
  void resume();

  int getState();

  // FIXME progress listener

}

/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

public class Constants
{
  // The player state machine is complicated by the way the Android API's
  // MediaPlayer changes states. Suffice to say that while the player is
  // preparing, no other action can be taken immediately.
  // We solve that problem not by blocking until the player has finished
  // preparing, but by introducing a pending state. BooPlayer's functions
  // only set the pending state; once the player has finished preparing,
  // that pending state is entered.
  //
  // Some of these states are pseudo-states in that they do not affect
  // state transitions. In particular, STATE_ERROR and STATE_NONE are both
  // states from which the same transitions can be made. The same applies to
  // STATE_PLAYING and STATE_BUFFERING.
  //
  // Those states exist because they are of interest to the ProgressListener.
  // The ProgressListener will be sent the following states as they are entered
  // - STATE_PLAYING
  // - STATE_BUFFERING
  // - STATE_FINISHED aka STATE_NONE
  // - STATE_ERROR
  public static final int STATE_NONE            = 0;
  public static final int STATE_PREPARING       = 1;
  public static final int STATE_PAUSED          = 2;
  public static final int STATE_PLAYING         = 3;

  public static final int STATE_FINISHED        = 4;
  public static final int STATE_BUFFERING       = 5;
  public static final int STATE_ERROR           = 6;

  // Events
  public static final String EVENT_PROGRESS     = "fm.audioboo.service.events.progress";

  // Event data.
  public static final String PROGRESS_STATE     = "fm.audioboo.service.event.data.progress-state";
  public static final String PROGRESS_PROGRESS  = "fm.audioboo.service.event.data.progress-progress";
  public static final String PROGRESS_TOTAL     = "fm.audioboo.service.event.data.progress-total";


}

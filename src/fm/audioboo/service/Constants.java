/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
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
  // state transitions.
  public static final int STATE_NONE            = 0;
  public static final int STATE_PREPARING       = 1;
  public static final int STATE_PAUSED          = 2;
  public static final int STATE_PLAYING         = 3;
  public static final int STATE_FINISHED        = 4; // Similar to STATE_PAUSED in that resume() works, but
                                                     // internally, things are quite different.

  // Pseudo-states
  public static final int STATE_BUFFERING       = 5; // Same as STATE_PLAYING; temporary state.
  public static final int STATE_ERROR           = 6;

  // Upload chunk size.
  public static final int MIN_UPLOAD_CHUNK_SIZE = 1024 * 50;
  public static final int MAX_UPLOAD_CHUNK_SIZE = 1024 * 1000;
  public static final int MIN_UPLOAD_TIME       = 10000; // msec
  public static final int MAX_UPLOAD_TIME       = 40000; // msec

  // Notification IDs
  public static final int  NOTIFICATION_PLAYING_BACK  = 0;
  public static final int  NOTIFICATION_UPLOADING     = 1;
  public static final int  NOTIFICATION_UPLOAD_DONE   = 2;
  public static final int  NOTIFICATION_UPLOAD_ERROR  = 3;
  public static final int  NOTIFICATION_MESSAGES      = 4;

  // Preference keys
  public static final String PREF_POLL_INTERVAL = "poll-interval";
}

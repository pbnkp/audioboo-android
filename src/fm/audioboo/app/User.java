/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.net.Uri;

/**
 * Representation of an AudioBoo user's data.
 **/
public class User
{
  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int    mId;          // Unique user ID
  public String mUsername;    // Username

  // URLs associated with the user.
  public Uri    mProfileUrl;  // Profile url
  public Uri    mImageUrl;    // Avatar/image url

  // Usage statistics.
  public int    mFollowers;   // # users following this one
  public int    mFollowings;  // # users this one follows
  public int    mAudioClips;  // # clips this user uploaded
}

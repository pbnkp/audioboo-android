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

import java.util.Date;
import java.util.LinkedList;

/**
 * Representation of a Boo's data.
 **/
public class Boo
{
  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int                mId;
  public String             mTitle;

  public float              mDuration;

  public LinkedList<String> mTags;

  public User               mUser;

  // Timestamps
  public Date               mRecordedAt;
  public Date               mUploadedAt;

  // TODO location!

  // URLs associated with the Boo.
  public Uri                mHighMP3Url;
  public Uri                mImageUrl;
  public Uri                mDetailUrl;

  // Usage statistics.
  public int                mPlays;
  public int                mComments;
}

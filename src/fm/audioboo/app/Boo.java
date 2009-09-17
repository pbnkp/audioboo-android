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

  public double             mDuration;

  public LinkedList<String> mTags;

  public User               mUser;

  // Timestamps
  public Date               mRecordedAt;
  public Date               mUploadedAt;

  // Boo location
  public Location           mLocation;

  // URLs associated with the Boo.
  public Uri                mHighMP3Url;
  public Uri                mImageUrl;
  public Uri                mDetailUrl;

  // Usage statistics.
  public int                mPlays;
  public int                mComments;


  public String toString()
  {
    return String.format("<%d:%s:%f:[%s]>", mId, mTitle, mDuration, mUser);
  }
}

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
 * Representation of a Tag for a Boo.
 **/
public class Tag
{
  /***************************************************************************
   * Public data
   **/
  // Display vs. normalised version of the tag.
  public String mDisplay;
  public String mNormalised;

  // URL leading to boos for that tag.
  public Uri    mUrl;


  public String toString()
  {
    return mDisplay;
  }
}

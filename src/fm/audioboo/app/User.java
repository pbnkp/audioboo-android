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

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

/**
 * Representation of an AudioBoo user's data.
 **/
public class User implements Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int            mId;          // Unique user ID
  public String         mUsername;    // Username

  // URLs associated with the user.
  public transient Uri  mProfileUrl;  // Profile url
  public transient Uri  mImageUrl;    // Avatar/image url

  // Usage statistics.
  public int            mFollowers;   // # users following this one
  public int            mFollowings;  // # users this one follows
  public int            mAudioClips;  // # clips this user uploaded


  public String toString()
  {
    return String.format("<%d:%s:%d/%d/%d>", mId, mUsername, mFollowers,
        mFollowings, mAudioClips);
  }




  /***************************************************************************
   * Serializable implementation
   **/
  private void writeObject(java.io.ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject();

    out.writeObject(null != mProfileUrl ? mProfileUrl.toString() : null);
    out.writeObject(null != mImageUrl ? mImageUrl.toString() : null);
  }



  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();

    String s = (String) in.readObject();
    if (null != s) {
      mProfileUrl = Uri.parse(s);
    }

    s = (String) in.readObject();
    if (null != s) {
      mImageUrl = Uri.parse(s);
    }
  }
}

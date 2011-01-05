/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.net.Uri;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

/**
 * Representation of a Tag for a Boo.
 **/
public class Tag implements Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Display vs. normalised version of the tag.
  public String         mDisplay;
  public String         mNormalised;

  // URL leading to boos for that tag.
  public transient Uri  mUrl;


  public String toString()
  {
    return mDisplay;
  }



  /***************************************************************************
   * Serializable implementation
   **/
  private void writeObject(java.io.ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject();
    out.writeObject(null != mUrl ? mUrl.toString() : null);
  }



  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();
    String s = (String) in.readObject();
    if (null != s) {
      mUrl = Uri.parse(s);
    }
  }
}

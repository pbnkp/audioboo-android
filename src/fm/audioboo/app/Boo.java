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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.io.IOException;
import java.io.FileNotFoundException;

import android.util.Log;

/**
 * Representation of a Boo's data.
 **/
public class Boo implements Serializable
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG = "Boo";



  /***************************************************************************
   * Public constants
   **/
  // Serialized file extension.
  public static final String EXTENSION = ".boo";



  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int                mId;
  public String             mTitle;

  public double             mDuration;

  public LinkedList<Tag>    mTags;

  public User               mUser;

  // Timestamps
  public Date               mRecordedAt;
  public Date               mUploadedAt;

  // Boo location
  public BooLocation        mLocation;

  // URLs associated with the Boo.
  public transient Uri      mHighMP3Url;
  public transient Uri      mImageUrl;
  public transient Uri      mDetailUrl;

  // Usage statistics.
  public int                mPlays;
  public int                mComments;


  /**
   * Tries to construct a Boo from a file with the given filename.
   **/
  public static Boo constructFromFile(String filename)
  {
    File f = new File(filename);
    if (!f.exists()) {
      return null;
    }


    try {
      ObjectInputStream is = new ObjectInputStream(new FileInputStream(f));
      Boo boo = (Boo) is.readObject();
      is.close();

      return boo;
    } catch (FileNotFoundException ex) {
      Log.e(LTAG, "File not found: " + filename);
    } catch (ClassNotFoundException ex) {
      Log.e(LTAG, "Class not found: " + filename);
    } catch (IOException ex) {
      Log.e(LTAG, "Error reading file: " + filename);
    }

    return null;
  }



  /**
   * Serializes the Boo class to a file specified by the given filename.
   **/
  public void writeToFile(String filename)
  {
    try {
      ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(filename)));
      os.writeObject(this);
      os.flush();
      os = null;
    } catch (FileNotFoundException ex) {
      Log.e(LTAG, "File not found: " + filename);
    } catch (IOException ex) {
      Log.e(LTAG, "Error writing file '" + filename + "': " + ex.getMessage());
    }
  }



  public String toString()
  {
    return String.format("<%d:%s:%f:[%s]>", mId, mTitle, mDuration, mUser);
  }



  /***************************************************************************
   * Serializable implementation
   **/
  private void writeObject(java.io.ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject();

    out.writeObject(null != mHighMP3Url ? mHighMP3Url.toString() : null);
    out.writeObject(null != mImageUrl ? mImageUrl.toString() : null);
    out.writeObject(null != mDetailUrl ? mDetailUrl.toString() : null);
  }



  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();

    String s = (String) in.readObject();
    if (null != s) {
      mHighMP3Url = Uri.parse(s);
    }

    s = (String) in.readObject();
    if (null != s) {
      mImageUrl = Uri.parse(s);
    }

    s = (String) in.readObject();
    if (null != s) {
      mDetailUrl = Uri.parse(s);
    }
  }
}

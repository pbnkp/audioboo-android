/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009,2010 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.net.Uri;

import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.UUID;

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
   * Recording metadata.
   **/
  public static class Recording implements Serializable
  {
    String  mFilename;
    double  mDuration;

    public Recording(String filename, double duration)
    {
      mFilename = filename;
      mDuration = duration;
    }


    public Recording(String filename)
    {
      mFilename = filename;
    }


    public String toString()
    {
      return String.format("<%f:%s>", mDuration, mFilename);
    }
  }



  /***************************************************************************
   * Public constants
   **/
  // Serialized file extension.
  public static final String EXTENSION = ".boo";
  // Data dir extension
  public static final String DATA_EXTENSION = ".data";
  // Recording extension
  public static final String RECORDING_EXTENSION = ".rec";
  // Image file name
  public static final String IMAGE_FILE = "image.png";
  public static final String TEMP_IMAGE_FILE = "image.png";

  // Serialization UID
  public static final long serialVersionUID = 5505418760954089521L;



  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int                    mId;
  public String                 mTitle;

  // UUID. We generate this when creating new Boos.
  public UUID                   mUUID;

  public double                 mDuration; // XXX Deprecated; use getDuration()

  public List<Tag>              mTags;

  public User                   mUser;

  // Timestamps
  public Date                   mRecordedAt;
  public Date                   mUpdatedAt;
  public Date                   mUploadedAt;

  // Boo location
  public BooLocation            mLocation;

  // URLs associated with the Boo.
  public transient Uri          mHighMP3Url;
  public transient Uri          mImageUrl;
  public transient Uri          mDetailUrl;

  // Local information
  public String                 mFilename;
  //   Paths pointing to this Boo's recordings, in order.
  public List<Recording>        mRecordings;

  // Usage statistics.
  public int                    mPlays;
  public int                    mComments;


  /***************************************************************************
   * Implementation
   **/
  public Boo()
  {
    mUUID = UUID.randomUUID();
  }



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

      if (null == boo.mUpdatedAt) {
        boo.mUpdatedAt = new Date(f.lastModified());
      }
      if (null == boo.mFilename) {
        boo.mFilename = filename;
      }
      if (null == boo.mRecordings) {
        boo.mRecordings = new LinkedList<Recording>();
        if (null != boo.mHighMP3Url && boo.mHighMP3Url.getScheme().equals("file")) {
          boo.mRecordings.add(new Recording(boo.mHighMP3Url.getPath(),
                boo.mDuration));
        }
      }

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
  public void writeToFile()
  {
    if (null == mFilename) {
      throw new IllegalStateException("No filename set when attempting to save Boo.");
    }
    writeToFile(mFilename);
  }



  public void writeToFile(String filename)
  {
    mUpdatedAt = new Date();

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



  /**
   * Returns the Boo's duration. If the Boo is downloaded, this function returns
   * mDuration. If it's locally recorded, it returns the accumulated duration of
   * all individual recordings.
   **/
  public double getDuration()
  {
    if (null != mRecordings && mRecordings.size() > 0) {
      double duration = 0;
      for (Recording rec : mRecordings) {
        duration += rec.mDuration;
      }
      return duration;
    }

    // Still valid for downloaded Boos.
    return mDuration;
  }



  /**
   * Returns a new Recording metadata object for use in recording Boos. If the
   * latest known Recording is empty (does not have a duration), that is
   * returned, otherwise a new Recording is created and registered.
   **/
  public Recording getLastEmptyRecording()
  {
    if (null == mRecordings) {
      mRecordings = new LinkedList<Recording>();
    }

    Recording rec = null;
    if (0 != mRecordings.size()) {
      Recording r = mRecordings.get(mRecordings.size() - 1);
      if (0 == r.mDuration) {
        rec = r;
      }
    }

    if (null == rec) {
      rec = new Recording(
          Globals.get().getBooManager().getNewRecordingFilename(this));
      mRecordings.add(rec);
    }

    return rec;
  }




  public String toString()
  {
    return String.format("<%d:%s:%f:[%s]:%d>", mId, mTitle, getDuration(), mUser,
        (null == mRecordings ? 0 : mRecordings.size()));
  }



  public String getImageFilename()
  {
    return Globals.get().getBooManager().getImageFilename(this);
  }



  public String getTempImageFilename()
  {
    return Globals.get().getBooManager().getTempImageFilename(this);
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

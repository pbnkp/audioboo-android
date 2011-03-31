/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import java.util.List;
import java.util.LinkedList;

import java.io.File;
import java.io.FileFilter;

import java.nio.ByteBuffer;

import java.util.zip.CRC32;

import android.util.Log;


/**
 * Like a file manager, except for Boos.
 *
 * BooManager searches multiple paths for serialized Boos. It also contains
 * convenience functions like e.g. finding the latest Boo, etc.
 **/
public class BooManager
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooManager";


  /***************************************************************************
   * FileFilter for Boos
   **/
  public static class BooFileFilter implements FileFilter
  {
    public boolean accept(File f)
    {
      if (!(f.exists() && f.isFile())) {
        //Log.w(LTAG, "Entry '" + f + "' is not a file.");
        return false;
      }

      if (!(f.canRead() && f.canWrite())) {
        //Log.w(LTAG, "Do not have r/w access to '" + f + "'.");
        return false;
      }

      // Try to construct a Boo from it. That's simplest for checking contents.
      Boo b = Boo.constructFromFile(f.getPath());
      if (null == b) {
        //Log.w(LTAG, "Entry '" + f + "' is not a valid Boo file.");
        return false;
      }

      return true;
    }
  }


  /***************************************************************************
   * Private data
   **/
  // Paths to find.
  private List<String>  mPaths;
  // Preferred path for creating new Boos; an index into mPaths
  private int           mCreateIndex;
  // Boos found.
  private List<Boo>     mDrafts;
  private List<Boo>     mBooUploads;
  private List<Boo>     mMessageUploads;




  /***************************************************************************
   * Implementation
   **/
  public BooManager(List<String> paths)
  {
    mPaths = paths;
    mCreateIndex = 0;
    rebuildIndex();
  }



  public BooManager(List<String> paths, int createIndex)
  {
    if (null == paths || 0 == paths.size()) {
      throw new IllegalArgumentException("BooManager requires at least one path.");
    }
    mPaths = paths;

    if (createIndex < 0 || createIndex >= mPaths.size()) {
      throw new IllegalArgumentException("Invalid createIndex");
    }
    mCreateIndex = createIndex;

    rebuildIndex();
  }



  public List<Boo> getDrafts()
  {
    // FIXME getBooDrafts()
    return mDrafts;
  }



  public List<Boo> getMessageDrafts()
  {
    // FIXME
    return null;
  }



  public List<Boo> getBooUploads()
  {
    return mBooUploads;
  }



  public List<Boo> getMessageUploads()
  {
    return mMessageUploads;
  }



  public Boo getLatestDraft()
  {
    if (null == mDrafts) {
      return null;
    }

    Boo latest = null;
    for (Boo b : mDrafts) {
      if (null == latest) {
        latest = b;
        continue;
      }

      if (b.mData.mUpdatedAt.after(latest.mData.mUpdatedAt)) {
        latest = b;
      }
    }

    return latest;
  }



  public Boo createBoo()
  {
    // First determine what the preferred directory is for creating Boos.
    String path = mPaths.get(mCreateIndex);

    // Make sure the create path exists and is a directory.
    File d = new File(path);
    if (!d.exists()) {
      d.mkdirs();
    }
    if (!d.isDirectory()) {
      throw new IllegalStateException("Create path '" + path + "' either does not exist, or exists but is not a directory.");
    }

    // Now create a new name for the Boo file. To avoid collisions, we'll
    // just create a hash of the current system time.
    String filename = null;
    boolean exists = false;
    do {
      // Write time to bytes.
      ByteBuffer buf = ByteBuffer.allocateDirect(Long.SIZE / 8);
      buf.putLong(System.currentTimeMillis());
      byte[] bytes = new byte[Long.SIZE / 8];
      buf.position(0);
      buf.get(bytes);

      // Create hash.
      CRC32 crc = new CRC32();
      crc.update(bytes, 0, bytes.length);

      // Convert hash value to string.
      buf.rewind();
      buf.putInt(((Long) crc.getValue()).intValue());
      buf.position(0);
      buf.get(bytes);

      String name = "";
      for (int i = 0 ; i < Long.SIZE / 16 ; ++i) {
        name += Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1);
      }

      // Full name.
      filename = name + Boo.EXTENSION;

      // Figure out if a Boo file with that name already exists.
      exists = false;
      for (File f : d.listFiles()) {
        if (f.getName().endsWith(filename)) {
          exists = true;
          break;
        }
      }

    } while (exists);

    // Alright, we have a filename. Now create a Boo with that name!
    Boo boo = new Boo();
    boo.mData.mFilename = path + File.separator + filename;
    return boo;
  }



  public String getImageFilename(Boo boo)
  {
    String data_dir = ensureDataDir(boo);
    if (null == data_dir) {
      return null;
    }
    return data_dir + File.separator + Boo.IMAGE_FILE;
  }



  public String getTempImageFilename(Boo boo)
  {
    String data_dir = ensureDataDir(boo);
    if (null == data_dir) {
      return null;
    }
    return data_dir + File.separator + Boo.TEMP_IMAGE_FILE;
  }




  public String getNewRecordingFilename(Boo boo)
  {
    String data_dir = ensureDataDir(boo);
    if (null == data_dir) {
      return null;
    }

    // Now search the data dir for recording files. We'll create a new recording
    // file with a higher sequence number.
    File d = new File(data_dir);
    int seq = 0;
    for (File f : d.listFiles()) {
      String name = f.getName();
      if (!name.endsWith(Boo.RECORDING_EXTENSION)) {
        // Ignore non-recording files.
        continue;
      }
      name = name.substring(0, name.lastIndexOf(Boo.RECORDING_EXTENSION));

      try {
        Integer value = Integer.valueOf(name);

        if (value >= seq) {
          seq = value + 1;
        }
      } catch (NumberFormatException ex) {
        // pass
      }
    }

    // Alright, now construct a new file name with that sequence number.
    return data_dir + File.separator + String.format("%d", seq) + Boo.RECORDING_EXTENSION;
  }



  public void rebuildIndex()
  {
    List<Boo> drafts = new LinkedList<Boo>();
    List<Boo> booUploads = new LinkedList<Boo>();
    List<Boo> messageUploads = new LinkedList<Boo>();

    BooFileFilter filter = new BooFileFilter();

    for (String path : mPaths) {
      Log.i(LTAG, "Searching for Boos in path '" + path + "'...");

      File d = new File(path);
      if (!d.exists()) {
        Log.w(LTAG, "Path '" + path + "' does not exist, can't find Boos there.");
        continue;
      }

      if (!d.isDirectory()) {
        Log.w(LTAG, "Path '" + path + "' is not a directory, can't find Boos there.");
        continue;
      }

      // Make sure that a .nomedia file exists in each search directory.
      File nomedia = new File(path + File.separator + ".nomedia");
      if (!nomedia.exists()) {
        try {
          nomedia.createNewFile();
        } catch (java.io.IOException ex) {
          Log.w(LTAG, "Could not create .nomedia file in '" + path + "'.");
        }
      }

      File[] localBoos = d.listFiles(filter);
      if (null == localBoos) {
        Log.w(LTAG, "Error reading Boos from path '" + path + "'.");
        continue;
      }

      for (File f : localBoos) {
        Boo b = Boo.constructFromFile(f.getPath());
        if (null == b) {
          Log.w(LTAG, "Could not construct Boo from '" + f + "'.");
          continue;
        }

        if (null == b.mData.mUploadInfo) {
          drafts.add(b);
        }
        else {
          if (b.mData.mIsMessage) {
            messageUploads.add(b);
          }
          else {
            booUploads.add(b);
          }
        }
      }
    }

    if (0 == drafts.size()) {
      Log.w(LTAG, "No Boos found!");
    }

    mDrafts = drafts;
    mMessageUploads = messageUploads;
    mBooUploads = booUploads;
  }




  private String ensureDataDir(Boo boo)
  {
    if (null == boo) {
      return null;
    }

    // Get data dir filename.
    String data_dir = boo.mData.mFilename;
    if (data_dir.endsWith(Boo.EXTENSION)) {
      data_dir = data_dir.substring(0, data_dir.lastIndexOf(Boo.EXTENSION));
    }
    else {
      Log.w(LTAG, "Invalid Boo file extension: " + data_dir);
    }
    data_dir += Boo.DATA_EXTENSION;

    // Make sure data dir exists.
    File d = new File(data_dir);
    if (!d.exists()) {
      d.mkdirs();
    }

    return data_dir;
  }



  public void deleteBoo(Boo boo)
  {
    String data_dir = ensureDataDir(boo);
    if (null != data_dir) {
      File d = new File(data_dir);
      if (d.exists() && d.isDirectory()) {
        for (File f : d.listFiles()) {
          f.delete();
        }
        d.delete();
      }
    }

    File f = new File(boo.mData.mFilename);
    f.delete();
  }
}

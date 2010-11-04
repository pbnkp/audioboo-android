/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2010 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import java.util.List;
import java.util.LinkedList;

import java.io.File;
import java.io.FileFilter;

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
        Log.w(LTAG, "Entry '" + f + "' ist not a file.");
        return false;
      }

      if (!(f.canRead() && f.canWrite())) {
        Log.w(LTAG, "Do not have r/w access to '" + f + "'.");
        return false;
      }

      // Try to construct a Boo from it. That's simplest for checking contents.
      Boo b = Boo.constructFromFile(f.getPath());
      if (null == b) {
        Log.w(LTAG, "Entry '" + f + "' is not a valid Boo file.");
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
  // Boos found.
  private List<Boo>     mBooList;



  /***************************************************************************
   * Implementation
   **/
  public BooManager(List<String> paths)
  {
    mPaths = paths;
    rebuildIndex();
  }



  public List<Boo> getBoos()
  {
    return mBooList;
  }



  public Boo getLatestBoo()
  {
    if (null == mBooList) {
      return null;
    }

    Boo latest = null;
    for (Boo b : mBooList) {
      if (null == latest) {
        latest = b;
        continue;
      }

      if (b.mUpdatedAt.after(latest.mUpdatedAt)) {
        latest = b;
      }
    }

    return latest;
  }



  public void rebuildIndex()
  {
    List<Boo> boos = new LinkedList<Boo>();
    BooFileFilter filter = new BooFileFilter();

    for (String path : mPaths) {
      File d = new File(path);
      if (!d.exists()) {
        Log.w(LTAG, "Path '" + path + "' does not exist, can't find Boos there.");
        continue;
      }

      if (!d.isDirectory()) {
        Log.w(LTAG, "Path '" + path + "' is not a directory, can't find Boos there.");
        continue;
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

        boos.add(b);
      }
    }

    if (0 == boos.size()) {
      Log.w(LTAG, "No Boos found!");
    }

    mBooList = boos;
  }

}

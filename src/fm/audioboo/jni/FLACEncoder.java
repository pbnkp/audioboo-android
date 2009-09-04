/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.jni;

/**
 * This is *not* a full JNI wrapper for the FLAC codec, but merely exports
 * the minimum of functions necessary for the purposes of the AudioBoo client.
 **/
public class FLACEncoder
{
  /***************************************************************************
   * Exception class, thrown by native code
   **/
  public static class FLACException extends Exception
  {
  }


  /***************************************************************************
   * Interface
   **/
  public FLACEncoder()
  {
    init();
  }



  protected void finalize() throws Throwable
  {
    try {
      deinit();
    } finally {
      super.finalize();
    }
  }


  /***************************************************************************
   * JNI Implementation
   **/

  // Pointer to opaque data in C
  private long  mObject;

  // Constructor equivalent
  native private void init();

  // Destructor equivalent
  native private void deinit();

  // Load native library
  static {
    System.loadLibrary("audioboo-native");
  }
}

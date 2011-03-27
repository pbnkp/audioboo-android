/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.data;

import android.os.Parcelable;
import android.os.Parcel;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;



/**
 * Encapsulates enough information about uploads so that we can resume them.
 **/
public class UploadInfo implements Parcelable, Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Chunk size and last chunk successfully processed. Offsets into the data
  // can be calculated from there.
  public int      mChunkSize;
  public int      mLastFinishedChunk;


  /***************************************************************************
   * Parcelable implementation
   **/
  public int describeContents()
  {
    return 0;
  }



  public void writeToParcel(Parcel out, int flags)
  {
    out.writeInt(mChunkSize);
    out.writeInt(mLastFinishedChunk);
  }



  public static final Parcelable.Creator<UploadInfo> CREATOR = new Parcelable.Creator<UploadInfo>()
  {
    public UploadInfo createFromParcel(Parcel in)
    {
      return new UploadInfo(in);
    }

    public UploadInfo[] newArray(int size)
    {
      return new UploadInfo[size];
    }
  };



  private UploadInfo(Parcel in)
  {
    mChunkSize = in.readInt();
    mLastFinishedChunk = in.readInt();
  }



  /***************************************************************************
   * Serializable implementation
   **/
  private void writeObject(java.io.ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject();
  }



  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();
  }
}

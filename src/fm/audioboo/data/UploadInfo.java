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

import fm.audioboo.service.Constants;


/**
 * Encapsulates enough information about uploads so that we can resume them.
 **/
public class UploadInfo implements Parcelable, Serializable
{
  /***************************************************************************
   * Public constants
   **/
  // Upload stage; first the audio is uploaded, then the image, then metadata.
  public static final int UPLOAD_STAGE_AUDIO    = 0;
  public static final int UPLOAD_STAGE_IMAGE    = 1;
  public static final int UPLOAD_STAGE_METADATA = 2;


  /***************************************************************************
   * Public data
   **/
  // Chunk/attachment IDs we already know, and how much of each we've already
  // uploaded.
  public int      mAudioChunkId     = -1;
  public int      mAudioUploaded    = 0;
  public int      mImageChunkId     = -1;
  public int      mImageUploaded    = 0;
  public int      mUploadStage      = UPLOAD_STAGE_AUDIO;


  public UploadInfo()
  {
  }


  /***************************************************************************
   * Parcelable implementation
   **/
  public int describeContents()
  {
    return 0;
  }



  public void writeToParcel(Parcel out, int flags)
  {
    out.writeInt(mAudioChunkId);
    out.writeInt(mAudioUploaded);
    out.writeInt(mImageChunkId);
    out.writeInt(mImageUploaded);
    out.writeInt(mUploadStage);
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
    mAudioChunkId = in.readInt();
    mAudioUploaded = in.readInt();
    mImageChunkId = in.readInt();
    mImageUploaded = in.readInt();
    mUploadStage = in.readInt();
  }
}

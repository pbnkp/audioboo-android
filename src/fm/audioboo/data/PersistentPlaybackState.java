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
 * Representation of the player service's playback state.
 **/
public class PersistentPlaybackState implements Parcelable, Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Boo we're playing back, or null. If null, ignore the other fields.
  public BooData        mBooData;

  // Playback state
  public int            mState;
  public double         mProgress;


  public PersistentPlaybackState()
  {
  }



  public String toString()
  {
    return mBooData == null ? null : mBooData.toString();
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
    out.writeParcelable(mBooData, flags);
    out.writeInt(mState);
    out.writeDouble(mProgress);
  }



  public static final Parcelable.Creator<PersistentPlaybackState> CREATOR = new Parcelable.Creator<PersistentPlaybackState>()
  {
    public PersistentPlaybackState createFromParcel(Parcel in)
    {
      return new PersistentPlaybackState(in);
    }

    public PersistentPlaybackState[] newArray(int size)
    {
      return new PersistentPlaybackState[size];
    }
  };



  private PersistentPlaybackState(Parcel in)
  {
    mBooData = in.readParcelable(BooData.class.getClassLoader());
    mState = in.readInt();
    mProgress = in.readDouble();
  }
}

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
 * Player state. To be sent in onProgress() from the player.
 **/
public class PlayerState implements Parcelable, Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Current state. One of the Constants.STATE_* values;
  public int      mState;

  // Current progress/total.
  public double   mProgress;
  public double   mTotal;

  // Currently playing Boo
  public int      mBooId;
  public String   mBooTitle;
  public String   mBooUsername;
  public boolean  mBooIsMessage;
  public boolean  mBooIsLocal;


  public PlayerState()
  {
    mState = Constants.STATE_NONE;
    mProgress = 0f;
    mTotal = 0f;
    mBooId = -1;
    mBooTitle = null;
    mBooUsername = null;
    mBooIsMessage = false;
    mBooIsLocal = false;
  }


  public String toString()
  {
    return String.format("<%d|%f/%f|%d|%s|%s|%d|%d>", mState, mProgress, mTotal, mBooId,
        mBooTitle, mBooUsername, mBooIsMessage ? 1 : 0, mBooIsLocal ? 1 : 0);
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
    out.writeInt(mState);

    out.writeDouble(mProgress);
    out.writeDouble(mTotal);

    out.writeInt(mBooId);
    out.writeString(mBooTitle);
    out.writeString(mBooUsername);
    out.writeInt(mBooIsMessage ? 1 : 0);
  }



  public static final Parcelable.Creator<PlayerState> CREATOR = new Parcelable.Creator<PlayerState>()
  {
    public PlayerState createFromParcel(Parcel in)
    {
      return new PlayerState(in);
    }

    public PlayerState[] newArray(int size)
    {
      return new PlayerState[size];
    }
  };



  private PlayerState(Parcel in)
  {
    mState = in.readInt();

    mProgress = in.readDouble();
    mTotal = in.readDouble();

    mBooId = in.readInt();
    mBooTitle = in.readString();
    mBooUsername = in.readString();
    mBooIsMessage = (in.readInt() != 0);
  }
}

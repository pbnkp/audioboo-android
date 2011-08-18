/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
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
public class DestinationInfo implements Parcelable, Serializable
{
  /***************************************************************************
   * Public data
   **/
  // Destination is either a channel or a user.
  public int      mDestinationId;
  public boolean  mIsChannel;
  public String   mDestinationName;

  // If !mIsChannel, then mInReplyTo specifies the ID of a private message
  // this Boo is in reply to.
  public int      mInReplyTo = -1;


  public DestinationInfo()
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
    out.writeInt(mDestinationId);
    out.writeInt(mIsChannel ? 1 : 0);
    out.writeString(mDestinationName);
    out.writeInt(mInReplyTo);
  }



  public static final Parcelable.Creator<DestinationInfo> CREATOR = new Parcelable.Creator<DestinationInfo>()
  {
    public DestinationInfo createFromParcel(Parcel in)
    {
      return new DestinationInfo(in);
    }

    public DestinationInfo[] newArray(int size)
    {
      return new DestinationInfo[size];
    }
  };



  private DestinationInfo(Parcel in)
  {
    mDestinationId = in.readInt();
    mIsChannel = 0 != in.readInt();
    mDestinationName = in.readString();
    mInReplyTo = in.readInt();
  }
}

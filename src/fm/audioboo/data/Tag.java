/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.data;

import android.net.Uri;

import android.os.Parcelable;
import android.os.Parcel;


/**
 * Representation of a Tag for a Boo.
 **/
public class Tag implements Parcelable
{
  /***************************************************************************
   * Public data
   **/
  // Display vs. normalised version of the tag.
  public String         mDisplay;
  public String         mNormalised;

  // URL leading to boos for that tag.
  public transient Uri  mUrl;



  public Tag()
  {
  }



  public String toString()
  {
    return mDisplay;
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
    out.writeString(mDisplay);
    out.writeString(mNormalised);
  }



  public static final Parcelable.Creator<Tag> CREATOR = new Parcelable.Creator<Tag>()
  {
    public Tag createFromParcel(Parcel in)
    {
      return new Tag(in);
    }

    public Tag[] newArray(int size)
    {
      return new Tag[size];
    }
  };



  private Tag(Parcel in)
  {
    mDisplay = in.readString();
    mNormalised = in.readString();
  }
}

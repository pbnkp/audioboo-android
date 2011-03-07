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

import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.UUID;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;


/**
 * Representation of a Boo's data.
 **/
public class BooData implements Parcelable, Serializable
{
  /***************************************************************************
   * Recording metadata.
   **/
  public static class Recording implements Parcelable
  {
    public String  mFilename;
    public double  mDuration;

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


    /***************************************************************************
     * Parcelable implementation
     **/
    public int describeContents()
    {
      return 0;
    }



    public void writeToParcel(Parcel out, int flags)
    {
      out.writeString(mFilename);
      out.writeDouble(mDuration);
    }



    public static final Parcelable.Creator<Recording> CREATOR = new Parcelable.Creator<Recording>()
    {
      public Recording createFromParcel(Parcel in)
      {
        return new Recording(in);
      }

      public Recording[] newArray(int size)
      {
        return new Recording[size];
      }
    };



    private Recording(Parcel in)
    {
      mFilename = in.readString();
      mDuration = in.readDouble();
    }
  }



  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int                    mId;
  public String                 mTitle;

  // Only for messages
  public boolean                mIsMessage;
  public boolean                mIsRead;

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

  public transient Uri          mThumbImageUrl; // Conveniently pre-sized thumbnail/full
  public transient Uri          mFullImageUrl;  // image URIs

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
  public BooData()
  {
    mUUID = UUID.randomUUID();
  }



  public String toString()
  {
    return String.format("<%d:%s:%f:[%s]:%d>", mId, mTitle, getDuration(), mUser,
        (null == mRecordings ? 0 : mRecordings.size()));
  }



  public Uri getThumbUrl()
  {
    if (null != mThumbImageUrl) {
      return mThumbImageUrl;
    }
    if (null != mFullImageUrl) {
      return mFullImageUrl;
    }
    return mImageUrl;
  }



  public Uri getFullUrl()
  {
    if (null != mFullImageUrl) {
      return mFullImageUrl;
    }
    return mImageUrl;
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




  /***************************************************************************
   * Parcelable implementation
   **/
  public int describeContents()
  {
    return 0;
  }



  public void writeToParcel(Parcel out, int flags)
  {
    out.writeInt(mId);
    out.writeString(mTitle);
    out.writeInt(mIsMessage ? 1 : 0);
    out.writeInt(mIsRead ? 1 : 0);

    out.writeSerializable(mUUID);

    out.writeDouble(mDuration);

    out.writeTypedList(mTags);

    out.writeParcelable(mUser, flags);

    out.writeSerializable(mRecordedAt);
    out.writeSerializable(mUpdatedAt);
    out.writeSerializable(mUploadedAt);

    out.writeParcelable(mLocation, flags);

    out.writeParcelable(mHighMP3Url, flags);
    out.writeParcelable(mImageUrl, flags);
    out.writeParcelable(mDetailUrl, flags);
    out.writeParcelable(mThumbImageUrl, flags);
    out.writeParcelable(mFullImageUrl, flags);

    out.writeString(mFilename);

    out.writeTypedList(mRecordings);

    out.writeInt(mPlays);
    out.writeInt(mComments);
  }



  public static final Parcelable.Creator<BooData> CREATOR = new Parcelable.Creator<BooData>()
  {
    public BooData createFromParcel(Parcel in)
    {
      return new BooData(in);
    }

    public BooData[] newArray(int size)
    {
      return new BooData[size];
    }
  };



  private BooData(Parcel in)
  {
    mId             = in.readInt();
    mTitle          = in.readString();
    mIsMessage      = (in.readInt() != 0);
    mIsRead         = (in.readInt() != 0);

    mUUID           = (UUID) in.readSerializable();

    mDuration       = in.readDouble();

    LinkedList<Tag> tags = new LinkedList<Tag>();
    in.readTypedList(tags, Tag.CREATOR);
    mTags           = tags.size() > 0 ? tags : null;

    mUser           = in.readParcelable(User.class.getClassLoader());

    mRecordedAt     = (Date) in.readSerializable();
    mUpdatedAt      = (Date) in.readSerializable();
    mUploadedAt     = (Date) in.readSerializable();

    mLocation       = in.readParcelable(BooLocation.class.getClassLoader());

    mHighMP3Url     = in.readParcelable(Uri.class.getClassLoader());
    mImageUrl       = in.readParcelable(Uri.class.getClassLoader());
    mDetailUrl      = in.readParcelable(Uri.class.getClassLoader());
    mThumbImageUrl  = in.readParcelable(Uri.class.getClassLoader());
    mFullImageUrl   = in.readParcelable(Uri.class.getClassLoader());

    mFilename       = in.readString();

    LinkedList<Recording> recordings = new LinkedList<Recording>();
    in.readTypedList(recordings, Recording.CREATOR);
    mRecordings     = recordings.size() > 0 ? recordings : null;

    mPlays          = in.readInt();
    mComments       = in.readInt();
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
    out.writeObject(null != mThumbImageUrl ? mThumbImageUrl.toString() : null);
    out.writeObject(null != mFullImageUrl ? mFullImageUrl.toString() : null);
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

    s = (String) in.readObject();
    if (null != s) {
      mThumbImageUrl = Uri.parse(s);
    }

    s = (String) in.readObject();
    if (null != s) {
      mFullImageUrl = Uri.parse(s);
    }
  }
}

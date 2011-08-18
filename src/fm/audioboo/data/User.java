/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
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

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;


/**
 * Representation of an AudioBoo user's data.
 **/
public class User implements Parcelable, Serializable
{

  /***************************************************************************
   * Public data
   **/
  // Basic information
  public int            mId;                // Unique user ID
  public String         mUsername;          // Username
  public String         mFullName;          // Full name
  public String         mDescription;       // Profile description
  public boolean        mIsMessageSender;   // Set if this is a sender in messages
  public boolean        mMessagingEnabled;  // Only used if the above is true.
  public boolean        mFollowingEnabled;  // Whether or not following is enabled

  // URLs associated with the user.
  public transient Uri  mProfileUrl;        // Profile url
  public transient Uri  mImageUrl;          // Avatar/image url

  public transient Uri  mThumbImageUrl;     // Conveniently pre-sized thumbnail/full
  public transient Uri  mFullImageUrl;      // image URIs

  // Usage statistics.
  public int            mFollowers;         // # users following this one
  public int            mFollowings;        // # users this one follows
  public int            mAudioClips;        // # clips this user uploaded
  public int            mFavorites;         // # clips favorited



  public User()
  {
  }



  public String toString()
  {
    return String.format("<%d:%s:%d/%d/%d>", mId, mUsername, mFollowers,
        mFollowings, mAudioClips);
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
    out.writeString(mUsername);
    out.writeString(mFullName);
    out.writeString(mDescription);
    out.writeInt(mIsMessageSender ? 1 : 0);
    out.writeInt(mMessagingEnabled ? 1 : 0);
    out.writeInt(mFollowingEnabled ? 1 : 0);

    out.writeParcelable(mProfileUrl, flags);
    out.writeParcelable(mImageUrl, flags);
    out.writeParcelable(mThumbImageUrl, flags);
    out.writeParcelable(mFullImageUrl, flags);

    out.writeInt(mFollowers);
    out.writeInt(mFollowings);
    out.writeInt(mAudioClips);
    out.writeInt(mFavorites);
  }



  public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>()
  {
    public User createFromParcel(Parcel in)
    {
      return new User(in);
    }

    public User[] newArray(int size)
    {
      return new User[size];
    }
  };



  private User(Parcel in)
  {
    mId               = in.readInt();
    mUsername         = in.readString();
    mFullName         = in.readString();
    mDescription      = in.readString();
    mIsMessageSender  = (in.readInt() != 0);
    mMessagingEnabled = (in.readInt() != 0);
    mFollowingEnabled = (in.readInt() != 0);

    mProfileUrl       = in.readParcelable(Uri.class.getClassLoader());
    mImageUrl         = in.readParcelable(Uri.class.getClassLoader());
    mThumbImageUrl    = in.readParcelable(Uri.class.getClassLoader());
    mFullImageUrl     = in.readParcelable(Uri.class.getClassLoader());

    mFollowers        = in.readInt();
    mFollowings       = in.readInt();
    mAudioClips       = in.readInt();
    mFavorites        = in.readInt();
  }



  /***************************************************************************
   * Serializable implementation
   **/
  private void writeObject(java.io.ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject();

    out.writeObject(null != mProfileUrl ? mProfileUrl.toString() : null);
    out.writeObject(null != mImageUrl ? mImageUrl.toString() : null);
    out.writeObject(null != mThumbImageUrl ? mThumbImageUrl.toString() : null);
    out.writeObject(null != mFullImageUrl ? mFullImageUrl.toString() : null);
  }



  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();

    String s = (String) in.readObject();
    if (null != s) {
      mProfileUrl = Uri.parse(s);
    }

    s = (String) in.readObject();
    if (null != s) {
      mImageUrl = Uri.parse(s);
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

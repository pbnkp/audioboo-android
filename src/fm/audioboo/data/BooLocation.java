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

import android.os.Parcelable;
import android.os.Parcel;

import android.content.Context;

import android.location.Location;
import android.location.Geocoder;
import android.location.Address;

import java.util.List;

import java.io.Serializable;

import android.util.Log;

/**
 * Representation of a Boo's or user's location
 **/
public class BooLocation implements Parcelable, Serializable
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG = "BooLocation";



  /***************************************************************************
   * Public data
   **/
  // Latitude, longitude and accuracy are for displaying on maps.
  public double mLatitude;
  public double mLongitude;
  public double mAccuracy;

  // Descriptive string.
  public String mDescription;


  public String toString()
  {
    return String.format("<%f,%f(%f):%s>", mLatitude, mLongitude, mAccuracy,
        mDescription);
  }


  // Constructors
  public BooLocation()
  {
  }



  public BooLocation(Context ctx, Location loc)
  {
    mLatitude = loc.getLatitude();
    mLongitude = loc.getLongitude();
    mAccuracy = loc.getAccuracy();

    Geocoder coder = new Geocoder(ctx);
    try {
      // We're only interested in a single address.
      List<Address> addresses = coder.getFromLocation(mLatitude, mLongitude, 1);
      if (null != addresses && addresses.size() > 0) {
        Address addr = addresses.get(0);

        String locality = addr.getLocality();
        String admin = addr.getAdminArea();
        String country = addr.getCountryName();

        String descr = null;
        if (null != country) {
          descr = country;
          if (null != admin) {
            descr = String.format("%s, %s", admin, descr);
          }
          if (null != locality) {
            descr = String.format("%s, %s", locality, descr);
          }
          mDescription = descr;
        }
      }
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Could not determine location description: " + ex.getMessage());
    }
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
    out.writeDouble(mLatitude);
    out.writeDouble(mLongitude);
    out.writeDouble(mAccuracy);

    out.writeString(mDescription);
  }



  public static final Parcelable.Creator<BooLocation> CREATOR = new Parcelable.Creator<BooLocation>()
  {
    public BooLocation createFromParcel(Parcel in)
    {
      return new BooLocation(in);
    }

    public BooLocation[] newArray(int size)
    {
      return new BooLocation[size];
    }
  };



  private BooLocation(Parcel in)
  {
    mLatitude     = in.readDouble();
    mLongitude    = in.readDouble();
    mAccuracy     = in.readDouble();

    mDescription  = in.readString();
  }
}

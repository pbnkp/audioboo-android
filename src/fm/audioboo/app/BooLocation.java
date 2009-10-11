/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

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
public class BooLocation implements Serializable
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
}

/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

/**
 * Representation of a Boo's or user's location
 **/
public class Location
{
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
}

/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import java.util.HashMap;

/**
 * Simple in-memory cache for arbitrary objects.
 **/
public class ObjectMemoryCache
{
  /***************************************************************************
   * Private constants
   **/
  private static final String     LTAG = "ObjectMemoryCache";



  /***************************************************************************
   * Private data
   **/
  // First item in pair is an expiration timestamp, second is the data.
  private HashMap<Object, Pair<Double, Object>> mCache = new HashMap<Object, Pair<Double, Object>>();


  /***************************************************************************
   * Implementation
   **/

  /**
   * Put data in cache, with the given timeout in seconds.
   **/
  public void put(Object key, Object value, double timeout)
  {
    double now = System.currentTimeMillis() / 1000.0;
    mCache.put(key, new Pair<Double, Object>(now + timeout, value));
  }



  /**
   * Retrieve data from cache. null is returned if either the key does not exist
   * in the cache, or the associated value has timed out.
   **/
  public Object get(Object key)
  {
    double now = System.currentTimeMillis() / 1000.0;

    if (!mCache.containsKey(key)) {
      return null;
    }

    Pair<Double, Object> value = mCache.get(key);
    if (value.mFirst < now) {
      mCache.remove(key);
      return null;
    }

    return value.mSecond;
  }



  /**
   * Invalidate a cache object.
   **/
  public void invalidate(Object key)
  {
    if (!mCache.containsKey(key)) {
      return;
    }

    mCache.remove(key);
  }

}

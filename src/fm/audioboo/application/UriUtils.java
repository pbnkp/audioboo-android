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

package fm.audioboo.application;

import android.net.Uri;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.NameValuePair;

import java.util.List;

import android.util.Log;


/**
 * Utilities for URI manipulation.
 **/
public class UriUtils
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "UriUtils";


  /***************************************************************************
   * Implementation
   **/

  /**
   * Retrieve a List<NameValuePair> with URI parameters from an Uri object.
   **/
  public static List<NameValuePair> getQuery(Uri uri)
  {
    try {
      // Parse Uri as URI. Confusing? Yep. Wasteful? Even more so. Typically
      // Java? Spot on!
      URI juri = new URI(uri.toString());

      // Parse query
      List<NameValuePair> params = URLEncodedUtils.parse(juri, "utf8");
//      for (NameValuePair pair : params) {
//        Log.d(LTAG, "pair: " + pair.getName() + " = " + pair.getValue());
//      }

      return params;
    } catch (URISyntaxException ex) {
      Log.e(LTAG, "Invalid URI format: " + uri);
      return null;
    }
  }



  /**
   * Create an audioboo-schemed URI for opening a Boo's details page.
   **/
  public static Uri createDetailsUri(Boo boo)
  {
    if (null == boo || null == boo.mData) {
      return null;
    }
    return createDetailsUri(boo.mData.mId, boo.mData.mIsMessage);
  }


  public static Uri createDetailsUri(int id, boolean message)
  {
    return Uri.parse(String.format("audioboo:///boo_details?id=%d&message=%d",
          id, message ? 1 : 0));
  }
}

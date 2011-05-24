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
import java.net.URLEncoder;

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


  /**
   * Create an audioboo-schemed URI for recording to a destination user or
   * channel.
   **/
  public static Uri createRecordUri(int destination_id, String destination_name,
      boolean isChannel)
  {
    return createRecordUri(destination_id, destination_name, isChannel, -1, null);
  }


  public static Uri createRecordUri(int destination_id, String destination_name,
      int in_reply_to, String title)
  {
    return createRecordUri(destination_id, destination_name, false, in_reply_to, title);
  }


  public static Uri createRecordUri(int destination_id, String destination_name,
      boolean isChannel, int in_reply_to, String title)
  {
    String uri = null;
    try {
      uri = String.format("audioboo:///record_to?destination_name=%s&",
          URLEncoder.encode(destination_name, "utf8"));
    } catch (java.io.UnsupportedEncodingException ex) {
      Log.e(LTAG, "utf8 is unsupported? unlikely.");
      return null;
    }

    String id_format = null;
    if (isChannel) {
      id_format = "destination[stream_id]";
    }
    else {
      id_format = "destination[recipient_id]";
    }
    uri += String.format("%s=%d", id_format, destination_id);

    if (-1 != in_reply_to) {
      uri += String.format("&destination[parent_id]=%d", in_reply_to);
    }

    if (null != title) {
      try {
        uri += String.format("&destination[title]=%s", URLEncoder.encode(title, "utf8"));
      } catch (java.io.UnsupportedEncodingException ex) {
        Log.e(LTAG, "utf8 is unsupported? unlikely.");
        return null;
      }
    }

    return Uri.parse(uri);
  }

}

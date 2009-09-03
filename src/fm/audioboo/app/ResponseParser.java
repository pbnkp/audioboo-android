/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Helper class for parsing API responses.
 **/
class ResponseParser
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "ResponseParser";


  private static final int EXPECTED_VERSION = 

  /***************************************************************************
   * Implementation
   **/
  public String getBody(String response)
  {
    JSONObject object = new JSONObject(response);


  }

}

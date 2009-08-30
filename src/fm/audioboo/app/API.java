/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.util.Log;

/**
 * Abstraction for the AudioBoo API.
 **/
public class API
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "API";

  // Base URL for API calls. XXX Trailing slash is expected.
  private static final String BASE_URL  = "http://api.audioboo.fm/";

  // Request/response format for API calls. XXX Leading dot is expected.
  private static final String FORMAT    = ".json";
}

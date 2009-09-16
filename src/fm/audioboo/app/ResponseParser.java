/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

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

  // Expected response version. In the future, we may want to handle more than
  // one version and add support for several.
  private static final int EXPECTED_VERSION           = 200;

  // Keys found in the JSON response
  private static final String VERSION                 = "version";
  private static final String BODY                    = "body";

  private static final String WINDOW                  = "window";
  private static final String TOTALS                  = "totals";
  private static final String TOTALS_OFFSET           = "offset";
  private static final String TOTALS_COUNT            = "count";
  private static final String TIMESTAMP               = "timestamp";
  private static final String AUDIO_CLIPS             = "audio_clips";

  private static final String USER                    = "user";
  private static final String USER_ANONYMOUS          = "anonymous";
  private static final String USER_URLS               = "urls";
  private static final String USER_URLS_PROFILE       = "profile";
  private static final String USER_URLS_IMAGE         = "image";
  private static final String USER_USERNAME           = "username";
  private static final String USER_ID                 = "id";
  private static final String USER_COUNTS             = "counts";
  private static final String USER_COUNTS_FOLLOWERS   = "followers";
  private static final String USER_COUNTS_AUDIO_CLIPS = "audio_clips";
  private static final String USER_COUNTS_FOLLOWINGS  = "followings";

  private static final String BOO_ID                  = "id";
  private static final String BOO_TITLE               = "title";
  private static final String BOO_TAGS                = "tags";
  private static final String BOO_URLS                = "urls";
  private static final String BOO_URLS_HIGH_MP3       = "high_mp3";
  private static final String BOO_URLS_IMAGE          = "image";
  private static final String BOO_URLS_DETAIL         = "detail";
  private static final String BOO_UPLOADED_AT         = "uploaded_at";
  private static final String BOO_RECORDED_AT         = "recorded_at";
  private static final String BOO_COUNTS              = "counts";
  private static final String BOO_COUNTS_PLAYS        = "plays";
  private static final String BOO_COUNTS_COMMENTS     = "comments";
  private static final String BOO_DURATION            = "duration";

  private static final String BOO_LOCATION            = "location";


  /***************************************************************************
   * Implementation
   **/
  public BooList parseBooList(String response)
  {
    try {
      JSONObject object = new JSONObject(response);

      // Check version of the response first. We expect a particular version
      // at the moment.
      int version = object.getInt(VERSION);
      if (EXPECTED_VERSION != version) {
        Log.e(LTAG, "Response version did not match our expectations.");
        // FIXME use handler
        return null;
      }

      JSONObject body = object.getJSONObject(BODY);

      BooList result = new BooList();

      // Read metadata first.
      result.mWindow = object.getInt(WINDOW);
      result.mTimestamp = new Date(object.getInt(TIMESTAMP));

      JSONObject totals = body.getJSONObject(TOTALS);
      result.mOffset = totals.getInt(TOTALS_OFFSET);
      result.mCount = totals.getInt(TOTALS_COUNT);

      // Read metadata for individual boos
      JSONArray boos = body.getJSONArray(AUDIO_CLIPS);
      for (int i = 0 ; i < boos.length() ; ++i) {
        result.mClips.add(parseBoo(boos.getJSONObject(i)));
      }
      Log.d(LTAG, "# clips: " + result.mClips.size());

      return result;

    } catch (JSONException ex) {
      Log.e(LTAG, "Could not parse JSON response: " + ex);
      // FIXME use handler
      return null;
    }
  }



  private Boo parseBoo(JSONObject boo) throws JSONException
  {
    Boo result = new Boo();

    // Parse user data
    result.mUser = parseUser(boo.getJSONObject(USER));

    // Parse location data
    // TODO

    // Parse Boo metadata
//    result.mId  = boo.getInt(BOO_ID);
    result.mTitle = boo.getString(BOO_TITLE);

    result.mDuration = boo.getDouble(BOO_DURATION);

    // TODO result.mTags;

    // Timestamps
//     result.mRecordedAt = new Date(boo.getInt(BOO_RECORDED_AT));
//     result.mUploadedAt = new Date(boo.getInt(BOO_UPLOADED_AT));

    // URLs
    JSONObject urls = boo.getJSONObject(BOO_URLS);
    result.mHighMP3Url = Uri.parse(urls.getString(BOO_URLS_HIGH_MP3));
    result.mDetailUrl = Uri.parse(urls.getString(BOO_URLS_DETAIL));
    if (urls.has(BOO_URLS_IMAGE)) {
      result.mImageUrl = Uri.parse(urls.getString(BOO_URLS_IMAGE));
    }

    // Stats
    JSONObject stats = boo.getJSONObject(BOO_COUNTS);
    result.mPlays = stats.getInt(BOO_COUNTS_PLAYS);
    result.mComments = stats.getInt(BOO_COUNTS_COMMENTS);

    Log.d(LTAG, "result: " + result);

    return result;
  }



  private User parseUser(JSONObject user) throws JSONException
  {
    if (user.has(USER_ANONYMOUS) && user.getBoolean(USER_ANONYMOUS)) {
      return null;
    }

    User result = new User();

    // Basic information
    result.mId = user.getInt(USER_ID);
    result.mUsername = user.getString(USER_USERNAME);

    // Urls
    JSONObject urls = user.getJSONObject(USER_URLS);
    result.mProfileUrl = Uri.parse(urls.getString(USER_URLS_PROFILE));
    result.mImageUrl = Uri.parse(urls.getString(USER_URLS_IMAGE));

    // Stats
    JSONObject counts = user.getJSONObject(USER_COUNTS);
    result.mFollowers = counts.getInt(USER_COUNTS_FOLLOWERS);
    result.mFollowings = counts.getInt(USER_COUNTS_FOLLOWINGS);
    result.mAudioClips = counts.getInt(USER_COUNTS_AUDIO_CLIPS);

//    Log.d(LTAG, "User: " + result);

    return result;
  }
}

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

import android.app.Activity;

import android.os.Bundle;

import android.net.Uri;

import android.content.Intent;

import android.widget.Toast;

import java.util.List;
import java.util.LinkedList;

/**
 * DispatchActivity listens to URIs with the audioboo scheme, and dispatches
 * them to other Activities in the application. It's intentionally short-lived.
 *
 * The reason is that - unfortunately - Android does not differentiate URIs by
 * path if the host component is missing, which the existing URIs unfortunately
 * omit, e.g. audioboo:///record_to?parameters
 *
 * That means in Android, one cannot register one Activity to listen to the
 * /record_to path, and another Activity to another.
 *
 * So to work around that, we catch all audioboo URIs in this activity, and
 * dispatch manually.
 **/
public class DispatchActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "DispatchActivity";

  // Dispatch map.
  private static final List<Pair<String, Class>> DISPATCH_MAP = new LinkedList<Pair<String, Class>>();
  static {
    // Match path prefixes to Activities
    DISPATCH_MAP.add(new Pair<String, Class>("/record_to", RecordActivity.class));
    DISPATCH_MAP.add(new Pair<String, Class>("/send_message", RecordActivity.class));
    DISPATCH_MAP.add(new Pair<String, Class>("/boo_details", BooDetailsActivity.class));
    DISPATCH_MAP.add(new Pair<String, Class>("/play_boo", BooDetailsActivity.class));
    DISPATCH_MAP.add(new Pair<String, Class>("/play_boo", BooDetailsActivity.class));
    DISPATCH_MAP.add(new Pair<String, Class>("/messages", MessagesActivity.class));
  }


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    // Grab data URI
    Uri uri = getIntent().getData();
    if (null == uri) {
      // No data URI means nothing to dispatch. This activity must have been
      // called in an unsupported way - just exit quickly.
      finish();
      return;
    }

    // Match patch component in data URI against the dispatch map.
    String path = uri.getPath();
    for (Pair<String, Class> pair : DISPATCH_MAP) {
      if (path.startsWith(pair.mFirst)) {
        // Got a match!
        Intent intent = new Intent(this, pair.mSecond);
        intent.setData(uri);
        startActivity(intent);
        finish();
        return;
      }
    }

    // No match!
    Toast.makeText(this, R.string.dispatch_error, Toast.LENGTH_LONG).show();
    finish();
  }
}

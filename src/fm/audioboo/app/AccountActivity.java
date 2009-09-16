/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.app.ListActivity;

import android.os.Bundle;

// FIXME import android.os.Handler;
// FIXME import android.os.Message;

import android.content.res.Configuration;

// FIXME import android.view.View;
// FIXME import java.util.LinkedList;

import android.util.Log;

/**
 * FIXME
 **/
public class AccountActivity extends ListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "AccountActivity";


  /***************************************************************************
   * Data members
   **/


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

//     setContentView(R.layout.recent_boos);
//     View v = findViewById(R.id.recent_boos_empty);
//     getListView().setEmptyView(v);
  }



  @Override
  public void onStart()
  {
    super.onStart();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }
}

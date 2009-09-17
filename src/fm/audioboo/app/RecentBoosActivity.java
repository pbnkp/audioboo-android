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

import android.os.Handler;
import android.os.Message;

import android.content.res.Configuration;

import android.view.View;

import android.view.Menu;
import android.view.MenuItem;

import java.util.LinkedList;

import android.util.Log;

/**
 * The RecentBoosActivity loads recent boos and displays them in a ListView.
 **/
public class RecentBoosActivity extends ListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "RecentBoosActivity";

  // Action identifiers -- must correspond to the indices of the array
  // "recent_boos_actions" in res/values/localized.xml
  private static final int  ACTION_REFRESH  = 0;


  /***************************************************************************
   * Data members
   **/
  // API instance
  private API             mApi;

  // Flag, set to true when a request is in progress.
  private boolean         mRequesting;

  // Content
  private BooList         mBoos;

  // Adapter
  private BooListAdapter  mAdapter;

  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.recent_boos);
    View v = findViewById(R.id.recent_boos_empty);
    getListView().setEmptyView(v);
  }



  @Override
  public void onStart()
  {
    super.onStart();
    Log.d(LTAG, "Start");
  }



  @Override
  public void onResume()
  {
    super.onResume();
    Log.d(LTAG, "Resume");

    // Load boos, but only if that hasn't happened yet..
    if (null == mBoos) {
      refreshBoos();
    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void refreshBoos()
  {
    if (null == mApi) {
      mApi = new API();
    }

    if (mRequesting) {
      // Wait for the previous request to finish
    }

    mRequesting = true;
    mApi.fetchRecentBoos(new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        mRequesting = false;
        if (API.ERR_SUCCESS == msg.what) {
          onReceiveRecentBoos((BooList) msg.obj);
        }
        else {
          onRecentBoosError(msg.what, (String) msg.obj);
        }
        return true;
      }

    }));
  }



  private void onReceiveRecentBoos(BooList boos)
  {
    Log.d(LTAG, "Got response!");

    mBoos = boos;

    mAdapter = new BooListAdapter(this, R.layout.recent_boos_item, mBoos);
    setListAdapter(mAdapter);
  }



  private void onRecentBoosError(int code, String msg)
  {
    // FIXME add error view to layout and display that.
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    String[] menu_titles = getResources().getStringArray(R.array.recent_boos_actions);
    final int[] menu_icons = {
      R.drawable.ic_menu_refresh,
    };
    assert(menu_icons.length == menu_titles.length);

    for (int i = 0 ; i < menu_titles.length ; ++i) {
      menu.add(0, i, 0, menu_titles[i]).setIcon(menu_icons[i]);
    }
    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case ACTION_REFRESH:
        // Refresh Boos! While we do that, we want to empty the listview
        mAdapter = null;
        setListAdapter(mAdapter);
        refreshBoos();
        break;

      default:
        // FIXME error toast
        return false;
    }

    return true;
  }
}

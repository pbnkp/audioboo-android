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
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.CompoundButton;

import android.view.Menu;
import android.view.MenuItem;

import java.util.LinkedList;

import fm.audioboo.widget.PlayPauseButton;

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

  // Multiplier applied to boo playback progress (in seconds) before it's
  // used as max/current in the progress display.
  private static final int  PROGRESS_MULTIPLIER = 5000;

  /***************************************************************************
   * Data members
   **/
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

    getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id)
      {
        // Use id rather than position, because of (future?) filtering.
        onItemSelected(view, (int) id);
      }
    });
  }



  @Override
  public void onStart()
  {
    super.onStart();
    //Log.d(LTAG, "Start");
  }



  @Override
  public void onResume()
  {
    super.onResume();
    //Log.d(LTAG, "Resume");

    // Load boos, but only if that hasn't happened yet..
    if (null == mBoos) {
      refreshBoos();
    }
  }



  @Override
  public void onPause()
  {
    super.onPause();

    // TODO: In future, we'd like to install a notification here that'll allow
    //       us to access a player UI, and continue playback.
    Globals.get().mPlayer.stopPlaying();
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
    if (mRequesting) {
      // Wait for the previous request to finish
    }

    mRequesting = true;
    Globals.get().mAPI.fetchRecentBoos(new Handler(new Handler.Callback() {
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
    mBoos = boos;

    mAdapter = new BooListAdapter(this, R.layout.recent_boos_item, mBoos);
    getListView().setOnScrollListener(new BooListAdapter.ScrollListener(mAdapter));
    setListAdapter(mAdapter);
  }



  private void onRecentBoosError(int code, String msg)
  {
    // FIXME add error view to layout and display that.
    Log.e(LTAG, "Error: (" + code + ") " + msg);
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
        getListView().setOnScrollListener(null);
        setListAdapter(mAdapter);
        refreshBoos();
        break;

      default:
        // FIXME error toast
        return false;
    }

    return true;
  }



  private void onItemSelected(final View view, final int id)
  {
    // First, deal with the visual stuff. It's complex enough for it's own
    // function.
    mAdapter.markSelected(view, id);

    // Next, we'll need to kill the audio player and restart it, but only if
    // it's a different view that's been selected.
    Boo boo = mBoos.mClips.get(id);

    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    final PlayPauseButton button = (PlayPauseButton) view.findViewById(R.id.recent_boos_item_playpause);
    button.setChecked(false);
    button.setIndeterminate(true);
    button.setMax((int) (boo.mDuration * PROGRESS_MULTIPLIER));

    Globals.get().mPlayer.setProgressListener(new BooPlayer.ProgressListener() {
      public void onProgress(int state, double progress)
      {
        switch (state) {
          case BooPlayer.STATE_PLAYBACK:
            button.setIndeterminate(false);
            button.setProgress((int) (progress * PROGRESS_MULTIPLIER));
            break;

          case BooPlayer.STATE_BUFFERING:
            button.setIndeterminate(true);
            break;

          case BooPlayer.STATE_FINISHED:
            onItemUnselected(view, id);
            break;
        }
      }
    });
    Globals.get().mPlayer.play(boo);

    // Install handler for listening to the toggle.
    button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
      {
        onItemUnselected(view, id);
      }
    });
  }



  void onItemUnselected(final View view, final int id)
  {
     // We don't care here whether the button is checked or not, we just
     // stop playback.
     Globals.get().mPlayer.stopPlaying();

     // And also switch the view to unselected.
     mAdapter.unselect(view, id);
  }
}

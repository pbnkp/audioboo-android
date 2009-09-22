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

import android.view.Menu;
import android.view.MenuItem;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.LinkedList;

import fm.audioboo.widget.BooPlayerView;

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
  // Flag, set to true when a request is in progress.
  private boolean         mRequesting;

  // Content
  private BooList         mBoos;

  // Adapter
  private BooListAdapter  mAdapter;


  /***************************************************************************
   * Helper for respoding to playback end.
   **/
  private class PlaybackEndListener extends BooPlayerView.PlaybackEndListener
  {
    private View  mView;
    private int   mId;


    public PlaybackEndListener(View view, int id)
    {
      mView = view;
      mId = id;
    }


    public void onPlaybackEnded(BooPlayerView view, int endState)
    {
      // FIXME toast if endState != success
      onItemUnselected(mView, mId);
    }
  }


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
    else {
      // Resume playback.
      BooPlayerView player = (BooPlayerView) findViewById(R.id.recent_boos_player);
      if (null != player) {
        player.resume();
      }
    }

  }



  @Override
  public void onPause()
  {
    super.onPause();

    // Pause playback.
    BooPlayerView player = (BooPlayerView) findViewById(R.id.recent_boos_player);
    if (null != player) {
      player.pause();
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



  private void onItemSelected(View view, int id)
  {
    // First, deal with the visual stuff. It's complex enough for it's own
    // function.
    mAdapter.markSelected(view, id);

    // Next, we'll need to kill the audio player and restart it, but only if
    // it's a different view that's been selected.
    Boo boo = mBoos.mClips.get(id);

    // Fade in player view
    BooPlayerView player = (BooPlayerView) findViewById(R.id.recent_boos_player);
    if (null != player) {
      // Capture clicks for the whole window, so that underlying views don't receive
      // them.
      player.setClickable(true);

      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
      player.startAnimation(animation);

      player.setPlaybackEndListener(new PlaybackEndListener(view, id));
      player.play(boo);
    }
  }



  void onItemUnselected(View view, int id)
  {
    // And also switch the view to unselected.
    mAdapter.unselect(view, id);

    // Fade out player view
    final BooPlayerView player = (BooPlayerView) findViewById(R.id.recent_boos_player);
    if (null != player) {
      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
      animation.setAnimationListener(new Animation.AnimationListener() {
        public void onAnimationEnd(Animation animation)
        {
          // When the player finished fading out, stop capturing clicks.
          player.setClickable(false);
        }

        public void onAnimationRepeat(Animation animation)
        {
          // pass
        }

        public void onAnimationStart(Animation animation)
        {
          // pass
        }
      });
      player.startAnimation(animation);

      player.stop();
    }
  }
}

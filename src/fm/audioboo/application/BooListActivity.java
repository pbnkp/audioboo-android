/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/
package fm.audioboo.application;

import android.app.ExpandableListActivity;

import android.os.Bundle;
import android.os.Parcelable;

import android.content.Intent;
import android.content.res.Configuration;

import android.view.View;

import android.widget.ExpandableListView;
import android.widget.Toast;

import java.util.List;

import fm.audioboo.service.Constants;
import fm.audioboo.widget.BooPlayerView;

import fm.audioboo.service.BooPlayerClient;

import android.util.Log;

/**
 * Base for lists of Boos, such as BrowseActivity, MessagesActivity, etc.
 **/
public abstract class BooListActivity
       extends ExpandableListActivity
       implements BooListPaginator.Callback,
                  BooListPaginator.PaginatorDataSource
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooListActivity";


  /***************************************************************************
   * Protected constants
   **/
  // Action identifiers -- must correspond to the indices of the array
  // "recent_boos_actions" in res/values/localized.xml
  protected static final int  ACTION_REFRESH  = 0;
  protected static final int  ACTION_LAST     = ACTION_REFRESH;


  /***************************************************************************
   * Data members
   **/
  // Content
  protected BooListPaginator  mPaginator;


  /***************************************************************************
   * Helper for respoding to playback end.
   **/
  private class PlaybackEndListener extends BooPlayerView.PlaybackEndListener
  {
    public View mView;
    public int  mId;


    public PlaybackEndListener(View view, int id)
    {
      mView = view;
      mId = id;
    }


    public void onPlaybackEnded(BooPlayerView view, int endState)
    {
      if (BooPlayerView.END_STATE_SUCCESS != endState) {
        Toast.makeText(BooListActivity.this, R.string.error_message_playback,
            Toast.LENGTH_LONG).show();
      }
      if (null != mView) {
        onItemUnselected(mView, mId);
      }
      else {
        hidePlayer();
      }
    }
  }


  /***************************************************************************
   * Subclass interface - you should also overload onError()
   **/
  /**
   * Public constants, that are passed to getViewId()
   **/
  public static final int VIEW_ID_NONE              = -1;
  public static final int VIEW_ID_LAYOUT            = 0;
  public static final int VIEW_ID_EMPTY_VIEW        = 1;
  public static final int VIEW_ID_PLAYER            = 2;
  public static final int VIEW_ID_PLAYER_CONTAINER  = 3;
  public static final int VIEW_ID_LOADING           = 4;
  public static final int VIEW_ID_RETRY             = 5;

  /**
   * Get View identifier for the specified View constant. Return VIEW_ID_NONE
   * if not supported.
   **/
  public abstract int getViewId(int viewSpec);


  /**
   * Retrieve the API call to populate the view when starting up the Activity.
   **/
  public abstract int getInitAPI();


  /**
   * Retrieve title string to use given the current API from the Paginator.
   **/
  public abstract String getTitleString(int api);


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(getViewId(VIEW_ID_LAYOUT));
    View v = findViewById(getViewId(VIEW_ID_EMPTY_VIEW));
    getExpandableListView().setEmptyView(v);

    // Initialize paginator
    mPaginator = new BooListPaginator(getInitAPI(), this, this, this);
    mPaginator.setDisclosureListener(new View.OnClickListener() {
      public void onClick(View v)
      {
        onDisclosureClicked((Boo) v.getTag());
      }
    });

    // Initialize "retry" button on list empty vew
    v = findViewById(getViewId(VIEW_ID_RETRY));
    if (null != v) {
      v.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            refresh();
          }
      });
    }
  }



  @Override
  public void onResume()
  {
    super.onResume();
    //Log.d(LTAG, "Resume");

    // Load boos, but only if that hasn't happened yet..
    if (null == mPaginator.getPaginatedList()) {
      refresh();
    }

    // Also initialize the player view
    BooPlayerView pv = (BooPlayerView) findViewById(getViewId(VIEW_ID_PLAYER));
    if (null != pv) {
      PlaybackEndListener listener = (PlaybackEndListener) pv.getPlaybackEndListener();

      BooPlayerClient player = Globals.get().mPlayer;
      if (null == player || Constants.STATE_NONE == player.getState()) {
        // Right, hide the player if it's still visible.
        // This is a bit tricky... the only place where we reliably remember
        // the view/id for unselecting an item is in the playback end listener.
        if (null != listener) {
          onItemUnselected(listener.mView, listener.mId);
        }
      }
      else {
        // We appear to be playing back, so let's fade the player in and let it
        // update.
        showPlayer();
        if (null == listener) {
          pv.setPlaybackEndListener(new PlaybackEndListener(null, -1));
        }
      }
    }
  }



  @Override
  public void onPause()
  {
    super.onPause();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  public void refresh(int type)
  {
    mPaginator.refresh(type);
    setTitle(getTitleString(type));
  }



  public void refresh()
  {
    refresh(mPaginator.getType());
  }



  private void showPlayer()
  {
    final View player = findViewById(getViewId(VIEW_ID_PLAYER_CONTAINER));
    if (null == player) {
      return;
    }

    player.setVisibility(View.VISIBLE);
  }



  private void hidePlayer()
  {
    final View player = findViewById(getViewId(VIEW_ID_PLAYER_CONTAINER));
    if (null == player) {
      return;
    }

    player.setVisibility(View.GONE);
  }



  private void onItemSelected(View view, int group, int position)
  {
    // The data source knows what group content exists, regardless of whether
    // we've got a paginated group or not.
    List<Boo> boos = mPaginator.getGroup(group);
    if (null == boos) {
      Log.e(LTAG, "No entries for group: " + group);
      return;
    }

    if (position < 0 || position >= boos.size()) {
      Log.e(LTAG, "Position " + position + " exceeds size of group " + group);
      return;
    }

    final Boo boo = boos.get(position);

    // Fade in player view
    showPlayer();

    // Start playback
    final BooPlayerView player = (BooPlayerView) findViewById(getViewId(VIEW_ID_PLAYER));
    if (null != player) {
      player.setPlaybackEndListener(new PlaybackEndListener(view, boo.mData.mId));
      player.play(boo);
    }
  }



  private void onItemUnselected(View view, int id)
  {
    // Fade out player view
    hidePlayer();

    // Stop playback
    final BooPlayerView player = (BooPlayerView) findViewById(getViewId(VIEW_ID_PLAYER));
    if (null != player) {
      player.stop();
    }
  }



  private void setPageLoading(boolean loading)
  {
    // Find out which view the progress view is, and switch it to "loading".
    ExpandableListView lv = getExpandableListView();
    int pos = mPaginator.getAdapter().mapPosition(paginatedGroup(),
        mPaginator.getPaginatedList().mClips.size());
    pos = pos - lv.getFirstVisiblePosition();

    if (pos < 0 || pos >= lv.getChildCount()) {
      mPaginator.getAdapter().setLoading(loading, null);
    }
    else {
      mPaginator.getAdapter().setLoading(loading, lv.getChildAt(pos));
    }
  }



  public void onDisclosureClicked(Boo boo)
  {
    if (null == boo || null == boo.mData) {
      // Silently ignore clicks on empty fields.
      return;
    }
    Intent i = new Intent(this, BooDetailsActivity.class);
    i.putExtra(BooDetailsActivity.EXTRA_BOO_DATA, (Parcelable) boo.mData);
    startActivity(i);
  }



  /***************************************************************************
   * BooListPaginator.Callback implementation
   **/
  public void onStartRequest(boolean firstPage)
  {
    if (firstPage) {
      // Replace the list view with a loading screen.
      setListAdapter(null);

      View view = findViewById(getViewId(VIEW_ID_LOADING));
      if (null != view) {
        view.setVisibility(View.VISIBLE);
      }
      view = findViewById(getViewId(VIEW_ID_RETRY));
      if (null != view) {
        view.setVisibility(View.GONE);
      }
    }
    else {
      setPageLoading(true);
    }
  }



  public void onResults(boolean firstPage)
  {
    // Initialize list view if this was a first request.
    if (firstPage) {
      setListAdapter(mPaginator.getAdapter());
    }
    else {
      setPageLoading(false);
    }
  }



  public void onError(int code, API.APIException exception)
  {
    // Also reset view.
    setListAdapter(null);

    View view = findViewById(getViewId(VIEW_ID_LOADING));
    if (null != view) {
      view.setVisibility(View.GONE);
    }
    view = findViewById(getViewId(VIEW_ID_RETRY));
    if (null != view) {
      view.setVisibility(View.VISIBLE);
    }

    // Same for "loading" view; not that it matters at this point, but it
    // will when the view is populated again.
    if (null != mPaginator.getAdapter()) {
      mPaginator.getAdapter().setLoading(false, null);
    }
  }



  public void onItemClick(ExpandableListView parent, View view, int group, int position, long id)
  {
    onItemSelected(view, group, position);
  }



  public boolean onItemLongClick(ExpandableListView parent, View view, int group, int position, long id)
  {
    onDisclosureClicked((Boo) view.getTag());
    return true;
  }
}

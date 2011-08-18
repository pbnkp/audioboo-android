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

import android.app.ExpandableListActivity;

import android.os.Bundle;
import android.os.Parcelable;

import android.content.Intent;
import android.content.res.Configuration;

import android.view.View;

import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.Toast;

import java.util.List;

import fm.audioboo.service.Constants;
import fm.audioboo.service.BooPlayerClient;
import fm.audioboo.data.PlayerState;

import android.util.Log;

/**
 * Base for lists of Boos, such as BrowseActivity, MessagesActivity, etc.
 **/
public abstract class BooListActivity
       extends ExpandableListActivity
       implements BooListPaginator.Callback,
                  BooListPaginator.PaginatorDataSource,
                  BooPlayerClient.ProgressListener
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

  // Activity codes
  protected static final int  ACTIVITY_DETAILS  = 0;


  /***************************************************************************
   * Data members
   **/
  // Content
  protected BooListPaginator  mPaginator;



  /***************************************************************************
   * Subclass interface - you should also overload onError()
   **/
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

    setContentView(R.layout.boo_list);
    View v = findViewById(R.id.boo_list_empty);
    getExpandableListView().setEmptyView(v);

    // Initialize paginator
    mPaginator = new BooListPaginator(getInitAPI(), this, this, this);
    mPaginator.setDisclosureListener(new View.OnClickListener() {
      public void onClick(View v)
      {
        @SuppressWarnings("unchecked")
        Pair<Boo, Integer> pair = (Pair<Boo, Integer>) v.getTag();
        if (null == pair) {
          Log.e(LTAG, "No tag set!");
          return;
        }
        onDisclosureClicked(pair.mFirst, pair.mSecond);
      }
    });

    // Initialize "retry" button on list empty vew
    v = findViewById(R.id.boo_list_retry);
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
    View pv = findViewById(R.id.boo_list_player);
    if (null != pv) {
      BooPlayerClient player = Globals.get().mPlayer;
      if (null != player) {
        PlayerState state = player.getState();
        switch (state.mState) {
          case Constants.STATE_PREPARING:
          case Constants.STATE_BUFFERING:
          case Constants.STATE_PLAYING:
            showPlayer();
            break;

          default:
            hidePlayer();
            break;
        }
      }
    }
  }



  @Override
  public void onPause()
  {
    super.onPause();

    BooPlayerClient client = Globals.get().mPlayer;
    if (null != client) {
      client.removeProgressListener(this);
    }
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
    ViewAnimator anim = (ViewAnimator) findViewById(R.id.boo_list_error_flipper);
    if (null != anim) {
      anim.setDisplayedChild(0);
    }

    mPaginator.refresh(type);
    setTitle(getTitleString(type));
  }



  public void refresh()
  {
    refresh(mPaginator.getType());
  }



  private void showPlayer()
  {
    final View player = findViewById(R.id.boo_list_player_container);
    if (null == player) {
      return;
    }

    player.setVisibility(View.VISIBLE);

    Globals.get().mPlayer.addProgressListener(this);
  }



  private void hidePlayer()
  {
    final View player = findViewById(R.id.boo_list_player_container);
    if (null == player) {
      return;
    }

    player.setVisibility(View.GONE);

    Globals.get().mPlayer.removeProgressListener(this);
  }



  private void setPageLoading(boolean loading)
  {
    BooList list = mPaginator.getPaginatedList();
    if (null == list || null == list.mClips) {
      return;
    }

    // Find out which view the progress view is, and switch it to "loading".
    ExpandableListView lv = getExpandableListView();
    int pos = mPaginator.getAdapter().mapPosition(paginatedGroup(),
        list.mClips.size());
    pos = pos - lv.getFirstVisiblePosition();

    if (pos < 0 || pos >= lv.getChildCount()) {
      mPaginator.getAdapter().setLoading(loading, null);
    }
    else {
      mPaginator.getAdapter().setLoading(loading, lv.getChildAt(pos));
    }
  }



  public void onDisclosureClicked(Boo boo, int group)
  {
    if (null == boo || null == boo.mData) {
      return;
    }

    Intent i = new Intent(this, BooDetailsActivity.class);
    i.putExtra(BooDetailsActivity.EXTRA_BOO_DATA, (Parcelable) boo.mData);
    startActivityForResult(i, ACTIVITY_DETAILS);
  }



  /***************************************************************************
   * BooListPaginator.Callback implementation
   **/
  public void onStartRequest(boolean firstPage)
  {
    if (firstPage) {
      // Replace the list view with a loading screen.
      setListAdapter(null);

      ViewAnimator anim = (ViewAnimator) findViewById(R.id.boo_list_progress);
      if (null != anim) {
        anim.setDisplayedChild(0);
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
    ViewAnimator anim = (ViewAnimator) findViewById(R.id.boo_list_progress);
    if (null != anim) {
      anim.setDisplayedChild(1);
    }

    // Same for "loading" view; not that it matters at this point, but it
    // will when the view is populated again.
    if (null != mPaginator.getAdapter()) {
      mPaginator.getAdapter().setLoading(false, null);
    }

    // If we've only got one group to display, all we can do at this
    // point is error out.
    if (1 >= mPaginator.getGroupCount()) {
      // Also reset view.
      setListAdapter(null);

      // Flip error flipper to show error view.
      anim = (ViewAnimator) findViewById(R.id.boo_list_error_flipper);
      if (null != anim) {
        anim.setDisplayedChild(1);

        TextView msg = (TextView) findViewById(R.id.boo_list_error);
        if (null != msg) {
          int error_id = R.string.boo_list_generic_error;
          if (API.ERR_LOCATION_REQUIRED == code) {
            error_id = R.string.boo_list_location_error;
          }
          else {
            if (null != exception) {
              Log.d(LTAG, "Error code: " + exception.getCode());
              switch (exception.getCode()) {
                case 403:
                  error_id = R.string.boo_list_login_error;
                  break;
              }
            }
          }
          msg.setText(error_id);
        }
      }
    }
    else {
      // If, on the other hand, we have multiple groups, then we can still
      // show the dynamic one as errored out.
      mPaginator.setPaginatedError(true);
    }
  }



  public void onItemClick(ExpandableListView parent, View view, int group, int position, long id)
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

    onItemClicked(boos.get(position), group);
  }



  public void onItemClicked(Boo boo, int group)
  {
    if (null == boo || null == boo.mData) {
      return;
    }

    showPlayer();
    Globals.get().mPlayer.play(boo, true);
  }



  public boolean onItemLongClick(ExpandableListView parent, View view, int group, int position, long id)
  {
    @SuppressWarnings("unchecked")
    Pair<Boo, Integer> pair = (Pair<Boo, Integer>) view.getTag();
    if (null == pair) {
      Log.e(LTAG, "No tag set!");
      return false;
    }
    onDisclosureClicked(pair.mFirst, pair.mSecond);
    return true;
  }


  /***************************************************************************
   * Default implementations for BooListPaginator.PaginatorDataSource
   **/
  public int getBackgroundResource(int viewType)
  {
    switch (viewType) {
      case BooListAdapter.VIEW_TYPE_BOO:
        return R.drawable.boo_list_background;

      case BooListAdapter.VIEW_TYPE_MORE:
        return R.drawable.boo_list_background_more;

      default:
        return -1;
    }
  }



  public int getElementColor(int element)
  {
    switch (element) {
      case BooListAdapter.ELEMENT_AUTHOR:
        return R.color.boo_list_author;

      case BooListAdapter.ELEMENT_TITLE:
        return R.color.boo_list_title;

      case BooListAdapter.ELEMENT_LOCATION:
        return R.color.boo_list_location;

      default:
        return -1;
    }
  }



  public int getGroupType(int group)
  {
    return BooListAdapter.VIEW_TYPE_BOO;
  }



  /***************************************************************************
   * BooPlayerClient.ProgressListener implementation
   **/
  public void onProgress(PlayerState state)
  {
    switch (state.mState) {
      case Constants.STATE_ERROR:
        Toast.makeText(this, R.string.error_message_playback,
            Toast.LENGTH_LONG).show();
        // XXX Fall through

      case Constants.STATE_PAUSED:
      case Constants.STATE_FINISHED:
      case Constants.STATE_NONE:
        hidePlayer();
        break;

     default:
        showPlayer();
        break;
    }
  }
}

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
import android.content.res.Resources;
import android.content.res.ColorStateList;

import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

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


  // Text view IDs in list items
  private static final int TEXT_VIEW_IDS[] = {
    R.id.recent_boos_item_author,
    R.id.recent_boos_item_title,
    R.id.recent_boos_item_location,
  };


  // Color IDs for the above text view IDs for the regular, unselected state
  private static final int TEXT_VIEW_COLORS_REGULAR[] = {
    R.color.recent_boos_author,
    R.color.recent_boos_title,
    R.color.recent_boos_location,
  };

  // Color IDs for the above text view IDs for the selected state
  private static final int TEXT_VIEW_COLORS_SELECTED[] = {
    R.color.recent_boos_author_selected,
    R.color.recent_boos_title_selected,
    R.color.recent_boos_location_selected,
  };

  // Color IDs for the item background for the regular, unselected state
  private static final int BACKGROUND_RESOURCE_REGULAR[] = {
    R.drawable.recent_boos_background_odd,
    R.drawable.recent_boos_background_even,
  };

  // Color IDs for the item background for the selected state
  private static final int BACKGROUND_RESOURCE_SELECTED[] = {
    R.color.recent_boos_background_odd_active,
    R.color.recent_boos_background_even_active,
  };

  /***************************************************************************
   * Data members
   **/
  // Flag, set to true when a request is in progress.
  private boolean         mRequesting;

  // Content
  private BooList         mBoos;

  // Adapter
  private BooListAdapter  mAdapter;

  // State required for restoring the last selected view to it's original
  // looks.
  private View            mLastView;
  private int             mLastId;
  private Boo             mLastBoo;

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
        onItemSelected(view, (int) id);
      }
    });
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



  private void highlightSelectedView(View view, int id)
  {
    // Set view attributes
    drawViewInternal(view, id, BACKGROUND_RESOURCE_SELECTED,
        TEXT_VIEW_COLORS_SELECTED);

    // Fade in play/pause button
    View v = view.findViewById(R.id.recent_boos_item_image);
    v.setVisibility(View.GONE);
    v = view.findViewById(R.id.recent_boos_item_playpause);
    v.setVisibility(View.VISIBLE);
  }



  private void drawViewInternal(View view, int id, int[] backgrounds, int[] text_colors)
  {
    // First, set the background according to whether or not id points to an
    // odd or even cell.
    view.setBackgroundResource(backgrounds[id % 2]);

    // Next, iterate over the known text views, and set their colors.
    Resources r = getResources();
    for (int i = 0 ; i < TEXT_VIEW_IDS.length ; ++i) {
      int viewId = TEXT_VIEW_IDS[i];
      TextView text_view = (TextView) view.findViewById(viewId);
      if (null != text_view) {
        text_view.setTextColor(r.getColorStateList(text_colors[i]));
      }
    }
  }



  private void restoreLastView(View view, int id)
  {
    if (null == view) {
      return;
    }

    // This turns out to be fairly hard, because views are re-used. We can only
    // be certain that the last selected view needs to be re-coloured if it
    // hasn't been re-used in the meantime.
    //
    // Since all cell views have a unique tag that corresponds to the Boo they're
    // representing, all we need to do is remember the tag and the view
    // separately. If the last view's current tag is identical to the one we
    // remembered, then the view hasn't been re-used and needs to be coloured
    // again.
    if (mLastBoo != (Boo) view.getTag()) {
      return;
    }

    drawViewInternal(view, id, BACKGROUND_RESOURCE_REGULAR,
        TEXT_VIEW_COLORS_REGULAR);

    // Fade out play/pause button.
//    View v = view.findViewById(R.id.recent_boos_item_image);
//    v.setVisibility(View.VISIBLE);
//    v = view.findViewById(R.id.recent_boos_item_playpause);
//    v.setVisibility(View.GONE);
  }



  private void switchSelectedItems(View view, int id)
  {

    // Highlight the view that's just been selected.
    highlightSelectedView(view, id);

    // If that was all we did, we would end up colouring all views in the
    // same selected colour, so we also need to reset the previously
    // selected view to it's previous colour.
    restoreLastView(mLastView, mLastId);

    // Now remember the currently selected view, it's id, and it's tag.
    mLastView = view;
    mLastId = id;
    mLastBoo = (Boo) view.getTag();
  }



  private void onItemSelected(View view, int id)
  {
    // First, deal with the visual stuff. It's complex enough for it's own
    // function.
    switchSelectedItems(view, id);

    // Next, we'll need to kill the audio player and restart it, but only if
    // it's a different view that's been selected.
    // TODO
    Log.d(LTAG, "on item selecteD: " + id);
  }
}

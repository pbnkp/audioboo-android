/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.ListActivity;

import android.os.Bundle;

import android.os.Handler;
import android.os.Message;

import android.content.res.Configuration;
import android.content.res.Resources;

import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.AbsListView;

import android.view.Menu;
import android.view.MenuItem;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.widget.Toast;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import java.util.LinkedList;

import fm.audioboo.widget.BooPlayerView;

import android.util.Log;

/**
 * The BrowseActivity loads recent boos and displays them in a ListView.
 **/
public class BrowseActivity
       extends ListActivity
       implements BooListPaginator.Callback
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BrowseActivity";

  // Action identifiers -- must correspond to the indices of the array
  // "recent_boos_actions" in res/values/localized.xml
  private static final int  ACTION_REFRESH  = 0;
  private static final int  ACTION_FILTER   = 1;

  // Index into filter array where the "followed" action is.
  private static final int  FOLLOWED_INDEX  = 1;

  // Dialog IDs
  private static final int  DIALOG_FILTERS  = 1;
  private static final int  DIALOG_ERROR    = Globals.DIALOG_ERROR;

  /***************************************************************************
   * Data members
   **/
  // Content
  private BooListPaginator  mPaginator;

  // Last error information - used and cleared in onCreateDialog
  private int               mErrorCode = -1;
  private API.APIException  mException;

  // Labels for filters
  private String[]          mFilterLabels;


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
        Toast.makeText(BrowseActivity.this, R.string.browse_boos_playback_error,
            Toast.LENGTH_LONG).show();
      }
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

    setContentView(R.layout.browse_boos);
    View v = findViewById(R.id.browse_boos_empty);
    getListView().setEmptyView(v);

    // Initialize paginator
    mPaginator = new BooListPaginator(API.BOOS_POPULAR, this, this);
    mPaginator.setDisclosureListener(new View.OnClickListener() {
      public void onClick(View v)
      {
        onDisclosureClicked((Boo) v.getTag());
      }
    });

    // Initialize "retry" button on list empty vew
    v = findViewById(R.id.browse_boos_retry);
    if (null != v) {
      v.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
            mPaginator.refresh();
            setTitle(mFilterLabels[mPaginator.getType()]);
          }
      });
    }

    // Load filter labels
    mFilterLabels = getResources().getStringArray(R.array.browse_boos_filters);
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
    if (null == mPaginator.getList()) {
      mPaginator.refresh();
      setTitle(mFilterLabels[mPaginator.getType()]);
    }
    else {
      // Resume playback.
      BooPlayerView player = (BooPlayerView) findViewById(R.id.browse_boos_player);
      if (null != player) {
        if (player.isPaused()) {
          player.resume();
        }
        else {
          // This is a bit tricky... the only place where we reliably remember
          // the view/id for unselecting an item is in the playback end listener.
          PlaybackEndListener listener = (PlaybackEndListener) player.getPlaybackEndListener();
          if (null != listener) {
            onItemUnselected(listener.mView, listener.mId);
          }
        }
      }
    }

  }



  @Override
  public void onPause()
  {
    super.onPause();

    // Pause playback.
    BooPlayerView player = (BooPlayerView) findViewById(R.id.browse_boos_player);
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



  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    String[] menu_titles = getResources().getStringArray(R.array.browse_boos_actions);
    final int[] menu_icons = {
      R.drawable.ic_menu_refresh,
      R.drawable.ic_menu_filter,
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
        mPaginator.refresh();
        setTitle(mFilterLabels[mPaginator.getType()]);
        break;


      case ACTION_FILTER:
        showDialog(DIALOG_FILTERS);
        break;

      default:
        Toast.makeText(this, "Unreachable line reached.", Toast.LENGTH_LONG).show();
        return false;
    }

    return true;
  }



  private void onItemSelected(View view, int id)
  {
    // First, deal with the visual stuff. It's complex enough for it's own
    // function.
    mPaginator.getAdapter().markSelected(view, id);

    // Next, we'll need to kill the audio player and restart it, but only if
    // it's a different view that's been selected.
    Boo boo = mPaginator.getList().mClips.get(id);

    // Fade in player view
    BooPlayerView player = (BooPlayerView) findViewById(R.id.browse_boos_player);
    if (null != player) {
      player.setVisibility(View.VISIBLE);
      player.bringToFront();

      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
      player.startAnimation(animation);

      player.setPlaybackEndListener(new PlaybackEndListener(view, id));
      player.play(boo);
    }
  }



  private void onItemUnselected(View view, int id)
  {
    // And also switch the view to unselected.
    BooListAdapter adapter = mPaginator.getAdapter();
    if (null != adapter) {
      adapter.unselect(view, id);
    }

    // Fade out player view
    final BooPlayerView player = (BooPlayerView) findViewById(R.id.browse_boos_player);
    if (null != player) {
      Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
      animation.setAnimationListener(new Animation.AnimationListener() {
        public void onAnimationEnd(Animation animation)
        {
          // When the player finished fading out, stop capturing clicks.
          player.setVisibility(View.GONE);
          getListView().bringToFront();
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



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;
    Resources res = getResources();

    switch (id) {
      case DIALOG_ERROR:
        dialog = Globals.get().createDialog(this, id, mErrorCode, mException);
        mErrorCode = -1;
        mException = null;
        break;

      case DIALOG_FILTERS:
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
          .setTitle(res.getString(R.string.browse_boos_filter_title))
          .setItems(new String[] { "dummy" },
              new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which)
                  {
                    AlertDialog d = (AlertDialog) dialog;

                    // See if we're using the unmodified options or not.
                    int booType = which;
                    if (mFilterLabels.length != d.getListView().getCount()) {
                      if (booType >= FOLLOWED_INDEX) {
                        ++booType;
                      }
                    }

                    mPaginator.refresh(booType);
                    setTitle(mFilterLabels[booType]);
                  }
              }
            );
        dialog = builder.create();
        break;
    }

    return dialog;
  }



  protected void onPrepareDialog(int id, Dialog dialog)
  {
    Resources res = getResources();

    switch (id) {
      case DIALOG_FILTERS:
        AlertDialog ad = (AlertDialog) dialog;

        // Filter out 'followed' if the device is not linked
        String[] opts = mFilterLabels;
        API.Status status = Globals.get().getStatus();
        if (null == status || !status.mLinked) {
          opts = new String[mFilterLabels.length - 1];
          int offset = 0;
          for (int i = 0 ; i < opts.length ; ++i) {
            if (FOLLOWED_INDEX == i) {
              offset = 1;
            }
            opts[i] = mFilterLabels[i + offset];
          }
        }

        // Populate the dialog's list view.
        final ListView list = ad.getListView();
        list.setAdapter(new ArrayAdapter<CharSequence>(this,
            android.R.layout.select_dialog_item, android.R.id.text1, opts));
    }
  }



  private void setPageLoading(boolean loading)
  {
    // Find out which view the progress view is, and switch it to "loading".
    ListView lv = getListView();
    int pos = mPaginator.getList().mClips.size();
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
    Log.d(LTAG, "disclosure of " + boo + " clicked.");
  }



  /***************************************************************************
   * BooListPaginator.Callback implementation
   **/
  public void onStartRequest(boolean firstPage)
  {
    if (firstPage) {
      // Replace the list view with a loading screen.
      setListAdapter(null);

      View view = findViewById(R.id.browse_boos_progress);
      if (null != view) {
        view.setVisibility(View.VISIBLE);
      }
      view = findViewById(R.id.browse_boos_retry);
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
    // Show error dialog.
    mErrorCode = code;
    mException = exception;
    showDialog(DIALOG_ERROR);

    // Also reset view.
    setListAdapter(null);
    View view = findViewById(R.id.browse_boos_progress);
    if (null != view) {
      view.setVisibility(View.GONE);
    }
    view = findViewById(R.id.browse_boos_retry);
    if (null != view) {
      view.setVisibility(View.VISIBLE);
    }

    // Same for "loading" view; not that it matters at this point, but it
    // will when the view is populated again.
    if (null != mPaginator.getAdapter()) {
      mPaginator.getAdapter().setLoading(false, null);
    }
  }



  public void onItemClick(AdapterView<?> parent, View view, int position, long id)
  {
    // Use id rather than position, because of (future?) filtering.
    onItemSelected(view, (int) id);
  }
}

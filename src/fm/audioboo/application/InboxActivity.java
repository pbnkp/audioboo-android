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

import android.os.Bundle;

import android.content.res.Resources;

import android.widget.ListView;
import android.widget.ArrayAdapter;

import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import android.util.Log;

/**
 * The InboxActivity messages addressed to the current user.
 **/
public class InboxActivity extends BooListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "InboxActivity";

  // Action identifiers -- must correspond to the indices of the array
  // "recent_boos_actions" in res/values/localized.xml
  private static final int  ACTION_REFRESH  = 0;

  // Dialog IDs
  private static final int  DIALOG_ERROR    = Globals.DIALOG_ERROR;

  /***************************************************************************
   * Data members
   **/
  // Last error information - used and cleared in onCreateDialog
  private int               mErrorCode = -1;
  private API.APIException  mException;


  /***************************************************************************
   * BooListActivity implementation
   **/
  public int getViewId(int viewSpec)
  {
    switch (viewSpec) {
      case VIEW_ID_LAYOUT:
        return R.layout.inbox;

      case VIEW_ID_EMPTY_VIEW:
        return R.id.inbox_empty;

      case VIEW_ID_PLAYER:
        return R.id.inbox_player;

      case VIEW_ID_LOADING:
        return R.id.inbox_progress;

      case VIEW_ID_RETRY:
        return R.id.inbox_retry;

      default:
        return VIEW_ID_NONE;
    }
  }



  public int getInitAPI()
  {
    return API.BOOS_INBOX;
  }



  public String getTitleString(int api)
  {
    return "Inbox"; // FIXME
  }



  @Override
  public void onError(int code, API.APIException exception)
  {
    super.onError(code, exception);

    // Store error variables.
    mErrorCode = code;
    mException = exception;
    showDialog(DIALOG_ERROR);
  }



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
  }



  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    return false; // FIXME refresh only
//    String[] menu_titles = getResources().getStringArray(R.array.browse_boos_actions);
//    final int[] menu_icons = {
//      R.drawable.ic_menu_refresh,
//      R.drawable.ic_menu_filter,
//    };
//    assert(menu_icons.length == menu_titles.length);
//
//    for (int i = 0 ; i < menu_titles.length ; ++i) {
//      menu.add(0, i, 0, menu_titles[i]).setIcon(menu_icons[i]);
//    }
//    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case ACTION_REFRESH:
        refresh();
        break;


      default:
        Toast.makeText(this, "Unreachable line reached.", Toast.LENGTH_LONG).show();
        return false;
    }

    return true;
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
    }

    return dialog;
  }
}

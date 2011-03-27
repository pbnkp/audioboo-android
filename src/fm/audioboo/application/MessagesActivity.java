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

import java.util.List;

import android.util.Log;

/**
 * The MessagesActivity shows messages (boos with a sender and addressee) that
 * the user has sent, received or is uploading.
 **/
public class MessagesActivity extends BooListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "MessagesActivity";

  // Action identifiers -- must correspond to the indices of the array
  // "recent_boos_actions" in res/values/localized.xml
  private static final int  ACTION_REFRESH  = 0;
  private static final int  ACTION_SWITCH   = 1;

  // Dialog IDs
  private static final int  DIALOG_ERROR    = Globals.DIALOG_ERROR;

  // Display modes
  private static final int DISPLAY_MODE_INBOX   = 0;
  private static final int DISPLAY_MODE_OUTBOX  = 1;

  /***************************************************************************
   * Data members
   **/
  // Last error information - used and cleared in onCreateDialog
  private int               mErrorCode = -1;
  private API.APIException  mException;

  // Display mode
  private int               mDisplayMode = DISPLAY_MODE_INBOX;
  private int               mApiType = getInitAPI();


  /***************************************************************************
   * BooListActivity implementation
   **/
  public int getInitAPI()
  {
    return API.BOOS_INBOX;
  }



  public String getTitleString(int api)
  {
    return getResources().getString(R.string.inbox_title);
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
    menu.add(0, ACTION_REFRESH, 0, getResources().getString(R.string.inbox_menu_refresh))
      .setIcon(R.drawable.ic_menu_refresh);
    menu.add(1, ACTION_SWITCH, 1, getResources().getString(R.string.inbox_menu_outbox))
      .setIcon(R.drawable.ic_menu_upload);
    return true;
  }



  @Override
  public boolean onPrepareOptionsMenu(Menu menu)
  {
    MenuItem item = menu.getItem(1);

    if (DISPLAY_MODE_INBOX == mDisplayMode) {
      item.setTitle(R.string.inbox_menu_outbox);
      item.setIcon(R.drawable.ic_menu_upload);
    }
    else {
      item.setTitle(R.string.inbox_menu_inbox);
      item.setIcon(R.drawable.ic_menu_archive);
    }

    return true;
  }



  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId()) {
      case ACTION_REFRESH:
        refresh();
        break;


      case ACTION_SWITCH:
        if (DISPLAY_MODE_INBOX == mDisplayMode) {
          mDisplayMode = DISPLAY_MODE_OUTBOX;
          refresh(API.BOOS_INBOX); // FIXME should be OUTBOX
        }
        else {
          mDisplayMode = DISPLAY_MODE_INBOX;
          refresh(API.BOOS_INBOX);
        }
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



  /***************************************************************************
   * BooListPaginator.PaginatorDataSource implementation
   **/
  public int getGroupCount()
  {
    switch (mDisplayMode) {
      case DISPLAY_MODE_INBOX:
        return 1;

      case DISPLAY_MODE_OUTBOX:
        return 2;

      default:
        Log.e(LTAG, "unreachable line reached.");
        return 0;
    }
  }



  public int paginatedGroup()
  {
    switch (mDisplayMode) {
      case DISPLAY_MODE_INBOX:
        return 0;

      case DISPLAY_MODE_OUTBOX:
        return 1;

      default:
        Log.e(LTAG, "unreachable line reached.");
        return 0;
    }
  }



  public List<Boo> getGroup(int group)
  {
    switch (mDisplayMode) {
      case DISPLAY_MODE_INBOX:
        return null;

      case DISPLAY_MODE_OUTBOX:
        if (group == paginatedGroup()) {
          Log.e(LTAG, "Wait, what? We can't source the paginated group here.");
          return null;
        }
        return Globals.get().getBooManager().getMessageUploads();

      default:
        Log.e(LTAG, "unreachable line reached.");
        return null;
    }
  }



  public String getGroupLabel(int group)
  {
    switch (mDisplayMode) {
      case DISPLAY_MODE_INBOX:
        return "";

      case DISPLAY_MODE_OUTBOX:
        if (0 == group) {
          return getResources().getString(R.string.inbox_outbox);
        }
        return getResources().getString(R.string.inbox_sent);

      default:
        Log.e(LTAG, "unreachable line reached.");
        return null;
    }
  }



  public int getBackgroundResource(int viewType)
  {
    switch (viewType) {
      case BooListAdapter.VIEW_TYPE_BOO:
        return R.drawable.message_list_background;

      case BooListAdapter.VIEW_TYPE_MORE:
        return R.drawable.message_list_background_more;

      default:
        return -1;
    }
  }



  public int getElementColor(int element)
  {
    switch (element) {
      case BooListAdapter.ELEMENT_AUTHOR:
        return R.color.message_list_author;

      case BooListAdapter.ELEMENT_TITLE:
        return R.color.message_list_title;

      case BooListAdapter.ELEMENT_LOCATION:
        return R.color.message_list_location;

      default:
        return -1;
    }
  }
}

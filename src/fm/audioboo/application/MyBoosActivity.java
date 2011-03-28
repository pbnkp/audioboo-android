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

import android.content.Intent;
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
 * The MyBoosActivity shows any non-message boos that the user has crated, is
 * in the process of creating, or is uploading.
 **/
public class MyBoosActivity extends BooListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "MyBoosActivity";

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
  public int getInitAPI()
  {
    return API.BOOS_MINE;
  }



  public String getTitleString(int api)
  {
    return getResources().getString(R.string.my_boos_title);
  }



  @Override
  public void modifyDisclosureIntent(Boo boo, Intent intent)
  {
    if (null != boo.mData.mUploadInfo) {
      // If we have upload info, this shouldn't even happen.
      Log.e(LTAG, "Huh, disclosure clicked on an upload item?");
      return;
    }
    else if (null != boo.mData.mUploadedAt) {
      // Must be uploaded... we'll not modify stuff here.
      return;
    }

    // Right, neither uploaded nor uploading -- must be a draft.
    intent.putExtra(BooDetailsActivity.EXTRA_SHOW_EDIT, 1);
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
    return true;
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



  /***************************************************************************
   * BooListPaginator.PaginatorDataSource implementation
   **/
  public int getGroupCount()
  {
    return 3;
  }



  public int paginatedGroup()
  {
    // Last group is paginated.
    return getGroupCount() - 1;
  }



  public List<Boo> getGroup(int group)
  {
    // The order of groups is "uploads", "drafts", "my boos"
    switch (group) {
      case 0:
        return Globals.get().getBooManager().getBooUploads();

      case 1:
        return Globals.get().getBooManager().getDrafts();

      case 2:
        Log.e(LTAG, "Can't return the paginated group here.");
        return null;

      default:
        Log.e(LTAG, "unreachable line reached.");
        return null;
    }
  }



  public String getGroupLabel(int group)
  {
    switch (group) {
      case 0:
        return getResources().getString(R.string.my_uploads);

      case 1:
        return getResources().getString(R.string.my_drafts);

      case 2:
        return getResources().getString(R.string.my_boos);

      default:
        Log.e(LTAG, "unreachable line reached.");
        return null;
    }
  }



  @Override
  public int getGroupType(int group)
  {
    switch (group) {
      case 0:
        return BooListAdapter.VIEW_TYPE_UPLOAD;

      case 1:
      case 2:
        return BooListAdapter.VIEW_TYPE_BOO;

      default:
        Log.e(LTAG, "unreachable line reached.");
        return -1;
    }
  }
}

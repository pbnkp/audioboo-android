/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * Copyright (C) 2010,2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Activity;

import android.os.Bundle;

import android.content.Intent;
import android.content.res.Resources;

import android.widget.ListView;
import android.widget.ArrayAdapter;

import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

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

  // Activity codes
  private static final int ACTIVITY_RECORD  = ACTIVITY_DETAILS + 1;
  private static final int ACTIVITY_PUBLISH = ACTIVITY_DETAILS + 2;


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
  public void onDisclosureClicked(Boo boo, int group)
  {
    if (null == boo || null == boo.mData) {
      return;
    }

    switch (group) {
      case 0:
        Log.e(LTAG, "What? Disclosure on uploads clicked?");
        return;

      case 1:
        boo.writeToFile();
        Intent i = new Intent(this, PublishActivity.class);
        i.putExtra(PublishActivity.EXTRA_BOO_FILENAME, boo.mData.mFilename);
        startActivityForResult(i, ACTIVITY_PUBLISH);
        return;

      case 2:
        super.onDisclosureClicked(boo, group);
        return;

      default:
        Log.e(LTAG, "unreachable line");
        break;
    }
  }



  @Override
  public void onItemClicked(Boo boo, int group)
  {
    if (null == boo || null == boo.mData) {
      return;
    }

    switch (group) {
      case 0: // Uploads
        // Re-kick upload manager.
        Globals.get().mUploader.processQueue();
        Toast.makeText(this, getResources().getString(R.string.outbox_upload_kicked),
            Toast.LENGTH_LONG).show();
        return;

      case 1: // Drafts
        boo.writeToFile();
        Intent i = new Intent(this, RecordActivity.class);
        i.putExtra(RecordActivity.EXTRA_BOO_FILENAME, boo.mData.mFilename);
        startActivityForResult(i, ACTIVITY_RECORD);
        break;

      default:
        super.onItemClicked(boo, group);
        break;
    }
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



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    switch (requestCode) {
      case ACTIVITY_RECORD:
      case ACTIVITY_PUBLISH:
        if (Activity.RESULT_CANCELED == resultCode) {
          return;
        }
        refresh();
        break;

      case ACTIVITY_DETAILS:
      default:
        // Ignore
        break;
    }
  }



  @Override
  public void refresh()
  {
    Globals.get().getBooManager().rebuildIndex();
    super.refresh();
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
        return Globals.get().getBooManager().getBooDrafts();

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

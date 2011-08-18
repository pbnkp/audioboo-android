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
import android.app.NotificationManager;

import android.os.Bundle;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;

import android.widget.ListView;
import android.widget.ArrayAdapter;

import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

import fm.audioboo.service.Constants;

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
  private static final int  ACTION_SWITCH   = ACTION_REFRESH + 1;

  // Activity codes
  private static final int ACTIVITY_RECORD  = ACTIVITY_DETAILS + 1;
  private static final int ACTIVITY_PUBLISH = ACTIVITY_DETAILS + 2;


  /***************************************************************************
   * Public constants
   **/
  public static final String EXTRA_DISPLAY_MODE = "fm.audioboo.extras.display-mode";

  // Display modes
  public static final int DISPLAY_MODE_INBOX    = 0;
  public static final int DISPLAY_MODE_OUTBOX   = 1;


  /***************************************************************************
   * Data members
   **/
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
    if (API.BOOS_INBOX == api) {
      return getResources().getString(R.string.inbox_title_inbox);
    }
    return getResources().getString(R.string.inbox_title_outbox);
  }



  @Override
  public void onDisclosureClicked(Boo boo, int group)
  {
    if (null == boo || null == boo.mData) {
      return;
    }

    if (DISPLAY_MODE_INBOX == mDisplayMode) {
      super.onDisclosureClicked(boo, group);
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

    if (DISPLAY_MODE_INBOX == mDisplayMode) {
      super.onItemClicked(boo, group);
      return;
    }

    switch (group) {
      case 0: // Uploads
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

    Bundle extras = getIntent().getExtras();
    if (null != extras) {
      int mode = extras.getInt(EXTRA_DISPLAY_MODE, -1);
      switch (mode) {
        case DISPLAY_MODE_INBOX:
          mDisplayMode = mode;
          mApiType = API.BOOS_INBOX;
          break;

        case DISPLAY_MODE_OUTBOX:
          mDisplayMode = mode;
          mApiType = API.BOOS_OUTBOX;
          break;

        default:
          // Not set or invalid - we'll just ignore it.
          break;
      }
    }

    // If we've been launched as the inbox, clear messages notification
    if (DISPLAY_MODE_INBOX == mDisplayMode) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      nm.cancel(Constants.NOTIFICATION_MESSAGES);
    }
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
          refresh(API.BOOS_OUTBOX);
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
    switch (mDisplayMode) {
      case DISPLAY_MODE_INBOX:
        return 1;

      case DISPLAY_MODE_OUTBOX:
        return 3;

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
        return 2;

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
        switch (group) {
          case 0:
            return Globals.get().getBooManager().getMessageUploads();

          case 1:
            return Globals.get().getBooManager().getMessageDrafts();

          default:
            Log.e(LTAG, "Nope, not valid.");
            return null;
        }

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
        switch (group) {
          case 0:
            return getResources().getString(R.string.inbox_outbox);

          case 1:
            return getResources().getString(R.string.inbox_drafts);

          case 2:
            return getResources().getString(R.string.inbox_sent);

          default:
            Log.e(LTAG, "unreachable line");
            return null;
        }

      default:
        Log.e(LTAG, "unreachable line reached.");
        return null;
    }
  }



  @Override
  public int getBackgroundResource(int viewType)
  {
    switch (viewType) {
      case BooListAdapter.VIEW_TYPE_BOO:
      case BooListAdapter.VIEW_TYPE_UPLOAD:
        return R.drawable.message_list_background;

      case BooListAdapter.VIEW_TYPE_MORE:
        return R.drawable.message_list_background_more;

      default:
        return -1;
    }
  }



  @Override
  public int getElementColor(int element)
  {
    switch (element) {
      case BooListAdapter.ELEMENT_AUTHOR:
        return R.color.message_list_author;

      case BooListAdapter.ELEMENT_TITLE:
        return R.color.message_list_title;

      case BooListAdapter.ELEMENT_LOCATION:
        return R.color.message_list_location;

      case BooListAdapter.ELEMENT_PROGRESS:
        return R.color.message_list_progress;

      default:
        return -1;
    }
  }



  @Override
  public int getGroupType(int group)
  {
    if (DISPLAY_MODE_INBOX == mDisplayMode) {
      return BooListAdapter.VIEW_TYPE_BOO;
    }

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

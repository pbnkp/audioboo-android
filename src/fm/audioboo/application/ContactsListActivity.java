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

import android.app.ListActivity;
import android.app.Dialog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;

import android.view.View;

import android.widget.ViewAnimator;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import java.util.List;

import fm.audioboo.data.User;

import android.util.Log;


/**
 * Displays a list of contacts for the logged-in user.
 **/
public class ContactsListActivity extends ListActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "ContactsListActivity";

  // Dialog IDs
  private static final int  DIALOG_ERROR            = Globals.DIALOG_ERROR;


  /***************************************************************************
   * Public constants
   **/
  // Contact list cache key & timeout in seconds
  public static final String CONTACT_LIST_KEY       = "fm.audioboo.cache.contacts";
  public static final double CONTACT_LIST_TIMEOUT   = 300.0;


  /***************************************************************************
   * Private data
   **/
  // Current contact list.
  private List<User> mUsers;

  // Last error information - used and cleared in onCreateDialog
  private int               mErrorCode = -1;
  private API.APIException  mException;

  // Request handling
  private boolean                       mRequesting = false;
  private Handler                       mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        mRequesting = false;

        // Flip view
        ViewAnimator loading = (ViewAnimator) findViewById(R.id.contacts_progress_flipper);
        if (null != loading) {
          loading.showNext();
        }

        // Process results.
        if (API.ERR_SUCCESS == msg.what) {
          @SuppressWarnings("unchecked")
          List<User> users = (List<User>) msg.obj;
          onReceiveContacts(users);
        }
        else {
          onError(msg.what, (API.APIException) msg.obj);
        }
        return true;
      }

  });


  /***************************************************************************
   * Adapter implementation
   **/
  private class ContactsAdapter extends ArrayAdapter<String>
  {
    public ContactsAdapter()
    {
      super(ContactsListActivity.this, android.R.layout.simple_list_item_1);
    }



    public int getCount()
    {
      if (null == mUsers) {
        return 0;
      }
      return mUsers.size();
    }



    public String getItem(int position)
    {
      if (null == mUsers) {
        return null;
      }
      return mUsers.get(position).mUsername;
    }



    public long getItemId(int position)
    {
      if (null == mUsers) {
        return -1;
      }
      return mUsers.get(position).mId;
    }
  }


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.contacts);
    View v = findViewById(R.id.contacts_empty);
    getListView().setEmptyView(v);

    setTitle(R.string.contacts_title);

    // Initialize loading view
    ViewAnimator loading = (ViewAnimator) findViewById(R.id.contacts_progress_flipper);
    if (null != loading) {
      loading.setDisplayedChild(1);
    }

    // Initialize retry button
    v = findViewById(R.id.contacts_retry);
    if (null != v) {
      v.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            refresh();
          }
      });
    }
  }



  @Override
  public void onResume()
  {
    super.onResume();

    refresh();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void refresh()
  {
    if (mRequesting) {
      return;
    }

    @SuppressWarnings("unchecked")
    List<User> users = (List<User>) Globals.get().mObjectCache.get(CONTACT_LIST_KEY);
    if (null != users) {
      onReceiveContacts(users);
      return;
    }

    // Flip view
    ViewAnimator loading = (ViewAnimator) findViewById(R.id.contacts_progress_flipper);
    if (null != loading) {
      loading.showNext();
    }

    setListAdapter(null);

    // Load!
    mRequesting = true;
    Globals.get().mAPI.fetchContacts(mHandler);
  }



  private void onReceiveContacts(List<User> users)
  {
    // Add to cache.
    Globals.get().mObjectCache.put(CONTACT_LIST_KEY, users, CONTACT_LIST_TIMEOUT);

    // Populate list.
    mUsers = users;
    setListAdapter(new ContactsAdapter());

    // React to clicks.
    getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
          if (null == mUsers) {
            return;
          }

          User user = mUsers.get(position);
          Intent i = new Intent(ContactsListActivity.this, ContactDetailsActivity.class);
          i.putExtra(ContactDetailsActivity.EXTRA_CONTACT, (Parcelable) user);
          startActivity(i);
        }
    });
  }



  private void onError(int code, API.APIException exception)
  {
    mErrorCode = code;
    mException = exception;
    showDialog(DIALOG_ERROR);
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

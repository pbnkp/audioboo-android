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

import android.os.Handler;
import android.os.Message;

import android.app.ListActivity;

import android.view.View;

import android.widget.AdapterView;

import java.util.Date;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Holds a BooList, but also manages pagination. The class pretty much creates
 * a re-usable bridge between a ListActivity displaying a BooList, the
 * BooListAdapter required to do so, and handles API calls for fetching the
 * data.
 **/
public class BooListPaginator
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG                  = "BooListPaginator";

  // Page size for Boo requests
  public static final int BOO_PAGE_SIZE             = 15;



  /***************************************************************************
   * Result callback interface.
   **/
  public static interface Callback
  {
    /**
     * Called just before a request starts. The boolean flag specifies if the
     * request is for the first page, or a subsequent page.
     **/
    public void onStartRequest(boolean firstPage);

    /**
     * Called when a request delivers results successfully. Parameters are as
     * for onStartRequest().
     **/
    public void onResults(boolean firstPage);

    /**
     * Called on API errors. The exception may be null.
     **/
    public void onError(int code, API.APIException exception);

    /**
     * Also delegates onItemClick() from the ListActivity's ListView.
     **/
    public void onItemClick(AdapterView<?> parent, View view, int position, long id);
  };


  /***************************************************************************
   * Private data
   **/
  // Notification interface
  private Callback                      mCallback;

  // List related data
  private int                           mBooType;
  private BooList                       mBoos;

  // Activity/Adapter
  private WeakReference<ListActivity>   mActivity;
  private BooListAdapter                mAdapter;
  private BooListAdapter.ScrollListener mScrollListener;

  // Request/pagination related data
  private int                           mPage;
  private Date                          mTimestamp;
  private boolean                       mRequesting = false;

  private Handler                       mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        mRequesting = false;
        if (API.ERR_SUCCESS == msg.what) {
          onReceiveBoos((BooList) msg.obj);
        }
        else {
          // Correct pagination
          if (mPage > 1) {
            mPage -= 1;
          }

          if (null != mCallback) {
            mCallback.onError(msg.what, (API.APIException) msg.obj);
          }
        }
        return true;
      }

  });


  /***************************************************************************
   * Implementation
   **/
  public BooListPaginator(int booType, ListActivity activity, Callback callback)
  {
    // Initialize
    mActivity = new WeakReference<ListActivity>(activity);
    mCallback = callback;
    reset(booType);

    // Capture clicks on the "more" item.
    activity.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id)
      {
        if (position >= mBoos.mClips.size()) {
          if (mRequesting) {
            return;
          }

          nextPage();
        }
        else {
          if (null != mCallback) {
            mCallback.onItemClick(parent, view, position, id);
          }
        }
      }
    });
  }



  public BooList getList()
  {
    return mBoos;
  }



  public BooListAdapter getAdapter()
  {
    return mAdapter;
  }



  public int getType()
  {
    return mBooType;
  }



  public boolean isRequesting()
  {
    return mRequesting;
  }



  public void refresh(int booType)
  {
    reset(booType);
    request();
  }



  public void refresh()
  {
    reset(mBooType);
    request();
  }



  public void reset(int booType)
  {
    mBooType = booType;
    mAdapter = null;
    mPage = 1;
    mTimestamp = new Date();
  }



  public void nextPage()
  {
    mPage += 1;
    request();
  }



  private void request()
  {
    if (mRequesting) {
      // Wait for previous request to finish
      return;
    }

    // Notify that a request is starting.
    if (null != mCallback) {
      mCallback.onStartRequest(1 == mPage);
    }

    // Request boos
    mRequesting = true;
    Globals.get().mAPI.fetchBoos(mBooType, mHandler, mPage, BOO_PAGE_SIZE,
        mTimestamp);
  }



  private void onReceiveBoos(BooList boos)
  {
    ListActivity activity = mActivity.get();
    if (null == activity) {
      Log.e(LTAG, "Activity is dead, discarding results!");
      return;
    }


    // Either replace results or add results.
    if (null == mBoos || 0 == boos.mOffset || mBoos.mClips.size() != boos.mOffset) {
      mBoos = boos;
    }
    else {
      mBoos.mClips.addAll(boos.mClips);
      mBoos.mOffset = boos.mOffset;
      mBoos.mCount = boos.mCount;
    }

    // Log.d(LTAG, "Boos now: " + mBoos.mClips.size() + " - " + mBoos.mOffset);

    boolean firstPage = false;
    if (null == mAdapter) {
      firstPage = true;
      mAdapter = new BooListAdapter(activity, R.layout.boo_list_item, mBoos, R.layout.boo_list_more);
      activity.getListView().setOnScrollListener(new BooListAdapter.ScrollListener(mAdapter));
    }
    else {
      mAdapter.notifyDataSetChanged();
    }

    // Notify callback
    if (null != mCallback) {
      mCallback.onResults(firstPage);
    }
  }
}

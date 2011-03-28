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

import android.app.ExpandableListActivity;

import android.view.View;

import android.widget.AdapterView;
import android.widget.ExpandableListView;

import java.util.Date;
import java.util.List;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Holds a BooList, but also manages pagination. The class pretty much creates
 * a re-usable bridge between a ExpandableListActivity displaying a BooList, the
 * BooListAdapter required to do so, and handles API calls for fetching the
 * data.
 **/
public class BooListPaginator implements BooListAdapter.DataSource
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
     * Also delegates onItemClick() from the ExpandableListActivity's ListView.
     **/
    public void onItemClick(ExpandableListView parent, View view, int group,
        int position, long id);

    /**
     * Delegate long clicks, too
     **/
    public boolean onItemLongClick(ExpandableListView parent, View view,
        int group, int position, long id);
  };


  /***************************************************************************
   * Result callback interface.
   **/
  /**
   * Simplification of the BooListAdapter.DataSource concept: just returns
   * static data.
   **/
  public static interface PaginatorDataSource
  {
    /**
     * Returns the number of groups in the data source.
     **/
    public int getGroupCount();

    /**
     * Returns the group ID that's *not* served by this source, by a strange
     * twist of logic. The Paginator needs to know where it has to insert its
     * paginated data, after all.
     **/
    public int paginatedGroup();

    /**
     * Returns data for the given group, or null if the group ID provided is
     * either the same as paginatedGroup() returns, or outside the range of
     * 0..getGroupCount().
     **/
    public List<Boo> getGroup(int group);

    /**
     * Returns the group label.
     **/
    public String getGroupLabel(int group);

    /**
     * Returns the background resource for the given view type.
     **/
    public int getBackgroundResource(int viewType);

    /**
     * Returns the state drawable resource for coloring text elements.
     **/
    public int getElementColor(int element);

    /**
     * Return the view type for a given group. Usually expected to be
     * VIEW_TYPE_BOO; perhaps VIEW_TYPE_UPLOAD.
     **/
    public int getGroupType(int group);
  }



  /***************************************************************************
   * Private data
   **/
  // Notification interface
  private Callback                              mCallback;

  // List related data
  private int                                   mBooType;
  private BooList                               mBoos;
  private PaginatorDataSource                   mData;

  // Activity/Adapter
  private WeakReference<ExpandableListActivity> mActivity;
  private BooListAdapter                        mAdapter;
  private BooListAdapter.ScrollListener         mScrollListener;
  private View.OnClickListener                  mDisclosureListener;

  // Ugly hack: prevent long lciks from also registering as clicks
  private Pair<Integer, Integer>                mLastLongClick = null;

  // Request/pagination related data
  private int                                   mPage;
  private Date                                  mTimestamp;
  private boolean                               mRequesting = false;

  private Handler                               mHandler = new Handler(new Handler.Callback() {
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
  public BooListPaginator(int booType, ExpandableListActivity activity,
      PaginatorDataSource data, Callback callback)
  {
    // Initialize
    mActivity = new WeakReference<ExpandableListActivity>(activity);
    mData = data;
    mCallback = callback;
    reset(booType);

    // Capture clicks on the "more" item.
    activity.getExpandableListView().setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
      public boolean onChildClick(ExpandableListView parent, View view, int group, int position, long id)
      {
        if (null != mLastLongClick && mLastLongClick.mFirst == group
            && mLastLongClick.mSecond == position) {
          mLastLongClick = null;
          return true;
        }

        if (group > mData.getGroupCount()) {
          return false;
        }

        // Log.d(LTAG, "click: " + group + " / " + position);

        if (group == mData.paginatedGroup()) {
          // We're either asked for more data or for dispatching a click event.
          if (position >= mBoos.mClips.size()) {
            if (mRequesting) {
              return true;
            }

            nextPage();
          }
          else {
            if (null != mCallback) {
              mCallback.onItemClick(parent, view, group, position, id);
            }
          }
        }
        else {
          // For non-paginated groups, we're always asked for a click event
          if (null != mCallback) {
            mCallback.onItemClick(parent, view, group, position, id);
          }
        }

        return true;
      }
    });

    // Also capture long clicks
    activity.getExpandableListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
      {
        // Unfortunately there is no long click listener specific to
        // ExpandableListView, so we have to map a single position back to a
        // group/position-within-group.
        Pair<Integer, Integer> mapped = mAdapter.mapPosition(position);
        if (null == mapped) {
          return false;
        }
        mLastLongClick = mapped;

        // Log.d(LTAG, "long click: " + mapped.mFirst + " / " + mapped.mSecond);

        // For a paginated group, the "more" view exists and should be ignored
        if (mapped.mFirst == mData.paginatedGroup()
            && mapped.mSecond >= mBoos.mClips.size())
        {
          return true;
        }

        // Dispatch click event.
        if (null != mCallback) {
          return mCallback.onItemLongClick((ExpandableListView) parent, view, mapped.mFirst, mapped.mSecond, id);
        }

        return false;
      }
    });

  }



  void setDisclosureListener(View.OnClickListener listener)
  {
    mDisclosureListener = listener;
    if (null != mAdapter) {
      mAdapter.setDisclosureListener(mDisclosureListener);
    }
  }




  public BooList getPaginatedList()
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
    ExpandableListActivity activity = mActivity.get();
    if (null == activity) {
      Log.e(LTAG, "Activity is dead, discarding results!");
      return;
    }

    // Either replace results or add results.
    if (null == mBoos || 0 == boos.mOffset) {
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
      mAdapter = new BooListAdapter(activity, this, new int[] {
            R.layout.boo_list_item,
            R.layout.boo_list_group,
            R.layout.boo_list_more,
          });
      mAdapter.setDisclosureListener(mDisclosureListener);
      activity.getExpandableListView().setAdapter(mAdapter);
      activity.getExpandableListView().setOnScrollListener(new BooListAdapter.ScrollListener(mAdapter));
    }
    else {
      mAdapter.notifyDataSetChanged();
    }

    // Notify callback
    if (null != mCallback) {
      mCallback.onResults(firstPage);
    }
  }


  /***************************************************************************
   * BooListAdapter.DataSource implementation
   **/
  public int getGroupCount()
  {
    return mData.getGroupCount();
  }



  public boolean hideGroup(int group)
  {
    // XXX This could be massively more sophisticated, but we're using things
    //     this way only. Ah, well.
    if (1 == mData.getGroupCount()) {
      return true;
    }
    return false;
  }



  public List<Boo> getGroup(int group)
  {
    if (group == mData.paginatedGroup()) {
      return mBoos.mClips;
    }
    return mData.getGroup(group);
  }



  public boolean doesPaginate(int group)
  {
    return (group == mData.paginatedGroup());
  }



  public String getGroupLabel(int group)
  {
    return mData.getGroupLabel(group);
  }



  public int getBackgroundResource(int viewType)
  {
    return mData.getBackgroundResource(viewType);
  }



  public int getElementColor(int element)
  {
    return mData.getElementColor(element);
  }



  public int getGroupType(int group)
  {
    return mData.getGroupType(group);
  }
}

/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.widget.BaseAdapter;

import android.app.Activity;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.TextView;

import android.util.Log;

/**
 * Adapter for presenting a BooList in a ListView, Gallery or similar.
 **/
public class BooListAdapter extends BaseAdapter
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooListAdapter";


  /***************************************************************************
   * Data
   **/
  // Boo data
  private BooList   mBoos;

  // Layout for the individual boos
  private int       mBooLayoutId;

  // Calling Activity
  private Activity  mActivity;


  /***************************************************************************
   * Implementation
   **/
  public BooListAdapter(Activity activity, int booLayoutId, BooList boos)
  {
    mActivity = activity;
    mBooLayoutId = booLayoutId;
    mBoos = boos;
  }



  public View getView(int position, View convertView, ViewGroup parent)
  {
    // Create new view, if required.
    View view = convertView;
    if (null == view) {
      LayoutInflater inflater = mActivity.getLayoutInflater();
      view = inflater.inflate(mBooLayoutId, null);
    }

    // Set alternating background colors.
    if (0 == position % 2) {
      view.setBackgroundResource(R.drawable.recent_boos_background_odd);
    }
    else {
      view.setBackgroundResource(R.drawable.recent_boos_background_even);
    }

    Boo boo = mBoos.mClips.get(position);

    // Fill view with data.
    if (null != boo.mUser) {
      TextView text_view = (TextView) view.findViewById(R.id.recent_boos_item_author);
      if (null != text_view) {
        text_view.setText(boo.mUser.mUsername);
      }
    }

    TextView text_view = (TextView) view.findViewById(R.id.recent_boos_item_title);
    if (null != text_view) {
      text_view.setText(boo.mTitle);
    }

    if (null != boo.mLocation && null != boo.mLocation.mDescription) {
      text_view = (TextView) view.findViewById(R.id.recent_boos_item_location);
      if (null != text_view) {
        text_view.setText(boo.mLocation.mDescription);
      }
    }

    // TODO add more

    return view;
  }



  public int getCount()
  {
    return (null == mBoos ? 0 : mBoos.mClips.size());
  }



  public long getItemId(int position)
  {
    return position;
  }



  public Object getItem(int position)
  {
    return mBoos.mClips.get(position);
  }
}

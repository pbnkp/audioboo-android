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

import android.app.ListActivity;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.AbsListView;

import android.net.Uri;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import android.os.Handler;
import android.os.Message;

import java.util.LinkedList;

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
   * Helper class. Makes the BooListAdapter aware of relevant changes in
   * the scroll state of the ListView it's filling, so it can trigger the
   * or cancel heavy lifiting such as downloading images.
   **/
  public static class ScrollListener implements AbsListView.OnScrollListener
  {
    private BooListAdapter  mAdapter;

    private boolean         mSentInitial = false;
    private int             mFirst;
    private int             mCount;

    public ScrollListener(BooListAdapter adapter)
    {
      mAdapter = adapter;
    }



    public void onScroll(AbsListView view, int firstVisibleItem,
        int visibleItemCount, int totalItemCount)
    {
      mFirst = firstVisibleItem;
      mCount = visibleItemCount;

      if (!mSentInitial && visibleItemCount > 0) {
        mSentInitial = true;
        mAdapter.startHeavyLifting(mFirst, mCount);
      }
    }



    public void onScrollStateChanged(AbsListView view, int scrollState)
    {
      if (SCROLL_STATE_IDLE != scrollState) {
        mAdapter.cancelHeavyLifting();
        return;
      }

      mAdapter.startHeavyLifting(mFirst, mCount);
    }
  }


  /***************************************************************************
   * Data
   **/
  // Boo data
  private BooList       mBoos;

  // Layout for the individual boos
  private int           mBooLayoutId;

  // Calling Activity
  private ListActivity  mActivity;

  // Image dimensions. XXX The (reasonably safe) assumption is that in the view
  // this adapter fills, all Boo images are to be displayed at the same size.
  private int           mDimensions = -1;


  /***************************************************************************
   * Implementation
   **/
  public BooListAdapter(ListActivity activity, int booLayoutId, BooList boos)
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

    // Set the view's tag to the Boo we want to display. This is for later
    // identification.
    Boo boo = mBoos.mClips.get(position);
    view.setTag(boo);

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

    // If the image cache contains an appropriate image at the right size, then
    // we'll display that. If not, display a default image. We need to do the
    // second in case the item view is being reused.
    ImageView image_view = (ImageView) view.findViewById(R.id.recent_boos_item_image);
    if (null != image_view) {
      // First, determine the url we want to display.
      Uri image_url = getDisplayUrl(boo);

      boolean customImageSet = false;
      if (null != image_url) {
        // If we don't know the image dimensions yet, determine them now.
        if (-1 == mDimensions) {
          mDimensions = image_view.getLayoutParams().width
            - image_view.getPaddingLeft() - image_view.getPaddingRight();
        }

        // Next, try to grab an image from the cache.
        Bitmap bitmap = Globals.get().mImageCache.get(image_url, mDimensions);

        if (null != bitmap) {
          image_view.setImageDrawable(new BitmapDrawable(bitmap));
          customImageSet = true;
        }
      }

      // If we were not able to set a custom image here, default to the
      // anonymous_boo one.
      if (!customImageSet) {
        image_view.setImageResource(R.drawable.anonymous_boo);
      }
    }

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



  private void cancelHeavyLifting()
  {
    Globals.get().mImageCache.cancelFetching();
  }



  private void startHeavyLifting(int first, int count)
  {
    // Log.d(LTAG, "Downloads for items from " + first + " to " + (first + count));

    // Prepare the list of uris to download.
    LinkedList<ImageCache.CacheItem> uris = new LinkedList<ImageCache.CacheItem>();
    for (int i = 0 ; i < count ; ++i) {
      int index = first + i;
      Boo boo = mBoos.mClips.get(index);
      Uri uri = getDisplayUrl(boo);

      if (null == uri) {
        continue;
      }

      uris.add(new ImageCache.CacheItem(index, i, uri, mDimensions));
    }

    if (0 < uris.size()) {
      // Use the cache to fetch these images.
      Globals.get().mImageCache.fetch(uris, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          ImageCache.CacheItem item = (ImageCache.CacheItem) msg.obj;
          switch (msg.what) {
            case ImageCache.MSG_OK:
              onCacheItemFetched(item);
              break;

            case ImageCache.MSG_CANCELLED:
              //Log.d(LTAG, "Download of image cancelled: " + item.mImageUri);
              break;

            case ImageCache.MSG_ERROR:
            default:
              Log.e(LTAG, "Error fetching image at URL: " + item.mImageUri);
              break;
          }
          return true;
        }
      }));
    }
  }



  public void onCacheItemFetched(ImageCache.CacheItem item)
  {
    // Log.d(LTAG, "got results for : " + item.mImageUri);

    // Right, we got an image. Now we just need to figure out the right view
    // to go with it.
    View item_view = mActivity.getListView().getChildAt(item.mViewIndex);
    if (null == item_view) {
      return;
    }

    // Make sure that the item for which the request was scheduled and the
    // item we're displaying currently are the same. That's what we set the
    // view's tag for in getView().
    Boo expected_boo = mBoos.mClips.get(item.mItemIndex);
    Boo current_boo = (Boo) item_view.getTag();
    if (expected_boo.mId != current_boo.mId) {
      // There's been a race between cancelling downloads and sending the
      // message with the resulting bitmap.
      return;
    }

    // Now display the image.
    ImageView image_view = (ImageView) item_view.findViewById(R.id.recent_boos_item_image);
    if (null == image_view) {
      return;
    }

    image_view.setImageDrawable(new BitmapDrawable(item.mBitmap));
  }



  private Uri getDisplayUrl(Boo boo)
  {
    if (null == boo) {
      return null;
    }

    Uri result = boo.mImageUrl;
    if (null == result && null != boo.mUser) {
      result = boo.mUser.mImageUrl;
    }

    return result;
  }
}

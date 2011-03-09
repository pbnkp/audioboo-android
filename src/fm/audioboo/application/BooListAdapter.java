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

import android.widget.BaseAdapter;

import android.app.ListActivity;

import android.content.res.Resources;

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

import java.lang.ref.WeakReference;

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


  // Text view IDs in list items
  private static final int TEXT_VIEW_IDS[] = {
    R.id.boo_list_item_author,
    R.id.boo_list_item_title,
    R.id.boo_list_item_location,
  };


  // Color IDs for the above text view IDs for the regular, unselected state
  private static final int TEXT_VIEW_COLORS_REGULAR[] = {
    R.color.boo_list_author,
    R.color.boo_list_title,
    R.color.boo_list_location,
  };

  // Color IDs for the above text view IDs for the selected state
  private static final int TEXT_VIEW_COLORS_SELECTED[] = {
    R.color.boo_list_author_selected,
    R.color.boo_list_title_selected,
    R.color.boo_list_location_selected,
  };

  // Color IDs for the item background for the regular, unselected state
  private static final int BACKGROUND_RESOURCE_REGULAR[] = {
    R.drawable.boo_list_background,
  };

  // Color IDs for the item background for the selected state
  private static final int BACKGROUND_RESOURCE_SELECTED[] = {
    R.color.boo_list_background_active,
  };


  // Item view types
  private static final int VIEW_TYPE_BOO  = 0;
  private static final int VIEW_TYPE_MORE = 1;


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
   * Cache baton
   **/
  private class Baton
  {
    public int itemIndex;
    public int viewIndex;

    Baton(int _itemIndex, int _viewIndex)
    {
      itemIndex = _itemIndex;
      viewIndex = _viewIndex;
    }
  }



  /***************************************************************************
   * Data
   **/
  // Boo data
  private BooList                     mBoos;

  // Layout for the individual boos, the "..." (more) view.
  private int                         mBooLayoutId;
  private int                         mMoreLayoutId = -1;

  // Flag, for deciding whether the "more" view should be shown as loading or
  // not.
  private boolean                     mLoading = false;

  // Calling Activity
  private WeakReference<ListActivity> mActivity;

  // Image dimensions. XXX The (reasonably safe) assumption is that in the view
  // this adapter fills, all Boo images are to be displayed at the same size.
  private int                         mDimensions = -1;

  // State required for restoring the last selected view to it's original
  // looks.
  private View                        mLastView;
  private int                         mLastId = -1;
  private Boo                         mLastBoo;

  // Handler for disclosure clicks
  private View.OnClickListener        mDisclosureListener;


  /***************************************************************************
   * Implementation
   **/
  public BooListAdapter(ListActivity activity, int booLayoutId, BooList boos)
  {
    super();

    mActivity = new WeakReference<ListActivity>(activity);
    mBooLayoutId = booLayoutId;
    mBoos = boos;
  }



  public BooListAdapter(ListActivity activity, int booLayoutId, BooList boos,
      int moreLayoutId)
  {
    super();

    mActivity = new WeakReference<ListActivity>(activity);
    mBooLayoutId = booLayoutId;
    mMoreLayoutId = moreLayoutId;
    mBoos = boos;
  }



  void setDisclosureListener(View.OnClickListener listener)
  {
    mDisclosureListener = listener;
  }



  public View getView(int position, View convertView, ViewGroup parent)
  {
    ListActivity activity = mActivity.get();
    if (null == activity) {
      return null;
    }

    // View type
    int type = getItemViewType(position);

    // Create new view, if required.
    View view = convertView;
    if (null == view) {
      LayoutInflater inflater = activity.getLayoutInflater();
      if (VIEW_TYPE_BOO == type) {
        view = inflater.inflate(mBooLayoutId, null);
      }
      else {
        if (mMoreLayoutId == -1) {
          Log.e(LTAG, "No layout for the 'more' view provided...");
          return null;
        }
        view = inflater.inflate(mMoreLayoutId, null);
      }
    }

    if (VIEW_TYPE_MORE == type) {
      prepareMoreView(view);
      return view;
    }

    // Set the view's tag to the Boo we want to display. This is for later
    // identification.
    Boo boo = mBoos.mClips.get(position);
    view.setTag(boo);

    // Make sure the view is properly selected/deselected
    if (mLastId == position) {
      drawViewAsHighlighted(view, position);
      mLastView = view;
      mLastBoo = boo;
    }
    else {
      drawViewAsRegular(view, position);
    }

    // Fill view with data.
    TextView text_view = (TextView) view.findViewById(R.id.boo_list_item_author);
    if (null != text_view) {
      if (null != boo.mData.mUser && null != boo.mData.mUser.mUsername) {
        text_view.setText(boo.mData.mUser.mUsername);
      }
      else {
        text_view.setText("");
      }
    }

    text_view = (TextView) view.findViewById(R.id.boo_list_item_title);
    if (null != text_view) {
      text_view.setText(null != boo.mData.mTitle ? boo.mData.mTitle : "");
    }

    text_view = (TextView) view.findViewById(R.id.boo_list_item_location);
    if (null != text_view) {
      if (null != boo.mData.mLocation && null != boo.mData.mLocation.mDescription) {
        text_view.setText(boo.mData.mLocation.mDescription);
      }
      else {
        text_view.setText("");
      }
    }

    // If the image cache contains an appropriate image at the right size, then
    // we'll display that. If not, display a default image. We need to do the
    // second in case the item view is being reused.
    ImageView image_view = (ImageView) view.findViewById(R.id.boo_list_item_image);
    if (null != image_view) {
      // First, determine the url we want to display.
      Pair<String, Uri> image_url = getDisplayUrl(boo);

      boolean customImageSet = false;
      if (null != image_url) {
        // If we don't know the image dimensions yet, determine them now.
        if (-1 == mDimensions) {
          mDimensions = image_view.getLayoutParams().width
            - image_view.getPaddingLeft() - image_view.getPaddingRight();
        }

        // Next, try to grab an image from the cache.
        Bitmap bitmap = Globals.get().mImageCache.get(image_url.mFirst, mDimensions);

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

    // Disclosure
    View disclosure = view.findViewById(R.id.boo_list_item_disclosure);
    if (null != disclosure && null != mDisclosureListener) {
      disclosure.setTag(boo);
      disclosure.setOnClickListener(mDisclosureListener);
    }

    return view;
  }



  public void markSelected(View view, int id)
  {
    //Log.d(LTAG, "switch selected");

    // Highlight the view that's just been selected.
    drawViewAsHighlighted(view, id);

    // If that was all we did, we would end up colouring all views in the
    // same selected colour, so we also need to reset the previously
    // selected view to it's previous colour.
    //
    // This turns out to be fairly hard, because views are re-used. We can only
    // be certain that the last selected view needs to be re-coloured if it
    // hasn't been re-used in the meantime.
    //
    // Since all cell views have a unique tag that corresponds to the Boo they're
    // representing, all we need to do is remember the tag and the view
    // separately. If the last view's current tag is identical to the one we
    // remembered, then the view hasn't been re-used and needs to be coloured
    // again.
    if (mLastBoo != (Boo) view.getTag()) {
      drawViewAsRegular(mLastView, mLastId);
    }

    // Now remember the currently selected view, it's id, and it's tag.
    mLastView = view;
    mLastId = id;
    mLastBoo = (Boo) view.getTag();
  }



  public void unselect(View view, int id)
  {
    drawViewAsRegular(view, id);

    mLastView = null;
    mLastId = -1;
    mLastBoo = null;
  }



  public void drawViewAsHighlighted(View view, int id)
  {
    if (null == view) {
      return;
    }

    // Set view attributes
    drawViewInternal(view, id, BACKGROUND_RESOURCE_SELECTED,
        TEXT_VIEW_COLORS_SELECTED);
  }



  public void drawViewAsRegular(View view, int id)
  {
    if (null == view) {
      return;
    }

    // Set view attributes
    drawViewInternal(view, id, BACKGROUND_RESOURCE_REGULAR,
        TEXT_VIEW_COLORS_REGULAR);
  }



  private void drawViewInternal(View view, int id, int[] backgrounds, int[] text_colors)
  {
    ListActivity activity = mActivity.get();
    if (null == activity) {
      return;
    }

    // First, set the background according to whether or not id points to an
    // odd or even cell.
    view.setBackgroundResource(backgrounds[id % backgrounds.length]);

    // Next, iterate over the known text views, and set their colors.
    Resources r = activity.getResources();
    for (int i = 0 ; i < TEXT_VIEW_IDS.length ; ++i) {
      int viewId = TEXT_VIEW_IDS[i];
      TextView text_view = (TextView) view.findViewById(viewId);
      if (null != text_view) {
        text_view.setTextColor(r.getColorStateList(text_colors[i]));
      }
    }
  }



  public int getViewTypeCount()
  {
    return (mMoreLayoutId == -1 ? 1 : 2);
  }



  public int getItemViewType(int position)
  {
    if (position < mBoos.mClips.size()) {
      return VIEW_TYPE_BOO;
    }
    return VIEW_TYPE_MORE;
  }



  public int getCount()
  {
    return (null == mBoos ? 0 : mBoos.mClips.size()) + (mMoreLayoutId == -1 ? 0 : 1);
  }



  public long getItemId(int position)
  {
    return position;
  }



  public Object getItem(int position)
  {
    if (position >= mBoos.mClips.size()) {
      return null;
    }
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
      if (index >= mBoos.mClips.size()) {
        break;
      }

      Boo boo = mBoos.mClips.get(index);
      Pair<String, Uri> uri = getDisplayUrl(boo);

      if (null == uri) {
        continue;
      }

      uris.add(new ImageCache.CacheItem(uri.mSecond, mDimensions,
            new Baton(index, i), uri.mFirst));
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
              Log.e(LTAG, "Error fetching image at URL: " + (item != null ? item.mImageUri : null));
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
    ListActivity activity = mActivity.get();
    if (null == activity) {
      return;
    }

    Baton baton = (Baton) item.mBaton;

    // Right, we got an image. Now we just need to figure out the right view
    // to go with it.
    View item_view = activity.getListView().getChildAt(baton.viewIndex);
    if (null == item_view) {
      return;
    }

    // Make sure that the item for which the request was scheduled and the
    // item we're displaying currently are the same. That's what we set the
    // view's tag for in getView().
    Boo expected_boo = mBoos.mClips.get(baton.itemIndex);
    Boo current_boo = (Boo) item_view.getTag();
    if (expected_boo.mData.mId != current_boo.mData.mId) {
      // There's been a race between cancelling downloads and sending the
      // message with the resulting bitmap.
      return;
    }

    // Now display the image.
    ImageView image_view = (ImageView) item_view.findViewById(R.id.boo_list_item_image);
    if (null == image_view) {
      return;
    }

    image_view.setImageDrawable(new BitmapDrawable(item.mBitmap));
  }



  private Pair<String, Uri> getDisplayUrl(Boo boo)
  {
    if (null == boo) {
      return null;
    }

    Uri result = boo.mData.getThumbUrl();
    if (null == result && null != boo.mData.mUser) {
      result = boo.mData.mUser.getThumbUrl();
    }

    if (null == result) {
      return null;
    }

    String cacheKey = result.toString();

    // If the result is relative (i.e. has no authority), then we need to make
    // it absolute. Also, we need to sign it.
    if (null == result.getAuthority()) {
      result = Globals.get().mAPI.makeAbsoluteUri(result);
      result = Globals.get().mAPI.signUri(result);
    }

    return new Pair<String, Uri>(cacheKey, result);
  }



  public void setLoading(boolean loading, View view)
  {
    mLoading = loading;
    if (null != view) {
      prepareMoreView(view);
    }
  }



  public void prepareMoreView(View view)
  {
    View swap = view.findViewById(R.id.boo_list_more_dots);
    if (null != swap) {
      swap.setVisibility(mLoading ? View.GONE : View.VISIBLE);
    }

    swap = view.findViewById(R.id.boo_list_more_progress);
    if (null != swap) {
      swap.setVisibility(mLoading ? View.VISIBLE : View.GONE);
    }
  }
}

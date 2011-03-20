/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;

import android.content.Intent;
import android.content.res.Configuration;

import android.net.Uri;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;

import android.app.Dialog;

import java.util.LinkedList;

import fm.audioboo.data.BooData;

import android.util.Log;

/**
 * Show details for a Boo.
 **/
public class BooDetailsActivity
       extends Activity
       implements ViewTreeObserver.OnGlobalLayoutListener
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooDetailsActivity";


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_DATA = "fm.audioboo.extras.boo-data";


  /***************************************************************************
   * Cache baton
   **/
  private class Baton
  {
    public int containerId;
    public int imageId;
    public int progressId;

    Baton(int _containerId, int _imageId, int _progressId)
    {
      containerId = _containerId;
      imageId = _imageId;
      progressId = _progressId;
    }
  }



  /***************************************************************************
   * Private data
   **/
  private Boo   mBoo;


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.boo_details);

    // Grab extras
    BooData data = getIntent().getParcelableExtra(EXTRA_BOO_DATA);
    if (null == data) {
      throw new IllegalArgumentException("Intent needs to define the '"
          + EXTRA_BOO_DATA + "' extra.");
    }

    mBoo= new Boo(data);
    Log.d(LTAG, "Boo: " + mBoo);

    // Fill view with data!
    populateView();

    // Listen to changes in layout; we really need this to scale images
    // properly.
    findViewById(R.id.boo_details).getViewTreeObserver().addOnGlobalLayoutListener(this);
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again. XXX We need to ignore this in the parent activity so the child
    // actvities don't get restarted. Ignoring in the child activities is also
    // required.
    super.onConfigurationChanged(config);
  }



  private void populateView()
  {
    if (null == mBoo || null == mBoo.mData) {
      return;
    }

    // Prepare for loading images.
    LinkedList<ImageCache.CacheItem> uris = new LinkedList<ImageCache.CacheItem>();

    // Thumbnail
    ImageView image_view = (ImageView) findViewById(R.id.boo_thumb);
    if (null != image_view) {
      if (null != mBoo.mData.mUser) {
        Uri uri = mBoo.mData.mUser.getThumbUrl();
        if (null != uri) {
          int size = image_view.getLayoutParams().width - image_view.getPaddingLeft()
            - image_view.getPaddingRight();
         uris.add(new ImageCache.CacheItem(uri, size, new Baton(-1, R.id.boo_thumb, -1)));
        }
      }


      image_view.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            if (null != mBoo.mData.mUser) {
              Intent i = new Intent(BooDetailsActivity.this, ContactDetailsActivity.class);
              i.putExtra(ContactDetailsActivity.EXTRA_CONTACT, (Parcelable) mBoo.mData.mUser);
              startActivity(i);
            }
            else {
              Toast.makeText(BooDetailsActivity.this, R.string.details_user_not_available, Toast.LENGTH_LONG).show();
            }
          }
      });
    }

    // Author
    TextView text_view = (TextView) findViewById(R.id.boo_author);
    if (null != text_view) {
      if (null != mBoo.mData.mUser && null != mBoo.mData.mUser.mUsername) {
        text_view.setText(mBoo.mData.mUser.mUsername);
      }
      else {
        text_view.setText(getResources().getString(R.string.boo_unknown_author));
      }
    }

    // Title
    text_view = (TextView) findViewById(R.id.boo_title);
    if (null != text_view) {
      text_view.setText(null != mBoo.mData.mTitle ? mBoo.mData.mTitle : "");
    }

    // Date
    text_view = (TextView) findViewById(R.id.boo_date);
    if (null != text_view) {
      text_view.setText(NaturalDateFormat.format(mBoo.mData.mRecordedAt));
    }

    // Image
    View container = findViewById(R.id.boo_image_container);
    if (null != container) {
      Uri image_uri = mBoo.mData.getFullUrl();
      if (null == image_uri) {
        container.setVisibility(View.GONE);
      }
      else {
        // Create signed absolute URI
        String cacheKey = image_uri.toString();
        if (null == image_uri.getAuthority()) {
          image_uri = Globals.get().mAPI.makeAbsoluteUri(image_uri);
          image_uri = Globals.get().mAPI.signUri(image_uri);
        }

        // Setup view.
        image_view = (ImageView) container.findViewById(R.id.boo_image);
        if (null != image_view) {
          int size = (int) (Globals.get().FULL_IMAGE_WIDTH
              * getResources().getDisplayMetrics().density);
 
          uris.add(new ImageCache.CacheItem(image_uri, size,
                new Baton(R.id.boo_image_container, R.id.boo_image, R.id.boo_image_progress),
                cacheKey));
        }
      }
    }

    // Location
    container = findViewById(R.id.boo_location_container);
    if (null != container) {
      if (null == mBoo.mData.mLocation) {
        container.setVisibility(View.GONE);
      }
      else {
        // Text
        text_view = (TextView) container.findViewById(R.id.boo_location_text);
        if (null != text_view) {
          text_view.setText(mBoo.mData.mLocation.mDescription);
        }

        // Image
        Uri uri = Uri.parse(String.format("http://maps.google.com/maps/api/staticmap?center=%f,%f&zoom=11&size=300x300&maptype=roadmap&markers=%f,%f&sensor=true",
              mBoo.mData.mLocation.mLatitude, mBoo.mData.mLocation.mLongitude,
              mBoo.mData.mLocation.mLatitude, mBoo.mData.mLocation.mLongitude));
        image_view = (ImageView) container.findViewById(R.id.boo_location);
        if (null != image_view) {
          int size = (int) (Globals.get().FULL_IMAGE_WIDTH
              * getResources().getDisplayMetrics().density);
 
          uris.add(new ImageCache.CacheItem(uri, size,
                new Baton(R.id.boo_location_container, R.id.boo_location, R.id.boo_location_progress)));

          // Launch a location query
          container.setOnClickListener(new View.OnClickListener() {
              public void onClick(View v)
              {
                Uri data = Uri.parse(String.format("http://maps.google.com/?ll=%f,%f&saddr=%f,%f&z=13",
                    mBoo.mData.mLocation.mLatitude, mBoo.mData.mLocation.mLongitude,
                    mBoo.mData.mLocation.mLatitude, mBoo.mData.mLocation.mLongitude));
                Intent i = new Intent(Intent.ACTION_VIEW, data);
                startActivity(i);
              }
          });
        }
      }
    }


    // Finally, fire off requests for images.
    if (0 < uris.size()) {
      Globals.get().mImageCache.fetch(uris, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          ImageCache.CacheItem item = (ImageCache.CacheItem) msg.obj;
          Baton baton = (Baton) item.mBaton;

          ImageView image_view = (ImageView) findViewById(baton.imageId);
          if (null == image_view) {
            Log.d(LTAG, "did not find view with id: " + baton.imageId);
            return true;
          }

          switch (msg.what) {
            case ImageCache.MSG_OK:
              image_view.setImageDrawable(new BitmapDrawable(item.mBitmap));
              image_view.setVisibility(View.VISIBLE);

              if (ImageView.ScaleType.FIT_XY == image_view.getScaleType()) {
                ViewGroup.LayoutParams params = image_view.getLayoutParams();
                params.height = item.mBitmap.getScaledHeight(getResources().getDisplayMetrics());
                image_view.setLayoutParams(params);
              }

              if (-1 != baton.progressId) {
                View progress = findViewById(baton.progressId);
                if (null != progress) {
                  progress.setVisibility(View.GONE);
                }
              }
              break;

            case ImageCache.MSG_ERROR:
            default:
              Log.e(LTAG, "Error fetching image at URL: " + (item != null ? item.mImageUri : null));

            case ImageCache.MSG_CANCELLED:
              // Hide container
              if (-1 != baton.containerId) {
                View container = findViewById(baton.containerId);
                if (null != container) {
                  container.setVisibility(View.GONE);
                }
              }
              break;
          }
          return true;
        }
      }));

    }
  }



  /***************************************************************************
   * ViewTreeObserver.OnGlobalLayoutListener implementation
   **/
  public void onGlobalLayout()
  {
    // Rescale boo image if necessary
    ImageView image_view = (ImageView) findViewById(R.id.boo_image);
    if (null != image_view) {
      Drawable drawable = image_view.getDrawable();

      float ratio = ((float) drawable.getIntrinsicWidth()) / drawable.getIntrinsicHeight();

      ViewGroup.LayoutParams params = image_view.getLayoutParams();
      int height = (int) (image_view.getWidth() / ratio);
      // Only set params if the height changes, or we enter an infinite loop
      if (height != params.height) {
        params.height = height;
        image_view.setLayoutParams(params);
      }
    }

    // Rescale boo location image if necessary
    image_view = (ImageView) findViewById(R.id.boo_location);
    if (null != image_view) {
      Drawable drawable = image_view.getDrawable();

      float ratio = ((float) drawable.getIntrinsicWidth()) / drawable.getIntrinsicHeight();

      ViewGroup.LayoutParams params = image_view.getLayoutParams();
      int height = (int) (image_view.getWidth() / ratio);
      // Only set params if the height changes, or we enter an infinite loop
      if (height != params.height) {
        params.height = height;
        image_view.setLayoutParams(params);
      }
    }


  }




}

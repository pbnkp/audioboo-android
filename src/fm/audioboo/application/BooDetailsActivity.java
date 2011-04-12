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
import android.content.res.ColorStateList;

import android.net.Uri;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ViewAnimator;
import android.widget.Toast;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;

import android.app.Dialog;

import android.util.TypedValue;

import java.util.List;
import java.util.LinkedList;

import org.apache.http.NameValuePair;

import fm.audioboo.data.BooData;
import fm.audioboo.data.PlayerState;
import fm.audioboo.data.Tag;
import fm.audioboo.service.BooPlayerClient;
import fm.audioboo.service.Constants;
import fm.audioboo.widget.LeftAlignedLayout;

import android.util.Log;

/**
 * Show details for a Boo.
 **/
public class BooDetailsActivity
       extends Activity
       implements ViewTreeObserver.OnGlobalLayoutListener,
                  BooPlayerClient.ProgressListener
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
  public static final String EXTRA_BOO_DATA   = "fm.audioboo.extras.boo-data";

  // Cache keys
  public static final String BOO_KEY_FORMAT = "fm.audioboo.cache.boo-%d";
  public static final double BOO_TIMEOUT = 1200.0;


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
  private Boo     mBoo;

  // Request handling
  private Handler mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          mBoo = (Boo) msg.obj;

          String key = String.format(BOO_KEY_FORMAT, mBoo.mData.mId);
          Globals.get().mObjectCache.put(key, mBoo, BOO_TIMEOUT);

          populateView();
        }
        else {
          Toast.makeText(BooDetailsActivity.this, R.string.details_load_error, Toast.LENGTH_LONG).show();
          finish();
        }
        return true;
      }
  });


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.boo_details);

    Intent intent = getIntent();

    // See if we perhaps are launched via ACTION_VIEW;
    Uri dataUri = intent.getData();
    if (null != dataUri) {
      int id = -1;
      boolean message = false;

      List<NameValuePair> params = UriUtils.getQuery(dataUri);
      for (NameValuePair pair : params) {
        if (pair.getName().equals("id")) {
          try {
            id = Integer.valueOf(pair.getValue());
          } catch (NumberFormatException ex) {
            id = Boo.INVALID_BOO; // signal error
          }
        }
        else if (pair.getName().equals("message")) {
          try {
            message = Integer.valueOf(pair.getValue()) != 0;
          } catch (NumberFormatException ex) {
            message = false;
          }
        }
      }

      if (Boo.INVALID_BOO == id) {
        Toast.makeText(this, R.string.details_invalid_uri, Toast.LENGTH_LONG).show();
        finish();
        return;
      }
      else if (Boo.INTRO_BOO == id) {
        mBoo = Boo.createIntroBoo(this);
      }

      if (null == mBoo) {
        String key = String.format(BOO_KEY_FORMAT, id);
        mBoo = (Boo) Globals.get().mObjectCache.get(key);
      }

      if (null == mBoo) {
        Globals.get().mAPI.fetchBooDetails(id, mHandler, message);
      }
    }
    else {
      // Grab extras
      BooData data = intent.getParcelableExtra(EXTRA_BOO_DATA);
      if (null == data) {
        throw new IllegalArgumentException("Intent needs to define the '"
            + EXTRA_BOO_DATA + "' extra.");
      }
      mBoo = new Boo(data);
    }

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
      if (Boo.INTRO_BOO == mBoo.mData.mId) {
        image_view.setImageResource(R.drawable.icon_flat);
        image_view.setFocusable(false);
        image_view.setFocusableInTouchMode(false);
      }
      else if (null != mBoo.mData.mUser) {
        Uri uri = mBoo.mData.mUser.getThumbUrl();
        if (null != uri) {
          int size = image_view.getLayoutParams().width - image_view.getPaddingLeft()
            - image_view.getPaddingRight();
          uris.add(new ImageCache.CacheItem(uri, size, new Baton(-1, R.id.boo_thumb, -1)));
        }

        image_view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
              Intent i = new Intent(BooDetailsActivity.this, ContactDetailsActivity.class);
              i.putExtra(ContactDetailsActivity.EXTRA_CONTACT, (Parcelable) mBoo.mData.mUser);
              startActivity(i);
            }
        });
      }
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

    // Tags
    LeftAlignedLayout tags = (LeftAlignedLayout) findViewById(R.id.boo_tags);
    if (null != tags) {
      if (null == mBoo.mData.mTags || 0 == mBoo.mData.mTags.size()) {
        tags.setVisibility(View.GONE);
      }
      else {
        tags.setVisibility(View.VISIBLE);

        int margin = (int) (2 * getResources().getDisplayMetrics().density);
        float textSize = getResources().getDimensionPixelSize(R.dimen.details_tag);
        ColorStateList textColor = getResources().getColorStateList(R.color.details_tag);

        for (Tag tag : mBoo.mData.mTags) {
          TextView tv = new TextView(this);
          LeftAlignedLayout.LayoutParams params = new LeftAlignedLayout.LayoutParams(
              LeftAlignedLayout.LayoutParams.WRAP_CONTENT,
              LeftAlignedLayout.LayoutParams.WRAP_CONTENT);
          params.bottomMargin = params.topMargin = params.leftMargin = params.rightMargin = margin;
          tv.setLayoutParams(params);
          tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
          tv.setTextColor(textColor);
          tv.setBackgroundResource(R.drawable.tag_background);
          tv.setText(null != tag.mDisplay ? tag.mDisplay : tag.mNormalised);
          tags.addView(tv);
        }
      }
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

    // Play button!
    View button = findViewById(R.id.boo_play);
    if (null != button) {
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            // Switch playback to this boo!
            Globals.get().mPlayer.play(mBoo, true);
          }
      });
    }

    // Reply button!
    button = findViewById(R.id.boo_reply);
    if (null != button) {
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            String title = getResources().getString(R.string.details_reply_title);
            title = String.format(title, mBoo.mData.mTitle);
            Intent intent = new Intent(Intent.ACTION_VIEW,
                UriUtils.createRecordUri(mBoo.mData.mUser.mId,
                  mBoo.mData.mUser.mUsername, mBoo.mData.mId, title));
            startActivity(intent);
          }
      });

      if (null != Globals.get().mAccount
          && mBoo.mData.mIsMessage
          && mBoo.mData.mUser.mId != Globals.get().mAccount.mId)
      {
        button.setVisibility(View.VISIBLE);
      }
      else {
        button.setVisibility(View.GONE);
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



  @Override
  public void onResume()
  {
    super.onResume();
    //Log.d(LTAG, "Resume");

    // Also initialize the player view
    View pv = findViewById(R.id.boo_player_container);
    if (null != pv) {
      BooPlayerClient player = Globals.get().mPlayer;
      if (null != player) {
        PlayerState state = player.getState();
        switch (state.mState) {
          case Constants.STATE_PREPARING:
          case Constants.STATE_BUFFERING:
          case Constants.STATE_PLAYING:
          case Constants.STATE_PAUSED:
            showPlayerContainer();
            break;

          default:
            hidePlayerContainer();
            break;
        }
      }
    }
  }



  @Override
  public void onPause()
  {
    super.onPause();

    BooPlayerClient client = Globals.get().mPlayer;
    if (null != client) {
      client.removeProgressListener(this);
    }
  }



  private void showPlayerContainer()
  {
    final View player = findViewById(R.id.boo_player_container);
    if (null == player) {
      return;
    }

    player.setVisibility(View.VISIBLE);

    Globals.get().mPlayer.addProgressListener(this);

    // What's interesting as well is whether or not it's *this* Boo that's being
    // played or not.
    PlayerState state = Globals.get().mPlayer.getState();
    if (null == state) {
      return;
    }

    ViewAnimator flipper = (ViewAnimator) findViewById(R.id.boo_player_flipper);
    if (null == mBoo || null == mBoo.mData || mBoo.mData.mId == state.mBooId) {
      // We have a match. Since we do, we want to show the player
      flipper.setDisplayedChild(0);
    }
    else {
      // Show the button to let people play the current view instead!
      flipper.setDisplayedChild(1);
    }
  }



  private void hidePlayerContainer()
  {
    final View player = findViewById(R.id.boo_player_container);
    if (null == player) {
      return;
    }

    player.setVisibility(View.GONE);

    Globals.get().mPlayer.removeProgressListener(this);
  }





  /***************************************************************************
   * BooPlayerClient.ProgressListener implementation
   **/
  public void onProgress(PlayerState state)
  {
    switch (state.mState) {
      case Constants.STATE_ERROR:
        Toast.makeText(this, R.string.error_message_playback,
            Toast.LENGTH_LONG).show();
        // XXX Fall through

      case Constants.STATE_FINISHED:
      case Constants.STATE_NONE:
        hidePlayerContainer();
        break;

     default:
        showPlayerContainer();
        break;
    }
  }
}

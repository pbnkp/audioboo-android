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

import android.app.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.content.res.Configuration;

import android.net.Uri;

import android.view.View;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ViewAnimator;
import android.widget.Button;
import android.widget.Toast;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;

import java.util.List;
import java.util.LinkedList;

import fm.audioboo.data.User;

import android.util.Log;

/**
 * Displays details for a user, and includes the ability to follow/unfollow
 * users, etc.
 **/
public class ContactDetailsActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "ContactDetailsActivity";


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_CONTACT = "fm.audioboo.extra.contact";

  // Cache key format for users + timeout (in seconds)
  public static final String CONTACT_KEY_FORMAT = "fm.audioboo.cache.contact-%d";
  public static final double CONTACT_TIMEOUT = 300.0;



  /***************************************************************************
   * Private data
   **/
  private int   mUserId;

  // Request handling
  private Handler mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          User user = (User) msg.obj;

          String key = String.format(CONTACT_KEY_FORMAT, user.mId);
          Globals.get().mObjectCache.put(key, user, CONTACT_TIMEOUT);

          populateView();
        }
        else {
          Toast.makeText(ContactDetailsActivity.this, R.string.contact_load_error, Toast.LENGTH_LONG).show();
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
    setContentView(R.layout.contact_details);
    setTitle(R.string.contacts_title);

    // Grab extras
    User user = getIntent().getParcelableExtra(EXTRA_CONTACT);
    if (null == user) {
      throw new IllegalArgumentException("Intent needs to define the '"
          + EXTRA_CONTACT + "' extra.");
    }
    mUserId = user.mId;

    Log.d(LTAG, "User: " + user);
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



  @Override
  public void onResume()
  {
    super.onResume();

    // Fill view with data!
    populateView();
  }



  private void populateView()
  {
    // Get user info.
    String key = String.format(CONTACT_KEY_FORMAT, mUserId);
    final User user = (User) Globals.get().mObjectCache.get(key);
    if (null == user) {
      // Show loading view
      ViewAnimator loading = (ViewAnimator) findViewById(R.id.contact_flipper);
      if (null != loading) {
        loading.setDisplayedChild(0);
      }

      Globals.get().mAPI.fetchAccount(mUserId, mHandler);
      return;
    }

    // Switch to content views.
    ViewAnimator loading = (ViewAnimator) findViewById(R.id.contact_flipper);
    if (null != loading) {
      loading.setDisplayedChild(1);
    }

    // Fill various text views.
    TextView text_view = (TextView) findViewById(R.id.contact_author);
    if (null != text_view) {
      text_view.setText(user.mUsername);
    }

    text_view = (TextView) findViewById(R.id.contact_description);
    if (null != text_view) {
      text_view.setText(null == user.mDescription ? "" : user.mDescription);
    }

    text_view = (TextView) findViewById(R.id.contact_boos);
    if (null != text_view) {
      text_view.setText(String.format("%d", user.mAudioClips));
    }

    text_view = (TextView) findViewById(R.id.contact_favorites);
    if (null != text_view) {
      text_view.setText(String.format("%d", user.mFavorites));
    }

    text_view = (TextView) findViewById(R.id.contact_followings);
    if (null != text_view) {
      text_view.setText(String.format("%d", user.mFollowings));
    }

    text_view = (TextView) findViewById(R.id.contact_followers);
    if (null != text_view) {
      text_view.setText(String.format("%d", user.mFollowers));
    }

    // Buttons!
    Button button = (Button) findViewById(R.id.contact_follow);
    if (null != button) {
      button.setEnabled(user.mFollowingEnabled);

      // Follow or unfollow?
      final boolean following = isFollowing(user);
      button.setText(following ? R.string.contact_button_unfollow : R.string.contact_button_follow);
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            if (following) {
              onUnfollow(user);
            }
            else {
              onFollow(user);
            }
          }
      });
    }

    button = (Button) findViewById(R.id.contact_message);
    if (null != button) {
      button.setEnabled(user.mMessagingEnabled);
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            // FIXME 
          }
      });
    }

    // Hide buttons if this is the current user's page.
    User account = Globals.get().mAccount;
    if (null == account || user.mId == account.mId) {
      View view = findViewById(R.id.contact_buttons);
      if (null != view) {
        view.setVisibility(View.GONE);
      }
    }

    // Prepare for loading images.
    LinkedList<ImageCache.CacheItem> uris = new LinkedList<ImageCache.CacheItem>();

    // Thumbnail
    ImageView image_view = (ImageView) findViewById(R.id.contact_thumb);
    if (null != image_view) {
      Uri uri = user.getThumbUrl();
      if (null != uri) {
        int size = image_view.getLayoutParams().width - image_view.getPaddingLeft()
          - image_view.getPaddingRight();
        uris.add(new ImageCache.CacheItem(uri, size, null));
      }
    }

    // Finally, fire off requests for images.
    if (0 < uris.size()) {
      Globals.get().mImageCache.fetch(uris, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          ImageCache.CacheItem item = (ImageCache.CacheItem) msg.obj;

          ImageView image_view = (ImageView) findViewById(R.id.contact_thumb);
          if (null == image_view) {
            Log.d(LTAG, "did not find thumbnail view");
            return true;
          }

          switch (msg.what) {
            case ImageCache.MSG_OK:
              image_view.setImageDrawable(new BitmapDrawable(item.mBitmap));
              break;

            case ImageCache.MSG_ERROR:
            default:
              Log.e(LTAG, "Error fetching image at URL: " + (item != null ? item.mImageUri : null));

            case ImageCache.MSG_CANCELLED:
              break;
          }
          return true;
        }
      }));
    }
  }



  private void onFollow(final User user)
  {
    Button button = (Button) findViewById(R.id.contact_follow);
    if (null != button) {
      button.setEnabled(false);
    }

    Globals.get().mAPI.followUser(user, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          if (API.ERR_SUCCESS == msg.what) {
            // Add to local following list.
            @SuppressWarnings("unchecked")
            List<User> following = (List<User>) Globals.get().mObjectCache.get(ContactsListActivity.CONTACT_LIST_KEY);
            if (null == following) {
              following = new LinkedList<User>();
            }

            boolean doAdd = true;
            for (User u : following) {
              if (u.mId == user.mId) {
                doAdd = false;
                break;
              }
            }

            if (doAdd) {
              following.add(user);
              Globals.get().mObjectCache.put(ContactsListActivity.CONTACT_LIST_KEY,
                  following, ContactsListActivity.CONTACT_LIST_TIMEOUT);
            }

            // Invalidate in cache.
            String key = String.format(CONTACT_KEY_FORMAT, user.mId);
            Globals.get().mObjectCache.invalidate(key);
          }

          populateView();
          return true;
        }
    }));
  }



  private void onUnfollow(final User user)
  {
    Button button = (Button) findViewById(R.id.contact_follow);
    if (null != button) {
      button.setEnabled(false);
    }

    Globals.get().mAPI.unfollowUser(user, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          if (API.ERR_SUCCESS == msg.what) {
            // Remove from local following list.
            @SuppressWarnings("unchecked")
            List<User> following = (List<User>) Globals.get().mObjectCache.get(ContactsListActivity.CONTACT_LIST_KEY);
            if (null != following) {
              for (int i = 0 ; i < following.size() ; ++i) {
                User u = following.get(i);
                if (u.mId == user.mId) {
                  following.remove(i);
                  Globals.get().mObjectCache.put(ContactsListActivity.CONTACT_LIST_KEY,
                      following, ContactsListActivity.CONTACT_LIST_TIMEOUT);
                  break;
                }
              }
            }

            // Invalidate in cache.
            String key = String.format(CONTACT_KEY_FORMAT, user.mId);
            Globals.get().mObjectCache.invalidate(key);
          }

          populateView();
          return true;
        }
    }));

  }



  private boolean isFollowing(User user)
  {
    @SuppressWarnings("unchecked")
    List<User> following = (List<User>) Globals.get().mObjectCache.get(ContactsListActivity.CONTACT_LIST_KEY);
    if (null == following) {
      return false;
    }

    for (User u : following) {
      if (u.mId == user.mId) {
        return true;
      }
    }

    return false;
  }
}

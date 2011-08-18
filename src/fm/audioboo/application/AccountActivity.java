/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
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
import android.os.Parcelable;

import android.net.Uri;

import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import android.view.View;

import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import android.graphics.drawable.BitmapDrawable;

import java.util.LinkedList;

import fm.audioboo.data.User;

import fm.audioboo.service.Constants;

import android.util.Log;

/**
 * Displays a settings pane, and allows to link or unlink the device to/from
 * an account.
 **/
public class AccountActivity
       extends Activity
       implements AdapterView.OnItemSelectedListener
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "AccountActivity";

  // View states
  private static final int VIEW_FLAGS_FORM[] = new int[] {
    View.GONE, View.VISIBLE,
  };
  private static final int VIEW_FLAGS_PROGRESS[] = new int[] {
    View.VISIBLE, View.GONE,
  };

  // View IDs the above states
  private static final int VIEW_IDS[] = new int[] {
    R.id.account_progress,
    R.id.account_content,
  };

  // Dialog IDs.
  private static final int DIALOG_CONFIRM_UNLINK  = 0;
  private static final int DIALOG_GPS_SETTINGS    = Globals.DIALOG_GPS_SETTINGS;
  private static final int DIALOG_ERROR           = Globals.DIALOG_ERROR;

  // Background resource IDs for the progress view
  private static final int BG_BLACK               = android.R.color.black;
  private static final int BG_WHITE               = android.R.color.white;


  /***************************************************************************
   * Data members
   **/
  // Flag, shows whether to use location information or not.
  private boolean     mUseLocation;

  // Last error code reported.
  private int         mLastErrorCode = API.ERR_SUCCESS;

  // Request code - sent to AccountLinkActivity so it can respond appropriately.
  private int         mRequestCode;


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.account);
  }



  @Override
  public void onStart()
  {
    super.onStart();

    // Set listener for the link button and check box.
    ToggleButton tb = (ToggleButton) findViewById(R.id.account_link);
    if (null != tb) {
      tb.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (null == Globals.get().getStatus()) {
            return;
          }

          if (Globals.get().getStatus().mLinked == isChecked) {
            return;
          }

          if (isChecked) {
            onLinkRequested();
          }
          else {
            onUnlinkRequested();
          }
        }
      });
    }

    CheckBox cb = (CheckBox) findViewById(R.id.account_use_location);
    if (null != cb) {
      cb.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (mUseLocation != isChecked) {
            onUseLocationChanged(isChecked);
          }
        }
      });
    }


    // Load status. Also triggers initUI(), and the whole shebang.
    determineStatus(false);
  }



  private void determineStatus(boolean force)
  {
    // If we can retrieve the API status, we know what to display here. If not,
    // then we need to do that first.
    if (!force && null != Globals.get().getStatus()) {
      initUI();
      return;
    }

    // First thing, show the progress view.
    switchToViewState(VIEW_FLAGS_PROGRESS, R.string.account_progress_label_status,
        BG_BLACK);

    Globals.get().updateStatus(new Handler(new Handler.Callback() {
       public boolean handleMessage(Message msg)
       {
         if (API.ERR_SUCCESS == msg.what) {
           initUI();
         }
         else {
           mLastErrorCode = msg.what;
           showDialog(DIALOG_ERROR);
         }
         return true;
       }
    }));
  }



  private void initUI()
  {
    API.Status status = Globals.get().getStatus();
//    Log.d(LTAG, "init UI: " + status);
    if (null == status) {
      // Shouldn't happen, but can if the user switches tabs before the status
      // request completes.
      return;
    }

    // First thing, show the content view.
    switchToViewState(VIEW_FLAGS_FORM);

    // Set values/states according to the status.
    // If the status is unlinked, then the toggle button must be off.
    ToggleButton tb = (ToggleButton) findViewById(R.id.account_link);
    if (null != tb) {
      tb.setChecked(status.mLinked);
    }

    // Set the username & description fields.
    if (status.mLinked) {
      TextView text_view = (TextView) findViewById(R.id.account_name);
      if (null != text_view) {
        text_view.setText(status.mUsername);
      }

      text_view = (TextView) findViewById(R.id.account_description);
      if (null != text_view) {
        text_view.setText(getResources().getString(R.string.account_description_linked));
      }

      activateThumbnail();
    }
    else {
      TextView text_view = (TextView) findViewById(R.id.account_name);
      if (null != text_view) {
        text_view.setText(getResources().getString(R.string.account_not_linked));
      }

      text_view = (TextView) findViewById(R.id.account_description);
      if (null != text_view) {
        text_view.setText(getResources().getString(R.string.account_description_unlinked));
      }

      ImageView image_view = (ImageView) findViewById(R.id.account_thumb);
      if (null != image_view) {
        image_view.setImageResource(R.drawable.anonymous_boo);
        image_view.setFocusable(false);
        image_view.setOnClickListener(null);
      }
    }

    // Set location checkbox status.
    CheckBox cb = (CheckBox) findViewById(R.id.account_use_location);
    if (null != cb) {
      SharedPreferences prefs = Globals.get().getPrefs();
      mUseLocation = prefs.getBoolean(Globals.PREF_USE_LOCATION, false);
      cb.setChecked(mUseLocation);
    }

    // Poll spinner
    Spinner spinner = (Spinner) findViewById(R.id.account_poll_spinner);
    if (null != spinner) {
      ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
          this, R.array.account_poll_options, android.R.layout.simple_spinner_item);
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      spinner.setAdapter(adapter);

      // Restore settings & set spinner's selection accordingly
      SharedPreferences prefs = Globals.get().getPrefs();
      int pos = prefs.getInt(Constants.PREF_POLL_INTERVAL, 0);
      spinner.setSelection(pos);

      spinner.setOnItemSelectedListener(this);

      spinner.setEnabled(status.mLinked);
    }

    // Play intro again button
    Button button = (Button) findViewById(R.id.account_intro);
    if (null != button) {
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            Globals.get().mPlayer.play(Boo.createIntroBoo(AccountActivity.this), true);
          }
      });
    }

    // Version
    TextView text_view = (TextView) findViewById(R.id.account_version);
    if (null != text_view) {
      String version = "-";
      try {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        version = info.versionName;
      } catch (PackageManager.NameNotFoundException ex) {
        Log.e(LTAG, "Can't find our own package?!?");
      }

      version = String.format(getResources().getString(R.string.account_version_format), version);
      text_view.setText(version);
    }
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void onLinkRequested()
  {
    Intent i = new Intent(this, AccountLinkActivity.class);
    startActivityForResult(i, ++mRequestCode);
  }



  private void onUnlinkRequested()
  {
    showDialog(DIALOG_CONFIRM_UNLINK);
  }



  private void performUnlink()
  {
    // Switch to progress view state.
    switchToViewState(VIEW_FLAGS_PROGRESS,
        R.string.account_progress_label_unlink, BG_BLACK);

    // Fire off unlink request.
    Globals.get().mAPI.unlinkDevice(new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        // Switch to regular view.
        switchToViewState(VIEW_FLAGS_FORM);

        if (API.ERR_SUCCESS == msg.what) {
          // The status was updated, so we need to fetch it again.
          determineStatus(false);
        }
        return true;
      }
    }));
  }



  private void onUseLocationChanged(boolean use_location)
  {
    // Store in the Activity, to help determine whether to call this function.
    mUseLocation = use_location;

    // Store in preferences.
    SharedPreferences prefs = Globals.get().getPrefs();
    SharedPreferences.Editor edit = prefs.edit();
    edit.putBoolean(Globals.PREF_USE_LOCATION, use_location);
    edit.commit();

    // Start/stop listening for location updates.
    if (use_location) {
      if (!Globals.get().startLocationUpdates()) {
        showDialog(DIALOG_GPS_SETTINGS);
      }
    }
    else {
      Globals.get().stopLocationUpdates();
    }
  }



  private void switchToViewState(int[] flags)
  {
    switchToViewState(flags, -1, BG_BLACK);
  }



  private void switchToViewState(int[] flags, int progress_text_id,
      int progress_background_id)
  {
    for (int i = 0 ; i < flags.length ; ++i) {
      View view = findViewById(VIEW_IDS[i]);
      view.setVisibility(flags[i]);
    }

    if (-1 != progress_text_id) {
      TextView view = (TextView) findViewById(R.id.account_progress_label);
      if (null != view) {
        view.setText(getResources().getString(progress_text_id));
      }
    }

    View v = findViewById(R.id.account_progress);
    if (null != v) {
      v.setBackgroundResource(progress_background_id);

      TextView tv = (TextView) v.findViewById(R.id.account_progress_label);
      if (null != tv) {
        int label_color = R.color.account_progress_label_light;
        if (BG_WHITE == progress_background_id) {
          label_color = R.color.account_progress_label_dark;
        }

        tv.setTextColor(getResources().getColorStateList(label_color));
      }
    }

  }



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_CONFIRM_UNLINK:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getResources().getString(R.string.account_unlink_dialog_message))
          .setCancelable(false)
          .setPositiveButton(getResources().getString(R.string.account_unlink_dialog_confirm),
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  performUnlink();
                }
              })
          .setNegativeButton(getResources().getString(R.string.account_unlink_dialog_cancel),
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  ToggleButton tb = (ToggleButton) findViewById(R.id.account_link);
                  if (null != tb) {
                    tb.setChecked(Globals.get().getStatus().mLinked);
                  }
                  dialog.cancel();
                }
              });
        dialog = builder.create();
        break;


      case DIALOG_GPS_SETTINGS:
        dialog = Globals.get().createDialog(this, id);
        break;

      case DIALOG_ERROR:
        dialog = Globals.get().createDialog(this, id, mLastErrorCode, null);
        break;
    }

    return dialog;
  }



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (mRequestCode != requestCode) {
      return;
    }

    determineStatus(true);
  }


  private void activateThumbnail()
  {
    final User account = Globals.get().mAccount;
    Log.d(LTAG, "Account: " + account);
    if (null == account) {
      return;
    }

    ImageView image_view = (ImageView) findViewById(R.id.account_thumb);
    if (null == image_view) {
      return;
    }

    // Prepare for loading images.
    LinkedList<ImageCache.CacheItem> uris = new LinkedList<ImageCache.CacheItem>();

    Uri uri = account.getThumbUrl();
    if (null != uri) {
      int size = image_view.getLayoutParams().width - image_view.getPaddingLeft()
        - image_view.getPaddingRight();
      uris.add(new ImageCache.CacheItem(uri, size, null));
    }

    image_view.setFocusable(true);
    image_view.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v)
        {
          Intent i = new Intent(AccountActivity.this, ContactDetailsActivity.class);
          i.putExtra(ContactDetailsActivity.EXTRA_CONTACT, (Parcelable) account);
          startActivity(i);
        }
    });

    // Finally, fire off requests for images.
    if (0 < uris.size()) {
      Globals.get().mImageCache.fetch(uris, new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg)
        {
          ImageCache.CacheItem item = (ImageCache.CacheItem) msg.obj;

          ImageView image_view = (ImageView) findViewById(R.id.account_thumb);
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


  /***************************************************************************
   * AdapterView.OnItemSelectedListener implementation
   **/
  public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
  {
    SharedPreferences prefs = Globals.get().getPrefs();
    SharedPreferences.Editor edit = prefs.edit();
    edit.putInt(Constants.PREF_POLL_INTERVAL, pos);
    edit.commit();

    Globals.get().updatePolling();
  }


  public void onNothingSelected(AdapterView parent)
  {
    // Do nothing.
  }
}

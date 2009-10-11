/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.app.Activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.net.Uri;

import android.content.res.Configuration;
import android.content.SharedPreferences;

import android.view.View;

import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.CheckBox;
import android.widget.TextView;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import android.util.Log;

/**
 * Displays a settings pane, and allows to link or unlink the device to/from
 * an account.
 **/
public class AccountActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "AccountActivity";

  // View states
  private static final int VIEW_FLAGS_FORM[] = new int[] {
    View.GONE, View.VISIBLE, View.GONE,
  };
  private static final int VIEW_FLAGS_PROGRESS[] = new int[] {
    View.VISIBLE, View.GONE, View.GONE,
  };
  private static final int VIEW_FLAGS_WEB[] = new int[] {
    View.GONE, View.GONE, View.VISIBLE,
  };

  // View IDs the above states
  private static final int VIEW_IDS[] = new int[] {
    R.id.account_progress,
    R.id.account_content,
    R.id.account_webview,
  };

  // Dialog IDs.
  private static final int DIALOG_CONFIRM_UNLINK  = 0;
  private static final int DIALOG_GPS_SETTINGS    = Globals.DIALOG_GPS_SETTINGS;



  /***************************************************************************
   * Data members
   **/
  // API Status. We'll need that to determine what to display exactly.
  private API.Status  mStatus;

  // Flag, shows whether to use location information or not.
  private boolean     mUseLocation;


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
          if (null == mStatus) {
            return;
          }

          if (mStatus.mLinked == isChecked) {
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
    mStatus = Globals.get().mAPI.getStatus();
    determineStatus();
  }



  private void determineStatus()
  {
    // If we can retrieve the API status, we know what to display here. If not,
    // then we need to do that first.
    if (null != mStatus) {
      initUI();
      return;
    }

    // First thing, show the progress view.
    switchToViewState(VIEW_FLAGS_PROGRESS, R.string.account_progress_label_status);

    Globals.get().mAPI.updateStatus(new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          mStatus = Globals.get().mAPI.getStatus();
          initUI();
        }
        else {
          // Go into an infinite loop of trying to update the status.
          // XXX Could be handled better.
          Globals.get().mAPI.updateStatus(new Handler(this));
        }
        return true;
      }
    }));
  }



  private void initUI()
  {
    // First thing, show the content view.
    switchToViewState(VIEW_FLAGS_FORM);

    // Set values/states according to the status.
    // If the status is unlinked, then the toggle button must be off.
    ToggleButton tb = (ToggleButton) findViewById(R.id.account_link);
    if (null != tb) {
      tb.setChecked(mStatus.mLinked);
    }

    // Set the username & description fields.
    if (mStatus.mLinked) {
      TextView text_view = (TextView) findViewById(R.id.account_name);
      if (null != text_view) {
        text_view.setText(mStatus.mUsername);
      }

      text_view = (TextView) findViewById(R.id.account_description);
      if (null != text_view) {
        text_view.setText(getResources().getString(R.string.account_description_linked));
      }
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
    }

    // Set location checkbox status.
    CheckBox cb = (CheckBox) findViewById(R.id.account_use_location);
    if (null != cb) {
      SharedPreferences prefs = Globals.get().getPrefs();
      mUseLocation = prefs.getBoolean(Globals.PREF_USE_LOCATION, false);
      cb.setChecked(mUseLocation);
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
    // First, switch on the webview.
    switchToViewState(VIEW_FLAGS_WEB);

    // Send link request.
    String link_url = Globals.get().mAPI.getSignedLinkUrl();

    // Load link URL.
    WebView webview = (WebView) findViewById(R.id.account_webview);
    webview.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url)
      {
        Uri parsed_uri = Uri.parse(url);

        // Check whether we need to treat the new URI specially.
        if (parsed_uri.getScheme().equals("audioboo")) {
          // XXX We only get this scheme on success at the moment. Weird,
          //     but so be it.
          mStatus = null;
          determineStatus();
          return true;
        }

        view.loadUrl(url);
        return true;
      }

      @Override
      public void onLoadResource(WebView view, String url)
      {
        // If the url is just audioboo's base URL, i.e. has no path, then
        // we assume the form needs to be displayed and everything was
        // cancelled.
        Uri parsed_uri = Uri.parse(url);
        if ((null == parsed_uri.getPath() || parsed_uri.getPath().equals("/"))
            && parsed_uri.getHost().endsWith("audioboo.fm"))
        {
          view.stopLoading();

          ToggleButton tb = (ToggleButton) findViewById(R.id.account_link);
          if (null != tb) {
            tb.setChecked(mStatus.mLinked);
          }

          switchToViewState(VIEW_FLAGS_FORM);
        }
      }
    });
    webview.loadUrl(link_url);
  }



  private void onUnlinkRequested()
  {
    showDialog(DIALOG_CONFIRM_UNLINK);
  }



  private void performUnlink()
  {
    // Switch to progress view state.
    switchToViewState(VIEW_FLAGS_PROGRESS, R.string.account_progress_label_unlink);

    // Fire off unlink request.
    Globals.get().mAPI.unlinkDevice(new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        // Switch to regular view.
        switchToViewState(VIEW_FLAGS_FORM);

        if (API.ERR_SUCCESS == msg.what) {
          // The status was updated, so we need to fetch it again.
          mStatus = null;
          determineStatus();
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
    switchToViewState(flags, -1);
  }



  private void switchToViewState(int[] flags, int progress_text_id)
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
                    tb.setChecked(mStatus.mLinked);
                  }
                  dialog.cancel();
                }
              });
        dialog = builder.create();
        break;


      case DIALOG_GPS_SETTINGS:
        dialog = Globals.get().createDialog(this, id);
        break;
    }

    return dialog;
  }
}

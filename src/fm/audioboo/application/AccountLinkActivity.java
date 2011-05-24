/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.app.Activity;

import android.os.Bundle;

import android.view.Window;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.graphics.Bitmap;

import android.net.Uri;

/**
 * Displays a settings pane, and allows to link or unlink the device to/from
 * an account.
 **/
public class AccountLinkActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "AccountLinkActivity";


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    requestWindowFeature(Window.FEATURE_PROGRESS);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.account_link);
  }



  @Override
  public void onStart()
  {
    super.onStart();

    // First, switch on the progress view
    setProgressBarVisibility(true);

    // Send link request.
    String link_url = Globals.get().mAPI.getSignedLinkUrl();

    // Load link URL.
    WebView webview = (WebView) findViewById(R.id.account_webview);
    webview.getSettings().setJavaScriptEnabled(true);
    webview.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url)
      {
        Uri parsed_uri = Uri.parse(url);

        // Check whether we need to treat the new URI specially.
        if (parsed_uri.getScheme().equals("audioboo")) {
          // XXX We only get this scheme on success at the moment. Weird,
          //     but so be it.
          setResult(Activity.RESULT_OK);
          finish();
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

          setResult(Activity.RESULT_CANCELED);
          finish();
        }
      }

      @Override
      public void onPageFinished(WebView view, String url)
      {
        setProgressBarVisibility(false);
      }

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon)
      {
        setProgressBarVisibility(true);
      }
    });
    webview.loadUrl(link_url);
  }

}

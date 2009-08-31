/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.os.Handler;
import android.os.Message;

import java.net.URI;
import java.net.URISyntaxException;

import java.io.IOException;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.PlainSocketFactory;
// import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.HttpVersion;
//import org.apache.http.auth.UsernamePasswordCredentials;
//import org.apache.http.auth.AuthScope;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.StringTokenizer;
import java.text.SimpleDateFormat;

import android.util.Log;

/**
 * Abstraction for the AudioBoo API.
 **/
public class API
{
  /***************************************************************************
   * Public constants
   **/
  // Error constants.
  public static final int ERR_SUCCESS         = 0;
  public static final int ERR_INVALID_URI     = 1001;
  public static final int ERR_EMPTY_RESPONSE  = 1002;
  public static final int ERR_TRANSMISSION    = 1003;
  // TODO move to Error class; read localized descriptions

  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "API";

  // Base URL for API calls. XXX Trailing slash is expected.
  private static final String BASE_URL    = "http://api.audioboo.fm/";

  // Request/response format for API calls. XXX Leading dot is expected.
  private static final String FORMAT      = ".json";

  // URI snippets for various APIs
  private static final String API_RECENT  = "audio_clips";

  // HTTP Client parameters
  private static final int          HTTP_TIMEOUT = 60 * 1000;
  private static final HttpVersion  HTTP_VERSION = HttpVersion.HTTP_1_1;

  // Response processing
  private static final int          READ_BUFFER_SIZE  = 8192;

  // Fetcher startup delay. Avoids high load at startup that could impact UX.
  private static final int      FETCHER_STARTUP_DELAY = 5 * 1000;


  /***************************************************************************
   * Protected static data
   **/
  protected static DefaultHttpClient            sClient;
  protected static ThreadSafeClientConnManager  sConnectionManager;


  /***************************************************************************
   * Helper class for fetching API responses in the background.
   **/
  private class Fetcher extends Thread
  {
    public boolean keepRunning = true;

    private String    mUriString;
    private Handler   mHandler;

    public Fetcher(String uri, Handler handler)
    {
      super();
      mUriString = uri;
      mHandler = handler;
    }

    @Override
    public void run()
    {
      // Delay before starting to fetch stuff.
      try {
        sleep(FETCHER_STARTUP_DELAY);
      } catch (java.lang.InterruptedException ex) {
        // pass
      }

      // The loop construct prevents updateFeed from being executed if an
      // external interrupt occurred with a request to shut down the thread.
      while (keepRunning) {
        fetch(mUriString, mHandler);
        keepRunning = false;
      }
    }
  }


  /***************************************************************************
   * Data members
   **/

  // Fetcher. There's only one instance, so only one API call can be scheduled
  // at a time.
  private Fetcher mFetcher;

  /***************************************************************************
   * Implementation
   **/
  public API()
  {
    if (null == sConnectionManager || null == sClient) {
      // Set up an HttpClient instance that can be used by multiple threads
      HttpParams params = defaultHttpParams();

      SchemeRegistry registry = new SchemeRegistry();
      registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(),
            80));
      // XXX Only if SSL is needed.
      // registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(),
      //       443));

      sConnectionManager = new ThreadSafeClientConnManager(params, registry);
      sClient = new DefaultHttpClient(sConnectionManager, params);
    }
  }



  /**
   * Converts an InputStream to a String containing the InputStream's content.
   **/
  public static String readStream(InputStream is)
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is),
        READ_BUFFER_SIZE);
    StringBuilder sb = new StringBuilder();

    String line = null;
    try {
      while (null != (line = reader.readLine())) {
        sb.append(line + "\n");
      }
    } catch (IOException ex) {
      Log.e(LTAG, "Failed to read from HTTP stream: " + ex);
    } finally {
      try {
        is.close();
      } catch (IOException ex) {
        Log.e(LTAG, "Failed to close HTTP stream: " + ex);
      }
    }

    return sb.toString();
  }



  /**
   * Returns a SimpleDateFormat instance initialized to parse/format UTC
   * timestamps according to ISO 8601.
   **/
  public static SimpleDateFormat ISO8601Format()
  {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
  }



  /**
   * Returns a set of default HTTP parameters.
   **/
  protected static HttpParams defaultHttpParams()
  {
    HttpParams params = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(params, HTTP_TIMEOUT);
    HttpProtocolParams.setVersion(params, HTTP_VERSION);
    return params;
  }


  /**
   * Fetch recent Boos.
   *
   * The Handler instance handed to this function should expect
   * a) msg.what to be ERR_SUCCESS, in which case msg.obj is a LinkedList<Boo>
   * b) msg.what to be other than ERR_SUCCESS, in which case it will correspond
   *    to one of the error codes defined above. Also, msg.obj may either be
   *    null, or a String providing details about the error.
   **/
  public void fetchRecentBoos(final Handler result_handler)
  {
    String uri_string = BASE_URL + API_RECENT + FORMAT;

    if (null != mFetcher) {
      mFetcher.keepRunning = false;
      mFetcher.interrupt();
    }

    mFetcher = new Fetcher(uri_string, new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (ERR_SUCCESS == msg.what) {
          parseRecentBoosResponse((String) msg.obj, result_handler);
        }
        else {
          result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
        }
        return true;
      }
    }));
    mFetcher.start();
  }



  /**
   * Parse recent Boos response, and notify the result handler when done.
   **/
  private void parseRecentBoosResponse(String response, Handler result_handler)
  {
    Log.d(LTAG, "Response: " + response);
  }



  /**
   * Helper function for fetching API responses.
   **/
  private void fetch(String uri_string, Handler handler)
  {
    Log.d(LTAG, "Fetching " + uri_string);
    // Construct URI for recent boos.
    URI request_uri = null;
    try {
      request_uri = new URI(uri_string);
    } catch (URISyntaxException ex) {
      Log.e(LTAG, "Invalid API URI: " + uri_string);
      handler.obtainMessage(ERR_INVALID_URI, uri_string).sendToTarget();
      return;
    }

    // Construct request
    HttpGet request = new HttpGet(request_uri);
    HttpResponse response;
    try {
      response = sClient.execute(request);

      // Read response
      HttpEntity entity = response.getEntity();
      if (null == entity) {
        Log.e(LTAG, "Response is empty: " + request_uri);
        handler.obtainMessage(ERR_EMPTY_RESPONSE, uri_string).sendToTarget();
        return;
      }

      String content = readStream(entity.getContent());
      handler.obtainMessage(ERR_SUCCESS, content).sendToTarget();

    } catch (IOException ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: " + ex);
      handler.obtainMessage(ERR_TRANSMISSION, ex.getMessage());
    }
  }
}

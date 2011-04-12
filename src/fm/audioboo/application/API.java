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

import android.os.Handler;
import android.os.Message;

import android.os.Build;

import android.location.Location;

import android.net.Uri;

import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.HttpVersion;
import org.apache.http.Header;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.HttpResponse;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.*;
import org.apache.http.message.BasicNameValuePair;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.NameValuePair;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;

import java.util.StringTokenizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.math.BigInteger;
import java.security.MessageDigest;

import android.content.SharedPreferences;

import fm.audioboo.data.Tag;
import fm.audioboo.data.User;

import fm.audioboo.service.UploadManager;

import android.util.Log;

/**
 * Abstraction for the AudioBoo API.
 **/
public class API
{
  /***************************************************************************
   * Public constants
   **/
  // Error message IDs.
  // - ERR_SUCCESS may have the message object set, depending on the type of
  //   request. If it's set, it's the expected response data.
  // - ERR_API_ERROR has the message object set to an instance of APIException
  // - Other error codes do not have the message object set.
  public static final int ERR_SUCCESS               = 0;
  public static final int ERR_API_ERROR             = 10001;
  public static final int ERR_EMPTY_RESPONSE        = 10002;
  public static final int ERR_TRANSMISSION          = 10003;
  public static final int ERR_VERSION_MISMATCH      = 10004;
  public static final int ERR_PARSE_ERROR           = 10005;
  public static final int ERR_INVALID_STATE         = 10006;
  public static final int ERR_UNKNOWN               = 10007;
  public static final int ERR_LOCATION_REQUIRED     = 10008;
  public static final int ERR_METHOD_NOT_ALLOWED    = 10009;

  // Boo types - XXX same order as recent_boos_filters
  public static final int BOOS_FEATURED             = 0;
  public static final int BOOS_FOLLOWED             = 1;
  public static final int BOOS_RECENT               = 2;
  public static final int BOOS_POPULAR              = 3;
  public static final int BOOS_NEARBY               = 4;
  public static final int BOOS_MINE                 = 5;
  public static final int BOOS_INBOX                = 6;
  public static final int BOOS_OUTBOX               = 7;

  // API version we're requesting
  public static final int API_VERSION               = 200;

  // Maximum number of retries to use when updating the device status.
  public static final int STATUS_UPDATE_MAX_RETRIES = 3;


  /***************************************************************************
   * The APIException class is sent as the message object in ERR_API_ERROR
   * messages.
   **/
  public static class APIException extends Exception
  {
    private int mCode;

    public APIException(String message, int code)
    {
      super(message);
      mCode = code;
    }


    public int getCode()
    {
      return mCode;
    }
  }


  /***************************************************************************
   * The status class holds status information as retrieved via updateStatus()
   **/
  public static class Status
  {
    // ** Common fields
    // Flag showing whether the device is linked to an account or not.
    public boolean  mLinked;

    // ** Fields for unlinked devices
    // The URI to contact when attempting to link a device.
    public Uri      mLinkUri;

    // ** Fields for linked devices
    // Username and email address for the linked user.
    public String   mUsername;
    public String   mEmail;


    public String toString()
    {
      if (mLinked) {
        return String.format("<linked:%s:%s>", mUsername, mEmail);
      }
      else {
        return String.format("<unlinked:%s>", mLinkUri.toString());
      }
    }
  }



  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "API";

  // Default API host. Used as a fallback if SRV lookup fails.
  private static final String DEFAULT_API_HOST            = "api.audioboo.fm";
//  private static final String DEFAULT_API_HOST            = "api.staging.audioboo.fm";

  // Scheme for API requests.
  private static final String API_REQUEST_URI_SCHEME      = "http";

  // Base URL for API calls. XXX Trailing slash is expected.
  private static final String BASE_URL                    = "http://api.audioboo.fm/";
  // private static final String BASE_URL                    = "http://api.staging.audioboo.fm/";

  // Format string for making SRV lookups. %s is the client ID.
  private static final String SRV_LOOKUP_FORMAT           = "_%s._audioboo._tcp.audioboo.fm.";
  // Attempts to perform SRV lookup before reverting to BASE_URL above.
  //private static final int    SRV_LOOKUP_ATTEMPTS_MAX     = 3; 
  // FIXME disable SRV lookup for now.
  private static final int    SRV_LOOKUP_ATTEMPTS_MAX     = 0; 

  // Request/response format for API calls.
  private static final String API_FORMAT                  = "json";

  // URI snippets for various APIs - XXX indices are BOOS_xxx constants above
  private static final String API_BOO_URLS[] = {
    "audio_clips/featured",           // BOOS_FEATURED
    "account/audio_clips/followed",   // BOOS_FOLLOWED
    "audio_clips",                    // BOOS_RECENT
    "audio_clips/popular",            // BOOS_POPULAR
    "audio_clips/located",            // BOOS_LOCATED
    "account/audio_clips",            // BOOS_MINE
    "account/inbox",                  // BOOS_INBOX
    "account/outbox",                 // BOOS_OUTBOX
  };

  private static final String API_BOO_UPLOAD              = "account/audio_clips";
  private static final String API_MESSAGE_UPLOAD          = "account/outbox";

  private static final String API_REGISTER                = "sources/register";
  private static final String API_STATUS                  = "sources/status";
  // XXX API_LINK isn't required; the link uri is returned in the status call.
  private static final String API_UNLINK                  = "sources/unlink";
  private static final String API_CONTACTS                = "account/followings";
  private static final String API_ACCOUNT                 = "account";
  private static final String API_USER                    = "users/%d";
  private static final String API_BOO_DETAILS             = "audio_clips/%d";
  private static final String API_MESSAGE_DETAILS         = "account/messages/%d";
  private static final String API_ATTACHMENTS             = "attachments";

  // API version, format parameter
  private static final String KEY_API_VERSION             = "version";
  private static final String KEY_API_FORMAT              = "fmt";

  // Signature-related keys
  private static final String KEY_SOURCE_KEY              = "source[key]";
  private static final String KEY_SOURCE_SIGNATURE        = "source[signature]";
  private static final String KEY_SOURCE_TIMESTAMP        = "source[timestamp]";

  private static final String KEY_SERVICE_KEY             = "service[key]";
  private static final String KEY_SERVICE_SIGNATURE       = "service[signature]";
  private static final String KEY_SERVICE_TIMESTAMP       = "service[timestamp]";

  // Prefix for signed parameters
  private static final String SIGNED_PARAM_PREFIX         = "signed_";

  // Service key/secret
  private static final String SERVICE_KEY                 = "75d2035cc37d81c27a5d79b9";
  private static final String SERVICE_SECRET              = "75056ca984faa8e23ea97e1537796ab105458c9bc0d1efaaab054ae7b54cc89e";

  // Request types: we have GET, FORM and MULTIPART.
  private static final int RT_GET                         = 0;
  private static final int RT_FORM                        = 1;
  private static final int RT_MULTIPART_POST              = 2;
  private static final int RT_MULTIPART_PUT               = 3;
  private static final int RT_DELETE                      = 4;

  // Map of APIs to request types.
  private static final HashMap<String, Integer> REQUEST_TYPES;
  static {
    REQUEST_TYPES = new HashMap<String, Integer>();
    for (int i = 0 ; i < API_BOO_URLS.length ; ++i) {
      REQUEST_TYPES.put(API_BOO_URLS[i], RT_GET);
    }
    REQUEST_TYPES.put(API_REGISTER,       RT_FORM);
    REQUEST_TYPES.put(API_STATUS,         RT_GET);
    REQUEST_TYPES.put(API_UNLINK,         RT_FORM);
    REQUEST_TYPES.put(API_CONTACTS,       RT_GET);
    REQUEST_TYPES.put(API_ACCOUNT,        RT_GET);
    // XXX Add request types for different API calls; if they're not specified
    //     here, the default is RT_GET.
    // XXX API_USER varies in form, can't be easily matched like this, but wants
    //     RT_GET anyway.
  }

  // HTTP Client parameters
  private static final int          HTTP_CONNECT_TIMEOUT    = 30 * 1000;
  private static final int          HTTP_RESPONSE_TIMEOUT   = 30 * 1000;
  private static final HttpVersion  HTTP_VERSION            = HttpVersion.HTTP_1_1;

  // Requester startup delay. Avoids high load at startup that could impact UX.
  private static final int          REQUESTER_SLEEP_TIME    = 300 * 1000;

  // Chunk size to read responses in (in Bytes).
  private static final int          READ_CHUNK_SIZE         = 8192;

  /***************************************************************************
   * Protected static data
   **/
  protected static DefaultHttpClient            sClient;
  protected static ThreadSafeClientConnManager  sConnectionManager;



  /***************************************************************************
   * Context for each request
   **/
  public class Request
  {
    private String                  mApi;
    private HashMap<String, Object> mParams;
    private HashMap<String, Object> mSignedParams;
    private HashMap<String, Object> mFileParams;
    private Handler.Callback        mCallback;
    private int                     mRequestType;
    private Object                  mBaton;


    public Request(String api,
        HashMap<String, Object> params,
        HashMap<String, Object> signedParams,
        Handler.Callback callback)
    {
      this(api, params, signedParams, callback, -1, null);
    }


    public Request(String api,
        HashMap<String, Object> params,
        HashMap<String, Object> signedParams,
        Handler.Callback callback,
        int requestType)
    {
      this(api, params, signedParams, callback, requestType, null);
    }


    public Request(String api,
        HashMap<String, Object> params,
        HashMap<String, Object> signedParams,
        Handler.Callback callback,
        int requestType,
        HashMap<String, Object> fileParams)
    {
      super();
      mApi = api;
      mParams = params;
      mSignedParams = signedParams;
      mCallback = callback;
      mRequestType = requestType;
      mFileParams = fileParams;
    }
  }


  /***************************************************************************
   * Helper class for fetching API responses in the background.
   **/
  private class Requester extends Thread
  {
    public volatile boolean mKeepRunning = true;


    @Override
    public void run()
    {
      while (mKeepRunning) {
        try {
          sleep(REQUESTER_SLEEP_TIME);
        } catch (java.lang.InterruptedException ex) {
          // pass
        }

        do {
          // Log.d(LTAG, "Current queue: " + mRequestQueue.size());

          // Resolve API host. Should return immediately after the first time
          // it's run.
          if (!resolveAPIHost()) {
            // Go back to sleep
            break;
          }

          // Grab the next request off the queue.
          Request req = mRequestQueue.poll();
          if (null == req) {
            // Go back to sleep
            break;
          }

          // After resolving the API host, we need to obtain the appropriate
          // key(s) for API calls. This should return immediately after the
          // first time it's run.
          initializeAPIKeys(req);

          // Update status. This should return immediately after the first time
          // it's run. If the API requested is in fact the status update API, then
          // the Requester will terminate after this call.
          if (req.mApi.equals(API_STATUS)) {
            updateStatus(req, true);
            break;
          }
          else {
            if (!updateStatus(req, false)) {
              Log.e(LTAG, "Could not update status, going back to sleep.");
              break;
            }
          }

          // Construct request.
          HttpRequestBase request = constructRequest(req);

          // Perform request.
          byte[] data = fetchRawSynchronous(request, req);
          if (null != data) {
            sendMessage(req, ERR_SUCCESS, new String(data));
          }
        } while (true);
      }
    }
  }


  /***************************************************************************
   * Data members
   **/

  // Requester. There's only one instance, so only one API call can be scheduled
  // at a time.
  private Requester     mRequester;
  private ConcurrentLinkedQueue<Request> mRequestQueue = new ConcurrentLinkedQueue<Request>();
  private Handler       mHandler = new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        Request req = (Request) msg.obj;
        msg.obj = req.mBaton;
        return req.mCallback.handleMessage(msg);
      }
  });

  // API host to use in requests.
  private String        mAPIHost;

  // Key and secret for signing requests. Defaults to service (not source/device)
  // values.
  private String        mAPIKey             = SERVICE_KEY;
  private String        mAPISecret          = SERVICE_SECRET;

  // Parameter names for the key and signature.
  private String        mParamNameKey       = KEY_SERVICE_KEY;
  private String        mParamNameSignature = KEY_SERVICE_SIGNATURE;
  private String        mParamNameTimestamp = KEY_SERVICE_TIMESTAMP;

  // API Status.
  private Status        mStatus;
  private long          mStatusTimeout;

  // Informational - it's not really used yet, but this is the last server
  // timestamp we got from an updateStatus request.
  private int           mServerTimestamp;


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

    // Start requester.
    mRequester = new Requester();
    mRequester.start();
  }



  /**
   * Returns the status
   **/
  public Status getStatus()
  {
    long current = System.currentTimeMillis();
    if (current > mStatusTimeout) {
      mStatus = null;
    }
    return mStatus;
  }



  /**
   * Converts an InputStream to a String containing the InputStream's content.
   **/
  public static String readStream(InputStream is) throws IOException
  {
    return new String(readStreamRaw(is));
  }



  /**
   * Converts an InputStream to a byte array containing the InputStream's content.
   **/
  public static byte[] readStreamRaw(InputStream is) throws IOException
  {
    ByteArrayOutputStream os = new ByteArrayOutputStream(READ_CHUNK_SIZE);
    byte[] bytes = new byte[READ_CHUNK_SIZE];

    try {
      // Read bytes from the input stream in chunks and write
      // them into the output stream
      int bytes_read = 0;
      while (-1 != (bytes_read = is.read(bytes))) {
        os.write(bytes, 0, bytes_read);
      }

      byte[] retval = os.toByteArray();

      is.close();
      os.close();

      return retval;
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Could not read input stream: " + ex.getMessage());
    }
    return null;
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
    HttpConnectionParams.setConnectionTimeout(params, HTTP_CONNECT_TIMEOUT);
    HttpConnectionParams.setSoTimeout(params, HTTP_RESPONSE_TIMEOUT);
    HttpProtocolParams.setVersion(params, HTTP_VERSION);
    return params;
  }


  /**
   * Fetch details for a Boo
   **/
  public void fetchBooDetails(int booId, final Handler result_handler)
  {
    fetchBooDetails(booId, result_handler, false);
  }

  public void fetchBooDetails(int booId, final Handler result_handler,
      final boolean isMessage)
  {
    String api = null;
    HashMap<String, Object> signedParams = null;

    if (isMessage) {
      api = String.format(API_MESSAGE_DETAILS, booId);
      signedParams = new HashMap<String, Object>();
    }
    else {
      api = String.format(API_BOO_DETAILS, booId);
    }

    // This request has no parameters.
    mRequestQueue.add(new Request(api, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<Boo> boo
                  = ResponseParser.parseBooResponse((String) msg.obj, result_handler, isMessage);
              if (null != boo) {
                result_handler.obtainMessage(ERR_SUCCESS, boo.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Fetch Boos.
   * On success, the message object will be a LinkedList<Boo>.
   **/
  public void fetchBoos(final int type, final Handler result_handler, int page,
      int amount, Date timestamp)
  {
    // Honor pagination
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("page[items]", String.format("%d", amount));
    signedParams.put("page[number]", String.format("%d", page));

    // Other parameters
    signedParams.put("max_time", String.format("%d", timestamp.getTime() / 1000));

    signedParams.put("find[pg_rated]", "1");
    signedParams.put("image_size_hint[thumb]", String.format("%dx%d<",
          Globals.get().FULL_IMAGE_WIDTH,
          Globals.get().FULL_IMAGE_HEIGHT));
    signedParams.put("image_size_hint[full]", String.format("%dx%d>",
          Globals.get().FULL_IMAGE_WIDTH,
          Globals.get().FULL_IMAGE_HEIGHT));

    // Location related parameters
    if (BOOS_NEARBY == type) {
      Location loc = Globals.get().mLocation;
      if (null == loc) {
        result_handler.obtainMessage(ERR_LOCATION_REQUIRED, null).sendToTarget();
        return;
      }

      signedParams.put("find[latitude]", String.format("%f", loc.getLatitude()));
      signedParams.put("find[longitude]", String.format("%f", loc.getLongitude()));
    }

    // This request has no parameters.
    mRequestQueue.add(new Request(API_BOO_URLS[type], null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<BooList> boos
                  = ResponseParser.parseBooList((String) msg.obj, result_handler);
              if (null != boos) {
                // If boos were null, then the ResponseParser would already have sent an
                // error message to the result_handler.
                result_handler.obtainMessage(ERR_SUCCESS, boos.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Fetch a user's contacts
   **/
  public void fetchContacts(final Handler result_handler)
  {
    // Must force signature.
    HashMap<String, Object> signedParams = new HashMap<String, Object>();

    // This request has no parameters.
    mRequestQueue.add(new Request(API_CONTACTS, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<List<User>> users
                  = ResponseParser.parseUserList((String) msg.obj, result_handler);
              if (null != users) {
                // If boos were null, then the ResponseParser would already have sent an
                // error message to the result_handler.
                result_handler.obtainMessage(ERR_SUCCESS, users.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Fetch account information for the linked user's account
   **/
  public void fetchAccount(final Handler result_handler)
  {
    // Must force signature.
    HashMap<String, Object> signedParams = new HashMap<String, Object>();

    // This request has no parameters.
    mRequestQueue.add(new Request(API_ACCOUNT, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<User> user
                  = ResponseParser.parseUserResponse((String) msg.obj, result_handler);
              if (null != user) {
                // If boos were null, then the ResponseParser would already have sent an
                // error message to the result_handler.
                result_handler.obtainMessage(ERR_SUCCESS, user.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Fetch account information for another user's account.
   **/
  public void fetchAccount(int userId, final Handler result_handler)
  {
    String api = String.format(API_USER, userId);

    // This request has no parameters.
    mRequestQueue.add(new Request(api, null, null,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<User> user
                  = ResponseParser.parseUserResponse((String) msg.obj, result_handler);
              if (null != user) {
                // If boos were null, then the ResponseParser would already have sent an
                // error message to the result_handler.
                result_handler.obtainMessage(ERR_SUCCESS, user.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Unlinks the device, if it's currently linked.
   * On success, the message object will not be set.
   **/
  public void unlinkDevice(final Handler result_handler)
  {
    if (null == mStatus) {
      // Can't unlink if we don't know our status.
      result_handler.obtainMessage(ERR_INVALID_STATE).sendToTarget();
      return;
    }

    if (!mStatus.mLinked) {
      // If the device is not linked, we can report success immediately
      result_handler.obtainMessage(ERR_SUCCESS).sendToTarget();
      return;
    }

    // This request has no parameters. We pass empty signed parameters to force
    // singing.
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    mRequestQueue.add(new Request(API_UNLINK, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<Boolean> unlinked
                  = ResponseParser.parseUnlinkResponse((String) msg.obj, result_handler);

              if (null != unlinked && unlinked.mContent) {
                // Freeing the status means that the next request has to fetch
                // an update.
                mStatus = null;
                result_handler.obtainMessage(ERR_SUCCESS).sendToTarget();
              }
              else {
                result_handler.obtainMessage(ERR_INVALID_STATE).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.interrupt();
  }



  /**
   * Updates the device status, if necessary.
   * On success, the message object will not be set.
   **/
  public void updateStatus(final Handler result_handler)
  {
    // Set status to null, otherwise nothing will be fetched.
    mStatus = null;

    // This request has no parameters. The result handler also handles
    // any responses itself.
    mRequestQueue.add(new Request(API_STATUS, null, null, new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            return true;
          }
    }));
    mRequester.interrupt();
  }



  /**
   * Follow/unfollow the given user.
   **/
  public void followUser(User user, final Handler result_handler)
  {
    followUserInternal(user, result_handler, RT_FORM);
  }

  public void unfollowUser(User user, final Handler result_handler)
  {
    followUserInternal(user, result_handler, RT_DELETE);
  }

  private void followUserInternal(User user, final Handler result_handler,
      int requestType)
  {
    // Must force signature.
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("following_user_id", String.format("%d", user.mId));

    // This request has no parameters.
    mRequestQueue.add(new Request(API_CONTACTS, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              // The body is pretty much pointless to parse here.
              // {"window":60,"timestamp":1300732052,"body":{"following":true},"version":200}
              result_handler.obtainMessage(ERR_SUCCESS).sendToTarget();
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }, requestType));
    mRequester.interrupt();
  }



  /**
   * Returns a fully signed URI for linking a device.
   **/
  public String getSignedLinkUrl()
  {
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("callback[success]", "audioboo:///link_success");
    signedParams.put("callback[cancelled]", "audioboo:///link_cancelled");
    signedParams.put("callback[failure]", "audioboo:///link_failure");

    HttpRequestBase request = constructRequestInternal(
        mStatus.mLinkUri.toString(), RT_GET, null, signedParams, null);

    return request.getURI().toString();
  }



  /**
   * Mark a message as read
   **/
  public void markRead(Boo boo, final Handler result_handler)
  {
      Log.d(LTAG, "MessagE: " + boo.mData.mIsMessage);
    if (null == boo.mData || !boo.mData.mIsMessage) {
      Log.e(LTAG, "Invalid Boo for marking as read.");
      result_handler.obtainMessage(ERR_API_ERROR).sendToTarget();
      return;
    }

    if (boo.mData.mIsRead) {
      result_handler.obtainMessage(ERR_SUCCESS).sendToTarget();
      return;
    }

    String api = String.format(API_MESSAGE_DETAILS, boo.mData.mId);

    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("message[played]", 1);

    mRequestQueue.add(new Request(api, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<Integer> result
                  = ResponseParser.parseUploadResponse((String) msg.obj,
                    result_handler);
              if (null != result) {
                result_handler.obtainMessage(ERR_SUCCESS, result.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }, RT_MULTIPART_PUT)
    );
    mRequester.interrupt();
  }



  /**
   * Uploads a Boo. Technically uploads metadata for a Boo; if any audio and
   * video is attached to the Boo, it must be in the Boo's upload info already.
   * On success, the message object will contain an Integer representing the
   * ID of the newly uploaded Boo.
   **/
  public void uploadBoo(Boo boo, final Handler result_handler)
  {
    if (null == boo.mData || null == boo.mData.mUploadInfo) {
      Log.e(LTAG, "Invalid Boo data for upload.");
      result_handler.obtainMessage(ERR_API_ERROR).sendToTarget();
      return;
    }

    String prefix = "audio_clip";
    if (boo.mData.mIsMessage) {
      prefix = "message";
    }

    // Prepare signed parameters
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put(String.format("%s[title]", prefix), boo.mData.mTitle);
    signedParams.put(String.format("%s[local_recorded_at]", prefix), boo.mData.mRecordedAt.toString());
    // signedParams.put(String.format("%s[recorded_at]", prefix), boo.mData.mRecordedAt.toString());
    signedParams.put(String.format("%s[author_locale]", prefix), Locale.getDefault().toString());

    // Tags
    if (!boo.mData.mIsMessage && null != boo.mData.mTags) {
      LinkedList<String> tags = new LinkedList<String>();
      for (Tag t : boo.mData.mTags) {
        tags.add(t.mNormalised);
      }
      signedParams.put(String.format("%s[tags]", prefix), tags);
    }

    if (null != boo.mData.mLocation) {
      signedParams.put(String.format("%s[public_location]", prefix), "1");
      signedParams.put(String.format("%s[location_latitude]", prefix),
          String.format("%f", boo.mData.mLocation.mLatitude));
      signedParams.put(String.format("%s[location_longitude]", prefix),
          String.format("%f", boo.mData.mLocation.mLongitude));
      signedParams.put(String.format("%s[location_accuracy]", prefix),
          String.format("%f", boo.mData.mLocation.mAccuracy));
    }

    if (null != boo.mData.mUUID) {
      signedParams.put(String.format("%s[uuid]", prefix), boo.mData.mUUID);
    }

    // Attachments
    signedParams.put(String.format("%s[uploaded_data][chunked_attachment_id]", prefix),
        boo.mData.mUploadInfo.mAudioChunkId);
    if (-1 != boo.mData.mUploadInfo.mImageChunkId) {
      signedParams.put(String.format("%s[uploaded_image][chunked_attachment_id]", prefix),
          boo.mData.mUploadInfo.mImageChunkId);
    }

    // Destination
    if (null != boo.mData.mDestinationInfo) {
      if (boo.mData.mIsMessage) {
        // Messages
        signedParams.put("message[recipient_id]", boo.mData.mDestinationInfo.mDestinationId);
        if (-1 != boo.mData.mDestinationInfo.mInReplyTo) {
          signedParams.put("message[parent_id]", boo.mData.mDestinationInfo.mInReplyTo);
        }
      }
      else {
        // Channels
        signedParams.put("audio_clip[destination][stream_id]", boo.mData.mDestinationInfo.mDestinationId);
      }
    }

    // API
    String api = API_BOO_UPLOAD;
    if (boo.mData.mIsMessage) {
      api = API_MESSAGE_UPLOAD;
    }

    // FIXME
    Log.d(LTAG, "API: " + api);
    for (String key : signedParams.keySet()) {
      Log.d(LTAG, "P: " + key + " = " + signedParams.get(key));
    }

    mRequestQueue.add(new Request(api, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<Integer> result
                  = ResponseParser.parseUploadResponse((String) msg.obj,
                    result_handler);
              if (null != result) {
                result_handler.obtainMessage(ERR_SUCCESS, result.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }, RT_MULTIPART_POST)
    );
    mRequester.interrupt();
  }



  /**
   * Create/add to attachments.
   **/
  public void createAttachment(String filename, int offset, int size,
      final Handler result_handler)
  {
    attachmentRequest(-1, filename, offset, size, result_handler);
  }


  public void appendToAttachment(int attachmentId, String filename, int offset,
      int size, final Handler result_handler)
  {
    attachmentRequest(attachmentId, filename, offset, size, result_handler);
  }


  private void attachmentRequest(int attachmentId, String filename, int offset,
      int size, final Handler result_handler)
  {
    File file = new File(filename);

    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("attachment[chunk_offset]", String.format("%d", offset));
    signedParams.put("attachment[size]", String.format("%d", file.length()));
    signedParams.put("attachment[chunk]", new FilePartBody(file, offset, size));

    int request_type = RT_MULTIPART_POST;
    String api = API_ATTACHMENTS;
    if (-1 != attachmentId) {
      request_type = RT_MULTIPART_PUT;
      api = String.format("%s/%d", API_ATTACHMENTS, attachmentId);
    }

    // Log.d(LTAG, "Creating attachment request: " + api);
    mRequestQueue.add(new Request(api, null, signedParams,
        new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              ResponseParser.Response<UploadManager.UploadResult> result
                  = ResponseParser.parseAttachmentResponse((String) msg.obj, result_handler);
              if (null != result)  {
                result_handler.obtainMessage(ERR_SUCCESS, result.mContent).sendToTarget();
              }
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }, request_type));
    mRequester.interrupt();
  }



  /**
   * Concatenates parameters into a URL-encoded query string.
   **/
  private String constructQueryString(HashMap<String, Object> params)
  {
    String query_string = "";

    if (null != params) {
      for (Map.Entry<String, Object> param : params.entrySet()) {
        Object obj = param.getValue();
        if (obj instanceof String) {
          query_string += String.format("%s=%s&",
              Uri.encode(param.getKey()), Uri.encode((String) obj));
        }
        else if (obj instanceof LinkedList<?>) {
          @SuppressWarnings("unchecked")
          LinkedList<String> cast_obj = (LinkedList<String>) obj;
          for (String s : cast_obj) {
            query_string += String.format("%s=%s[]&",
                Uri.encode(param.getKey()), Uri.encode(s));
          }
        }
      }
      if (0 < query_string.length()) {
        query_string = query_string.substring(0, query_string.length() - 1);
      }
    }

    return query_string;
  }



  /**
   * Helper function for fetching API responses.
   **/
  public void fetch(String uri_string, Request req)
  {
    byte[] data = fetchRawSynchronous(uri_string, req);
    if (null != data) {
      sendMessage(req, ERR_SUCCESS, new String(data));
    }
  }



  /**
   * Helper function for fetching raw response data.
   **/
  public void fetchRaw(String uri_string, Request req)
  {
    byte[] data = fetchRawSynchronous(uri_string, req);
    if (null != data) {
      sendMessage(req, ERR_SUCCESS, data);
    }
  }


  /**
   * Synchronous version of fetchRaw, used in both fetch() and fetchRaw(). May
   * send error messages, if the Handler is non-null, but always returns the
   * result as a parameter.
   **/
  public byte[] fetchRawSynchronous(Uri uri, Request req)
  {
    return fetchRawSynchronous(makeAbsoluteUriString(uri.toString()), req);
  }



  public byte[] fetchRawSynchronous(String uri_string, Request req)
  {
    HttpGet request = new HttpGet(uri_string);
    return fetchRawSynchronous(request, req);
  }



  public byte[] fetchRawSynchronous(Uri uri, final Handler handler)
  {
    return fetchRawSynchronous(uri, new Request(null, null, null, new Handler.Callback() {
            public boolean handleMessage(Message msg)
            {
              handler.obtainMessage(msg.what, msg.obj).sendToTarget();
              return true;
            }
          }));
  }



  public byte[] fetchRawSynchronous(HttpRequestBase request, Request req)
  {
    HttpResponse response;
    try {
      response = sClient.execute(request);

      int code = response.getStatusLine().getStatusCode();
      switch (code) {
        case 405:
          Log.d(LTAG, "Request method not allowed: " + request.getRequestLine().getMethod());
          sendMessage(req, ERR_METHOD_NOT_ALLOWED);
          return null;

        default:
          break;
      }

      // Log.d(LTAG, "Status code: " + code);

      // Read response
      HttpEntity entity = response.getEntity();
      if (null == entity) {
        Log.e(LTAG, "Response is empty: " + request.getURI().toString());
        sendMessage(req, ERR_EMPTY_RESPONSE);
        return null;
      }

      // Log.d(LTAG, "reading stream response");
      byte[] res = readStreamRaw(entity.getContent());
      // Log.d(LTAG, "bytes: " + res.length);
      return res;

    } catch (IOException ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: "
          + "(" + request.getURI().toString() + "|" + ex + ") " + ex.getMessage());
      sendMessage(req, ERR_TRANSMISSION);
    } catch (Exception ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: "
          + "(" + request.getURI().toString() + "|" + ex + ") " + ex.getMessage());
      sendMessage(req, ERR_UNKNOWN);
    }

    return null;
  }



  /**
   * Construct an HTTP request based on the API and parameters to query.
   **/
  private HttpRequestBase constructRequest(Request req)
  {
    return constructRequest(req.mApi, req.mParams, req.mSignedParams, req.mRequestType, req.mFileParams);
  }

  private HttpRequestBase constructRequest(String api,
      HashMap<String, Object> params,
      HashMap<String, Object> signedParams)
  {
    return constructRequest(api, params, signedParams, -1, null);
  }

  private HttpRequestBase constructRequest(String api,
      HashMap<String, Object> params,
      HashMap<String, Object> signedParams,
      int requestType,
      HashMap<String, Object> fileParams)
  {
    // Construct request URI.
    String request_uri = makeAbsoluteUriString(api);
    // Log.d(LTAG, "Request URI: " + request_uri);

    // Figure out the type of request to construct.
    int request_type = RT_GET;
    if (-1 == requestType) {
      Integer request_type_obj = REQUEST_TYPES.get(api);
      request_type = (null == request_type_obj ? RT_GET : (int) request_type_obj);
    }
    else {
      request_type = requestType;
    }

    return constructRequestInternal(request_uri, request_type,
        params, signedParams, fileParams);
  }



  private HttpRequestBase constructRequestInternal(String request_uri,
      int request_type,
      HashMap<String, Object> params,
      HashMap<String, Object> signedParams,
      HashMap<String, Object> fileParams)
  {
    // 1. Initialize params map. We always send the API version, and the API key
    if (null == params) {
      params = new HashMap<String, Object>();
    }
    // FIXME
    params.put("debug_signature", "true");

    // 2. If there are signed parameters, perform the signature dance.
    createSignature(request_uri, params, signedParams);

    // 3. Construct request.
    HttpRequestBase request = null;
    switch (request_type) {
      case RT_GET:
        {
          request_uri = String.format("%s?%s", request_uri,
              constructQueryString(params));
          request = new HttpGet(request_uri);
        }
        break;


      case RT_MULTIPART_PUT:
      case RT_MULTIPART_POST:
        {
          // POST or PUT, depending on request_type
          HttpEntityEnclosingRequestBase enclosing = null;
          if (RT_MULTIPART_PUT == request_type) {
            enclosing = new HttpPut(request_uri);
          }
          else {
            enclosing = new HttpPost(request_uri);
          }
          MultipartEntity content = new MultipartEntity();

          // Append all parameters as parts.
          for (Map.Entry<String, Object> param : params.entrySet()) {
            Object obj = param.getValue();
            if (null == obj) {
              Log.e(LTAG, "Ingoring null value for: " + param.getKey());
              continue;
            }

            try {
              if (obj instanceof List) {
                List cast = (List) obj;
                String key = String.format("%s[]", param.getKey());
                for (Object o : cast) {
                  content.addPart(key, new StringBody(o.toString()));
                }
              }

              else if (obj instanceof FilePartBody) {
                content.addPart(param.getKey(), (FilePartBody) obj);
              }

              else {
                content.addPart(param.getKey(), new StringBody(obj.toString()));
              }

            } catch (java.io.UnsupportedEncodingException ex) {
              Log.e(LTAG, "Unsupported encoding, skipping parameter: "
                  + param.getKey());
            }
          }

          // Append all files as parts.
          if (null != fileParams) {
            for (Map.Entry<String, Object> param : fileParams.entrySet()) {
              content.addPart(param.getKey(), new FileBody(new File(param.getValue().toString())));
            }
          }

          enclosing.setEntity(content);
          request = enclosing;
        }
        break;


      case RT_FORM:
        {
          HttpPost post = new HttpPost(request_uri);
          post.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

          // Push parameters into a list.
          LinkedList<BasicNameValuePair> p = new LinkedList<BasicNameValuePair>();
          for (Map.Entry<String, Object> param : params.entrySet()) {
            Object obj = param.getValue();
            if (null == obj) {
              Log.e(LTAG, "Ingoring null value for: " + param.getKey());
              continue;
            }

            if (obj instanceof List) {
              List cast = (List) obj;
              String key = String.format("%s[]", param.getKey());
              for (Object o : cast) {
                p.add(new BasicNameValuePair(key, o.toString()));
              }
            }

            else {
              p.add(new BasicNameValuePair(param.getKey(), obj.toString()));
            }
          }

          try {
            UrlEncodedFormEntity content = new UrlEncodedFormEntity(p);
            post.setEntity(content);
            request = post;
          } catch (java.io.UnsupportedEncodingException ex) {
            Log.e(LTAG, "Unsupported encoding, can't send request.");
          }
        }
        break;


      case RT_DELETE:
        {
          request_uri = String.format("%s?%s", request_uri,
              constructQueryString(params));
          request = new HttpDelete(request_uri);
        }
        break;


      default:
        Log.e(LTAG, "Unsupported request type: " + request_type);
    }

    // Set common request headers
    request.setHeader("Accept", "application/json");

    return request;
  }



  public String makeAbsoluteUriString(String relative)
  {
    String result = relative;
    Uri uri = Uri.parse(relative);
    if (null == uri.getAuthority()) {
      result = String.format("%s://%s/%s",
          API_REQUEST_URI_SCHEME, mAPIHost, relative);
    }

    return result;
  }



  public Uri makeAbsoluteUri(Uri relative)
  {
    if (null == relative.getAuthority()) {
      return Uri.parse(String.format("%s://%s%s",
          API_REQUEST_URI_SCHEME, mAPIHost, relative));
    }

    return relative;
  }



  /**
   * Signs a URI, discarding the fragment (if given).
   **/
  public Uri signUri(Uri unsigned)
  {
    if (null == unsigned) {
      return null;
    }

    // If there's a query string, we want to split it off and parse it, so we
    // can process the query parameters later.
    HashMap<String, Object> params = new HashMap<String, Object>();
    String base = unsigned.toString();
    String query = unsigned.getEncodedQuery();
    if (null != query) {
      // Split off query
      int pos = base.indexOf(query);
      base = base.substring(0, pos - 1);

      // Parse the query, and add it to params.
      try {
        List<NameValuePair> p = URLEncodedUtils.parse(
            new URI(unsigned.toString()), "utf-8");
        if (null != p) {
          for (NameValuePair pair : p) {
            params.put(pair.getName(), pair.getValue());
          }
        }
      } catch (URISyntaxException ex) {
        Log.e(LTAG, "Malformed URI: " + ex.getMessage());
        return null;
      }
    }

    // Create signature.
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    createSignature(base, params, signedParams);

    // Create signed Uri
    String uri_str = String.format("%s?%s", base, constructQueryString(params));
    return Uri.parse(uri_str);
  }



  /**
   * Resolves API host; may block for a long time, so must be run in the
   * background. May exit immediately if the API host has already been
   * resolved.
   **/
  private boolean resolveAPIHost()
  {
    // Exit if already resolved.
    if (null != mAPIHost) {
      return true;
    }

    Globals glob = Globals.get();
    if (null == glob) {
      return false;
    }

    // Resolve host name with client ID part to use for API requests.
    String id = glob.getClientID();
    if (null == id) {
      return false;
    }
    String srv_lookup = String.format(SRV_LOOKUP_FORMAT, id);

    Record[] records = null;
    int result = Lookup.TRY_AGAIN;
    try {
      int tries = 0;
      Lookup lookup = new Lookup(srv_lookup, Type.SRV);
      while (Lookup.TRY_AGAIN == result
          && tries < SRV_LOOKUP_ATTEMPTS_MAX)
      {
        Log.d(LTAG, "SRV lookup attempt #" + tries + ": " + srv_lookup);
        records = lookup.run();
        if (null != records) {
          break;
        }
        ++tries;
        result = lookup.getResult();
        Log.e(LTAG, "SRV lookup error: " + lookup.getErrorString());
      }
    } catch (TextParseException ex) {
      Log.e(LTAG, "Error performing SRV lookup: " + ex.getMessage());
    }

    // If after all attempts have been made (or an exception occurred) the
    // records array is still not set, we know we need to fall back to the
    // default API host.
    if (null == records) {
      mAPIHost = DEFAULT_API_HOST;
      return true;
    }

    // On the other hand, if there are records, we want to use the first
    // SRV record's target/port as the API host. There's not much use in
    // trying to look at more records than the first.
    SRVRecord srv = (SRVRecord) records[0];
    mAPIHost = String.format("%s:%d", srv.getTarget(), srv.getPort());

    return true;
  }



  /**
   * Create a signature from the signed params, and add it to the params map.
   **/
  private void createSignature(String request_uri, HashMap<String, Object> params,
      HashMap<String, Object> signedParams)
  {
    if (null == signedParams) {
      return;
    }

    // 1. Some parameters are required when signing.
    params.put(KEY_API_VERSION, String.valueOf(API_VERSION));
    params.put(KEY_API_FORMAT, API_FORMAT);
    params.put(mParamNameKey, mAPIKey);

    // 2. We always want a timestamp in the signed parameters.
    signedParams.put(mParamNameTimestamp, String.valueOf(System.currentTimeMillis() / 1000));

    // 3. Then all signed parameters need to be copied to the parameters
    //    with a prefix.
    for (Map.Entry<String, Object> param : signedParams.entrySet()) {
      params.put(String.format("%s%s", SIGNED_PARAM_PREFIX, param.getKey()),
            param.getValue());
    }

    // 4. Sort keys of signed parameters.
    List<String> keys = new LinkedList<String>();
    keys.addAll(signedParams.keySet());
    Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);

    // 5. Create the signature.
    try {
      MessageDigest m = MessageDigest.getInstance("SHA-1");
      m.update(String.format("%s:", request_uri).getBytes());

      for (int i = 0 ; i < keys.size() ; ++i) {
        String key = keys.get(i);
        Object obj = signedParams.get(key);
        if (null == obj) {
          Log.e(LTAG, "Ignoring null value for key: " + key);
          continue;
        }

        if (obj instanceof List) {
          List cast = (List) obj;
          for (int j = 0 ; j < cast.size() ; ++j) {
            m.update(String.format("%s[]=%s", key, cast.get(j).toString()).getBytes());

            if (j < (keys.size() - 1)) {
              m.update("&".getBytes());
            }
          }
        }

        else if (obj instanceof FilePartBody) {
          m.update(String.format("%s=", key).getBytes());
          FilePartBody part = (FilePartBody) obj;
          part.updateHash(m);
        }

        else {
          m.update(String.format("%s=%s", key, obj.toString()).getBytes());
        }


        if (i < (keys.size() - 1)) {
          m.update("&".getBytes());
        }
      }

      m.update(String.format(":%s", mAPISecret).getBytes());
      String signature = new BigInteger(1, m.digest()).toString(16);
      Log.d(LTAG, "signature: " + signature);
      params.put(mParamNameSignature, signature);
    } catch (java.security.NoSuchAlgorithmException ex) {
      Log.e(LTAG, "Error: could not sign request: " + ex.getMessage());
    }
  }



  /**
   * If no status is known, performs a status request.
   **/
  private boolean updateStatus(final Request req, boolean signalSuccess)
  {
    if (null != getStatus()) {
      if (signalSuccess) {
        sendMessage(req, ERR_SUCCESS);
      }
      return true;
    }

    // Construct status request. We pass an signedParams map to force signing
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    HttpRequestBase request = constructRequest(API_STATUS, null, signedParams);
    byte[] data = fetchRawSynchronous(request, req);
    if (null == data) {
      Log.e(LTAG, "No response to status update call.");
      return false;
    }

    ResponseParser.Response<Status> status
        = ResponseParser.parseStatusResponse(new String(data), req);

    if (null != status) {
      mStatus = status.mContent;
      mStatusTimeout = System.currentTimeMillis() + (status.mWindow * 1000);
      mServerTimestamp = status.mTimestamp;
    }

    if (null == mStatus) {
      sendMessage(req, ERR_EMPTY_RESPONSE);
      return false;
    }
    else {
      if (signalSuccess) {
        sendMessage(req, ERR_SUCCESS);
      }
    }

    return true;
  }



  /**
   * - Check whether we've already got an API key linked to the device.
   * - If not, attempt to read it from disk.
   * - If that fails, fetch it from the API.
   **/
  private void initializeAPIKeys(final Request req)
  {
    // We can check any of the mAPI* or mParamName* fields to determine
    // whether or not we need to do anything here. Let's stick to the first.
    if (!mAPIKey.equals(SERVICE_KEY)) {
      // That's it.
      //Log.d(LTAG, "Using source key: " + mAPIKey);
      return;
    }

    // Try load key/secret.
    Pair<String, String> creds = Globals.get().getCredentials();
    if (null != creds) {
      mAPIKey = creds.mFirst;
      mAPISecret = creds.mSecond;
      mParamNameKey = KEY_SOURCE_KEY;
      mParamNameSignature = KEY_SOURCE_SIGNATURE;
      mParamNameTimestamp = KEY_SOURCE_TIMESTAMP;
      //Log.d(LTAG, "Using source key: " + mAPIKey);
      return;
    }


    // Since we could not load the key/secret, we'll need to send a request
    // to fetch both.
    // BOARD: trout
    // BRAND: android-devphone1
    // DEVICE: dream
    // DISPLAY: dream_devphone-userdebug 1.5 CRB43 148830 test-keys
    // FINGERPRINT: android-devphone1/dream_devphone/dream/trout:1.5/CRB43/148830:userdebug/adp,test-keys
    // HOST: undroid11.mtv.corp.google.com
    // ID: CRB43
    // MODEL: Android Dev Phone 1
    // PRODUCT: dream_devphone
    // TAGS: test-keys
    // TYPE: userdebug
    // USER: android-build
    // VERSION.INCREMENTAL: 148830
    // VERSION.RELEASE: 1.5
    // VERSION.SDK: 3
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("source[unique_identifier]", Globals.get().getClientID());
    signedParams.put("source[device_name]", "none"); // No comparable concept exists.
    signedParams.put("source[device_model]", Uri.encode(Build.MODEL));
    signedParams.put("source[system_name]", "Android");
    signedParams.put("source[system_version]", String.format("%s-%s", Build.VERSION.RELEASE,
          Build.VERSION.INCREMENTAL));
    signedParams.put("force_mobile", "false");

    HttpRequestBase request = constructRequest(API_REGISTER, null, signedParams);
    byte[] data = fetchRawSynchronous(request, req);
    if (data == null) {
      Log.e(LTAG, "Empty response to registration call.");
      return;
    }
//    Log.d(LTAG, "registration response: " + new String(data));

    ResponseParser.Response<Pair<String, String>> results
        = ResponseParser.parseRegistrationResponse(new String(data), new Handler(new Handler.Callback() {
                public boolean handleMessage(Message msg)
                {
                  sendMessage(req, msg.what, msg.obj);
                  return true;
                }
              }));

    if (null != results) {
      mAPISecret = results.mContent.mFirst;
      mAPIKey = results.mContent.mSecond;
      mParamNameKey = KEY_SOURCE_KEY;
      mParamNameSignature = KEY_SOURCE_SIGNATURE;
      mParamNameTimestamp = KEY_SOURCE_TIMESTAMP;

      // Store these values
      SharedPreferences prefs = Globals.get().getPrefs();
      SharedPreferences.Editor edit = prefs.edit();
      edit.putString(Globals.PREF_API_KEY, mAPIKey);
      edit.putString(Globals.PREF_API_SECRET, mAPISecret);
      edit.commit();
    }
  }



  /**
   * Handler handling (haha, ohh I'm cracking myself up.)
   **/
  public void sendMessage(Request req, int type)
  {
    if (null == req) {
      Log.e(LTAG, "Request is null.");
      return;
    }
    req.mBaton = null;
    mHandler.obtainMessage(type, req).sendToTarget();
  }



  public void sendMessage(Request req, int type, Object obj)
  {
    if (null == req) {
      Log.e(LTAG, "Request is null.");
      return;
    }
    req.mBaton = obj;
    mHandler.obtainMessage(type, req).sendToTarget();
  }
}

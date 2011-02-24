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

import android.net.Uri;

import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.SimpleResolver;

import java.io.IOException;

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

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpResponse;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.*;
import org.apache.http.message.BasicNameValuePair;

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
import java.util.TreeMap;
import java.util.List;
import java.util.LinkedList;

import java.math.BigInteger;
import java.security.MessageDigest;

import android.content.SharedPreferences;

import fm.audioboo.data.Tag;

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
  // XXX
  // private static final String DEFAULT_API_HOST            = "api.staging.audioboo.fm";

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

  // URI snippets for various APIs
  private static final String API_RECENT                  = "audio_clips";
  //private static final String API_UPLOAD                  = "account/audio_clips";
  private static final String API_UPLOAD                  = "boos";

  private static final String API_REGISTER                = "sources/register";
  private static final String API_STATUS                  = "sources/status";
  // XXX API_LINK isn't required; the link uri is returned in the status call.
  private static final String API_UNLINK                  = "sources/unlink";

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
  private static final int RT_MULTIPART                   = 2;

  // Map of APIs to request types.
  private static final HashMap<String, Integer> REQUEST_TYPES;
  static {
    REQUEST_TYPES = new HashMap<String, Integer>();
    REQUEST_TYPES.put(API_RECENT,   RT_GET);
    REQUEST_TYPES.put(API_UPLOAD,   RT_MULTIPART);
    REQUEST_TYPES.put(API_REGISTER, RT_FORM);
    REQUEST_TYPES.put(API_STATUS,   RT_GET);
    REQUEST_TYPES.put(API_UNLINK,   RT_FORM);
    // XXX Add request types for different API calls; if they're not specified
    //     here, the default is RT_GET.
  }

  // HTTP Client parameters
  private static final int          HTTP_TIMEOUT            = 60 * 1000;
  private static final HttpVersion  HTTP_VERSION            = HttpVersion.HTTP_1_1;

  // Requester startup delay. Avoids high load at startup that could impact UX.
  private static final int          REQUESTER_STARTUP_DELAY = 1000;

  // Chunk size to read responses in (in Bytes).
  private static final int          READ_CHUNK_SIZE         = 8192;

  /***************************************************************************
   * Protected static data
   **/
  protected static DefaultHttpClient            sClient;
  protected static ThreadSafeClientConnManager  sConnectionManager;


  /***************************************************************************
   * Helper class for fetching API responses in the background.
   **/
  private class Requester extends Thread
  {
    public boolean keepRunning = true;

    private String                  mApi;
    private HashMap<String, Object> mParams;
    private HashMap<String, Object> mSignedParams;
    private HashMap<String, String> mFileParams;
    private Handler                 mHandler;

    public Requester(String api,
        HashMap<String, Object> params,
        HashMap<String, Object> signedParams,
        HashMap<String, String> fileParams,
        Handler handler)
    {
      super();
      mApi = api;
      mParams = params;
      mSignedParams = signedParams;
      mFileParams = fileParams;
      mHandler = handler;
    }


    @Override
    public void run()
    {
      // Delay before starting to fetch stuff.
      if (mDelayStartup) {
        try {
          sleep(REQUESTER_STARTUP_DELAY);
          mDelayStartup = false;
        } catch (java.lang.InterruptedException ex) {
          return;
        }
      }

      // The loop ensures that external interrrupts don't mean the request is
      // never executed..
      while (keepRunning) {
        // Resolve API host. Should return immediately after the first time
        // it's run.
        resolveAPIHost();

        // After resolving the API host, we need to obtain the appropriate
        // key(s) for API calls. This should return immediately after the
        // first time it's run.
        initializeAPIKeys(mHandler);

        // Update status. This should return immediately after the first time
        // it's run. If the API requested is in fact the status update API, then
        // the Requester will terminate after this call.
        if (mApi.equals(API_STATUS)) {
          updateStatus(mHandler, true);
          keepRunning = false;
          break;
        }
        else {
          if (!updateStatus(mHandler, false)) {
            keepRunning = false;
            break;
          }
        }

        // Construct request.
        HttpRequestBase request = constructRequest(mApi, mParams, mSignedParams,
            mFileParams);

        // Perform request.
        byte[] data = fetchRawSynchronous(request, mHandler);
        if (null != data) {
          mHandler.obtainMessage(ERR_SUCCESS, new String(data)).sendToTarget();
        }

        keepRunning = false;
      }
    }
  }


  /***************************************************************************
   * Data members
   **/

  // Requester. There's only one instance, so only one API call can be scheduled
  // at a time.
  private Requester     mRequester;
  private boolean       mDelayStartup = true;

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
    HttpConnectionParams.setConnectionTimeout(params, HTTP_TIMEOUT);
    HttpProtocolParams.setVersion(params, HTTP_VERSION);
    return params;
  }


  /**
   * Fetch recent Boos.
   * On success, the message object will be a LinkedList<Boo>.
   **/
  public void fetchRecentBoos(final Handler result_handler)
  {
    if (null != mRequester) {
      mRequester.keepRunning = false;
      mRequester.interrupt();
    }

    // This request has no parameters.
    mRequester = new Requester(API_RECENT, null, null, null,
        new Handler(new Handler.Callback() {
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
    mRequester.start();
  }



  /**
   * Unlinks the device, if it's currently linked.
   * On success, the message object will not be set.
   **/
  public void unlinkDevice(final Handler result_handler)
  {
    if (null != mRequester) {
      mRequester.keepRunning = false;
      mRequester.interrupt();
    }

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
    mRequester = new Requester(API_UNLINK, null, signedParams, null,
        new Handler(new Handler.Callback() {
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
    mRequester.start();
  }



  /**
   * Updates the device status, if necessary.
   * On success, the message object will not be set.
   **/
  public void updateStatus(final Handler result_handler)
  {
    if (null != mRequester) {
      mRequester.keepRunning = false;
      mRequester.interrupt();
    }

    // Set status to null, otherwise nothing will be fetched.
    mStatus = null;

    // This request has no parameters. The result handler also handles
    // any responses itself.
    mRequester = new Requester(API_STATUS, null, null, null,
        result_handler);
    mRequester.start();
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
   * Uploads a Boo.
   * On success, the message object will contain an Integer representing the
   * ID of the newly uploaded Boo.
   **/
  public void uploadBoo(Boo boo, final Handler result_handler)
  {
    if (null != mRequester) {
      mRequester.keepRunning = false;
      mRequester.interrupt();
    }

    // Prepare parameters.
    HashMap<String, Object> params = null;
//    params = new HashMap<String, Object>();
//    params.put("debug_signature", "true");

    // Prepare signed parameters
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    signedParams.put("audio_clip[title]", boo.mData.mTitle);
    signedParams.put("audio_clip[local_recorded_at]", boo.mData.mRecordedAt.toString());
    signedParams.put("audio_clip[author_locale]", Locale.getDefault().toString());

    // Tags
    if (null != boo.mData.mTags) {
      LinkedList<String> tags = new LinkedList<String>();
      for (Tag t : boo.mData.mTags) {
        tags.add(t.mNormalised);
      }
      signedParams.put("audio_clip[tags]", tags);
    }

    if (null != boo.mData.mLocation) {
      signedParams.put("audio_clip[public_location]", "1");
      signedParams.put("audio_clip[location_latitude]",
          String.format("%f", boo.mData.mLocation.mLatitude));
      signedParams.put("audio_clip[location_longitude]",
          String.format("%f", boo.mData.mLocation.mLongitude));
      signedParams.put("audio_clip[location_accuracy]",
          String.format("%f", boo.mData.mLocation.mAccuracy));
    }

    if (null != boo.mData.mUUID) {
      signedParams.put("audio_clip[uuid]", boo.mData.mUUID);
    }

    // Prepare files.
    HashMap<String, String> fileParams = new HashMap<String, String>();
    fileParams.put("audio_clip[uploaded_data]", boo.mData.mHighMP3Url.getPath());
    if (null != boo.mData.mImageUrl) {
      fileParams.put("audio_clip[uploaded_image]", boo.mData.mImageUrl.getPath());
    }

    mRequester = new Requester(API_UPLOAD, params, signedParams, fileParams,
        new Handler(new Handler.Callback() {
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
        }
    ));
    mRequester.start();
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
   * Concatenates parameters into a string without URL encoding.
   **/
  private String concatenateParametersSorted(HashMap<String, Object> params)
  {
    String result = "";

    if (null != params) {
      // Create a sorted map.
      TreeMap<String, Object> sorted = new TreeMap<String, Object>();
      for (Map.Entry<String, Object> param : params.entrySet()) {
        sorted.put(param.getKey(), param.getValue());
      }

      // Now concatenate the sorted values.
      for (Map.Entry<String, Object> param : sorted.entrySet()) {
        Object obj = param.getValue();
        if (obj instanceof String) {
          result += String.format("%s=%s&", param.getKey(), (String) obj);
        }
        else if (obj instanceof LinkedList<?>) {
          @SuppressWarnings("unchecked")
          LinkedList<String> cast_obj = (LinkedList<String>) obj;
          for (String s : cast_obj) {
            result += String.format("%s[]=%s&", param.getKey(), s);
          }
        }
      }
      if (0 < result.length()) {
        result = result.substring(0, result.length() - 1);
      }
    }

    return result;
  }



  /**
   * Helper function for fetching API responses.
   **/
  public void fetch(String uri_string, Handler handler)
  {
    byte[] data = fetchRawSynchronous(uri_string, handler);
    if (null != data) {
      handler.obtainMessage(ERR_SUCCESS, new String(data)).sendToTarget();
    }
  }



  /**
   * Helper function for fetching raw response data.
   **/
  public void fetchRaw(String uri_string, Handler handler)
  {
    byte[] data = fetchRawSynchronous(uri_string, handler);
    if (null != data) {
      handler.obtainMessage(ERR_SUCCESS, data).sendToTarget();
    }
  }


  /**
   * Synchronous version of fetchRaw, used in both fetch() and fetchRaw(). May
   * send error messages, if the Handler is non-null, but always returns the
   * result as a parameter.
   **/
  public byte[] fetchRawSynchronous(String uri_string, Handler handler)
  {
    HttpGet request = new HttpGet(uri_string);
    return fetchRawSynchronous(request, handler);
  }



  public byte[] fetchRawSynchronous(HttpRequestBase request, Handler handler)
  {
    HttpResponse response;
    try {
      response = sClient.execute(request);

      // Read response
      HttpEntity entity = response.getEntity();
      if (null == entity) {
        Log.e(LTAG, "Response is empty: " + request.getURI().toString());
        if (null != handler) {
          handler.obtainMessage(ERR_EMPTY_RESPONSE).sendToTarget();
        }
        return null;
      }

      return readStreamRaw(entity.getContent());

    } catch (IOException ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: "
          + ex.getMessage());
      if (null != handler) {
        handler.obtainMessage(ERR_TRANSMISSION).sendToTarget();
      }
    } catch (Exception ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: "
          + ex.getMessage());
      if (null != handler) {
        handler.obtainMessage(ERR_UNKNOWN).sendToTarget();
      }
    }

    return null;

  }



  /**
   * Construct an HTTP request based on the API and parameters to query.
   **/
  private HttpRequestBase constructRequest(String api,
      HashMap<String, Object> params,
      HashMap<String, Object> signedParams,
      HashMap<String, String> fileParams)
  {
    // Construct request URI.
    String request_uri = String.format("%s://%s/%s",
        API_REQUEST_URI_SCHEME, mAPIHost, api);
    // Log.d(LTAG, "Request URI: " + request_uri);

    // Figure out the type of request to construct.
    Integer request_type_obj = REQUEST_TYPES.get(api);
    int request_type = (null == request_type_obj ? RT_GET : (int) request_type_obj);
    if (null != fileParams) {
      request_type = RT_MULTIPART;
    }

    return constructRequestInternal(request_uri, request_type,
        params, signedParams, fileParams);
  }



  private HttpRequestBase constructRequestInternal(String request_uri,
      int request_type,
      HashMap<String, Object> params,
      HashMap<String, Object> signedParams,
      HashMap<String, String> fileParams)
  {
    // 1. Initialize params map. We always send the API version, and the API key
    if (null == params) {
      params = new HashMap<String, Object>();
    }
    params.put(KEY_API_VERSION, String.valueOf(API_VERSION));
    params.put(KEY_API_FORMAT, API_FORMAT);
    params.put(mParamNameKey, mAPIKey);

    // 2. If there are signed parameters, perform the signature dance.
    if (null != signedParams) {
      // 2.1 We always want a timestamp in the signed parameters.
      signedParams.put(mParamNameTimestamp, String.valueOf(System.currentTimeMillis() / 1000));

      // 2.2 Then all signed parameters need to be copied to the parameters
      //     with a prefix.
      for (Map.Entry<String, Object> param : signedParams.entrySet()) {
        params.put(String.format("%s%s", SIGNED_PARAM_PREFIX, param.getKey()),
              param.getValue());
      }

      // 2.3 Create the signature.
      String signature = String.format("%s:%s:%s", request_uri,
          concatenateParametersSorted(signedParams), mAPISecret);
      // Log.d(LTAG, "signature pre signing: " + signature);
      try {
        MessageDigest m = MessageDigest.getInstance("SHA-1");
        m.update(signature.getBytes());
        signature = new BigInteger(1, m.digest()).toString(16);
        // Log.d(LTAG, "signature: " + signature);
        params.put(mParamNameSignature, signature);
      } catch (java.security.NoSuchAlgorithmException ex) {
        Log.e(LTAG, "Error: could not sign request: " + ex.getMessage());
      }
    }

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


      case RT_MULTIPART:
        {
          HttpPost post = new HttpPost(request_uri);
          MultipartEntity content = new MultipartEntity();

          // Append all parameters as parts.
          for (Map.Entry<String, Object> param : params.entrySet()) {
            Object obj = param.getValue();
            try {
              if (obj instanceof String) {
                content.addPart(param.getKey(), new StringBody((String) obj));
              }
              else if (obj instanceof LinkedList<?>) {
                String key = param.getKey() + "[]";
                @SuppressWarnings("unchecked")
                LinkedList<String> cast_obj = (LinkedList<String>) obj;
                for (String s : cast_obj) {
                  content.addPart(key, new StringBody(s));
                }
              }
            } catch (java.io.UnsupportedEncodingException ex) {
              Log.e(LTAG, "Unsupported encoding, skipping parameter: "
                  + param.getKey() + "=" + param.getValue());
            }
          }

          // Append all files as parts.
          if (null != fileParams) {
            for (Map.Entry<String, String> param : fileParams.entrySet()) {
              content.addPart(param.getKey(), new FileBody(new File(param.getValue())));
            }
          }

          post.setEntity(content);
          request = post;
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
            if (obj instanceof String) {
              p.add(new BasicNameValuePair(param.getKey(), (String) obj));
            }
            else if (obj instanceof LinkedList<?>) {
              String key = param.getKey() + "[]";
              @SuppressWarnings("unchecked")
              LinkedList<String> cast_obj = (LinkedList<String>) obj;
              for (String s : cast_obj) {
                p.add(new BasicNameValuePair(key, s));
              }
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


      default:
        Log.e(LTAG, "Unsupported request type: " + request_type);
    }

    return request;
  }



  /**
   * Resolves API host; may block for a long time, so must be run in the
   * background. May exit immediately if the API host has already been
   * resolved.
   **/
  private void resolveAPIHost()
  {
    // Exit if already resolved.
    if (null != mAPIHost) {
      return;
    }

    // Resolve host name with client ID part to use for API requests.
    String srv_lookup = String.format(SRV_LOOKUP_FORMAT, Globals.get().getClientID());

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
      return;
    }

    // On the other hand, if there are records, we want to use the first
    // SRV record's target/port as the API host. There's not much use in
    // trying to look at more records than the first.
    SRVRecord srv = (SRVRecord) records[0];
    mAPIHost = String.format("%s:%d", srv.getTarget(), srv.getPort());
  }



  /**
   * If no status is known, performs a status request.
   **/
  private boolean updateStatus(Handler handler, boolean signalSuccess)
  {
    if (null != getStatus()) {
      if (signalSuccess) {
        handler.obtainMessage(ERR_SUCCESS).sendToTarget();
      }
      return true;
    }

    // Prepare parameters
    HashMap<String, Object> params = null;
//    params = new HashMap<String, Object>();
//    params.put("debug_signature", "true");

    // Construct status request. We pass an signedParams map to force signing
    HashMap<String, Object> signedParams = new HashMap<String, Object>();
    HttpRequestBase request = constructRequest(API_STATUS, params, signedParams,
        null);
    byte[] data = fetchRawSynchronous(request, handler);
    if (null == data) {
      Log.e(LTAG, "No response to status update call.");
      return false;
    }

    ResponseParser.Response<Status> status
        = ResponseParser.parseStatusResponse(new String(data), handler);
    if (null != status) {
      mStatus = status.mContent;
      mStatusTimeout = System.currentTimeMillis() + (status.mWindow * 1000);
      mServerTimestamp = status.mTimestamp;
    }

    if (null == mStatus) {
      handler.obtainMessage(ERR_EMPTY_RESPONSE).sendToTarget();
      return false;
    }
    else {
      if (signalSuccess) {
        handler.obtainMessage(ERR_SUCCESS).sendToTarget();
      }
    }

    return true;
  }



  /**
   * - Check whether we've already got an API key linked to the device.
   * - If not, attempt to read it from disk.
   * - If that fails, fetch it from the API.
   **/
  private void initializeAPIKeys(Handler handler)
  {
    // We can check any of the mAPI* or mParamName* fields to determine
    // whether or not we need to do anything here. Let's stick to the first.
    if (!mAPIKey.equals(SERVICE_KEY)) {
      // That's it.
      //Log.d(LTAG, "Using source key: " + mAPIKey);
      return;
    }

    // Try load key/secret.
    SharedPreferences prefs = Globals.get().getPrefs();
    String apiKey = prefs.getString(Globals.PREF_API_KEY, null);
    String apiSecret = prefs.getString(Globals.PREF_API_SECRET, null);
    if (null != apiKey && null != apiSecret) {
      mAPIKey = apiKey;
      mAPISecret = apiSecret;
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

    HttpRequestBase request = constructRequest(API_REGISTER, null, signedParams, null);
    byte[] data = fetchRawSynchronous(request, handler);
    if (data == null) {
      Log.e(LTAG, "Empty response to registration call.");
      return;
    }
//    Log.d(LTAG, "registration response: " + new String(data));

    ResponseParser.Response<Pair<String, String>> results
        = ResponseParser.parseRegistrationResponse(new String(data), handler);

    if (null != results) {
      mAPISecret = results.mContent.mFirst;
      mAPIKey = results.mContent.mSecond;
      mParamNameKey = KEY_SOURCE_KEY;
      mParamNameSignature = KEY_SOURCE_SIGNATURE;
      mParamNameTimestamp = KEY_SOURCE_TIMESTAMP;

      // Store these values
      SharedPreferences.Editor edit = prefs.edit();
      edit.putString(Globals.PREF_API_KEY, mAPIKey);
      edit.putString(Globals.PREF_API_SECRET, mAPISecret);
      edit.commit();
    }
  }
}

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

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase; // FIXME remove
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
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.LinkedList;

import java.math.BigInteger;
import java.security.MessageDigest;

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
  public static final int ERR_SUCCESS           = 0;
  public static final int ERR_INVALID_URI       = 1001;
  public static final int ERR_EMPTY_RESPONSE    = 1002;
  public static final int ERR_TRANSMISSION      = 1003;
  public static final int ERR_VERSION_MISMATCH  = 1004;
  public static final int ERR_PARSE_ERROR       = 1005;
  // TODO move to Error class; read localized descriptions

  // API version we're requesting
  public static final int API_VERSION = 200;

  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "API";

  // Default API host. Used as a fallback if SRV lookup fails.
  private static final String DEFAULT_API_HOST            = "api.audioboo.fm";
  //FIXME
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

  // Request/response format for API calls. XXX Leading dot is expected.
  private static final String API_FORMAT                  = "json";

  // URI snippets for various APIs
  private static final String API_RECENT                  = "audio_clips";
  private static final String API_UPLOAD                  = "account/audio_clips";

  private static final String API_REGISTER                = "sources/register";

  // API version, format parameter
  private static final String KEY_API_VERSION             = "version";
  private static final String KEY_API_FORMAT              = "fmt";

  // Signature-related keys
  private static final String KEY_SOURCE_KEY              = "source[key]";
  private static final String KEY_SOURCE_SIGNATURE        = "source[signature]";

  private static final String KEY_SERVICE_KEY             = "service[key]";
  private static final String KEY_SERVICE_SIGNATURE       = "service[signature]";

  // Prefix for signed parameters
  private static final String SIGNED_PARAM_PREFIX         = "signed_";

  // Service key/secret
  private static final String SERVICE_KEY                 = "06b4c02d1aa1cb98562264c1";
  private static final String SERVICE_SECRET              = "0334a90b23ee15b9d05859f21d6759169dd6758512ea852e8e7fb673b583c581";

  // Request types: we have GET, FORM and MULTIPART.
  private static final int RT_GET                         = 0;
  private static final int RT_FORM                        = 1;
  private static final int RT_MULTIPART                   = 2;

  // Map of APIs to request types.
  private static final HashMap<String, Integer> REQUEST_TYPES;
  static {
    REQUEST_TYPES = new HashMap<String, Integer>();
    REQUEST_TYPES.put(API_RECENT, RT_GET);
    REQUEST_TYPES.put(API_UPLOAD, RT_MULTIPART);
    REQUEST_TYPES.put(API_REGISTER, RT_FORM);
    // XXX Add request types for different API calls; if they're not specified
    //     here, the default is RT_GET.
  }

  // HTTP Client parameters
  private static final int          HTTP_TIMEOUT            = 60 * 1000;
  private static final HttpVersion  HTTP_VERSION            = HttpVersion.HTTP_1_1;

  // Requester startup delay. Avoids high load at startup that could impact UX.
  private static final int          REQUESTER_STARTUP_DELAY = 5 * 1000;

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
    private HashMap<String, String> mParams;
    private HashMap<String, String> mSignedParams;
    private HashMap<String, String> mFileParams;
    private Handler                 mHandler;

    public Requester(String api,
        HashMap<String, String> params,
        HashMap<String, String> signedParams,
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
      try {
// FIXME        sleep(Requester_STARTUP_DELAY);
        sleep(20);
      } catch (java.lang.InterruptedException ex) {
        // pass
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

  // API host to use in requests.
  private String        mAPIHost;

  // Key and secret for signing requests. Defaults to service (not source/device)
  // values.
  private String        mAPIKey             = SERVICE_KEY;
  private String        mAPISecret          = SERVICE_SECRET;

  // Parameter names for the key and signature.
  private String        mParamNameKey       = KEY_SERVICE_KEY;
  private String        mParamNameSignature = KEY_SERVICE_SIGNATURE;

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
   *
   * The Handler instance handed to this function should expect
   * a) msg.what to be ERR_SUCCESS, in which case msg.obj is a LinkedList<Boo>
   * b) msg.what to be other than ERR_SUCCESS, in which case it will correspond
   *    to one of the error codes defined above. Also, msg.obj may either be
   *    null, or a String providing details about the error.
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
              parseRecentBoosResponse((String) msg.obj, result_handler);
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


  // FIXME
  public void uploadBoo(Boo boo, final Handler result_handler)
  {
    if (null != mRequester) {
      mRequester.keepRunning = false;
      mRequester.interrupt();
    }

    // Prepare parameters.
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("debug_signature", "true");

    // Prepare signed parameters
    HashMap<String, String> signedParams = new HashMap<String, String>();
    signedParams.put("audio_clip[title]", "Android test");
    signedParams.put("audio_clip[local_recorded_at]", "06/10/2009 23:47");
//    HashMap<String, String> signedParams = null;
//    params.put("audio_clip[title]", "Android test");
//    params.put("audio_clip[local_recorded_at]", "06/10/2009 23:47");

    // Prepare files.
    HashMap<String, String> fileParams = new HashMap<String, String>();
    fileParams.put("audio_clip[uploaded_data]", boo.mHighMP3Url.getPath());

//	BBURLRequest* request = [BBURLRequest requestWithURL:[NSURL serverRelativeURL:@"boos"]];
//	[request setValue:@"xml" forParameter:@"fmt"];
//	[request setHTTPMethod:@"POST"];
//	
//	NSString* presentedTitle = self.title ? self.title : self.placeholderTitle;
//	[request setValue:presentedTitle forSignedParameter:@"audio_clip[title]"];
//	
//	if (self.tags)
//		[request setValue:self.tags forSignedParameter:@"audio_clip[tags]"];
//	if ([[audioWriters lastObject] lastModified])
//		[request setValue:[[[audioWriters lastObject] lastModified] description] forSignedParameter:@"audio_clip[local_recorded_at]"];
//	if (self.location) {
//		[request setValue:@"1" forSignedParameter:@"audio_clip[public_location]"];
//		[request setValue:[NSString stringWithFormat:@"%lf", self.location.coordinate.latitude] forSignedParameter:@"audio_clip[location_latitude]"];
//		[request setValue:[NSString stringWithFormat:@"%lf", self.location.coordinate.longitude] forSignedParameter:@"audio_clip[location_longitude]"];
//		[request setValue:[NSString stringWithFormat:@"%lf", self.location.horizontalAccuracy] forSignedParameter:@"audio_clip[location_accuracy]"];
//	}
//	
//	if ([self hasImageAttachment]) {
//		[request setFile:self.imagePath forParameter:@"audio_clip[uploaded_image]"];
//	}
//	
//	[request setFile:self.audioPath forParameter:@"audio_clip[uploaded_data]"];
//	
//	[connection release];
//	connection = [[ABURLConnection alloc] initWithRequest:request delegate:self];

    mRequester = new Requester(API_UPLOAD, params, signedParams, fileParams,
        new Handler(new Handler.Callback() {
          public boolean handleMessage(Message msg)
          {
            if (ERR_SUCCESS == msg.what) {
              Log.d(LTAG, "Response: " + (String) msg.obj);
            }
            else {
              result_handler.obtainMessage(msg.what, msg.obj).sendToTarget();
            }
            return true;
          }
        }
    ));
    mRequester.start();

//    Thread th = new Thread() {
//      public void run()
//      {
////    params.put("audio_clip[title]", boo.mTitle);
////    params.put("audio_clip[local_recorded_at]", boo.mRecordedAt.toString());
//    // TODO add tags, etc.
////    String uri_string = getApiUrl(API_UPLOAD, params);
//    Log.d(LTAG, "uri: " + uri_string);
//
//    HttpPost post = new HttpPost(uri_string);
//    FileBody bin = new FileBody(new File(boo.mHighMP3Url.getPath()));
//
//    MultipartEntity reqEntity = new MultipartEntity();
//    reqEntity.addPart("audio_clip[uploaded_data]", bin);
//
//    post.setEntity(reqEntity);
//    try {
//    HttpResponse response = sClient.execute(post);
//
//    HttpEntity resEntity = response.getEntity();
//    if (resEntity != null) {
//        resEntity.consumeContent();
//    }
//    } catch (IOException ex) {
//      Log.e(LTAG, "IO EXCEPTIOn: " + ex.getMessage());
//    }
//      }
//    };
//    th.start();
  }



  /**
   * Parse recent Boos response, and notify the result handler when done.
   **/
  private void parseRecentBoosResponse(String response, Handler result_handler)
  {
    // Log.d(LTAG, "Response: " + response);
    ResponseParser parser = new ResponseParser();
    BooList boos = parser.parseBooList(response, result_handler);
    if (null != boos) {
      // If boos were null, then the ResponseParser would already have sent an
      // error message to the result_handler.
      result_handler.obtainMessage(ERR_SUCCESS, boos).sendToTarget();
    }
  }



  /**
   * Concatenates parameters into a URL-encoded query string.
   **/
  private String constructQueryString(HashMap<String, String> params)
  {
    String query_string = "";

    if (null != params) {
      for (Map.Entry<String, String> param : params.entrySet()) {
        query_string += String.format("%s=%s&",
            Uri.encode(param.getKey()), Uri.encode(param.getValue()));
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
  private String concatenateParamtersSorted(HashMap<String, String> params)
  {
    String result = "";

    if (null != params) {
      // Create a sorted map.
      TreeMap<String, String> sorted = new TreeMap<String, String>();
      for (Map.Entry<String, String> param : params.entrySet()) {
        sorted.put(param.getKey(), param.getValue());
      }

      // Now concatenate the sorted values.
      for (Map.Entry<String, String> param : sorted.entrySet()) {
        result += String.format("%s=%s&", param.getKey(), param.getValue());
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
          handler.obtainMessage(ERR_EMPTY_RESPONSE, request.getURI().toString()
              ).sendToTarget();
        }
        return null;
      }

      return readStreamRaw(entity.getContent());

    } catch (IOException ex) {
      Log.e(LTAG, "An exception occurred when reading the API response: " + ex);
      if (null != handler) {
        handler.obtainMessage(ERR_TRANSMISSION, ex.getMessage()).sendToTarget();
      }
      return null;
    }
  }



  /**
   * Construct an HTTP request based on the API and parameters to query.
   **/
  private HttpRequestBase constructRequest(String api,
      HashMap<String, String> params,
      HashMap<String, String> signedParams,
      HashMap<String, String> fileParams)
  {
    // 1. Construct request URI.
    String request_uri = String.format("%s://%s/%s",
        API_REQUEST_URI_SCHEME, mAPIHost, api);
    Log.d(LTAG, "Request URI: " + request_uri);

    // 2. Initialize params map. We always send the API version, and the API key
    if (null == params) {
      params = new HashMap<String, String>();
    }
    params.put(KEY_API_VERSION, String.valueOf(API_VERSION));
    params.put(KEY_API_FORMAT, API_FORMAT);
    params.put(mParamNameKey, mAPIKey);

    // 3. If there are signed parameters, append them to the params map
    if (null != signedParams) {
      for (Map.Entry<String, String> param : signedParams.entrySet()) {
        params.put(String.format("%s%s", SIGNED_PARAM_PREFIX, param.getKey()),
              param.getValue());
      }
    }

    // 4. If there are signed parameters, create the signature.
    if (null != signedParams) {
      String signature = String.format("%s:%s:%s", request_uri,
          concatenateParamtersSorted(signedParams), mAPISecret);
      Log.d(LTAG, "signature pre signing: " + signature);
      try {
        MessageDigest m = MessageDigest.getInstance("SHA-1");
        m.update(signature.getBytes());
        signature = new BigInteger(1, m.digest()).toString(16);
        Log.d(LTAG, "signature: " + signature);
        params.put(mParamNameSignature, signature);
      } catch (java.security.NoSuchAlgorithmException ex) {
        Log.e(LTAG, "Error: could not sign request: " + ex.getMessage());
      }
    }

    // 5. Figure out the type of request to construct.
    Integer request_type_obj = REQUEST_TYPES.get(api);
    int request_type = (null == request_type_obj ? RT_GET : (int) request_type_obj);
    if (null != fileParams) {
      request_type = RT_MULTIPART;
    }

    // 6. Construct request.
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
          for (Map.Entry<String, String> param : params.entrySet()) {
            try {
              content.addPart(param.getKey(), new StringBody(param.getValue()));
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
          for (Map.Entry<String, String> param : params.entrySet()) {
            p.add(new BasicNameValuePair(param.getKey(), param.getValue()));
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

    // FIXME
    try {
    HttpEntityEnclosingRequestBase rc = (HttpEntityEnclosingRequestBase) request.clone();
    HttpEntity e = rc.getEntity();
    String s = readStream(e.getContent());
    Log.d(LTAG, "Request: " + s);
    } catch (Exception ex) {
      Log.d(LTAG, "ex: " + ex.getMessage());
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
      Log.d(LTAG, "Using source key: " + mAPIKey);
      return;
    }

    // TODO try load key/secret.



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
    HashMap<String, String> signedParams = new HashMap<String, String>();
    signedParams.put("source[unique_identifier]", Globals.get().getClientID());
    signedParams.put("source[device_name]", "none"); // No comparable concept exists.
    signedParams.put("source[device_model]", Uri.encode(Build.MODEL));
    signedParams.put("source[system_name]", "Android");
    signedParams.put("source[system_version]", String.format("%s-%s", Build.VERSION.RELEASE,
          Build.VERSION.INCREMENTAL));
    signedParams.put("force_mobile", "false");

    HttpRequestBase request = constructRequest(API_REGISTER, null, signedParams, null);
    byte[] data = fetchRawSynchronous(request, null);
//    Log.d(LTAG, "registration response: " + new String(data));

    ResponseParser parser = new ResponseParser();
    Pair<String, String> results = parser.parseRegistrationResponse(
        new String(data), handler);

    if (null != results) {
      mAPISecret = results.mFirst;
      mAPIKey = results.mSecond;
      mParamNameKey = KEY_SOURCE_KEY;
      mParamNameSignature = KEY_SOURCE_SIGNATURE;

      // TODO store these values
    }
  }
}

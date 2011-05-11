/**
 * Copyright (C) 2010 Jens Finkhaeuser <unwesen@users.sourceforge.net>
 * All rights reserved.
 *
 * $Id: ExceptionHandler.java 1159 2010-05-09 12:05:17Z unwesen $
 **/

package de.unwesen.web.stacktrace;

import android.content.Context;

import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;

import android.os.Build;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.File;
import java.io.FilenameFilter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.lang.Thread.UncaughtExceptionHandler;

import java.math.BigInteger;
import java.security.MessageDigest;

import de.unwesen.util.Base64;



/**
 * Register exception handler with a server URL and API key, and uncaught
 * exceptions will be submitted to that server when the app next starts.
 **/
public class ExceptionHandler
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG      = "de.unwesen.web.stacktrace.ExceptionHandler";

  // Trace file extensions
  private static final String TRACE_EXT = ".trace";

  // Parameter separator in trace files
  private static final String PARAM_SEP = ":";

  // BufferedReader/-Writer buffer size
  private static final int BUFSIZE      = 8192;

  // HTTP Client parameters
  // XXX These values are pretty much copied from example code and likely need
  //     to be tweaked.
  private static final int          HTTP_TIMEOUT = 60 * 1000;
  private static final HttpVersion  HTTP_VERSION = HttpVersion.HTTP_1_1;


  /***************************************************************************
   * Log class - proxies android.util.Log, and writes stack traces for e()
   **/
  public static class Log
  {
    public static int d(String tag, String msg)
    {
      return android.util.Log.d(tag, msg);
    }

    public static int i(String tag, String msg)
    {
      return android.util.Log.i(tag, msg);
    }

    public static int v(String tag, String msg)
    {
      return android.util.Log.v(tag, msg);
    }

    public static int w(String tag, String msg)
    {
      return android.util.Log.w(tag, msg);
    }

    public static int e(String tag, String msg)
    {
      try {
        writeStackTrace(new Throwable(), tag, msg);
      } catch (Exception e) {
        android.util.Log.e(LTAG, "Could not write stack trace for the following "
            + "error message.");
      }
      return android.util.Log.e(tag, msg);
    }
  }



  /***************************************************************************
   * ExceptionHandlerProxy class
   **/
  private static class ExceptionHandlerProxy implements UncaughtExceptionHandler
  {
    private UncaughtExceptionHandler mDefaultHandler;

    public ExceptionHandlerProxy(UncaughtExceptionHandler defaultHandler)
    {
      mDefaultHandler = defaultHandler;
    }


    public void uncaughtException(Thread thread, Throwable ex)
    {
      try {
        ExceptionHandler.writeStackTrace(ex);
      } catch (Exception e) {
        mDefaultHandler.uncaughtException(thread, e);
      }

      mDefaultHandler.uncaughtException(thread, ex);
    }
  }


  /***************************************************************************
   * Static data
   **/
  // Base dir for stack trace files.
  private static String smTraceDir;

  // List of stack trace files found at startup. We'll try to upload them.
  private static ConcurrentLinkedQueue<String> smTraceFiles
    = new ConcurrentLinkedQueue<String>();

  // API key, API Url.
  private static String smAPIKey;
  private static String smAPIUrl;

  // Other fields initialized in register()
  private static String smVersion;
  private static String smPackageName;
  private static String smPhoneModel;
  private static String smAndroidVersion;

  // Http client
  private static DefaultHttpClient            sClient;
  private static ThreadSafeClientConnManager  sConnectionManager;



  /***************************************************************************
   * Implementation
   **/

  /**
   * Register handler for unhandled exceptions. Returns true if stack traces
   * from a previous run were found, false otherwise.
   **/
  public static boolean register(Context context, String apiUrl, String apiKey)
  {
    // Remember parameters for later.
    smAPIUrl = apiUrl;
    smAPIKey = apiKey;

    // Initialize static variables.
    smTraceDir = context.getFilesDir().getAbsolutePath();
    smPackageName = context.getPackageName();
    smPhoneModel = Build.MODEL;
    smAndroidVersion = Build.VERSION.RELEASE;

    PackageManager pm = context.getPackageManager();
    try {
      PackageInfo pi = pm.getPackageInfo(smPackageName, 0);
      smVersion = pi.versionName;
    } catch (PackageManager.NameNotFoundException ex) {
      // Ignore... this can't happen, because we're using our own package name.
      android.util.Log.e(LTAG, "Unreachable line reached: " + ex.getMessage());
    }

    boolean result = false;

    // Find stack traces.
    File traceDir = new File(smTraceDir);
    traceDir.mkdirs();
    FilenameFilter filter = new FilenameFilter() {
      public boolean accept(File dir, String name)
      {
        return name.endsWith(TRACE_EXT);
      }
    };
    List<String> files = new LinkedList<String>();
    for (String str : traceDir.list(filter)) {
      files.add(str);
    }
    smTraceFiles.addAll(files);

    if (0 < files.size()) {
      result = true;
    }

    // Start a new thread for the bulk of the work.
    Thread th = new Thread() {
      @Override
      public void run()
      {
        // First, try to submit all stack traces we've found.
        submitStackTraces();

        // Next, install exception handler.
        UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (defaultHandler instanceof ExceptionHandler) {
          android.util.Log.w(LTAG, "Exception handler already registered.");
          return;
        }

        Thread.setDefaultUncaughtExceptionHandler(
            new ExceptionHandlerProxy(defaultHandler));
        android.util.Log.i(LTAG, "Exception handler registered.");
      }
    };
    th.start();

    return result;
  }



  public static void writeStackTrace(Throwable ex) throws Exception
  {
    writeStackTrace(ex, null, null);
  }



  public static void writeStackTrace(Throwable ex, String tag, String message) throws Exception
  {
    // Generate file name. It's a hash over the current time concatenated with
    // a random number.
    String uniqueName = null;
    Random generator = new Random();
    MessageDigest m = MessageDigest.getInstance("SHA-1");
    m.update(String.format("%d:%d", System.currentTimeMillis(),
          generator.nextInt(99999)).getBytes());
    uniqueName = new BigInteger(1, m.digest()).toString(16);
    String filename = String.format("%s%s%s%s", smTraceDir, File.separator,
        uniqueName, TRACE_EXT);


    // Serialize stack trace.
    final StringWriter result = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(result);
    ex.printStackTrace(printWriter);

    // Ensure directory exists.
    File d = new File(smTraceDir);
    d.mkdirs();

    // Write file.
    String lineSep = System.getProperty("line.separator");

    BufferedWriter writer = new BufferedWriter(new FileWriter(filename),
        BUFSIZE);
    writer.write(String.format("package_name:%s%s", smPackageName, lineSep));
    writer.write(String.format("package_version:%s%s", smVersion, lineSep));
    writer.write(String.format("phone_model:%s%s", smPhoneModel, lineSep));
    writer.write(String.format("android_version:%s%s", smAndroidVersion, lineSep));
    if (null != tag && null != message) {
      writer.write(String.format("tag:%s%s", tag, lineSep));
      writer.write(String.format("message:%s%s",
            Base64.encodeBytes(message.getBytes()), lineSep));
    }
    writer.write(String.format("trace:%s%s",
          Base64.encodeBytes(result.toString().getBytes()), lineSep));
    writer.flush();
    writer.close();
  }



  private static HashMap<String, String> readStackTrace(String filename)
  {
    try {
      HashMap<String, String> retval = new HashMap<String, String>();

      BufferedReader input = new BufferedReader(new FileReader(filename),
          BUFSIZE);
      String line = null;
      while (null != (line = input.readLine())) {
        int sepPos = line.indexOf(PARAM_SEP);
        if (-1 == sepPos) {
          android.util.Log.e(LTAG, "Could not parse trace line: " + line);
          continue;
        }
        String key = line.substring(0, sepPos);
        String value = line.substring(sepPos + 1);
        retval.put(key, value);
      }
      input.close();

      return retval;
    } catch (FileNotFoundException ex) {
      // Ignore. This happens if the file has already been processed by another
      // handler in the same process.
    } catch (IOException ex) {
      android.util.Log.e(LTAG, "IO Exception: " + ex.getMessage());
    }
    return null;
  }



  private static void submitStackTraces()
  {
    List<String> traceFiles = new LinkedList<String>();
    traceFiles.addAll(smTraceFiles);
    smTraceFiles.clear();

    // Create connection manager/client.
    if (null == sConnectionManager || null == sClient) {
      // Set up an HttpClient instance that can be used by multiple threads
      HttpParams params = new BasicHttpParams();
      HttpConnectionParams.setConnectionTimeout(params, HTTP_TIMEOUT);
      HttpProtocolParams.setVersion(params, HTTP_VERSION);

      SchemeRegistry registry = new SchemeRegistry();
      registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(),
            80));
      registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(),
            443));

      sConnectionManager = new ThreadSafeClientConnManager(params, registry);
      sClient = new DefaultHttpClient(sConnectionManager, params);
    }

    // Post files one by one.
    for (String filename : traceFiles) {
      String filePath = String.format("%s%s%s", smTraceDir, File.separator,
        filename);
      // android.util.Log.d(LTAG, "Uploading " + filePath);

      boolean delete = false;

      HashMap<String, String> trace = readStackTrace(filePath);
      if (null == trace) {
        android.util.Log.e(LTAG, "Could not read " + filename + ", skipping.");
        delete = true;
      }
      else {
        LinkedList<NameValuePair> params = new LinkedList<NameValuePair>();
        for (HashMap.Entry<String, String> entry : trace.entrySet()) {
          String key = entry.getKey();
          params.add(new BasicNameValuePair(key, entry.getValue()));
        }
        params.add(new BasicNameValuePair("api_key", smAPIKey));

        HttpPost post = new HttpPost(smAPIUrl);
        try {
          post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
          sClient.execute(post);
          delete = true;
        } catch (UnsupportedEncodingException ex) {
          android.util.Log.e(LTAG, "Fatal: " + ex.getMessage());
          break;
        } catch (IOException ex) {
          android.util.Log.e(LTAG, "IO Exception while transmitting, trying again next time.");
        }
      }

      if (delete) {
        File f = new File(filePath);
        f.delete();
      }
    }
  }
}

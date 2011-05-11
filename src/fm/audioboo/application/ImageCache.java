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

import android.content.Context;

import android.net.Uri;

import android.os.Handler;
import android.os.Message;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import android.content.ContentValues;

import android.provider.BaseColumns;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;

import java.util.LinkedList;

import java.lang.ref.WeakReference;

import de.unwesen.web.stacktrace.ExceptionHandler;
import de.unwesen.web.stacktrace.ExceptionHandler.Log;

/**
 * Cache for images downloaded for Boos/Users. Limits the cache contents to a
 * certain number of least recently used items.
 **/
public class ImageCache extends SQLiteOpenHelper
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "ImageCache";

  // Database file & version
  private static final String DATABASE_NAME     = "imagecache.db";
  private static final int DATABASE_VERSION     = 1;

  // Table names
  private static final String CACHE_TABLE       = "cache";

  // Fields for the cache table
  private static final String _ID               = BaseColumns._ID;
  private static final String DIMENSIONS        = "dimensions";
  private static final String ATIME             = "atime";
  private static final String DATA              = "data";

  // Chunk size for converting a Bitmap to a compressed byte stream
  private static final int COMPRESS_CHUNK_SIZE  = 8192;


  /***************************************************************************
   * Public constants
   **/
  // Messages sent in response to fetch requests. msg.obj is always the
  // original CacheItem, with mBitmap either null or set to the expected
  // result.
  public static final int MSG_OK          = 0;
  public static final int MSG_ERROR       = 1;
  public static final int MSG_CANCELLED   = 2;



  /***************************************************************************
   * Holds relevant data for each cache item.
   **/
  public static class CacheItem
  {
    public Uri        mImageUri;
    public int        mDimensions;
    public Object     mBaton;
    public String     mCacheKey;

    public Bitmap     mBitmap;

    public CacheItem(Uri uri, int dimensions, Object baton)
    {
      mImageUri = uri;
      mDimensions = dimensions;
      mBaton = baton;
      mCacheKey = null;
    }



    public CacheItem(Uri uri, int dimensions, Object baton, String cacheKey)
    {
      mImageUri = uri;
      mDimensions = dimensions;
      mBaton = baton;
      mCacheKey = cacheKey;
    }



    public String getCacheKey()
    {
      if (null != mCacheKey) {
        return mCacheKey;
      }
      return mImageUri.toString();
    }
  }


  /***************************************************************************
   * Fetcher thread
   **/
  private class Fetcher extends Thread
  {
    public boolean keepRunning = true;

    private LinkedList<CacheItem> mItems;
    private Handler               mHandler;

    public Fetcher(LinkedList<CacheItem> items, Handler handler)
    {
      super();
      mItems = items;
      mHandler = handler;
    }



    @Override
    public void run()
    {
      // Download items
      while (keepRunning && 0 < mItems.size()) {
        CacheItem item = mItems.remove();
        // Look up in cache again. It's possible that the previous download
        // fetched the same image
        Bitmap b = get(item.getCacheKey(), item.mDimensions);
        if (null != b) {
          item.mBitmap = b;
          mHandler.obtainMessage(MSG_OK, item).sendToTarget();
        }
        else {
          // Apparently it didn't, so fetch the item.
          processItem(item, mHandler);
        }
      }

      // If we've been cancelled, then send appropriate messages.
      while (!keepRunning && 0 < mItems.size()) {
        CacheItem item = mItems.remove();
        mHandler.obtainMessage(MSG_CANCELLED, item).sendToTarget();
      }

      // Lastly, clear the cache of it's least recently used items. Note that
      // this may well be interrupted again, and it can't be relied on that
      // this gets to finish every time downloads end or are cancelled.
      clearLRU();
    }
  }



  /***************************************************************************
   * Data members
   **/
  // Context in which the cache is run
  private WeakReference<Context>  mContext;

  // Number of items.
  private int         mCacheMax;

  // Current fetcher thread
  private Fetcher     mFetcher;


  /***************************************************************************
   * SQLiteOpenHelper implementation
   **/
  ImageCache(Context context, int cacheMax)
  {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);

    mContext = new WeakReference<Context>(context);
    mCacheMax = cacheMax;
  }



  @Override
  public void onCreate(SQLiteDatabase db)
  {
    Log.i(LTAG, "Initializing database '" + DATABASE_NAME + "'...");

    db.beginTransaction();

    db.execSQL("CREATE TABLE IF NOT EXISTS " + CACHE_TABLE + " ("
          + _ID             + " TEXT PRIMARY KEY, "
          + DIMENSIONS      + " INTEGER NOT NULL, "
          + ATIME           + " INTEGER NOT NULL, "
          + DATA            + " BLOB NOT NULL"
        + ");");

    db.setTransactionSuccessful();
    db.endTransaction();

    Log.i(LTAG, "Database initialized.");
  }



  @Override
  public void onOpen(SQLiteDatabase db)
  {
    db.setLockingEnabled(true);
  }



  private void clearDatabase(SQLiteDatabase db)
  {
    db.execSQL("DROP TABLE IF EXISTS " + CACHE_TABLE);
  }



  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
  {
    if (newVersion > oldVersion) {
      // TODO: when the first changes to the DB scheme are made, we need to
      //       perform a proper upgrade here.
    }
    else {
      // Kill and recreate
      clearDatabase(db);
      onCreate(db);
    }
  }



  /***************************************************************************
   * Cache implementation
   **/

  /**
   * Returns a Bitmap instance for the image file at the given Uri, or null if
   * it doesn't exist in the cache.
   * The second parameter specifies the dimensions (width AND height) of the
   * image. If the image is available at different sizes, that's not taken into
   * account, the function still returns null.
   **/
  public Bitmap get(Uri uri, int dimensions)
  {
    return get(uri.toString(), dimensions);
  }

  public Bitmap get(String cacheKey, int dimensions)
  {
    SQLiteDatabase db = getReadableDatabase();

    // Query the cache.
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(CACHE_TABLE);

    Cursor c = qb.query(db,
        new String[] { DATA },
        _ID + " = ? "
          + "AND " + DIMENSIONS + " = ?",
        new String[] {
          cacheKey,
          String.valueOf(dimensions)
        },
        null, null, null);

    if (null == c) {
      return null;
    }
    if (0 == c.getCount()) {
      c.close();
      return null;
    }


    // Read raw data from the database
    c.moveToFirst();
    byte[] data = c.getBlob(c.getColumnIndex(DATA));
    c.close();

    if (null == data) {
      return null;
    }

    // XXX So we don't update the atime. Doing so inline (i.e. in the main UI
    // thread) does actually degrade scroll performance by quite a bit. That
    // effectively turns the LRU mechanic into a least recently downloaded
    // mechanic.
    // The upshot is that images that are displayed a lot *will* be cleared
    // from the cache and downloaded again from time to time.
    // There are three choices:
    // a) Live with the slight stuttering, and use the code commented out below.
    // b) Live with the re-downloading, and leave things as they are.
    //    The effects of this can be alleviated by choosing a relatively large
    //    cache size. Currently, the cache size is at 200 items, when the
    //    recent boos list only contains 25 items. Assuming no other images
    //    are downloaded and cached, 50 items would be enough to avoid
    //    deletion and re-downloading of images displayed in the list. That
    //    should be good enough, and is the current state of the code.
    // c) Implement more complex code for updating ATIME in a background thread.

//    // Update atime.
//    ContentValues values = new ContentValues();
//    values.put(ATIME, System.currentTimeMillis());
//    db.update(CACHE_TABLE, values,
//        _ID + " = ?"
//          + "AND " + DIMENSIONS + " = ?",
//        new String[] {
//          imageUrl.toString(),
//          String.valueOf(dimensions),
//        });

    // Convert the data to a Bitmap instance, and return
    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    return bitmap;
  }



  /**
   * Fetch an image from the web, and place it into the cache. If the image is
   * larger or smaller than the specified dimensions, scale it to fit in the
   * dimensions, and also place the scaled version into the cache.
   * It's more efficient to specify a list of images rather than each individual
   * one here. The result handler is called for each individual result.
   **/
  public void fetch(LinkedList<CacheItem> uris, Handler resultHandler)
  {
    if (null != mFetcher) {
      cancelFetching();
    }

    mFetcher = new Fetcher(uris, resultHandler);
    mFetcher.start();
  }



  /**
   * Fetch item either via API or filesystem
   **/
  private void processItem(CacheItem item, Handler resultHandler)
  {
    byte[] data = null;

    if ("file".equals(item.mImageUri.getScheme())) {
      // Load data manually
      File f = new File(item.mImageUri.getPath());
      if (!f.exists() || !f.canRead()) {
        Log.e(LTAG, "File specified by URI '" + item.mImageUri + "' does not exist or is not readable.");
        return;
      }

      try {
        FileInputStream fis = new FileInputStream(f);
        data = API.readStreamRaw(fis);
      } catch (IOException ex) {
        Log.e(LTAG, "Read error when reading '" + item.mImageUri + "': " + ex.getMessage());
      }
    }
    else {
      // Delegate to API
      data = Globals.get().mAPI.fetchRawSynchronous(item.mImageUri, resultHandler);
    }

    // Process results
    if (null != data) {
      processItemResult(item, resultHandler, data);
    }
  }



  /**
   * Process an item's result, writing it into cache and scaling it, etc.
   **/
  private void processItemResult(CacheItem item, Handler resultHandler, byte[] data)
  {
    // Log.d(LTAG, "Got: " + item.mImageUri);

    // Great. First, create a Bitmap from the raw data. We'll need that to
    // determine the file's dimensions, both for storing in the DB and for
    // figuring out whether we need to scale it.
    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
    if (null == bitmap) {
      Log.e(LTAG, "Image response was corrupt: " + item.mImageUri);
      resultHandler.obtainMessage(MSG_ERROR, item).sendToTarget();
      return;
    }

    // Store raw image.
    int dimensions = Math.max(bitmap.getWidth(), bitmap.getHeight());
    storeImage(item.getCacheKey(), bitmap, dimensions);

    // If the dimensions are other than the requested one, scale the image
    // up/down.
    Bitmap scaled_bitmap = bitmap;
    if (dimensions != item.mDimensions) {
      float factor = ((float) item.mDimensions) / dimensions;

      int new_width = (int) (bitmap.getWidth() * factor);
      int new_height = (int) (bitmap.getHeight() * factor);
      try {
        scaled_bitmap = Bitmap.createScaledBitmap(bitmap, new_width, new_height, true);
        bitmap.recycle();

        if (null == scaled_bitmap) {
          Log.e(LTAG, "Unable to scale bitmap: " + item.mImageUri);
          resultHandler.obtainMessage(MSG_ERROR, item).sendToTarget();
          return;
        }
      } catch (OutOfMemoryError ex) {
        bitmap.recycle();
        Log.e(LTAG, "Out of memory, can't scale bitmap: " + item.mImageUri);
        resultHandler.obtainMessage(MSG_ERROR, item).sendToTarget();
        return;
      } catch (IllegalArgumentException ex) {
        bitmap.recycle();
        Log.e(LTAG, "Illegal argument: " + item.mImageUri + " @ " + new_width + "x" + new_height);
        resultHandler.obtainMessage(MSG_ERROR, item).sendToTarget();
        return;
      }

      // Store scaled image with the target dimensions
      storeImage(item.getCacheKey(), scaled_bitmap, item.mDimensions);
    }

    // We can send the scaled image on to the caller now.
    item.mBitmap = scaled_bitmap;
    resultHandler.obtainMessage(MSG_OK, item).sendToTarget();
  }



  /**
   * Cancels all downloads currently in progress. Handlers waiting for results
   * will receive a MSG_CANCELLED message.
   **/
  public void cancelFetching()
  {
    if (null == mFetcher) {
      return;
    }

    mFetcher.keepRunning = false;
    mFetcher.interrupt();
    mFetcher = null;
  }



  /**
   * Stores a bitmap's data in the cache database.
   **/
  private void storeImage(String cacheKey, Bitmap bitmap, int dimensions)
  {
    // First, we have to convert the Bitmap into a byte representation, for
    // which it'll need to be compressed.
    byte[] data = null;
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream(COMPRESS_CHUNK_SIZE);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
      data = os.toByteArray();
    } catch (OutOfMemoryError ex) {
      Log.e(LTAG, "Out of memory, cannot store bitmap: " + cacheKey);
      return;
    }

    // Now store it in the database.
    ContentValues values = new ContentValues();
    values.put(_ID, cacheKey);
    values.put(DIMENSIONS, dimensions);
    values.put(ATIME, System.currentTimeMillis());
    values.put(DATA, data);

    // Write into db
    SQLiteDatabase db = getWritableDatabase();
    try {
      db.insertOrThrow(CACHE_TABLE, _ID, values);
    } catch (SQLiteConstraintException ex) {
      db.update(CACHE_TABLE, values, _ID + " = ?",
          new String[] { values.getAsString(_ID) });
    }
  }



  /**
   * Clears the database of it's least recently used items.
   **/
  private void clearLRU()
  {
    SQLiteDatabase db = getReadableDatabase();

    // We'll try and keep the LRU logic simple: first determine the number of
    // items in the cache right now.
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(CACHE_TABLE);

    Cursor c = qb.query(db,
        new String[] { _ID, DIMENSIONS },
        null, null,
        null, null,
        ATIME + " ASC");

    if (null == c) {
      return;
    }

    int numberOfItems = c.getCount();
    if (0 == numberOfItems || mCacheMax >= numberOfItems) {
      // We're done.
      c.close();
      return;
    }

    // The number of items to delete is the difference between the current
    // count and the maximum count.
    int diff = numberOfItems - mCacheMax;

    // Need to determine the combination of _ID and DIMENSIONS for the
    // deletion candidates. Luckily, our cursor is already ordered properly.
    db.beginTransaction();

    c.moveToFirst();
    for (int i = 0 ; i < diff ; ++i, c.moveToNext()) {
      String id = c.getString(c.getColumnIndex(_ID));
      int dimensions = c.getInt(c.getColumnIndex(DIMENSIONS));

      db.delete(CACHE_TABLE,
        _ID + " = ? "
          + " AND " + DIMENSIONS + " = ? ",
        new String[] {
          id,
          String.valueOf(dimensions),
      });
    }
    c.close();

    db.setTransactionSuccessful();
    db.endTransaction();
  }
}

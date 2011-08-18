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

import android.net.Uri;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Date;
import java.util.LinkedList;
import java.util.Comparator;

import fm.audioboo.jni.FLACStreamEncoder;
import fm.audioboo.jni.FLACStreamDecoder;

import fm.audioboo.data.BooData;
import fm.audioboo.data.BooLocation;
import fm.audioboo.data.Tag;
import fm.audioboo.data.User;

import fm.audioboo.service.Constants;

import android.util.Log;

/**
 * Representation of a Boo's data.
 **/
public class Boo
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG = "Boo";



  /***************************************************************************
   * Public constants
   **/
  // Magic mId values
  public static final int INVALID_BOO = -1;
  public static final int INTRO_BOO   = -42;



  /***************************************************************************
   * Comparators
   **/
  public static class RecordingDateComparator implements Comparator<Boo>
  {
    public int compare(Boo boo1, Boo boo2)
    {
      return boo1.mData.mRecordedAt.compareTo(boo2.mData.mRecordedAt);
    }


    public boolean equals(Object obj)
    {
      return (obj instanceof RecordingDateComparator);
    }
  }


  /***************************************************************************
   * Public constants
   **/
  // Comparator
  public static final RecordingDateComparator RECORDING_DATE_COMPARATOR = new RecordingDateComparator();
  // Serialized file extension.
  public static final String EXTENSION = ".boo";
  // Data dir extension
  public static final String DATA_EXTENSION = ".data";
  // Recording extension
  public static final String RECORDING_EXTENSION = ".rec";
  // Image file name
  public static final String IMAGE_FILE = "image.png";
  public static final String TEMP_IMAGE_FILE = "image.png";



  /***************************************************************************
   * Public data
   **/
  public BooData                mData = null;


  /***************************************************************************
   * Implementation
   **/
  public Boo()
  {
    mData = new BooData();
  }



  public Boo(BooData data)
  {
    mData = data;
  }



  /**
   * Copies members from other to this Boo
   **/
  public void copyFrom(Boo other)
  {
    mData.mId = other.mData.mId;
    mData.mTitle = other.mData.mTitle;
    mData.mUUID = other.mData.mUUID;
    mData.mDuration = other.mData.mDuration;
    mData.mTags = other.mData.mTags;
    mData.mUser = other.mData.mUser;
    mData.mRecordedAt = other.mData.mRecordedAt;
    mData.mUpdatedAt = other.mData.mUpdatedAt;
    mData.mUploadedAt = other.mData.mUploadedAt;
    mData.mLocation = other.mData.mLocation;
    mData.mHighMP3Url = other.mData.mHighMP3Url;
    mData.mImageUrl = other.mData.mImageUrl;
    mData.mDetailUrl = other.mData.mDetailUrl;
    mData.mFilename = other.mData.mFilename;
    mData.mRecordings = other.mData.mRecordings;
    mData.mPlays = other.mData.mPlays;
    mData.mComments = other.mData.mComments;
  }



  /**
   * Tries to construct a Boo from a file with the given filename.
   **/
  public static Boo constructFromFile(String filename)
  {
    File f = new File(filename);
    if (!f.exists()) {
      return null;
    }

    if (f.getName().equals(".nomedia")) {
      return null;
    }

    try {
      ObjectInputStream is = new ObjectInputStream(new FileInputStream(f));
      BooData boo = (BooData) is.readObject();
      is.close();

      if (null == boo.mUpdatedAt) {
        boo.mUpdatedAt = new Date(f.lastModified());
      }
      if (null == boo.mFilename) {
        boo.mFilename = filename;
      }
      if (null == boo.mRecordings && null != boo.mHighMP3Url && boo.mHighMP3Url.getScheme().equals("file")) {
        //Log.d(LTAG, "Purging old recording.");
        new File(boo.mHighMP3Url.getPath()).delete();
        return null;
      }

      return new Boo(boo);
    } catch (FileNotFoundException ex) {
      Log.e(LTAG, "File not found: " + filename);
    } catch (ClassNotFoundException ex) {
      Log.e(LTAG, "Class not found: " + filename);
      f.delete();
    } catch (IOException ex) {
      Log.e(LTAG, "Error reading file: " + filename);
      f.delete();
    }

    return null;
  }



  /**
   * Reloads a Boo from mFilename.
   **/
  public boolean reload()
  {
    Boo b = constructFromFile(mData.mFilename);
    if (null == b) {
      return false;
    }
    copyFrom(b);
    return true;
  }



  /**
   * Serializes the Boo class to a file specified by the given filename.
   **/
  public void writeToFile()
  {
    if (null == mData.mFilename) {
      throw new IllegalStateException("No filename set when attempting to save Boo.");
    }
    writeToFile(mData.mFilename);
  }



  public void writeToFile(String filename)
  {
    mData.mUpdatedAt = new Date();
    //Log.d(LTAG, "Writing to file: " + this + " - " + filename);
    //Thread.dumpStack();

    try {
      ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(new File(filename)));
      os.writeObject(mData);
      os.flush();
      os = null;
    } catch (FileNotFoundException ex) {
      Log.e(LTAG, "File not found: " + filename);
    } catch (IOException ex) {
      Log.e(LTAG, "Error writing file '" + filename + "': " + ex.getMessage());
    }
  }



  /**
   * Deletes the Boo file and it's data files.
   **/
  public boolean delete()
  {
    return Globals.get().getBooManager().deleteBoo(this);
  }



  /**
   * @see BooData.getDuration
   **/
  public double getDuration()
  {
    if (null == mData) {
      return 0;
    }
    return mData.getDuration();
  }



  /**
   * Returns a new Recording metadata object for use in recording Boos. If the
   * latest known Recording is empty (does not have a duration), that is
   * returned, otherwise a new Recording is created and registered.
   **/
  public BooData.Recording getLastEmptyRecording()
  {
    if (null == mData.mRecordings) {
      mData.mRecordings = new LinkedList<BooData.Recording>();
    }

    BooData.Recording rec = null;
    if (0 != mData.mRecordings.size()) {
      BooData.Recording r = mData.mRecordings.get(mData.mRecordings.size() - 1);
      if (0 == r.mDuration) {
        rec = r;
      }
    }

    if (null == rec) {
      rec = new BooData.Recording(
          Globals.get().getBooManager().getNewRecordingFilename(this));
      mData.mRecordings.add(rec);
    }

    return rec;
  }




  public String toString()
  {
    if (null == mData) {
      return null;
    }
    return mData.toString();
  }



  public String getImageFilename()
  {
    return Globals.get().getBooManager().getImageFilename(this);
  }



  public String getTempImageFilename()
  {
    return Globals.get().getBooManager().getTempImageFilename(this);
  }



  public boolean isLocal()
  {
    if (null == mData) {
      return false;
    }

    if (null != mData.mRecordings) {
      return true;
    }

    if (null == mData.mHighMP3Url) {
      // Assuming that the server *never* sends back data without a URL.
      return true;
    }

    String scheme = mData.mHighMP3Url.getScheme();
    if ("file".equals(scheme)) {
      return true;
    }

    return false;
  }



  public boolean isIntro()
  {
    if (null == mData) {
      return false;
    }

    if (null == mData.mRecordings && null == mData.mHighMP3Url) {
      return true;
    }

    return false;
  }



  public boolean isRemote()
  {
    if (null == mData) {
      return false;
    }

    if (null == mData.mHighMP3Url) {
      return false;
    }

    String scheme = mData.mHighMP3Url.getScheme();
    if ("http".equals(scheme) || "https".equals(scheme)) {
      return true;
    }

    return false;
  }



  public static Boo createIntroBoo(Context ctx)
  {
    BooData data = new BooData();
    data.mId = Boo.INTRO_BOO;
    data.mTitle = ctx.getResources().getString(R.string.intro_boo_title);
    data.mDuration = 100f; // XXX intro_boo.mp3 is *currently* 100s long

    data.mUser = new User();
    data.mUser.mUsername = ctx.getResources().getString(R.string.intro_boo_author);

    data.mRecordedAt = new Date(1295906201000l);

    data.mLocation = new BooLocation();
    data.mLocation.mDescription = ctx.getResources().getString(R.string.intro_boo_location);
    data.mLocation.mLatitude = 51.501033;
    data.mLocation.mLongitude = -0.078691;
    data.mLocation.mAccuracy = 0f;

    return new Boo(data);
  }



  // Flattens the list of audio files as returned by BooManager.getAudioFiles()
  // into a single flac file. XXX Warning, this function blocks.
  public void flattenAudio()
  {
    // Log.d(LTAG, "flattenAudio: " + this);
    // First, check if audio is already flattened. If that's the case, we don't
    // want to flatten everything again.
    if (null != mData.mHighMP3Url) {
      String filename = mData.mHighMP3Url.getPath();
      File high_f = new File(filename);
      if (!high_f.exists()) {
        mData.mHighMP3Url = null;
      }
      else {
        long latest = 0;
        for (BooData.Recording rec : mData.mRecordings) {
          File f = new File(rec.mFilename);
          long d = f.lastModified();
          if (d > latest) {
            latest = d;
          }
        }

        if (high_f.lastModified() > latest) {
          // This boo seems to be flattened already.
          return;
        }
        else {
          // This boo was flattened, but new recordings were made
          // afterwards. Delete the previously flattened file.
          high_f.delete();
          mData.mHighMP3Url = null;
        }
      }
    }

    // If we reached here, then we need to flatten the Boo again.
    String target = Globals.get().getBooManager().getNewRecordingFilename(this);

    // Flatten the audio files.
    FLACStreamEncoder encoder = null;

    for (BooData.Recording rec : mData.mRecordings) {
      //Log.d(LTAG, "Using recording: " + rec);
      FLACStreamDecoder decoder = null;
      try {
        decoder = new FLACStreamDecoder(rec.mFilename);
      } catch (IllegalArgumentException ex) {
        Log.e(LTAG, "Could not open recording file, skipping.");
        continue;
      }

      int bufsize = decoder.minBufferSize();
      //Log.d(LTAG, "bufsize is: " + bufsize);
      ByteBuffer buffer = ByteBuffer.allocateDirect(bufsize);

      while (true) {
        int read = decoder.read(buffer, bufsize);
        if (read <= 0) {
          break;
        }
        //Log.d(LTAG, "read: " + read);


        if (null == encoder) {
          // Assume that all recordings share the format of the first recording.
          encoder = new FLACStreamEncoder(target, decoder.sampleRate(),
              decoder.channels(), decoder.bitsPerSample());
        }

        encoder.write(buffer, read);
      }

      encoder.flush();
      decoder.release();
      decoder = null;
    }

    if (null != encoder) {
      encoder.release();
    }
    encoder = null;

    // Next, set the high mp3 Uri for the Boo to be the target path.
    mData.mHighMP3Url = Uri.parse(String.format("file://%s", target));
    //Log.d(LTAG, "Flattened to: " + mData.mHighMP3Url);

    // Right, persist this flattened URL
    writeToFile();
  }



  /**
   * Returns upload progress as a percentage, or a negative value if this
   * Boo is not being uploaded.
   **/
  public double uploadProgress()
  {
    if (null == mData || null == mData.mUploadInfo) {
      return -1;
    }

    int finished = mData.mUploadInfo.mAudioUploaded + mData.mUploadInfo.mImageUploaded;
    double total = mData.mUploadInfo.mAudioSize + mData.mUploadInfo.mImageSize;

    // Add some for metadata. The /4 is arbitrary, just because the chunk size
    // is fairly large and metadata isn't.
    total += Constants.MIN_UPLOAD_CHUNK_SIZE / 4;

    if (finished > total) {
      // This is exceedingly weird; it's so weird, that we best log an error and
      // return 100% progress.
      Log.e(LTAG, "More uploaded than existed: " + finished + " > " + total);
      return 100.0;
    }

    return (finished / total) * 100;
  }
}

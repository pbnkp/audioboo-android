/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import org.apache.http.entity.mime.content.AbstractContentBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.mime.MIME;

import java.security.MessageDigest;

import android.util.Log;

/**
 * Similar to FileBody from org.apache, but you specify parts of a file, via an
 * offset and size.
 **/
public class FilePartBody extends AbstractContentBody
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "FilePartBody";



  /***************************************************************************
   * Private data
   **/
  private File  mFile;
  private long  mOffset;
  private long  mSize;



  /***************************************************************************
   * Implementation
   **/
  public FilePartBody(File file)
  {
    this(file, 0, -1);
  }


  public FilePartBody(File file, long offset, long size)
  {
    super("application/octet-stream");

    if (null == file) {
      throw new IllegalArgumentException("File cannot be null.");
    }

    long max = file.length() - offset;
    if (offset < 0 || max < 0) {
      throw new IllegalArgumentException("Offset parameter is invalid.");
    }

    if (size < 0 || max < size) {
      Log.i(LTAG, "Size exceeds file size or is -1, setting from " + size + " to " + max);
      mSize = max;
    }
    else {
      mSize = size;
    }

    mFile = file;
    mOffset = offset;
  }



  public void writeTo(final OutputStream out) throws IOException
  {
    consume(new Consumer() {
        public void consume(byte[] data, int size) throws IOException
        {
          out.write(data, 0, size);
        }
    });
    out.flush();
  }



  public String getCharset()
  {
    return null;
  }



  public String getTransferEncoding()
  {
    return MIME.ENC_BINARY;
  }



  public long getContentLength()
  {
    return mSize;
  }



  public String getFilename()
  {
    return mFile.getAbsolutePath();
  }



  public void updateHash(final MessageDigest digest)
  {
    try {
      consume(new Consumer() {
          public void consume(byte[] data, int size) throws IOException
          {
            digest.update(data, 0, size);
          }
      });
    } catch (IOException ex) {
      Log.e(LTAG, "Got exception: " + ex);
    }
  }


  /***************************************************************************
   * Helpers for consuming part of the file
   **/
  private static interface Consumer
  {
    public void consume(byte[] data, int size) throws IOException;
  }

  private void consume(Consumer con) throws IOException
  {
    // Log.d(LTAG, "consume: " + con);
    InputStream in = new FileInputStream(mFile);
    long skipped = in.skip(mOffset);
    if (skipped != mOffset) {
      throw new IOException("Seek failed!");
    }

    try {
      byte[] buf = new byte[8192];
      long remaining = mSize;
      do {
        int read = in.read(buf, 0, (int) Math.min(remaining, buf.length));
        if (read <= 0) {
          break;
        }
        // Log.d(LTAG, "read: " + read);
        con.consume(buf, read);
        remaining -= read;
      } while (true);
    } finally {
      in.close();
    }
    // Log.d(LTAG, "done");
  }
}

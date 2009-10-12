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

import android.content.Intent;

import android.view.View;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;

import android.content.res.Configuration;

import android.location.Location;

import android.provider.MediaStore;
import android.net.Uri;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

import java.util.List;
import java.util.LinkedList;

import android.util.Log;

import fm.audioboo.widget.EditTags;

/**
 * Contains code necessary for uploading Boos.
 **/
public class PublishActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "PublishActivity";

  // Base file name for the image file
  private static final String IMAGE_NAME        = "current_image.png";

  // Maximum image size.
  private static final int    IMAGE_MAX_SIZE    = 300;


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_FILENAME = "fm.audioboo.extras.boo-filename";


  /***************************************************************************
   * Data
   **/
  // Filename and Boo data.
  private String  mBooFilename;
  private Boo     mBoo;

  // Request code, for figuring out which Intent responses we need to ignore.
  private int     mRequestCode;


  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    // Grab extras
    Bundle extras = getIntent().getExtras();
    if (null == extras) {
      throw new IllegalArgumentException("Intent needs to define the '"
          + EXTRA_BOO_FILENAME + "' extra.");
    }

    // See if we've been passed a Media instance.
    String filename = extras.getString(EXTRA_BOO_FILENAME);
    Boo boo = Boo.constructFromFile(filename);
    if (null == boo) {
      throw new IllegalArgumentException("Boo file '" + filename + "' could "
          + "not be loaded.");
    }
    mBoo = boo;
    mBooFilename = filename;
    Log.d(LTAG, "Boo: " + mBoo);


    // Create view, etc.
    setContentView(R.layout.publish);
    setTitle(getResources().getString(R.string.publish_activity_title));

    // Hook up the buttons.
    Button submit = (Button) findViewById(R.id.publish_submit);
    if (null != submit) {
      submit.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v)
        {
          onSubmit();
        }
      });
    }

    ImageButton image = (ImageButton) findViewById(R.id.publish_image);
    if (null != image) {
      image.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v)
        {
          onEditImage();
        }
      });
    }
  }



  @Override
  public void onStart()
  {
    super.onStart();
  }



  @Override
  public void onPause()
  {
    super.onPause();

    // TODO write mBoo to mBooFilename. Let's us resume editing, to a degree.
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  private void onSubmit()
  {
    // Hide form, and show progress view.
    View view = findViewById(R.id.publish_form);
    view.setVisibility(View.GONE);
    view = findViewById(R.id.publish_progress);
    view.setVisibility(View.VISIBLE);

    // Grab Boo title.
    EditText edit_text = (EditText) findViewById(R.id.publish_title);
    if (null != edit_text) {
      mBoo.mTitle = edit_text.getText().toString();
    }
    if (null == mBoo.mTitle || 0 == mBoo.mTitle.length()) {
      // TODO if mTitle is not set, use the hint.
      mBoo.mTitle = "Android Boo";
    }

    // Grab tags
    EditTags tags_view = (EditTags) findViewById(R.id.publish_tags);
    if (tags_view != null) {
      List<String> tags = tags_view.getTags();
      if (null != tags) {
        mBoo.mTags = new LinkedList<Tag>();
        for (String tag : tags) {
          Tag t = new Tag();
          t.mNormalised = tag;
          mBoo.mTags.add(t);
        }
      }
    }

    // Make a last ditch attempt to get a Location for the Boo, if necessary.
    if (null == mBoo.mLocation) {
      Location loc = Globals.get().mLocation;
      if (null != loc) {
        mBoo.mLocation = new BooLocation(this, Globals.get().mLocation);
      }
    }

    Globals.get().mAPI.uploadBoo(mBoo, new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          onUploadSucceeded((String) msg.obj);
        }
        else if (API.ERR_API_ERROR == msg.what) {
          // FIXME
          API.APIException ex = (API.APIException) msg.obj;
          Log.d(LTAG, "API ERROR: " + ex.getMessage() + " " + ex.getCode());
          // onUploadError(msg.what, (String) msg.obj);
        } else {
          onUploadError(msg.what, (String) msg.obj);
        }
        return true;
      }
    }));
  }



  private void onEditImage()
  {
    Intent i = new Intent(Intent.ACTION_PICK,
        MediaStore.Images.Media.INTERNAL_CONTENT_URI);
    startActivityForResult(i, ++mRequestCode);
  }



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (mRequestCode != requestCode) {
      Log.d(LTAG, "Ignoring result for requestCode: " + requestCode);
      return;
    }

    if (Activity.RESULT_CANCELED == resultCode) {
      Log.d(LTAG, "Ignore cancelled.");
      return;
    }

    // Fetch the data the user picked.
    Uri uri = data.getData();
    Bitmap bm = null;
    try {
      bm = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
    } catch (java.io.FileNotFoundException ex) {
      Log.e(LTAG, "That's odd... the user picked a file that doesn't exist: "
          + ex.getMessage());
      return;
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Error when reading bitmap: " + ex.getMessage());
      return;
    }

    // First scale the bitmap, if necessary. Unfortunately that's more complex
    // than cropping it first, but necessary as to avoid excessive memory usage.
    int max = Math.max(bm.getWidth(), bm.getHeight());
    if (max > IMAGE_MAX_SIZE) {
      float factor = (float) IMAGE_MAX_SIZE / max;
      int scaled_width = (int) (bm.getWidth() * factor);
      int scaled_height = (int) (bm.getHeight() * factor);
      Bitmap new_bm = Bitmap.createScaledBitmap(bm, scaled_width, scaled_height, true);
      if (null != new_bm) {
        bm.recycle();
        bm = new_bm;
      }
    }

    // Crop the bitmap. We'll assume the smaller of the width and height to be
    // the desired value for both, and cut out the middle from the longer
    // dimension.
    int cropped = Math.min(bm.getWidth(), bm.getHeight());
    int x = (bm.getWidth() - cropped) / 2;
    int y = (bm.getHeight() - cropped) / 2;
    Bitmap new_bm = Bitmap.createBitmap(bm, x, y, cropped, cropped);
    if (null != new_bm) {
      bm.recycle();
      bm = new_bm;
    }

    // Write cropped image to storage.
    String filename = getImageFilename();
    boolean write_success = false;
    try {
      FileOutputStream os = new FileOutputStream(new File(filename));
      if (!bm.compress(Bitmap.CompressFormat.PNG, 100, os)) {
        Log.e(LTAG, "Failed to write image file!");
      }
      write_success = true;
    } catch (java.io.FileNotFoundException ex) {
      Log.e(LTAG, "Could not write image file: " + ex.getMessage());
    } finally {
      bm.recycle();
      if (!write_success) {
        return;
      }
    }

    // Treat the image written just now as the Boo image.
    setImageFile(filename);
  }



  private void setImageFile(String filename)
  {
    mBoo.mImageUrl = Uri.parse(String.format("file://%s", filename));

    ImageButton image = (ImageButton) findViewById(R.id.publish_image);
    if (null != image) {
      image.setImageBitmap(BitmapFactory.decodeFile(filename));
    }
  }



  private String getImageFilename()
  {
    String filename = Globals.get().getBasePath() + File.separator + IMAGE_NAME;
    File f = new File(filename);
    f.getParentFile().mkdirs();

    return filename;
  }



  private void onUploadSucceeded(String booId)
  {
    Log.d(LTAG, "Boo uploaded to: " + booId);

    // TODO delete boo, or signal recording boo that that should reset.

    finish();
  }



  private void onUploadError(int code, String message)
  {
    // FIXME want a popup
    Log.e(LTAG, "Error uploading boo: " + code + " " + message);
  }
}

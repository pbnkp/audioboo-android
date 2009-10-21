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

import android.content.DialogInterface;
import android.app.Dialog;

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

  // Maximum image size, in pixels. Images are assumed to be square.
  private static final int IMAGE_MAX_SIZE       = 300;

  // Dialog IDs.
  private static final int DIALOG_ERROR         = Globals.DIALOG_ERROR;


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_FILENAME = "fm.audioboo.extras.boo-filename";


  /***************************************************************************
   * Data
   **/
  // Filename and Boo data.
  private String            mBooFilename;
  private Boo               mBoo;

  // Request code, for figuring out which Intent responses we need to ignore.
  private int               mRequestCode;

  // Last error information - used and cleared in onCreateDialog
  private int               mErrorCode = -1;
  private API.APIException  mException;



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
    // Log.d(LTAG, "Boo: " + mBoo);


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

    // Initialize UI. It's possible that the Boo already contains a lot
    // of information, i.e.
    // - Title
    // - Tags
    // - Image

    // Try to set the image, if it exists.
    if (null != mBoo.mImageUrl) {
      setImageFile(mBoo.mImageUrl.getPath());
    }

    // Try to set the title, if it exists.
    EditText edit_text = (EditText) findViewById(R.id.publish_title);
    if (null != edit_text) {
      if (null != mBoo.mTitle) {
        edit_text.setText(mBoo.mTitle);
      }
      edit_text.setHint(Globals.get().mTitleGenerator.getTitle());
    }

    // Try to set tags, if they're defined.
    if (null != mBoo.mTags) {
      EditTags tags_view = (EditTags) findViewById(R.id.publish_tags);
      if (null != tags_view) {
        tags_view.setTags(mBoo.mTags);
      }
    }

  }



  @Override
  public void onPause()
  {
    super.onPause();

    // Save our current progress. The nice part is that this lets us resume
    // editing, and the title might even be reflected in the recorder view.
    updateBoo(false);
    mBoo.writeToFile(mBooFilename);
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again.
    super.onConfigurationChanged(config);
  }



  /**
   * Helper function; grabs information from the UI's views, and stores it
   * into mBoo. The parameter specifies whether the title field's hint should
   * be used if the title is empty.
   **/
  private void updateBoo(boolean useHint)
  {
    // Grab Boo title.
    EditText edit_text = (EditText) findViewById(R.id.publish_title);
    if (null != edit_text) {
      String title = edit_text.getText().toString();
      if (null != title && 0 != title.length()) {
        mBoo.mTitle = title;
      }
      if (null == mBoo.mTitle && useHint) {
        mBoo.mTitle = edit_text.getHint().toString();
      }
    }

    // Grab tags
    EditTags tags_view = (EditTags) findViewById(R.id.publish_tags);
    if (null != tags_view) {
      mBoo.mTags = tags_view.getTags();
    }

    // Make a last ditch attempt to get a Location for the Boo, if necessary.
    if (null == mBoo.mLocation) {
      Location loc = Globals.get().mLocation;
      if (null != loc) {
        mBoo.mLocation = new BooLocation(this, Globals.get().mLocation);
      }
    }
  }


  private void onSubmit()
  {
    // Hide form, and show progress view.
    View view = findViewById(R.id.publish_form);
    view.setVisibility(View.GONE);
    view = findViewById(R.id.publish_progress);
    view.setVisibility(View.VISIBLE);

    // Update mBoo's information
    updateBoo(true);

    Globals.get().mAPI.uploadBoo(mBoo, new Handler(new Handler.Callback() {
      public boolean handleMessage(Message msg)
      {
        if (API.ERR_SUCCESS == msg.what) {
          onUploadSucceeded((Integer) msg.obj);
        }
        else {
          mErrorCode = msg.what;
          mException = (API.APIException) msg.obj;
          showDialog(DIALOG_ERROR);
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
      //Log.d(LTAG, "Ignoring result for requestCode: " + requestCode);
      return;
    }

    if (Activity.RESULT_CANCELED == resultCode) {
      //Log.d(LTAG, "Ignore cancelled.");
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



  private void onUploadSucceeded(int booId)
  {
    // Log.d(LTAG, "Boo uploaded to: " + booId);

    // Clean up after ourselves... delete image file.
    File f = new File(getImageFilename());
    if (f.exists()) {
      f.delete();
    }

    // Signal to caller that we successfully uploaded the Boo.
    setResult(Activity.RESULT_OK);
    finish();
  }



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_ERROR:
        dialog = Globals.get().createDialog(this, id, mErrorCode, mException,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                setResult(Activity.RESULT_CANCELED);
                PublishActivity.this.finish();
              }
            });
        mErrorCode = -1;
        mException = null;
        break;
    }

    return dialog;
  }
}

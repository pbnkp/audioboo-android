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

import android.app.Activity;

import android.os.Bundle;

import android.content.Intent;

import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.Resources;

import android.location.Location;

import android.provider.MediaStore;
import android.net.Uri;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.content.DialogInterface;
import android.app.Dialog;
import android.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;

import java.util.List;
import java.util.LinkedList;

import android.util.Log;

import fm.audioboo.widget.EditTags;

import fm.audioboo.data.BooLocation;
import fm.audioboo.data.Tag;
import fm.audioboo.data.UploadInfo;


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

  // Maximum image size, in pixels. Images are assumed to be square.
  private static final int IMAGE_MAX_SIZE       = 800;

  // Dialog IDs.
  private static final int DIALOG_IMAGE_OPTIONS = 1;

  // Image option IDs.
  private static final int IMAGE_OPT_CHOOSE     = 0;
  private static final int IMAGE_OPT_CREATE     = 1;
  private static final int IMAGE_OPT_REMOVE     = 2;


  /***************************************************************************
   * Public constants
   **/
  // Extra names
  public static final String EXTRA_BOO_FILENAME = "fm.audioboo.extras.boo-filename";

  // Result codes:
  // - RESULT_CANCELED  - nothing done.
  // - RESULT_OK        - edited.
  // - RESULT_PUBLISHED - in publishing queue.
  public static final int RESULT_PUBLISHED      = Activity.RESULT_FIRST_USER;


  /***************************************************************************
   * Data
   **/
  private Boo               mBoo;

  // Flag to indicate whether the Boo has been changed at all.
  private boolean           mBooChanged = false;



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.publish);
    setTitle(getResources().getString(R.string.publish_activity_title));

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
    // Log.d(LTAG, "Boo: " + mBoo);

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
          showDialog(DIALOG_IMAGE_OPTIONS);
        }
      });
    }
  }



  @Override
  public void onResume()
  {
    super.onResume();

    // Initialize UI. It's possible that the Boo already contains a lot
    // of information, i.e.
    // - Title
    // - Tags
    // - Image

    // Try to set the image, if it exists.
    if (null != mBoo.mData.mImageUrl) {
      setImageFile(mBoo.mData.mImageUrl.getPath());
    }

    // Try to set the title, if it exists.
    EditText edit_text = (EditText) findViewById(R.id.publish_title);
    if (null != edit_text) {
      if (null != mBoo.mData.mTitle) {
        edit_text.setText(mBoo.mData.mTitle);
      }
      edit_text.setHint(Globals.get().mTitleGenerator.getTitle());
    }

    // Try to set tags, if they're defined.
    if (null != mBoo.mData.mTags) {
      EditTags tags_view = (EditTags) findViewById(R.id.publish_tags);
      if (null != tags_view) {
        tags_view.setTags(mBoo.mData.mTags);
      }
    }
  }



  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {
    if (KeyEvent.KEYCODE_BACK == keyCode && 0 == event.getRepeatCount()) {
      onBackPressedManual();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }



  public void onBackPressedManual()
  {
    if (null == mBoo.mData.mUploadInfo) {
      // Not published yet! Save our current progress. The nice part is that
      // this lets us resume editing, and the title might even be reflected
      // in the recorder view.
      if (updateBoo(false)) {
        mBoo.writeToFile();
        setResult(Activity.RESULT_OK);
      }
      else {
        setResult(Activity.RESULT_CANCELED);
      }
    }

    finish();
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
  private boolean updateBoo(boolean useHint)
  {
    if (null == mBoo) {
      return false;
    }

    boolean retval = mBooChanged;

    // Grab Boo title.
    EditText edit_text = (EditText) findViewById(R.id.publish_title);
    if (null != edit_text) {
      String title = edit_text.getText().toString();
      if (null == title || 0 == title.length()) {
        if (useHint) {
          title = edit_text.getHint().toString();
        }
      }
      else {
        if (null == mBoo.mData.mTitle || !mBoo.mData.mTitle.equals(title)) {
          // Log.d(LTAG, "title changed");
          mBoo.mData.mTitle = title;
          retval = true;
        }
      }
    }

    // Grab tags
    EditTags tags_view = (EditTags) findViewById(R.id.publish_tags);
    if (null != tags_view) {
      List<Tag> tags = tags_view.getTags();
      if (tagsChanged(mBoo.mData.mTags, tags)) {
        // Log.d(LTAG, "tags changed");
        mBoo.mData.mTags = tags;
        retval = true;
      }
    }

    // Make a last ditch attempt to get a Location for the Boo, if necessary.
    if (null == mBoo.mData.mLocation) {
      Location loc = Globals.get().mLocation;
      if (null == mBoo.mData.mLocation && null != loc) {
        // Log.d(LTAG, "location changed");
        mBoo.mData.mLocation = new BooLocation(this, loc);
        retval = true;
      }
    }

    mBooChanged = retval;
    return retval;
  }



  private void onSubmit()
  {
    // Hide form, and show progress view.
    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
    View view = findViewById(R.id.publish_form);
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

    // Update mBoo's information
    updateBoo(true);

    // Mark this for uploading.
    mBoo.mData.mUploadInfo = new UploadInfo();
    mBoo.writeToFile();

    // Notify upload manage that there's stuff to do!
    Globals.get().mUploader.processQueue();

    // Show a toast
    Toast.makeText(this, R.string.publish_queued, Toast.LENGTH_LONG).show();

    // That's it, it's published!
    setResult(RESULT_PUBLISHED);
    finish();
  }



  private void onChooseImage()
  {
    Intent i = new Intent(Intent.ACTION_PICK,
        MediaStore.Images.Media.INTERNAL_CONTENT_URI);
    startActivityForResult(i, IMAGE_OPT_CHOOSE);
  }



  private void onCreateImage()
  {
    File f = new File(mBoo.getTempImageFilename());
    if (f.exists()) {
      f.delete();
    }

    Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    Uri uri = Uri.parse(String.format("file://%s", mBoo.getTempImageFilename()));
    i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
    i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

    startActivityForResult(i, IMAGE_OPT_CREATE);
  }



  private void onRemoveImage()
  {
    // Delete image file.
    File f = new File(mBoo.getImageFilename());
    f.delete();

    // Remove reference to image file.
    mBoo.mData.mImageUrl = null;

    // Reset image button.
    ImageButton image = (ImageButton) findViewById(R.id.publish_image);
    if (null != image) {
      image.setImageResource(R.drawable.anonymous_boo);
    }
  }



  private Bitmap fetchBitmap(Uri uri)
  {
    try {
      return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
    } catch (java.io.FileNotFoundException ex) {
      Log.e(LTAG, "That's odd... the user picked a file that doesn't exist: "
          + ex.getMessage());
      return null;
    } catch (java.io.IOException ex) {
      Log.e(LTAG, "Error when reading bitmap: " + ex.getMessage());
      return null;
    } catch (OutOfMemoryError ex) {
      Log.e(LTAG, "Error: bitmap too large: " + ex.getMessage());
      return null;
    }
  }



  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    if (Activity.RESULT_CANCELED == resultCode) {
      //Log.d(LTAG, "Ignore cancelled.");
      return;
    }

    Uri uri = null == data ? null : data.getData();
    String filename = mBoo.getImageFilename();
    Bitmap bm = null;

    switch (requestCode) {
      case IMAGE_OPT_CHOOSE:
        bm = fetchBitmap(uri);
        break;


      case IMAGE_OPT_CREATE:
        // Try to open the image file. If that works, we'll use it, because
        // that's going to be easiest.
        File image = new File(mBoo.getTempImageFilename());
        if (image.exists()) {
          bm = BitmapFactory.decodeFile(mBoo.getTempImageFilename());
          image.delete();
        }
        else {
          // Some phones (e.g. HTC Hero, see AND-23) don't write to the file
          // location provided in the Intent. Instead they return a content
          // URl in the result, so let's check for that.
          if (null == uri) {
            // Permanent error
            return;
          }

          // Right, read the Bitmap from where the Uri points to.
          ContentResolver cr = getContentResolver();
          try {
            bm = BitmapFactory.decodeStream(cr.openInputStream(uri));
          } catch (java.io.FileNotFoundException ex) {
            Log.e(LTAG, "Created file not found: " + ex.getMessage());
            return;
          }
        }
        break;
    }

    // Bail out if all of that failed.
    if (null == bm) {
      Log.e(LTAG, "Could not load bitmap, giving up.");
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
    mBooChanged = true;
  }



  private void setImageFile(String filename)
  {
    mBoo.mData.mImageUrl = Uri.parse(String.format("file://%s", filename));

    ImageButton image = (ImageButton) findViewById(R.id.publish_image);
    if (null != image) {
      image.setImageBitmap(BitmapFactory.decodeFile(filename));
    }
  }



  protected Dialog onCreateDialog(int id)
  {
    Resources res = getResources();

    switch (id) {
     case DIALOG_IMAGE_OPTIONS:
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Call these functions so that a) views are created, and b) we can set the
        // click handler easily.
        builder
          .setTitle(res.getString(R.string.publish_image_option_title))
          .setItems(new String[] { "dummy" },
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which)
              {
                switch (which) {
                  case IMAGE_OPT_CHOOSE:
                    onChooseImage();
                    break;

                  case IMAGE_OPT_CREATE:
                    onCreateImage();
                    break;

                  case IMAGE_OPT_REMOVE:
                    onRemoveImage();
                    break;

                  default:
                    Log.e(LTAG, "Unknown image option: " + which);
                    break;
                }
              }
          });
        return builder.create();
    }

    return null;
  }



  protected void onPrepareDialog(int id, Dialog dialog)
  {
    Resources res = getResources();

    switch (id) {
      case DIALOG_IMAGE_OPTIONS:
        AlertDialog ad = (AlertDialog) dialog;

        // Grab option array from resources.
        String[] raw_opts = res.getStringArray(R.array.publish_image_options);

        // Hide "remove" option if no image is set yet.
        String[] opts = raw_opts;
        if (null == mBoo.mData.mImageUrl) {
          opts = new String[2];
          opts[0] = raw_opts[0];
          opts[1] = raw_opts[1];
        }

        // Populate the dialog's list view.
        final ListView list = ad.getListView();
        list.setAdapter(new ArrayAdapter<CharSequence>(this,
            android.R.layout.select_dialog_item, android.R.id.text1, opts));
    }
  }



  private boolean tagsChanged(List<Tag> first, List<Tag> second)
  {
    if (null == second) {
      return false;
    }

    if (null == first) {
      if (second.isEmpty()) {
        return false;
      }
      return true;
    }

    if (first.size() != second.size()) {
      return true;
    }

    for (int i = 0 ; i < first.size() ; ++i) {
      Tag f = first.get(i);
      Tag s = second.get(i);
      if (!f.equals(s)) {
        return true;
      }
    }

    return false;
  }
}

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

import android.view.View;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;

import android.content.res.Configuration;

import android.util.Log;

/**
 * FIXME
 **/
public class PublishActivity extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "PublishActivity";



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
    Log.d(LTAG, "Uploading...");

    Globals.get().mAPI.uploadBoo(mBoo, null);
    // FIXME
  }



  private void onEditImage()
  {
    Log.d(LTAG, "Editing image...");
  }
}

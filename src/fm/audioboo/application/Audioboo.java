/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * Copyright (C) 2010,2011 Audioboo Ltd.
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
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import android.widget.ImageView;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.drawable.Drawable;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;

import fm.audioboo.widget.Flow;

import android.app.Dialog;

import java.lang.ref.WeakReference;

import android.util.Log;


/**
 * Main Activity. Contains a few buttons (most in a Flow) and a player view.
 **/
public class Audioboo extends Activity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG                = "Audioboo";

  // Dialog IDs.
  private static final int DIALOG_GPS_SETTINGS    = Globals.DIALOG_GPS_SETTINGS;

  // Initially selected Flow entry.
  private static final int INITIAL_SELECTION      = 1;



  /***************************************************************************
   * Private data
   **/
  // Labels, actions for the main menu.
  private String[]    mLabels;
  private String[]    mActions;



  /***************************************************************************
   * ImageAdapter Implementation
   **/
  public class ImageAdapter extends BaseAdapter
  {
    private WeakReference<Context>  mContext;
    private TypedArray              mImages;


    public ImageAdapter(Context ctx, TypedArray images)
    {
      mContext = new WeakReference<Context>(ctx);
      mImages = images;
    }



    public int getCount()
    {
      return mImages.length();
    }



    public Object getItem(int position)
    {
      return position;
    }



    public long getItemId(int position)
    {
      return position;
    }



    public View getView(int position, View convertView, ViewGroup parent)
    {
      Context ctx = mContext.get();
      if (null == ctx) {
        return null;
      }

      Drawable img = mImages.getDrawable(position);

      ImageView imageView = new ImageView(ctx);
      imageView.setImageDrawable(img);
      imageView.setScaleType(ImageView.ScaleType.FIT_XY);
      imageView.setLayoutParams(new Flow.LayoutParams(
            (int) img.getIntrinsicWidth(),
            (int) img.getIntrinsicHeight()));
      imageView.setBackgroundResource(R.drawable.flow_item_background);

      return imageView;
    }
  }



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // Load resources
    mLabels = getResources().getStringArray(R.array.main_menu_labels);
    mActions = getResources().getStringArray(R.array.main_menu_actions);
    TypedArray icons = getResources().obtainTypedArray(R.array.main_menu_icons);

    // Create & populate Flow
    Flow flow = (Flow) findViewById(R.id.main_menu_flow);
    flow.setAdapter(new ImageAdapter(this, icons));

    // Initialize flow
    flow.setFocusable(true);
    flow.setFocusableInTouchMode(true);
    flow.setSelection(INITIAL_SELECTION);

    final TextView menu_label = (TextView) findViewById(R.id.main_menu_label);
    if (null == menu_label) {
      Log.e(LTAG, "No label found? Weird!");
    }
    else {
      flow.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView parent, View view, int position, long id)
          {
            menu_label.setText(mLabels[position]);
          }


          public void onNothingSelected(AdapterView<?> parent)
          {
            // Ignore
          }
      });
    }

    flow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id)
        {
          if (null == mActions[position] || 0 == mActions[position].length()) {
            Toast.makeText(getBaseContext(), "Action not implemented!", Toast.LENGTH_SHORT).show();
          }
          else {
            Intent i = new Intent();
            i.setClassName(Audioboo.this, mActions[position]);
            startActivity(i);
          }
        }
    });

    // Hook up record button.
    final View button = findViewById(R.id.main_record);
    if (null != button) {
      button.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            Intent i = new Intent(Audioboo.this, RecordActivity.class);
            startActivity(i);
          }
      });
    }
  }



  @Override
  public void onStart()
  {
    super.onStart();

    // Start listening to location updates, if that's required.
    if (!Globals.get().startLocationUpdates()) {
      showDialog(DIALOG_GPS_SETTINGS);
    }
  }



  @Override
  public void onWindowFocusChanged(boolean hasFocus)
  {
    super.onWindowFocusChanged(hasFocus);

    if (!hasFocus) {
      return;
    }

    // We need to put focus on the first selected view in order not to have it
    // captured by other elements. However, the first selected view is only
    // available in onWindowFocusChanged(); other functions are called before
    // layouting.
    Flow flow = (Flow) findViewById(R.id.main_menu_flow);
    flow.getSelectedView().requestFocus();
  }



  @Override
  public void onConfigurationChanged(Configuration config)
  {
    // Ignore when the keyboard opens to the extent that we don't fetch boos
    // again. XXX We need to ignore this in the parent activity so the child
    // actvities don't get restarted. Ignoring in the child activities is also
    // required.
    super.onConfigurationChanged(config);
  }



  @Override
  public void onStop()
  {
    super.onStop();
    Globals.get().stopLocationUpdates();
  }



  protected Dialog onCreateDialog(int id)
  {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_GPS_SETTINGS:
        dialog = Globals.get().createDialog(this, id);
        break;
    }

    return dialog;
  }
}

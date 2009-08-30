/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.app;

import android.app.TabActivity;

import android.os.Bundle;

import android.content.Intent;

import android.content.res.Configuration;
import android.content.res.TypedArray;

import android.widget.TabHost;

import android.util.Log;

/**
 * Main Activity. Does little more than set up the Tabs that contain the
 * other Activities.
 **/
public class AudioBoo extends TabActivity
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "AudioBoo";

  // Classes for the Intents used to launch Tab contents.
  // XXX Indices need to correspond with the "main_tab_labels" array in
  //     localized.xml and the "main_tab_drawables" array in arrays.xml
  private static final Class TAB_CONTENT_CLASSES[] = {
    RecentBoosActivity.class,
  };



  /***************************************************************************
   * Implementation
   **/
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
  }



  @Override
  public void onStart()
  {
    super.onStart();

    // Load resources describing the tabs.
    String[] labels = getResources().getStringArray(R.array.main_tab_labels);
    TypedArray drawables = getResources().obtainTypedArray(
        R.array.main_tab_drawables);

    if (labels.length != drawables.length()
        || labels.length != TAB_CONTENT_CLASSES.length)
    {
      Log.e(LTAG, "Programming error: differing numbers of tab labels, drawables "
          + "and classes found.");
      return;
    }

    // Create tabs.
    TabHost host = getTabHost();
    for (int i = 0 ; i < labels.length ; ++i) {
      host.addTab(host.newTabSpec("tab" + i)
          .setIndicator(labels[i], drawables.getDrawable(i))
          .setContent(
            new Intent(this, TAB_CONTENT_CLASSES[i])
          )
        );
    }
    host.setCurrentTab(0);
  }
}

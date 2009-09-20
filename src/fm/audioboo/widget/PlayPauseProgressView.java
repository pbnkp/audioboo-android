/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.view.View;

import android.content.Context;
import android.util.AttributeSet;
import android.content.res.TypedArray;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.Color;

import android.graphics.drawable.Drawable;

import fm.audioboo.app.R;

import android.util.Log;

/**
 * FIXME Similar to a ProgressBar, but simply draws a colored PlayPause.
 **/
public class PlayPauseProgressView extends PieProgressView
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "PlayPauseProgressView";


  /***************************************************************************
   * Data members
   **/
  // Context
  private Context         mContext;

  // Indeterminate drawable
  private Drawable        mIndeterminateDrawable;


  /***************************************************************************
   * Implementation
   **/

  public PlayPauseProgressView(Context context)
  {
    super(context);
    mContext = context;
  }



  public PlayPauseProgressView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    initWithAttrs(attrs);
  }



  public PlayPauseProgressView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = context;
    initWithAttrs(attrs);
  }



  @Override
  protected void initWithAttrs(AttributeSet attrs)
  {
    super.initWithAttrs(attrs);
//    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.PlayPauseProgressView);
//    mIndeterminateDrawable = a.getDrawable(R.styleable.PlayPauseProgressView_indeterminateDrawable);
//    a.recycle();
  }



  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
  }
}

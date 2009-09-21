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
import android.graphics.Bitmap;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import fm.audioboo.app.R;

import android.util.Log;

/**
 * Extends PieProgressView with an optional indeterminate view, that's
 * fairly specific to the use in PlayPauseButton.
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
  // Indeterminate bitmap & mask
  private Bitmap    mIndeterminate;
  private Bitmap    mMask;

  // Flag, determines whether we display an indeterminate view or the pie view
  private boolean         mIsIndeterminate = false;

  /***************************************************************************
   * Implementation
   **/

  public PlayPauseProgressView(Context context)
  {
    super(context);
  }



  public PlayPauseProgressView(Context context, AttributeSet attrs)
  {
    // Super class's ctor calls initWithAttrs() for us
    super(context, attrs);
  }



  public PlayPauseProgressView(Context context, AttributeSet attrs, int defStyle)
  {
    // Super class's ctor calls initWithAttrs() for us
    super(context, attrs, defStyle);
  }



  @Override
  protected void initWithAttrs(AttributeSet attrs)
  {
    super.initWithAttrs(attrs);
    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.PlayPauseProgressView);

    BitmapDrawable d = (BitmapDrawable) a.getDrawable(R.styleable.PlayPauseProgressView_indeterminateBitmap);
    mIndeterminate = d.getBitmap();

    d = (BitmapDrawable) a.getDrawable(R.styleable.PlayPauseProgressView_indeterminateMaskBitmap);
    mMask = d.getBitmap();

    a.recycle();
  }



  public void setIndeterminate(boolean newValue)
  {
    if (mIsIndeterminate != newValue) {
      invalidate();
    }
    mIsIndeterminate = newValue;
  }



  public boolean getIndeterminate()
  {
    return mIsIndeterminate;
  }



  @Override
  protected void onDraw(Canvas canvas)
  {
    if (!mIsIndeterminate) {
      super.onDraw(canvas);
      return;
    }

    if (null == mIndeterminate) {
      Log.e(LTAG, "Supposed to draw indeterminate drawable, but none set.");
      return;
    }

    float rotation = ((float) getProgress() / getMax()) * 360f;

    // The constraints in which we're drawing.
    int width = getWidth() - getPaddingLeft() - getPaddingRight();
    int height = getHeight() - getPaddingTop() - getPaddingBottom();

    // We'll reuse this paint all over the place.
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setFilterBitmap(true);

    // Some offline rendering fun, yay.
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);

    // Start the offline buffer in black.
    c.drawColor(Color.BLACK);

    // Rotate the canvas prior to drawing the indeterminate bitmap.
    c.rotate(rotation, width / 2, height / 2);

    // Paint the indeterminate drawable, offset so it's centered in the
    // center of the offscreen buffer.
    float indeterminate_x = (width - mIndeterminate.getWidth()) / 2;
    float indeterminate_y = (height - mIndeterminate.getHeight()) / 2;
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    c.drawBitmap(mIndeterminate, indeterminate_x, indeterminate_y, paint);

    // Rotate the canvas back before drawing the mask
    c.rotate(-1f * rotation, width / 2, height / 2);

    // Paint the mask, offset so it's centered in the center of the offscreen
    // buffer.
    float mask_x = (width - mMask.getWidth()) / 2;
    float mask_y = (height - mMask.getHeight()) / 2;
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    c.drawBitmap(mMask, mask_x, mask_y, paint);

    // Now render the offscreen buffer to the canvas.
    int x = getPaddingLeft();
    int y = getPaddingTop();
    paint.setXfermode(null);
    canvas.drawBitmap(b, x, y, paint);
  }
}

/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.content.Context;

import android.os.SystemClock;

import android.util.AttributeSet;

import android.view.View;
import android.view.MotionEvent;
import android.view.animation.Transformation;

import android.widget.Gallery;

/**
 * A Flow is a Gallery with smooth animations for transforming any but the
 * center views. It can be used to implement a CoverFlow-type animation
 * -- but be sure to clear that wik Apple ;)
 **/
public class Flow extends Gallery
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "Flow";


  /***************************************************************************
   * Transformation
   **/
  public interface ChildTransform
  {
    public boolean getChildTransformation(float scale, int flowCenter, View child,
        Transformation trans);
  };



  /***************************************************************************
   * Data members
   **/
  // Transformation to use. Default to FlowShrink
  private ChildTransform      mTransform = new FlowShrink();

  // Center of the Flow view.
  private int                 mCenter;

  // Last scroll event time that was registered.
  private long                mLastScrollEvent;



  /***************************************************************************
   * Implementation
   **/
  public Flow(Context context)
  {
    super(context);
    setStaticTransformationsEnabled(true);
  }



  public Flow(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setStaticTransformationsEnabled(true);
  }



  public Flow(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    setStaticTransformationsEnabled(true);
  }



  /**
   * Get the Centre of the Flow
   * @return The centre of this Flow.
   **/
  private int getCenterOfFlow()
  {
    return (getWidth() - getPaddingLeft() - getPaddingRight()) / 2 + getPaddingLeft();
  }



  /**
   * {@inheritDoc}
   *
   * @see #setStaticTransformationsEnabled(boolean)
   **/
  protected boolean getChildStaticTransformation(View child, Transformation trans)
  {
    return mTransform.getChildTransformation(getResources().getDisplayMetrics().density,
        mCenter, child, trans);
  }



  /**
   * This is called during layout when the size of this view has changed. If
   * you were just added to the view hierarchy, you're called with the old
   * values of 0.
   *
   * @param w Current width of this view.
   * @param h Current height of this view.
   * @param oldw Old width of this view.
   * @param oldh Old height of this view.
   **/
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    mCenter = getCenterOfFlow();
    super.onSizeChanged(w, h, oldw, oldh);
  }



  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
    // XXX Hack alert!
    // Ignore layout calls if we've had a scroll event in the last 250 msec;
    // that'll ignore the per-second layout calls we get through BooPlayerView's
    // updating.
    long now = SystemClock.uptimeMillis();
    if (Math.abs(now - mLastScrollEvent) > 250) {
      super.onLayout(changed, l, t, r, b);
    }
  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
  {
    mLastScrollEvent = SystemClock.uptimeMillis();
    return super.onScroll(e1, e2, distanceX, distanceY);
  }
}

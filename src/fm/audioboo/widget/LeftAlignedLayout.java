/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.app.Activity;
import android.widget.FrameLayout;

import android.view.View;
import android.view.View.MeasureSpec;

import android.content.Context;
import android.util.AttributeSet;


/**
 * Lays out it's subviews like left-aligned text, i.e. in one row until
 * the row is full, then breaking into the next row.
 **/
public class LeftAlignedLayout extends FrameLayout
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "LeftAlignedLayout";

  /***************************************************************************
   * Implementation
   **/
  public LeftAlignedLayout(Context context)
  {
    super(context);
  }



  public LeftAlignedLayout(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }



  public LeftAlignedLayout(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
  }



  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    // Log.d(LTAG, "wm: " + widthMode + "   hm: " + heightMode);
    // Log.d(LTAG, "ws: " + widthSize + "   hs: " + heightSize);

    boolean doBreak = widthMode != MeasureSpec.UNSPECIFIED;

    int maxWidth = 0;
    int maxHeight = 0;

    int cur_left = getPaddingLeft() + getPaddingRight();
    int cur_top = getPaddingTop() + getPaddingBottom();
    int row_height = 0;
    for (int i = 0 ; i < getChildCount() ; ++i) {
      View v = getChildAt(i);
      if (View.GONE == v.getVisibility()) {
        continue;
      }

      measureChildWithMargins(v, widthMeasureSpec, 0, heightMeasureSpec,
          0);

      LayoutParams layout_params = (LayoutParams) v.getLayoutParams();
      int v_width = v.getMeasuredWidth();
      int v_height = v.getMeasuredHeight();
      int v_left = cur_left + layout_params.leftMargin;
      int v_top = cur_top + layout_params.topMargin;

      int lw = v_width + layout_params.leftMargin + layout_params.rightMargin;
      int lh = v_height + layout_params.topMargin + layout_params.bottomMargin;
      // Log.d(LTAG, "Layout [" + v + "]: " + lw + "x" + lh);

      row_height = Math.max(row_height, lh);

      if (doBreak && cur_left + lw > widthSize) {
        // Log.d(LTAG, "new row!");
        maxWidth = Math.max(maxWidth, cur_left);
        maxHeight += row_height;
        row_height = lh;

        cur_left = getPaddingLeft() + getPaddingRight();
        cur_top += row_height;
        v_left = cur_left + layout_params.leftMargin;
        v_top = cur_top + layout_params.topMargin;
      }

      cur_left += lw;
    }

    maxWidth = Math.max(maxWidth, cur_left);
    maxHeight += row_height;
    // Log.d(LTAG, "max: " + maxWidth + "x" + maxHeight);

    // Right. Depending on width/height mode, we want to do some adjustments
    // here.
    switch (widthMode) {
      case MeasureSpec.UNSPECIFIED:
      case MeasureSpec.AT_MOST:
        break;

      case MeasureSpec.EXACTLY:
        maxWidth = widthSize; // Ah, well, we tried.
        break;
    }

    switch (heightMode) {
      case MeasureSpec.UNSPECIFIED:
        break;

      case MeasureSpec.AT_MOST:
        maxHeight = Math.min(maxHeight, heightSize);
        break;

      case MeasureSpec.EXACTLY:
        maxHeight = heightSize;
        break;
    }

    // Log.d(LTAG, "max: " + maxWidth + "x" + maxHeight);

    // Check against our minimum height and width
    maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
    maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

    setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
        resolveSize(maxHeight, heightMeasureSpec));
  }



  @Override
  public void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    super.onLayout(changed, left, top, right, bottom);
    // Log.d(LTAG, "Changed: " + changed + " (" + left + "," + top + ") - (" + right + "," + bottom + ")");

    final int width = right - left - getPaddingLeft() - getPaddingRight();
    int cur_left = getPaddingLeft();
    int cur_top = getPaddingTop();
    int row_height = 0;
    for (int i = 0 ; i < getChildCount() ; ++i) {
      View v = getChildAt(i);
      if (View.GONE == v.getVisibility()) {
        continue;
      }

      android.widget.TextView tv = (android.widget.TextView) v;
      // Log.d(LTAG, "view: " + tv.getText());

      LayoutParams layout_params = (LayoutParams) v.getLayoutParams();
      int v_width = v.getMeasuredWidth();
      int v_height = v.getMeasuredHeight();
      int v_left = cur_left + layout_params.leftMargin;
      int v_top = cur_top + layout_params.topMargin;

      int lw = v_width + layout_params.leftMargin + layout_params.rightMargin;
      int lh = v_height + layout_params.topMargin + layout_params.bottomMargin;
      // Log.d(LTAG, "Layout [" + v + "]: " + lw + "x" + lh);

      row_height = Math.max(row_height, lh);

      if (cur_left + lw > width) {
        // Log.d(LTAG, "new row!");
        row_height = lh;

        cur_left = getPaddingLeft();
        cur_top += row_height;
        v_left = cur_left + layout_params.leftMargin;
        v_top = cur_top + layout_params.topMargin;
      }

      // Log.d(LTAG, "Laying out at " + v_left + "x" + v_top);
      v.layout(v_left, v_top, v_left + v_width, v_top + v_height);
      cur_left += lw;
    }
  }
}

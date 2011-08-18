/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.view.View;
import android.view.Gravity;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

import android.widget.RelativeLayout;
import android.widget.ImageView;

import android.content.Context;
import android.util.AttributeSet;
import android.content.res.TypedArray;
import android.content.res.ColorStateList;

import java.lang.ref.WeakReference;

import fm.audioboo.application.R;

import android.util.Log;

/**
 * The SpectralView class displays recording amplitudes reported via the
 * setAmplitudes() function. The bars drawn are all based on the same overall
 * peak/average amplitude, but modified to look more like a spectral analysis
 * view.
 * Most of this view is controlled via it's XML attributes.
 **/
public class SpectralView extends RelativeLayout
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "SpectralView";

  // Max level for ClipDrawables, defined by Android
  private static final int    MAX_LEVEL                   = 10000;

  // Default number of bars to display
  private static final int    DEFAULT_NUMBER_OF_BARS      = 9;

  // Default animation duration and FPS.
  private static final int    DEFAULT_ANIMATION_MAX_FPS   = 15;

  // Low pass filter limits
  private static final double FILTER_LOWER_LIMIT          = 0.1;
  private static final double FILTER_UPPER_LIMIT          = 0.7;

  // Boost lower amplitudes by up to this much.
  private static final double BOOST_FACTOR                = 10.0;


  /***************************************************************************
   * Data members
   **/
  // Number of bars to display.
  private int                 mNumberOfBars;

  // Drawables for even and odd bars.
  private ClipDrawable        mBarDrawable;
  // Color for the grid
  private ColorStateList      mGridColor;
  // Padding for the bars & grid.
  private int                 mBarPaddingLeft;
  private int                 mBarPaddingRight;
  private int                 mBarPaddingTop;
  private int                 mBarPaddingBottom;
  // Grid spacing
  private int                 mGridSpacing;

  // Peak and average amplitude set via setAmplitudes.
  private float               mAverageAmp;
  private float               mPeakAmp;

  // Animation FPS.
  private double              mFPS;
  private int                 mFrames;
  private long                mFrameTimestamp;

  // Only animate if this flag is set.
  private boolean             mShouldAnimate;
  // Factor/exponent for the slot selected via mBarHeightSlot/mBarFilterSlot
  private double[]            mBarHeightExponents;
  private double[]            mBarFilterFactors;
  private double[]            mBarHeights;
  private int[]               mBarHeightSlot;
  private int[]               mBarFilterSlot;
  // Depends on limits of the filter and number of bars.
  private double              mFilterStep;

  // Context
  private WeakReference<Context>  mContext;


  /***************************************************************************
   * Implementation
   **/
  public SpectralView(Context context)
  {
    super(context);
    mContext = new WeakReference<Context>(context);
  }



  public SpectralView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = new WeakReference<Context>(context);
    initWithAttrs(attrs);
  }



  public SpectralView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = new WeakReference<Context>(context);
    initWithAttrs(attrs);
  }



  public void setAmplitudes(float average, float peak)
  {
    if (mShouldAnimate) {
      mAverageAmp = average;
      mPeakAmp = peak;
      invalidate();
    }
  }



  public void startAnimation()
  {
    mShouldAnimate = true;
    mFrameTimestamp = System.currentTimeMillis();
    invalidate();
  }



  public void stopAnimation()
  {
    mShouldAnimate = false;
    mAverageAmp = 0;
    mPeakAmp = 0;
    invalidate();
  }



  public void setGridColor(int resource)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    mGridColor = ctx.getResources().getColorStateList(resource);
  }



  public void setBarDrawable(int resource)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    Drawable draw = ctx.getResources().getDrawable(resource);
    if (null == draw) {
      Log.e(LTAG, "Could not load resource for bar drawable.");
      return;
    }

    mBarDrawable = new ClipDrawable(draw,
        Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, ClipDrawable.VERTICAL);
  }



  private void initWithAttrs(AttributeSet attrs)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.SpectralView);

    // Read number of bars, defaulting to DEFAULT_NUMBER_OF_BARS
    mNumberOfBars = a.getInt(R.styleable.SpectralView_numberOfBars,
        DEFAULT_NUMBER_OF_BARS);
    if (1 >= mNumberOfBars) {
      throw new IllegalArgumentException("Expect numberOfBars to be at least 1.");
    }

    // Remember bar heights.
    mBarHeights = new double[mNumberOfBars];

    // Determine bar height factors, based on the number of bars.
    mBarHeightExponents = new double[mNumberOfBars];

    final double base = 1.8; // experimentally deducded
    final double max_x = (mNumberOfBars - 1) / 2.0;
    final double max_y = Math.pow(base, max_x);

    final double min = 0.2; // from iOS code
    final double scale = 1.0 - min;

    for (int i = 0 ; i < mNumberOfBars ; ++i) {
      double x = Math.abs(i - max_x);
      mBarHeightExponents[i] = min + (Math.pow(base, x) * scale);
    }

    // Also compute factors for the low pass filter. We start at 1, work our
    // way up to (mNumberOfBars / 2) + 1 and then work our way down again
    mBarFilterFactors = new double[mNumberOfBars];
    double next = 1.0;
    double step = 1.0;
    for (int i = 0 ; i < mNumberOfBars ; ++i) {
      mBarFilterFactors[i] = next;

      if (next >= (mNumberOfBars / 2) + 1) {
        step = -1.0;
      }
      next += step;
    }

    // Lastly, compute the filter step.
    mFilterStep = (FILTER_UPPER_LIMIT - FILTER_LOWER_LIMIT) / (mNumberOfBars - 1);

    // Set up the slots that will be used to access the above. These will be
    // randomly permuated later on.
    mBarFilterSlot = new int[mNumberOfBars];
    mBarHeightSlot = new int[mNumberOfBars];
    for (int i = 0 ; i < mNumberOfBars ; ++i) {
      mBarFilterSlot[i] = mBarHeightSlot[i] = i;
    }

    // Determine drawables for even/odd bars
    Drawable draw = a.getDrawable(R.styleable.SpectralView_barDrawable);
    if (null == draw) {
      draw = new ColorDrawable(Color.BLUE);
    }
    mBarDrawable = new ClipDrawable(draw,
        Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, ClipDrawable.VERTICAL);

    // Read padding
    int padding = a.getDimensionPixelOffset(R.styleable.SpectralView_barPadding, 0);
    mBarPaddingLeft = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingLeft, padding);
    mBarPaddingRight = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingRight, padding);
    mBarPaddingTop = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingTop, padding);
    mBarPaddingBottom = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingBottom, padding);

    // Read grid spacing; default 2.
    mGridSpacing = a.getDimensionPixelOffset(R.styleable.SpectralView_gridSpacing, 2);

    // Get grid color
    mGridColor = a.getColorStateList(R.styleable.SpectralView_gridColor);

    a.recycle();
  }



  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    // Calculate FPS. Also calculate whether we want to permutate the height/filter
    // functions.
    boolean permutate = false;
    ++mFrames;
    long timestamp = System.currentTimeMillis();
    long diff = timestamp - mFrameTimestamp;
    if (diff > 1000) {
      mFPS = mFrames / (diff / 1000.0);
      mFrames = 0;
      mFrameTimestamp = timestamp;

      // Alright, "calculating" whether to permutate is a tiny bit of an
      // exaggeration.
      permutate = true;
    }

    // Log.d(LTAG, "FPS: " + mFPS);

    // Permutate height/filter slots every half second
    if (permutate) {
      int middle = (mNumberOfBars - 1) / 2;

      // Randomly permutate the filter function of any but the middle bar.
      int idx = (int) (Math.random() % mNumberOfBars);
      if (idx != middle) {
        mBarFilterSlot[idx] = (int) (Math.random() % mNumberOfBars);
      }

      // Randomly permutate the height function of any but the middle bar
      idx = (int) (Math.random() % mNumberOfBars);
      if (idx != middle) {
        mBarHeightSlot[idx] = (int) (Math.random() % mNumberOfBars);
      }
    }


    // Overall content dimensions, needed to calculate bar widths.
    int width = getWidth() - mBarPaddingLeft - mBarPaddingRight;
    int height = getHeight() - mBarPaddingTop - mBarPaddingBottom;;

    float bar_width = (float) width / mNumberOfBars;
    float x = mBarPaddingLeft;
    float y = mBarPaddingTop;


    // Draw the bars
    ClipDrawable d = mBarDrawable;
    for (int i = 0 ; i < mNumberOfBars ; ++i, x += bar_width) {
      double scale = 0.0;
      if (mShouldAnimate) {
        float amp = mPeakAmp;

        // Boost lower amplitudes, but clamp everything to 1.0.
        double boost = Math.sin(1.0 - amp) / Math.sin(1.0);
        double boosted = amp * (1 + (BOOST_FACTOR * boost));
        if (boosted > 1.0) {
          boosted = 1.0;
        }

        // The scale takes into consideration the height of each bar (via their
        // exponents), and a low pass filter is applied.
        scale = Math.pow(amp, mBarHeightExponents[mBarHeightSlot[i]]);
        double filterSize = FILTER_LOWER_LIMIT + mBarFilterFactors[mBarFilterSlot[i]] * mFilterStep;
        scale = filterSize * scale + (1.0 - filterSize) * mBarHeights[i];
      }
      mBarHeights[i] = scale;

      // Levels are from 0 to MAX_LEVEL. In orde to show at least one pixel, we need
      // to ensure we set the level to least MAX_LEVEL/height.
      int level = (int) (scale * MAX_LEVEL);
      int min_level = MAX_LEVEL / height;
      if (level < min_level) {
        level = min_level;
      }

      d.setBounds((int) x, (int) y, (int) (x + bar_width), height);
      d.setLevel(level);
      d.draw(canvas);
    }

    // Draw grid over the bars. We need to draw a line between bars.
    int currentColor = Color.BLACK;
    if (null != mGridColor) {
      currentColor = mGridColor.getColorForState(getDrawableState(), Color.BLACK);
    }
    ClipDrawable cd = new ClipDrawable(new ColorDrawable(currentColor),
        Gravity.FILL, ClipDrawable.VERTICAL);

    x = mBarPaddingLeft + (bar_width - (mGridSpacing / 2));
    for (int i = 0 ; i < (mNumberOfBars - 1) ; ++i, x += bar_width) {
      cd.setBounds((int) x, 0, (int) x + mGridSpacing, getHeight());
      cd.setLevel(MAX_LEVEL);
      cd.draw(canvas);
    }
    y = mBarPaddingTop + (bar_width - (mGridSpacing / 2));
    int numSquares = (int) (height / bar_width);
    for ( ; y < height ; y += bar_width) {
      cd.setBounds(0, (int) y, getWidth(), (int) y + mGridSpacing);
      cd.setLevel(MAX_LEVEL);
      cd.draw(canvas);
    }
  }
}

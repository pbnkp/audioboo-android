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

import fm.audioboo.app.R;

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

  // Default tint color for inactive views is 50% black
  private static final int    DEFAULT_INACTIVE_TINT_COLOR = Color.argb(127, 0, 0, 0);

  // Minimum/maximum bar exponent.
  private static final float  MIN_BAR_EXP                 = 0.2f;
  private static final float  MAX_BAR_EXP                 = 0.5f;

  // Low pass filter limits
  private static final float  FILTER_LOWER_LIMIT          = 0.1f;
  private static final float  FILTER_UPPER_LIMIT          = 0.7f;

  /***************************************************************************
   * Data members
   **/
  // Number of bars to display.
  private int                 mNumberOfBars;
  // Drawable for the frame to overlay over the bars.
  private Drawable            mFrameDrawable;
  // Drawable for displaying when the view is inactive, i.e. startAnimation has
  // not been called.
  private Drawable            mInactiveDrawable;
  // Tint color for inactive views
  private ColorStateList      mInactiveTintColor;
  // Drawables for even and odd bars.
  private ClipDrawable        mEvenBarDrawable;
  private ClipDrawable        mOddBarDrawable;
  // Color for the grid
  private ColorStateList      mGridColor;
  // Padding for the bars & grid.
  private int                 mBarPaddingLeft;
  private int                 mBarPaddingRight;
  private int                 mBarPaddingTop;
  private int                 mBarPaddingBottom;

  // Peak and average amplitude set via setAmplitudes.
  private float               mAverageAmp;
  private float               mPeakAmp;

  // Animation max FPS.
  private int                 mAnimationMaxFPS;
  // Last draw time, needded to not exceed FPS.
  private long                mLastDrawTime;
  // Only animate if this flag is set.
  private boolean             mShouldAnimate;
  // Factor/exponent for the slot selected via mBarHeightSlot/mBarFilterSlot
  private float[]             mBarHeightExponents;
  private float[]             mBarFilterFactors;
  private int[]               mBarHeightSlot;
  private int[]               mBarFilterSlot;
  // Depends on limits of the filter and number of bars.
  private float               mFilterStep;
  // Randomly change a bar's filter/height slot every MaxFPS steps. Need to
  // count frames for that.
  private int                 mFrameCount;

  // Context
  private Context             mContext;


  /***************************************************************************
   * Implementation
   **/
  public SpectralView(Context context)
  {
    super(context);
    mContext = context;
  }



  public SpectralView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    initWithAttrs(attrs);
  }



  public SpectralView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = context;
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
    invalidate();
  }



  public void stopAnimation()
  {
    mShouldAnimate = false;
    mAverageAmp = 0;
    mPeakAmp = 0;
    invalidate();
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    RelativeLayout content = (RelativeLayout) inflate(mContext, R.layout.spectral_view, this);

    // Set frame drawable, if we know it.
    if (null != mFrameDrawable) {
      ImageView image_view = (ImageView) content.findViewById(R.id.spectral_view_frame);
      if (null != image_view) {
        image_view.setImageDrawable(mFrameDrawable);
      }
    }
  }



  private int clamp(int value, int min, int max)
  {
    if (value < min) {
      return min;
    }
    else if (value > max) {
      return max;
    }
    return value;
  }



  private void initWithAttrs(AttributeSet attrs)
  {
    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.SpectralView);

    // Read number of bars, defaulting to DEFAULT_NUMBER_OF_BARS
    mNumberOfBars = a.getInt(R.styleable.SpectralView_numberOfBars,
        DEFAULT_NUMBER_OF_BARS);
    if (1 >= mNumberOfBars) {
      throw new IllegalArgumentException("Expect numberOfBars to be at least 1.");
    }

    // Determine bar height factors, based on the number of bars. If the number
    // of bars is odd, the middle one will reach to MAX_BAR_EXP, otherwise the
    // two sharing the center will. The left and rightmost bar will not be
    // treated to any exponent.
    mBarHeightExponents = new float[mNumberOfBars];
    int idx2 = mNumberOfBars / 2;
    int idx1 = idx2 - 1;
    int mid_idx = -1;
    if (0 != mNumberOfBars % 2) {
      mid_idx = idx2;
      idx2 += 1;
    }
    idx1 = clamp(idx1, 0, mNumberOfBars - 1);
    idx2 = clamp(idx2, 0, mNumberOfBars - 1);

    float exp = MIN_BAR_EXP;
    float step_diff = (MAX_BAR_EXP - MIN_BAR_EXP) / idx1;
    if (-1 != mid_idx) {
      mBarHeightExponents[mid_idx] = exp;
      exp += step_diff;
    }

    for ( ; idx1 > 0 ; --idx1, ++idx2) {
      mBarHeightExponents[idx1] = exp;
      mBarHeightExponents[idx2] = exp;
      exp += step_diff;
    }

    mBarHeightExponents[0] = 1.0f;
    mBarHeightExponents[mNumberOfBars - 1] = 1.0f;

    // Also compute factors for the low pass filter. We start at 1, work our
    // way up to (mNumberOfBars / 2) + 1 and then work our way down again
    mBarFilterFactors = new float[mNumberOfBars];
    float next = 1.0f;
    float step = 1.0f;
    for (int i = 0 ; i < mBarFilterFactors.length ; ++i) {
      mBarFilterFactors[i] = next;

      if (next >= (mNumberOfBars / 2) + 1) {
        step = -1.0f;
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


    // Read frame drawable
    mFrameDrawable = a.getDrawable(R.styleable.SpectralView_frameDrawable);

    // Determine drawables for even/odd bars
    Drawable even = a.getDrawable(R.styleable.SpectralView_evenBarDrawable);
    Drawable odd = a.getDrawable(R.styleable.SpectralView_oddBarDrawable);

    if (null == even || null == odd) {
      even = new ColorDrawable(Color.BLUE);
      odd = new ColorDrawable(Color.GREEN);
    }
    else if (null == even) {
      even = odd;
    }
    else if (null == odd) {
      odd = even;
    }
    mEvenBarDrawable = new ClipDrawable(even,
        Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, ClipDrawable.VERTICAL);
    mOddBarDrawable = new ClipDrawable(odd,
        Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, ClipDrawable.VERTICAL);

    // Read padding
    int padding = a.getDimensionPixelOffset(R.styleable.SpectralView_barPadding, 0);
    mBarPaddingLeft = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingLeft, padding);
    mBarPaddingRight = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingRight, padding);
    mBarPaddingTop = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingTop, padding);
    mBarPaddingBottom = a.getDimensionPixelOffset(R.styleable.SpectralView_barPaddingBottom, padding);

    // Read max animation FPS.
    mAnimationMaxFPS = a.getInt(R.styleable.SpectralView_barAnimationMaxFPS,
        DEFAULT_ANIMATION_MAX_FPS);

    // Get grid color
    mGridColor = a.getColorStateList(R.styleable.SpectralView_gridColor);

    // Get inactive drawable & tint color
    mInactiveDrawable = a.getDrawable(R.styleable.SpectralView_inactiveDrawable);
    mInactiveTintColor = a.getColorStateList(R.styleable.SpectralView_inactiveTintColor);

    a.recycle();
  }



  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    // Permutate height/filter slots every half second
    ++mFrameCount;
    if (0 == mFrameCount % (mAnimationMaxFPS / 2)) {
      // Randomly pick a bar whose height exponent we change randomly. Ignore
      // the middle bar (if any)
      int idx = (int) (Math.random() * (mNumberOfBars - 1));
      if (0 == mNumberOfBars % 2 || (int) (mNumberOfBars / 2) != idx) {
        mBarHeightSlot[idx] = (int) (Math.random() * (mNumberOfBars - 1));
      }

      // Randomly pick a bar whose filter factor we change randomly. Ignore
      // the middle bar (if any)
      idx = (int) (Math.random() * (mNumberOfBars - 1));
      if (0 == mNumberOfBars % 2 || (int) (mNumberOfBars / 2) != idx) {
        mBarFilterSlot[idx] = (int) (Math.random() * (mNumberOfBars - 1));
      }
    }


    // Overall content dimensions, needed to calculate bar widths.
    int width = getWidth() - mBarPaddingLeft - mBarPaddingRight;
    int height = getHeight() - mBarPaddingTop - mBarPaddingBottom;;

    float bar_width = (float) width / mNumberOfBars;
    float x = mBarPaddingLeft;
    float y = mBarPaddingTop;


    // Draw the bars
    for (int i = 0 ; i < mNumberOfBars ; ++i, x += bar_width) {
      ClipDrawable d = mEvenBarDrawable;
      if (0 != i % 2) {
        d = mOddBarDrawable;
      }

      float scale = 1.0f;
      if (mShouldAnimate) {
        // The scale takes into consideration the height of each bar (via their
        // exponents), and a low pass filter is applied.
        scale = (float) Math.pow(mPeakAmp, mBarHeightExponents[mBarHeightSlot[i]]);
        float filterSize = FILTER_LOWER_LIMIT + mBarFilterFactors[mBarFilterSlot[i]] * mFilterStep;
        scale = filterSize * scale + (1 - filterSize) * mAverageAmp;
      }

      // Levels are from 0 to MAX_LEVEL. In orde to show at least one pixel, we need
      // to ensure we set the level to least MAX_LEVEL/height.
      int level = (int) (scale * MAX_LEVEL);
      int min_level = MAX_LEVEL / height;
      if (level < min_level) {
        level = min_level;
      }
//      Log.d(LTAG, "Level: " + level);

      d.setBounds((int) x, (int) y, (int) (x + bar_width), height);
      d.setLevel(level);
      d.draw(canvas);
    }

    // Draw the inactive background, if necessary.
    if (!mShouldAnimate) {
      if (null != mInactiveDrawable) {
        int dwidth = mInactiveDrawable.getIntrinsicWidth();
        int dheight = mInactiveDrawable.getIntrinsicHeight();

        int xoffs = mBarPaddingLeft;
        int yoffs = mBarPaddingTop;
        if (dwidth >= width || dheight >= height) {
          int maxwidth = width - 2 * (mBarPaddingTop + mBarPaddingBottom);
          int maxheight = height - 2 * (mBarPaddingLeft + mBarPaddingRight);

          float wfactor = (float) maxwidth / dwidth;
          float hfactor = (float) maxheight / dheight;
          float factor = (float) Math.min(wfactor, hfactor);

          dwidth *= factor;
          dheight *= factor;
        }
        xoffs += (width - dwidth) / 2;
        yoffs += (height - dheight) / 2;

        mInactiveDrawable.setBounds(xoffs, yoffs, xoffs + dwidth, yoffs + dheight);
        mInactiveDrawable.draw(canvas);
      }

      // Tint bars
      int currentColor = DEFAULT_INACTIVE_TINT_COLOR;
      if (null != mInactiveTintColor) {
        currentColor = mInactiveTintColor.getColorForState(getDrawableState(),
            DEFAULT_INACTIVE_TINT_COLOR);
      }
      ClipDrawable cd = new ClipDrawable(new ColorDrawable(currentColor),
          Gravity.FILL, ClipDrawable.VERTICAL);

      cd.setBounds(0, 0, getWidth(), getHeight());
      cd.setLevel(MAX_LEVEL);
      cd.draw(canvas);
    }

    // Draw grid over the bars. We need to draw a 2px line between bars.
    int currentColor = Color.BLACK;
    if (null != mGridColor) {
      currentColor = mGridColor.getColorForState(getDrawableState(), Color.BLACK);
    }
    ClipDrawable cd = new ClipDrawable(new ColorDrawable(currentColor),
        Gravity.FILL, ClipDrawable.VERTICAL);

    x = mBarPaddingLeft + (bar_width - 1);
    for (int i = 0 ; i < (mNumberOfBars - 1) ; ++i, x += bar_width) {
      cd.setBounds((int) x, 0, (int) x + 2, getHeight());
      cd.setLevel(MAX_LEVEL);
      cd.draw(canvas);
    }
    y = mBarPaddingTop + (bar_width - 1);
    int numSquares = (int) (height / bar_width);
    for ( ; y < width ; y += bar_width) {
      cd.setBounds(0, (int) y, getWidth(), (int) y + 2);
      cd.setLevel(MAX_LEVEL);
      cd.draw(canvas);
    }

    // If the bars should animate, schedule the next frame. We're trying to
    // schedule this no more often than mAnimationMaxFPS dictates.
    if (mShouldAnimate) {
      long frame_delay = (long) (1000.0 / mAnimationMaxFPS);
      long current = System.currentTimeMillis();
      long diff = current - mLastDrawTime;

      if (diff >= frame_delay) {
        mLastDrawTime = current;
        frame_delay = (2 * frame_delay) - diff;
        if (frame_delay < 0) {
          frame_delay = 0;
        }
        postInvalidateDelayed(frame_delay);
      }
    }
  }
}

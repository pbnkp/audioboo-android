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
import android.view.KeyEvent;

import android.graphics.Canvas;

import android.widget.RelativeLayout;
import android.widget.CompoundButton;
import android.widget.TextView;

import android.content.Context;
import android.util.AttributeSet;
import android.content.res.TypedArray;

import fm.audioboo.app.R;

import android.util.Log;

/**
 * The record button in the AudioBoo client is just complex enough that it's
 * easiest to keep it's state in it's own class.
 * It's essentially a ToggleButton, but
 * - It's got three states, where the first state is only entered when it's
 *   state machine is (re)initialized; after that it switches back and forth
 *   between the other two states.
 * - It's got a background graphic changing depending on state, two other
 *   graphics and two text labels.
 **/
public class RecordButton extends RelativeLayout
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "RecordButton";


  /***************************************************************************
   * Data members
   **/
  // Text label for initial state, on state and off state.
  private String                mTextInitial;
  private String                mTextOn;
  private String                mTextOff;

  private int                   mProgressMax = 100;
  private int                   mProgressCurrent;

  // Context
  private Context               mContext;

  // Contained elements
  private View                  mOverlay;
  private NotifyingToggleButton mToggle;
  private TextView              mLabel;
  private PieProgressView       mProgress;
  private TextView              mProgressLabel;

  // Listener to toggle button presses
  private CompoundButton.OnCheckedChangeListener  mListener;

  /***************************************************************************
   * Implementation
   **/
  public RecordButton(Context context)
  {
    super(context);
    mContext = context;

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
  }



  public RecordButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    initWithAttrs(attrs);

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
  }



  public RecordButton(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = context;
    initWithAttrs(attrs);

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
  }



  /**
   * In contrast to Android's stock progress widgets, this one treats both the
   * maximum and current progress to be in units of seconds. The label next to
   * the pie view is updated accordingly.
   **/
  public void setProgress(int progress)
  {
    mProgressCurrent = progress;
    if (null != mProgress) {
      mProgress.setProgress(progress);
    }

    if (null != mProgressLabel) {
      // Split progress into minutes and seconds
      int minutes = progress / 60;
      int seconds = progress % 60;

      mProgressLabel.setText(String.format("%d:%02d", minutes, seconds));
    }
  }



  /**
   * see @setProgress
   **/
  public void setMax(int max)
  {
    mProgressMax = max;
    if (null != mProgress) {
      mProgress.setMax(max);
    }
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    RelativeLayout content = (RelativeLayout) inflate(mContext, R.layout.record_button, this);
    mOverlay = content.findViewById(R.id.record_button_overlay);

    mLabel = (TextView) content.findViewById(R.id.record_button_label);
    if (null != mLabel) {
      mLabel.setText(mTextInitial);
    }

    mToggle = (NotifyingToggleButton) content.findViewById(R.id.record_button_toggle);
    if (null != mToggle) {
      mToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          if (isChecked) {
            onChecked();
          }
          else {
            onUnchecked();
          }
        }
      });

      mToggle.setOnPressedListener(new NotifyingToggleButton.OnPressedListener() {
        public void onPressed(NotifyingToggleButton btn, boolean pressed)
        {
          // Propagate the toggle's pressed state to the overlay
          mOverlay.setPressed(pressed);
        }
      });
    }

    mProgress = (PieProgressView) content.findViewById(R.id.record_button_progress);
    if (null != mProgress) {
      mProgress.setMax(mProgressMax);
    }

    mProgressLabel = (TextView) content.findViewById(R.id.record_button_progress_label);

    setProgress(0);
  }



  public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener)
  {
    mListener = listener;
  }



  private void initWithAttrs(AttributeSet attrs)
  {
    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.RecordButton);
    mTextInitial = a.getString(R.styleable.RecordButton_textInitial);
    mTextOn = a.getString(R.styleable.RecordButton_textOn);
    mTextOff = a.getString(R.styleable.RecordButton_textOff);
    a.recycle();
  }



  private void onChecked()
  {
    if (null != mLabel) {
      mLabel.setText(mTextOn);
    }

    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, true);
    }
  }



  private void onUnchecked()
  {
    if (null != mLabel) {
      mLabel.setText(mTextOff);
    }

    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, false);
    }
  }
}

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
 * FIXME
 * The record button in the AudioBoo client is just complex enough that it's
 * easiest to keep it's state in it's own class.
 * It's essentially a ToggleButton, but
 * - It's got three states, where the first state is only entered when it's
 *   state machine is (re)initialized; after that it switches back and forth
 *   between the other two states.
 * - It's got a background graphic changing depending on state, two other
 *   graphics and two text labels.
 **/
public class PlayPauseButton extends RelativeLayout
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "PlayPauseButton";


  /***************************************************************************
   * Data members
   **/
  private int                   mProgressMax = 100;
  private int                   mProgressCurrent;

  // Context
  private Context               mContext;

  // Contained elements
  private NotifyingToggleButton mToggle;
  private PlayPauseProgressView mProgress;

  // Listener to toggle button presses
  private CompoundButton.OnCheckedChangeListener  mListener;

  /***************************************************************************
   * Implementation
   **/
  public PlayPauseButton(Context context)
  {
    super(context);
    mContext = context;

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
  }



  public PlayPauseButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    initWithAttrs(attrs);

    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
  }



  public PlayPauseButton(Context context, AttributeSet attrs, int defStyle)
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



  public void setIndeterminate(boolean newValue)
  {
    if (null != mProgress) {
      mProgress.setIndeterminate(newValue);
    }
  }



  public boolean getIndeterminate()
  {
    if (null == mProgress) {
      return false;
    }
    return mProgress.getIndeterminate();
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    RelativeLayout content = (RelativeLayout) inflate(mContext, R.layout.play_pause_button, this);

    mToggle = (NotifyingToggleButton) content.findViewById(R.id.play_pause_button_toggle);
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
    }

    mProgress = (PlayPauseProgressView) content.findViewById(R.id.play_pause_button_progress);
    if (null != mProgress) {
      mProgress.setMax(mProgressMax);
    }

    setProgress(43);
    setIndeterminate(true);
  }



  public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener)
  {
    mListener = listener;
  }



  private void initWithAttrs(AttributeSet attrs)
  {
//    TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.RecordButton);
//    a.recycle();
  }



  private void onChecked()
  {
    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, true);
    }
  }



  private void onUnchecked()
  {
    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, false);
    }
  }
}

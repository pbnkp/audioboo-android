/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.widget.RelativeLayout;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
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
  private String        mTextInitial;
  private String        mTextOn;
  private String        mTextOff;

  // Context
  private Context       mContext;

  // Contained elements
  private ToggleButton  mToggle;
  private TextView      mLabel;

  // Listener to toggle button presses
  private CompoundButton.OnCheckedChangeListener  mListener;

  /***************************************************************************
   * Implementation
   **/
  public RecordButton(Context context)
  {
    super(context);
    mContext = context;
  }



  public RecordButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;
    initWithAttrs(attrs);
  }



  public RecordButton(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = context;
    initWithAttrs(attrs);
  }



  @Override
  protected void onFinishInflate()
  {
    RelativeLayout content = (RelativeLayout) inflate(mContext, R.layout.record_button, this);

    mLabel = (TextView) content.findViewById(R.id.record_button_label);
    if (null != mLabel) {
      mLabel.setText(mTextInitial);
    }

    mToggle = (ToggleButton) content.findViewById(R.id.record_button_toggle);
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

    // FIXME also notify self, if possible.

//    if (null != mToggle) {
//      Log.d(LTAG, "text initial: " + mTextInitial);
//      Log.d(LTAG, "text on: " + mTextOn);
//      Log.d(LTAG, "text off: " + mTextOff);
//      mToggle.setTextOn(mTextOn);
//      mToggle.setTextOff(mTextOff);
//      requestLayout();
//      invalidate();
//    }

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
    Log.d(LTAG, "on checked!");

    if (null != mLabel) {
      mLabel.setText(mTextOn);
    }

    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, true);
    }
  }



  private void onUnchecked()
  {
    Log.d(LTAG, "on unchecked!");

    if (null != mLabel) {
      mLabel.setText(mTextOff);
    }

    if (null != mListener) {
      mListener.onCheckedChanged(mToggle, false);
    }
  }
}

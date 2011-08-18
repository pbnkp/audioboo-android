/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * Copyright (C) 2010,2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.view.View;
import android.view.KeyEvent;

import android.graphics.drawable.Drawable;

import android.widget.RelativeLayout;
import android.widget.CompoundButton;
import android.widget.TextView;

import android.content.Context;
import android.util.AttributeSet;
import android.content.res.TypedArray;

import fm.audioboo.application.R;

import java.lang.ref.WeakReference;

/**
 * The record button in the Audioboo client is just complex enough that it's
 * easiest to keep it's state in it's own class.
 * It's essentially a ToggleButton, but
 * - It's got three states, where the first state is only entered when it's
 *   state machine is (re)initialized; after that it switches back and forth
 *   between the other two states.
 * - It's got a background graphic changing depending on state, two other
 *   graphics and two text labels.
 **/
public class RecordButton extends NotifyingToggleButton
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
  private String                  mTextInitial;

  // Drawables for on/off states.
  private Drawable                mBackgroundInitial;
  private Drawable                mBackgroundOn;
  private Drawable                mBackgroundOff;

  // Context
  private WeakReference<Context>  mContext;

  // Listener to toggle button presses
  private CompoundButton.OnCheckedChangeListener  mListener;


  /***************************************************************************
   * Implementation
   **/
  public RecordButton(Context context)
  {
    super(context);
    mContext = new WeakReference<Context>(context);
  }



  public RecordButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = new WeakReference<Context>(context);
    initWithAttrs(context, attrs);
  }



  public RecordButton(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    mContext = new WeakReference<Context>(context);
    initWithAttrs(context, attrs);
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    setText(mTextInitial);
    setBackgroundDrawable(mBackgroundInitial);

    super.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
          setBackgroundDrawable(isChecked ? mBackgroundOn : mBackgroundOff);

          if (null != mListener) {
            mListener.onCheckedChanged(buttonView, isChecked);
          }
        }
    });
  }



  public void resetState()
  {
    setChecked(false);
    setText(mTextInitial);
    setBackgroundDrawable(mBackgroundInitial);
  }



  public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener)
  {
    mListener = listener;
  }



  private void initWithAttrs(Context ctx, AttributeSet attrs)
  {
    TypedArray a = ctx.obtainStyledAttributes(attrs, R.styleable.RecordButton);

    mTextInitial = a.getString(R.styleable.RecordButton_textInitial);

    mBackgroundInitial = a.getDrawable(R.styleable.RecordButton_backgroundInitial);
    mBackgroundOn = a.getDrawable(R.styleable.RecordButton_backgroundOn);
    mBackgroundOff = a.getDrawable(R.styleable.RecordButton_backgroundOff);

    a.recycle();
  }
}

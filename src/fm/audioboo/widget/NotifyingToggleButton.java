/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.widget.ToggleButton;

import android.content.Context;
import android.util.AttributeSet;


/**
 * Simple extension to ToggleButton; allows attaching a listener to be notified
 * when isPressed() changes.
 **/
public class NotifyingToggleButton extends ToggleButton
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "NotifyingToggleButton";


  /***************************************************************************
   * Notification class
   **/
  public static interface OnPressedListener
  {
    void onPressed(NotifyingToggleButton button, boolean isPressed);
  }


  /***************************************************************************
   * Data members
   **/
  private OnPressedListener mListener;
  private boolean           mOldPressed;


  /***************************************************************************
   * Implementation
   **/
  public NotifyingToggleButton(Context context)
  {
    super(context);
  }



  public NotifyingToggleButton(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }



  public NotifyingToggleButton(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
  }



  public void setOnPressedListener(OnPressedListener listener)
  {
    mListener = listener;
  }



  @Override
  protected void drawableStateChanged()
  {
    super.drawableStateChanged();

    if (mOldPressed != isPressed()) {
      mOldPressed = isPressed();
      if (null != mListener) {
        mListener.onPressed(this, isPressed());
      }
    }
  }
}

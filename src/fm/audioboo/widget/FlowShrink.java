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

import android.graphics.Camera;
import android.graphics.Matrix;

import android.view.View;
import android.view.animation.Transformation;

import android.widget.ImageView;


/**
 * Animation for Flow class; simply shrinks views that are not in the center.
 **/
public class FlowShrink implements Flow.ChildTransform
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG = "FlowShrink";

  // Max Zoom.
  private static final int  MAX_ZOOM            = -150;



  /***************************************************************************
   * Private data
   **/
  /**
   * Graphics Camera used for transforming the matrix of ImageViews
   */
  private Camera mCamera = new Camera();



  /***************************************************************************
   * Implementation
   **/

  public boolean getChildTransformation(float scale, int flowCenter, View child, Transformation trans)
  {
    final float childCenter = child.getLeft() + (child.getWidth() / 2);
    final float childWidth = child.getWidth();

    trans.clear();
    trans.setTransformationType(Transformation.TYPE_MATRIX);

    mCamera.save();
    final Matrix imageMatrix = trans.getMatrix();
    final int imageHeight = child.getLayoutParams().height;
    final int imageWidth = child.getLayoutParams().width;

    float zoomAmount = MAX_ZOOM * ((flowCenter - childCenter) / childWidth);
    zoomAmount = Math.abs(zoomAmount);

    mCamera.translate(0.0f, 0.0f, zoomAmount);

    mCamera.getMatrix(imageMatrix);
    imageMatrix.preTranslate(-(imageWidth/2), -(imageHeight/2));
    imageMatrix.postTranslate((imageWidth/2), (imageHeight/2));
    mCamera.restore();

    return true;
  }
}

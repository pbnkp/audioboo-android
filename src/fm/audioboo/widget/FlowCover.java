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
 * Animation for Flow class to implement CoverFlow-like transformations. Just
 * because we can.
 **/
public class FlowCover implements Flow.ChildTransform
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG = "FlowCover";

  // Max rotation angle.
  private static final int  MAX_ROTATION_ANGLE  = 60;

  // Max Zoom.
  private static final int  MAX_ZOOM            = -120;



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

    int rotationAngle = 0;
    if (childCenter != flowCenter) {
      rotationAngle = (int) (((flowCenter - childCenter)/ childWidth) * MAX_ROTATION_ANGLE);
      if (Math.abs(rotationAngle) > MAX_ROTATION_ANGLE) {
        rotationAngle = (rotationAngle < 0) ? -MAX_ROTATION_ANGLE : MAX_ROTATION_ANGLE;
      }
    }
    transformImageBitmap((ImageView) child, trans, rotationAngle);

    return true;
  }



  /**
   * Transform the Image Bitmap by the Angle passed
   *
   * @param imageView ImageView the ImageView whose bitmap we want to rotate
   * @param t transformation
   * @param rotationAngle the Angle by which to rotate the Bitmap
   */
  private void transformImageBitmap(ImageView child, Transformation trans, int rotationAngle)
  {
    mCamera.save();
    final Matrix imageMatrix = trans.getMatrix();
    final int imageHeight = child.getLayoutParams().height;
    final int imageWidth = child.getLayoutParams().width;
    final int rotation = Math.abs(rotationAngle);

    mCamera.translate(0.0f, 0.0f, 100.0f);

    // As the angle of the view gets less, zoom in
    if (rotation < MAX_ROTATION_ANGLE) {
      float zoomAmount = (float) (MAX_ZOOM + (rotation * 1.5));
      mCamera.translate(0.0f, 0.0f, zoomAmount);
    }

    mCamera.rotateY(rotationAngle);
    mCamera.getMatrix(imageMatrix);
    imageMatrix.preTranslate(-(imageWidth/2), -(imageHeight/2)); 
    imageMatrix.postTranslate((imageWidth/2), (imageHeight/2));
    mCamera.restore();
  }
}

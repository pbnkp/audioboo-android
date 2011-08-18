/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import java.util.LinkedList;

/**
 * Representation of a list of Boos, including metadata about the
 * window, offset, etc.
 **/
public class BooList
{
  /***************************************************************************
   * Public data
   **/
  // Most important things first: the clips themselves
  public LinkedList<Boo>  mClips = new LinkedList<Boo>();

  // Metadata
  public int    mOffset;
  public int    mCount;
}

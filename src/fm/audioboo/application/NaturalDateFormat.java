/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.application;

import android.content.Context;

import java.util.Date;
import java.util.GregorianCalendar;

import android.util.Log;

/**
 * Formats a time difference (from now) as "just now", "5 seconds ago", etc.
 **/
public class NaturalDateFormat
{
  /***************************************************************************
   * Private constants
   **/
  private static final String   LTAG        = "NaturalDateFormat";

  private static final long[]   THRESHOLDS  = {
      60 * 60 * 24 * 365,
      60 * 60 * 24 * 30,
      60 * 60 * 24 * 7,
      60 * 60 * 24,
      60 * 60,
      60,
      15,
    };


  /***************************************************************************
   * Private static data
   **/
  private static String[]   NAMES_SINGLE;
  private static String[]   NAMES_MULTIPLE;
  private static String     NAME_SHORTEST;
  private static String     FORMAT_STRING;

  static {
    Context ctx = Globals.get().mContext.get();
    if (null == ctx) {
      Log.e(LTAG, "No context? That's weird!");
    }

    if (null == NAMES_SINGLE) {
      NAMES_SINGLE = ctx.getResources().getStringArray(R.array.date_format_single);
    }

    if (null == NAMES_MULTIPLE) {
      NAMES_MULTIPLE = ctx.getResources().getStringArray(R.array.date_format_multiple);
    }

    if (null == NAME_SHORTEST) {
      NAME_SHORTEST = ctx.getResources().getString(R.string.date_format_shortest);
    }

    if (null == FORMAT_STRING) {
      FORMAT_STRING = ctx.getResources().getString(R.string.date_format);
    }
  }


  /***************************************************************************
   * Implementation
   **/
  /**
   * Formats the first parameter as a difference from the second in natural
   * langauge. If the second parameter is not given, it's assumed to be right
   * now.
   **/
  public static String format(Date date)
  {
    return format(date, new Date());
  }



  public static String format(Date date, Date base)
  {
    // Get timestamps
    GregorianCalendar calendar = new GregorianCalendar();

    calendar.setTime(date);
    long date_s = calendar.getTimeInMillis() / 1000;

    calendar.setTime(base);
    long base_s = calendar.getTimeInMillis() / 1000;

    // Diff
    long diff = Math.abs(base_s - date_s);

    // Format stuff!
    String pre_format = null;

    for (int i = 0 ; i < THRESHOLDS.length ; ++i) {
      long over = diff / THRESHOLDS[i];
      if (over > 1) {
        pre_format = String.format(NAMES_MULTIPLE[i], over);
        break;
      }
      else if (over == 1) {
        pre_format = String.format(NAMES_SINGLE[i], over);
        break;
      }
    }

    if (null == pre_format) {
      return NAME_SHORTEST;
    }

    return String.format(FORMAT_STRING, pre_format);
  }
}

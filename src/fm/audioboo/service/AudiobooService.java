/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import android.app.Service;

import android.os.IBinder;

import android.content.Intent;

import fm.audioboo.data.BooData;
import fm.audioboo.application.Boo;

import android.util.Log;


/**
 * Service implementing IAudiobooService
 **/
public class AudiobooService extends Service
{
  /***************************************************************************
   * Private constants
   **/
  private static final String LTAG = "AudiobooService";


  /***************************************************************************
   * Private data
   **/
  private BooPlayer   mPlayer;


  /***************************************************************************
   * Service implementation
   **/
  @Override
  public IBinder onBind(Intent intent)
  {
    if (IBooPlaybackService.class.getName().equals(intent.getAction())) {
      return mBinder;
    }
    return null;
  }



  @Override
  public void onCreate()
  {
    if (null == mPlayer) {
      mPlayer = new BooPlayer(this);
      mPlayer.start();
    }
  }



  @Override
  public void onDestroy()
  {
    if (null != mPlayer) {
      mPlayer.mShouldRun = false;
      mPlayer.interrupt();
      mPlayer = null;
    }
  }


  /***************************************************************************
   * IBooPlaybackService implementation
   **/
  private final IBooPlaybackService.Stub mBinder = new IBooPlaybackService.Stub()
  {
    public void play(BooData boo)
    {
      mPlayer.play(new Boo(boo));
    }



    public void stop()
    {
      mPlayer.stopPlaying();
    }



    public void pause()
    {
      mPlayer.pausePlaying();
    }



    public void resume()
    {
      mPlayer.resumePlaying();
    }



    public int getState()
    {
      return mPlayer.getPlaybackState();
    }
  };
}


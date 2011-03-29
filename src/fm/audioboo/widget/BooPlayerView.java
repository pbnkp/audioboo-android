/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd.
 * Copyright (C) 2010,2011 AudioBoo Ltd.
 * All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;

import android.widget.RelativeLayout;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import android.os.Handler;
import android.os.Message;

import fm.audioboo.application.Pair;
import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;

import fm.audioboo.service.Constants;
import fm.audioboo.service.BooPlayerClient;

import fm.audioboo.application.R;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Displays a player window, and uses the BooPlayer in Globals to play back
 * Boos.
 **/
public class BooPlayerView
       extends RelativeLayout
       implements BooPlayerClient.ProgressListener,
                  CompoundButton.OnCheckedChangeListener
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooPlayerView";

  // Scale for the seek bar
  private static final int  PROGRESS_MAX        = 10000;


  /***************************************************************************
   * Public constants
   **/
  // Playback ends either successfully or with an error.
  public static final int END_STATE_SUCCESS = 0;
  public static final int END_STATE_ERROR   = 1;



  /***************************************************************************
   * Informs users that playback ended
   **/
  public static abstract class PlaybackEndListener
  {
    public abstract void onPlaybackEnded(BooPlayerView view, int endState);
  }




  /***************************************************************************
   * Data members
   **/
  // Context
  private WeakReference<Context>  mContext;

  // Views
  private SeekBar                 mSeekBar;
  private NotifyingToggleButton   mButton;
  private TextView                mAuthor;
  private TextView                mTitle;
  private Button                  mDisclosure;

  // Configurables
  private boolean                 mShowDisclosure;

  // Listener
  private PlaybackEndListener     mListener;


  /***************************************************************************
   * Implementation
   **/
  public BooPlayerView(Context context)
  {
    super(context);
    setup(context, null);
  }



  public BooPlayerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setup(context, attrs);
  }



  public BooPlayerView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs);
    setup(context, null);
  }



  public void setPlaybackEndListener(PlaybackEndListener listener)
  {
    mListener = listener;
  }



  public PlaybackEndListener getPlaybackEndListener()
  {
    return mListener;
  }



  public void showDisclosure(boolean show)
  {
    mShowDisclosure = show;
    if (null != mDisclosure) {
      mDisclosure.setVisibility(show ? View.VISIBLE : View.GONE);
    }
  }



  public void setEnabled(boolean enabled)
  {
    if (null != mSeekBar) {
      mSeekBar.setEnabled(enabled);
    }
    if (null != mButton) {
      mButton.setEnabled(enabled);
    }
    if (null != mDisclosure) {
      mDisclosure.setEnabled(enabled);
    }
  }



  public void play(Boo boo)
  {
    play(boo, true);
  }



  public void play(Boo boo, boolean playImmediately)
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    setActive(true);

    // Log.d(LTAG, "view play: " + playImmediately);

    // Playback
    startPlaying(boo, playImmediately);

    updateMetadata();
  }



  private void resetProgress()
  {
    if (null != mSeekBar) {
      mSeekBar.setIndeterminate(false);
      mSeekBar.setMax(PROGRESS_MAX);
      mSeekBar.setProgress(0);
    }
  }



  private void showPlaying()
  {
    if (null != mSeekBar) {
      mSeekBar.setIndeterminate(false);
    }
  }



  private void showBuffering()
  {
    if (null != mSeekBar) {
      mSeekBar.setIndeterminate(true);
    }
  }



  private void setButtonState(int newState)
  {
    switch (newState) {
      case Constants.STATE_NONE: // Same as STATE_FINISHED
      case Constants.STATE_PREPARING:
      case Constants.STATE_PAUSED:
        mButton.setChecked(true);
        resetProgress();
        break;

      case Constants.STATE_ERROR:
        mButton.setChecked(true);
        resetProgress();
        sendEnded(END_STATE_ERROR);
        break;

      case Constants.STATE_PLAYING:
        mButton.setChecked(false);
        showPlaying();
        break;

      case Constants.STATE_BUFFERING:
        mButton.setChecked(false);
        showBuffering();
        break;
    }
  }



  private void startPlaying(Boo boo, boolean playImmediately)
  {
    // Log.d(LTAG, "view start");

    // Initialize button state
    setButtonState(Constants.STATE_BUFFERING);

    // Grab the play/pause button from the View. That's handed to the
    // BooPlayer.
    if (null != boo) {
      Globals.get().mPlayer.play(boo, playImmediately);
    }
    else {
      Globals.get().mPlayer.resume();
    }
  }



  public void setTitle(String title)
  {
    if (null != mTitle) {
      mTitle.setText(title);
    }
  }



  public void setAuthor(String author)
  {
    if (null != mAuthor) {
      mAuthor.setText(author);
    }
  }



  /**
   * Makes child elements clickable or not.
   **/
  public void setActive(boolean flag)
  {
    if (null != mButton) {
      mButton.setClickable(flag);
    }
    if (null != mSeekBar) {
      mButton.setClickable(flag);
    }
  }



  public void stop()
  {
    // Log.d(LTAG, "view stop");
    setActive(false);

    // Stops playback.
    Globals.get().mPlayer.stop();

    // Set button to a neutral state
    setButtonState(Constants.STATE_FINISHED);
  }



  public void pause()
  {
    // Log.d(LTAG, "view pause");
    // Pauses playback.
    Globals.get().mPlayer.pause();
  }



  public void resume()
  {
    // Log.d(LTAG, "view resume");
    // Resumes playback.
    Globals.get().mPlayer.resume();
  }



  public boolean isPaused()
  {
    return (Constants.STATE_PAUSED == Globals.get().mPlayer.getState());
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    ViewGroup content = (ViewGroup) inflate(ctx, R.layout.boo_player, this);

    // Set up seekbar
    mSeekBar = (SeekBar) content.findViewById(R.id.boo_player_seek);
    if (null != mSeekBar) {
      mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
        {
          // FIXME seek within audio
        }

        public void onStartTrackingTouch(SeekBar seekBar)
        {
          // Ignore
        }


        public void onStopTrackingTouch(SeekBar seekBar)
        {
          // Ignore
        }
      });
    }

    // Set up play/pause button
    mButton = (NotifyingToggleButton) content.findViewById(R.id.boo_player_button);
    if (null != mButton) {
      setButtonState(Constants.STATE_NONE);
      mButton.setOnCheckedChangeListener(this);
    }

    // Set the view to show buffering animations
    showBuffering();

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);
    mAuthor = (TextView) content.findViewById(R.id.boo_player_author);
    mDisclosure = (Button) content.findViewById(R.id.boo_player_disclosure);

    // Show disclosure?
    if (null != mDisclosure) {
      showDisclosure(mShowDisclosure);
    }

    // FIXME need this in main view, too!
    Globals g = Globals.get();
    if (null != g) {
      BooPlayerClient c = g.mPlayer;

      if (null != c) {
        // Be informed of whatever player state exists.
        c.setProgressListener(this);

        // If the playback service is playing, initialize title, etc.
        if (Constants.STATE_NONE != c.getState()) {
          // Initialize button state
          setButtonState(Constants.STATE_BUFFERING);
          updateMetadata();
        }
      }
    }
  }



  private void sendEnded(int state)
  {
    if (null != mListener) {
      mListener.onPlaybackEnded(this, state);
    }
  }



  private void setup(Context context, AttributeSet attrs)
  {
    mContext = new WeakReference<Context>(context);

    setClickable(true);

    setOnTouchListener(new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event)
        {
          // Log.d(LTAG, "Captured touch.");
          return true;
        }
    });

    if (null != attrs) {
      TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BooPlayerView);
      mShowDisclosure = a.getBoolean(R.styleable.BooPlayerView_showDisclosure, true);
      a.recycle();
    }
  }



  private void updateMetadata()
  {
    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }


    String title = Globals.get().mPlayer.getTitle();
    if (null == title) {
      setTitle(ctx.getResources().getString(R.string.boo_player_new_title));
    }
    else {
      setTitle(title);
    }


    String author = Globals.get().mPlayer.getUsername();
    if (null == author) {
      setAuthor(""); // FIXME
    }
    else {
      setAuthor(author);
    }
  }


  /***************************************************************************
   * BooPlayerClient.ProgressListener implementation
   **/
  public void onProgress(int state, double progress, double total)
  {
    // Update button state
    setButtonState(state);

    // If there's progress, update that, too.
    if (total > 0) {
      int cur = (int) ((progress / total) * PROGRESS_MAX);
      //Log.d(LTAG, "cur/max: " + cur + "/" + PROGRESS_MAX);

      if (null != mSeekBar) {
        mSeekBar.setMax(PROGRESS_MAX);
        mSeekBar.setProgress(cur);
      }
    }
  }



  /***************************************************************************
   * CompoundButton.OnCheckedChangeListener implementation
   **/
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
  {
    int state = Globals.get().mPlayer.getState();
    boolean shouldBeChecked = (Constants.STATE_NONE == state)
      || (Constants.STATE_PAUSED == state)
      || (Constants.STATE_ERROR == state);

    // Log.d(LTAG, "Is checked: " + isChecked + " - should be? " + shouldBeChecked);

    if (shouldBeChecked == isChecked) {
      return;
    }

    if (isChecked) {
      stop();
      sendEnded(END_STATE_SUCCESS);
    }
    else {
      if (Constants.STATE_PAUSED == state) {
        startPlaying(null, false);
      }
    }
  }
}

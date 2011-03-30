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
import android.content.Intent;
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

import android.net.Uri;

import fm.audioboo.application.Pair;
import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;

import fm.audioboo.service.Constants;
import fm.audioboo.service.BooPlayerClient;

import fm.audioboo.data.PlayerState;

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
      mSeekBar.setClickable(enabled);
    }
    if (null != mButton) {
      mButton.setEnabled(enabled);
      mButton.setClickable(enabled);
    }
    if (null != mDisclosure) {
      mDisclosure.setEnabled(enabled);
      mDisclosure.setClickable(enabled);
    }
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
//      mSeekBar.setMax(PROGRESS_MAX);
//      mSeekBar.setProgress(PROGRESS_MAX); // FIXME
    }
  }



  private void showBuffering()
  {
    if (null != mSeekBar) {
      mSeekBar.setIndeterminate(true);
    }
  }



  public void setTitle(String title)
  {
    if (null != mTitle) {
      mTitle.setText(title);
    }
  }



  public void setTitle(int resource)
  {
    if (null == mTitle) {
      return;
    }

    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    setTitle(ctx.getResources().getString(resource));
  }



  public void setAuthor(String author)
  {
    if (null != mAuthor) {
      mAuthor.setText(author);
    }
  }



  public void setAuthor(int resource)
  {
    if (null == mAuthor) {
      return;
    }

    Context ctx = mContext.get();
    if (null == ctx) {
      return;
    }

    setAuthor(ctx.getResources().getString(resource));
  }



  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    final Context ctx = mContext.get();
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
      mButton.setOnCheckedChangeListener(this);
    }

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);
    mAuthor = (TextView) content.findViewById(R.id.boo_player_author);
    mDisclosure = (Button) content.findViewById(R.id.boo_player_disclosure);

    // Show disclosure?
    if (null != mDisclosure) {
      showDisclosure(mShowDisclosure);
      mDisclosure.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v)
          {
            BooPlayerClient client = Globals.get().mPlayer;
            if (null == client) {
              return;
            }
            PlayerState state = client.getState();
            if (null == state) {
              return;
            }

            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                String.format("audioboo:boo_details?id=%d", state.mBooId)));
            ctx.startActivity(i);
          }
      });
    }

    // Initialize, if we can.
    Globals globals = Globals.get();
    if (null != globals) {
      BooPlayerClient client = globals.mPlayer;

      if (null == client) {
        setVisualState(Constants.STATE_NONE, null, null);
        globals.setClientBindListener(new Globals.ClientBindListener() {
            public void onBound()
            {
              Globals.get().setClientBindListener(null);
              initialize();
            }
        });
      }
      else {
        initialize();
      }
    }
  }



  private void initialize()
  {
    BooPlayerClient client = Globals.get().mPlayer;
    if (null == client) {
      Log.e(LTAG, "Initialized when no player is bound!");
      return;
    }

    // Be informed of whatever player state exists.
    client.addProgressListener(this);

    updateVisualState();
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



  private void setTitleAndAuthor(String title, String author)
  {
    if (null == title) {
      setTitle(R.string.boo_player_new_title);
    }
    else {
      setTitle(title);
    }

    if (null == author) {
      setAuthor(R.string.boo_player_no_author);
    }
    else {
      setAuthor(author);
    }
  }



  private void updateVisualState()
  {
    updateVisualState(null);
  }



  private void updateVisualState(PlayerState state)
  {
    int numericState = Constants.STATE_NONE;
    String title = null;
    String author = null;

    if (null != state) {
      numericState = state.mState;
      title = state.mBooTitle;
      author = state.mBooUsername;
    }

    setVisualState(numericState, title, author);
  }



  private void setVisualState(int state, String title, String author)
  {
    // Log.d(LTAG, "[" + this + "] Setting state: " + state + " " + title + "/" + author);
    switch (state) {
      case Constants.STATE_NONE:
      case Constants.STATE_ERROR:
        setEnabled(false);
        setTitle(R.string.boo_player_no_title);
        setAuthor(R.string.boo_player_no_author);
        mButton.setChecked(true);
        resetProgress();
        break;

      case Constants.STATE_PREPARING:
      case Constants.STATE_BUFFERING:
        setEnabled(true);
        setTitleAndAuthor(title, author);
        mButton.setChecked(false);
        showBuffering();
        break;

      case Constants.STATE_PLAYING:
        setEnabled(true);
        setTitleAndAuthor(title, author);
        mButton.setChecked(false);
        showPlaying();
        break;

      case Constants.STATE_FINISHED:
        resetProgress(); // fall through
      case Constants.STATE_PAUSED:
        setEnabled(true);
        setTitleAndAuthor(title, author);
        mButton.setChecked(true);
        showPlaying();
        break;
    }
  }


  /***************************************************************************
   * BooPlayerClient.ProgressListener implementation
   **/
  public void onProgress(PlayerState state)
  {
    // Update button state
    updateVisualState(state);

    // If there's progress, update that, too.
    if (null != mSeekBar && state.mTotal > 0) {
      int cur = (int) ((state.mProgress / state.mTotal) * PROGRESS_MAX);
      //Log.d(LTAG, "cur/max: " + cur + "/" + PROGRESS_MAX);

      mSeekBar.setMax(PROGRESS_MAX);
      mSeekBar.setProgress(cur);
    }
  }



  /***************************************************************************
   * CompoundButton.OnCheckedChangeListener implementation
   **/
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
  {
    PlayerState state = null;
    BooPlayerClient client = Globals.get().mPlayer;
    if (null != client) {
      state = client.getState();
    }

    boolean shouldBeChecked = (null == state)
      || (Constants.STATE_PAUSED == state.mState)
      || (Constants.STATE_ERROR == state.mState);

    // Log.d(LTAG, "Is checked: " + isChecked + " - should be? " + shouldBeChecked);

    if (shouldBeChecked == isChecked) {
      return;
    }

    if (isChecked) {
      Globals.get().mPlayer.pause();
    }
    else {
      if (null != state && Constants.STATE_PAUSED == state.mState) {
        Globals.get().mPlayer.resume();
      }
    }
  }



  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus)
  {
    super.onWindowFocusChanged(hasWindowFocus);

    if (!hasWindowFocus) {
      BooPlayerClient client = Globals.get().mPlayer;
      if (null != client) {
        client.removeProgressListener(this);
      }
      return;
    }

    // Ensure that the view always has the latest state.
    initialize();
  }
}

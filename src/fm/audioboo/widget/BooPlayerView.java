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

import android.content.Context;
import android.content.res.TypedArray;
import android.content.Intent;
import android.util.AttributeSet;

import android.os.SystemClock;

import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.MotionEvent;
import android.view.animation.Animation;

import android.widget.RelativeLayout;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

import android.graphics.Canvas;

import android.net.Uri;

import fm.audioboo.application.Pair;
import fm.audioboo.application.Boo;
import fm.audioboo.application.Globals;
import fm.audioboo.application.UriUtils;

import fm.audioboo.service.Constants;
import fm.audioboo.service.BooPlayerClient;

import fm.audioboo.data.PlayerState;
import fm.audioboo.data.User;

import fm.audioboo.application.R;

import java.lang.ref.WeakReference;

import android.util.Log;

/**
 * Displays a player window, and uses the BooPlayer in Globals to play back
 * Boos.
 **/
public class BooPlayerView
       extends RelativeLayout
       implements CompoundButton.OnCheckedChangeListener,
                  NotifyingToggleButton.OnPressedListener
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "BooPlayerView";

  // Scale for the seek bar
  private static final int  PROGRESS_SCALE        = 10000;


  /***************************************************************************
   * Data members
   **/
  // Context
  private WeakReference<Context>  mContext;

  // Toggle state detection
  private volatile boolean        mWasPressed;

  // Views
  private ViewAnimator            mSeekBarFlipper;
  private SeekBar                 mSeekBar;
  private ProgressBar             mIndeterminate;
  private NotifyingToggleButton   mButton;
  private TextView                mAuthor;
  private TextView                mTitle;
  private TextView                mProgress;
  private Button                  mDisclosure;

  // Animation related.
  private long                    mLastDraw   = 0;
  private Animation               mAnimation  = null;

  // Configurables
  private boolean                 mShowDisclosure;


  /***************************************************************************
   * Seekbar Listener
   **/
  private class SeekBarListener implements SeekBar.OnSeekBarChangeListener
  {
    private int mProgress;

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
      if (!fromUser) {
        return;
      }

      mProgress = progress;
    }

    public void onStartTrackingTouch(SeekBar seekBar)
    {
      // Ignore
    }


    public void onStopTrackingTouch(SeekBar seekBar)
    {
      BooPlayerClient client = Globals.get().mPlayer;
      if (null == client) {
        return;
      }

      PlayerState state = client.getState();
      if (null == state || Constants.STATE_PLAYING != state.mState) {
        return;
      }

      client.seekTo(((double) mProgress) / PROGRESS_SCALE);
    }
  }



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



  @Override
  public void setVisibility(int visibility)
  {
    super.setVisibility(visibility);
    // Log.d(LTAG, "Visibility set to: " + visibility);
    if (View.VISIBLE == visibility) {
      clearAnimation();
      mAnimation = null;
    }
  }



  private void showDisclosure(boolean show)
  {
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
    if (null != mSeekBarFlipper) {
      mSeekBarFlipper.setDisplayedChild(0);
    }
    if (null != mSeekBar) {
      mSeekBar.setMax(1);
      mSeekBar.setProgress(0);
    }
  }



  private void showPlaying(double progress, double total)
  {
    if (null != mSeekBarFlipper) {
      mSeekBarFlipper.setDisplayedChild(0);
    }
    if (null != mSeekBar) {
      mSeekBar.setMax((int) (total * PROGRESS_SCALE));
      mSeekBar.setProgress((int) (progress * PROGRESS_SCALE));
    }
  }



  private void showBuffering()
  {
    if (null != mSeekBarFlipper) {
      mSeekBarFlipper.setDisplayedChild(1);
    }
  }



  public void setProgress(double progress)
  {
    if (null == mProgress) {
      return;
    }
    int minutes = (int) (progress / 60);
    int seconds = ((int) progress) % 60;
    String text = String.format("%02d:%02d", minutes, seconds);
    mProgress.setText(text);
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
    mIndeterminate = (ProgressBar) content.findViewById(R.id.boo_player_indeterminate);
    if (null != mIndeterminate) {
      mIndeterminate.setIndeterminate(true);
    }
    mSeekBar = (SeekBar) content.findViewById(R.id.boo_player_seek);
    if (null != mSeekBar) {
      mSeekBar.setOnSeekBarChangeListener(new SeekBarListener());
    }
    mSeekBarFlipper = (ViewAnimator) content.findViewById(R.id.boo_player_seek_flipper);
    if (null != mSeekBarFlipper) {
      mSeekBarFlipper.setDisplayedChild(0);
    }

    // Set up play/pause button
    mButton = (NotifyingToggleButton) content.findViewById(R.id.boo_player_button);
    if (null != mButton) {
      mButton.setOnPressedListener(this);
      mButton.setOnCheckedChangeListener(this);
    }

    // Remember
    mTitle = (TextView) content.findViewById(R.id.boo_player_title);
    mAuthor = (TextView) content.findViewById(R.id.boo_player_author);
    mProgress = (TextView) content.findViewById(R.id.boo_player_progress);
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

            Intent i = new Intent(Intent.ACTION_VIEW, UriUtils.createDetailsUri(
                state.mBooId, state.mBooIsMessage));
            ctx.startActivity(i);
          }
      });
    }

    // Initialize, if we can.
    Globals globals = Globals.get();
    if (null != globals) {
      BooPlayerClient client = globals.mPlayer;

      if (null == client) {
        updateVisualState();
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

    updateVisualState(client.getState());
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



  private void setTitleAndAuthor(String title, String author, boolean isLocal)
  {
    if (null == title) {
      setTitle(R.string.boo_player_new_title);
    }
    else {
      setTitle(title);
    }

    if (null == author) {
      if (!isLocal) {
        setAuthor(R.string.boo_player_no_author);
      }
      else {
        // If it's local, that means the current user is the author. We may be
        // able to get the username from Globals.
        User account = Globals.get().mAccount;
        if (null != account && null != account.mUsername) {
          setAuthor(account.mUsername);
        }
        else {
          setAuthor(R.string.boo_player_author_self);
        }
      }
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
    // Log.d(LTAG, "[" + this + "] Setting state: " + state);

    // If the state is not set, treat this as Constants.STATE_NONE;
    // the block is pretty much identical to the one in the switch
    // statement.
    if (null == state) {
      setEnabled(false);
      setTitle(R.string.boo_player_no_title);
      setAuthor(R.string.boo_player_no_author);
      mButton.setChecked(true);
      resetProgress();
      setProgress(0f);
      return;
    }

    // Hide disclosure for all local items.
    showDisclosure(!state.mBooIsLocal);

    switch (state.mState) {
      case Constants.STATE_NONE:
      case Constants.STATE_ERROR:
        setEnabled(false);
        setTitle(R.string.boo_player_no_title);
        setAuthor(R.string.boo_player_no_author);
        mButton.setChecked(true);
        resetProgress();
        setProgress(state.mTotal);
        break;

      case Constants.STATE_PREPARING:
      case Constants.STATE_BUFFERING:
        setEnabled(true);
        setTitleAndAuthor(state.mBooTitle, state.mBooUsername, state.mBooIsLocal);
        mButton.setChecked(false);
        showBuffering();
        setProgress(state.mProgress);
        break;

      case Constants.STATE_PLAYING:
        setEnabled(true);
        setTitleAndAuthor(state.mBooTitle, state.mBooUsername, state.mBooIsLocal);
        mButton.setChecked(false);
        showPlaying(state.mProgress, state.mTotal);
        setProgress(state.mProgress);
        break;

      case Constants.STATE_FINISHED:
        resetProgress();
        setEnabled(true);
        setTitleAndAuthor(state.mBooTitle, state.mBooUsername, state.mBooIsLocal);
        mButton.setChecked(true);
        showPlaying(state.mProgress, state.mTotal);
        setProgress(state.mTotal);
        break;

      case Constants.STATE_PAUSED:
        setEnabled(state.mTotal > 0f);
        setTitleAndAuthor(state.mBooTitle, state.mBooUsername, state.mBooIsLocal);
        mButton.setChecked(true);
        showPlaying(state.mProgress, state.mTotal);
        setProgress(state.mTotal);
        break;
    }
  }



  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus)
  {
    super.onWindowFocusChanged(hasWindowFocus);

    if (!hasWindowFocus && null != mAnimation) {
      // Log.d(LTAG, "About to clear animation!");
      clearAnimation();
      mAnimation = null;
      return;
    }

    // Ensure that the view always has the latest state.
    initialize();
  }



  @Override
  protected void dispatchDraw(Canvas canvas)
  {
    super.dispatchDraw(canvas);

    if (null == mAnimation) {
      // Start an animation; it doesn't do *anything*, it just triggers at
      // least once per second, causing dispatchDraw() to be called again.
      mAnimation = new Animation() {};
      mAnimation.setRepeatCount(Animation.INFINITE);
      mAnimation.setDuration(1000L);
      startAnimation(mAnimation);
    }


    // As dispatchDraw is called at least once per second, we want to update
    // the player view state. But there's no point doing that more than once
    // per second, so let's limit ourselves to that... this *can* mean that
    // an update happens a frame late, but even on slow phones that should
    // not be noticeable.
    long draw = SystemClock.uptimeMillis();
    if (draw - mLastDraw > 1000) {
      // Log.d(LTAG, "dispatch draw!");
      mLastDraw = draw;
      BooPlayerClient client = Globals.get().mPlayer;
      if (null != client) {
        updateVisualState(client.getState());
      }
    }
  }




  /***************************************************************************
   * CompoundButton.OnCheckedChangeListener implementation
   **/
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
  {
    if (!mWasPressed) {
      return;
    }
    mWasPressed = false;

    PlayerState state = null;
    BooPlayerClient client = Globals.get().mPlayer;
    if (null != client) {
      state = client.getState();
    }

    boolean shouldBeChecked = (null == state)
      || (Constants.STATE_PAUSED == state.mState)
      || (Constants.STATE_ERROR == state.mState)
      || (Constants.STATE_FINISHED == state.mState);

    // Log.d(LTAG, "State: " + state);
    // Log.d(LTAG, "Is checked: " + isChecked + " - should be? " + shouldBeChecked);

    if (shouldBeChecked == isChecked) {
      return;
    }

    if (isChecked) {
      Globals.get().mPlayer.pause();
    }
    else {
      if (null != state && (Constants.STATE_PAUSED == state.mState
           || Constants.STATE_FINISHED == state.mState))
      {
        Globals.get().mPlayer.resume();
      }
    }
  }



  /***************************************************************************
   * NotifyingToggleButton.OnPressedListener
   **/
  public void onPressed(NotifyingToggleButton buttonView, boolean isPressed)
  {
    mWasPressed = isPressed;
  }
}

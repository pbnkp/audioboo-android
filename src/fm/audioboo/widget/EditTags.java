/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.widget;

import android.content.Context;
import android.util.AttributeSet;

import android.widget.EditText;

import android.text.Editable;
import android.text.TextWatcher;

import fm.audioboo.application.Pair;
import fm.audioboo.data.Tag;

import fm.audioboo.application.R;

import java.util.List;
import java.util.LinkedList;

/**
 * EditTags is an EditText that applies tag quoting rules to it's contents.
 **/
public class EditTags extends EditText
{
  /***************************************************************************
   * Private constants
   **/
  // Log ID
  private static final String LTAG  = "EditText";

  // Separator character
  private static final char SEPARATOR = ',';

  // Quotation character
  private static final char QUOTE     = '"';

  // State when scanning the text.
  private enum ScanState
  {
    AFTER_SEPARATOR,  // Initial state. Occurs when the scan position is after
                      // a separator.
    AFTER_QUOTES,     // Occurs when the scan position is after a closing quote,
                      // but it's unclear yet what (if anything) follows those.
    IN_QUOTES,        // Occurs when the scan position is somewhere after a
                      // starting quote, but before closing quotes have been
                      // applied.
    IN_TAG,           // Occurs when the scan position is somewhere within an
                      // unquoted tag, but before a separator is encountered.
  }


  /***************************************************************************
   * Processes chunks of CharSequences into a fully quoted final result.
   **/
  private class Quoter
  {
    public ScanState  mState;
    public String     mResult;

    public Quoter()
    {
      mState = ScanState.AFTER_SEPARATOR;
      mResult = "";
    }



    /**
     * Helper function. Considers c and state, appends c (and possibly extra
     * characters) to result, and returns an updated state.
     **/
    private ScanState processChar(char c, ScanState state, StringBuilder result)
    {
      ScanState resultState = state;

      switch (c) {
        case QUOTE:
            if (ScanState.AFTER_SEPARATOR == resultState) {
              // If we receive a quote after a separator, then we'll just
              // accept it and switch to IN_QUOTES.
              result.append(c);
              resultState = ScanState.IN_QUOTES;
            }
            else if (ScanState.AFTER_QUOTES == resultState) {
              // If we receive a quote after a quote, then we'll insert a
              // separator before accepting the quote.
              result.append(SEPARATOR);
              result.append(c);
              resultState = ScanState.IN_QUOTES;
            }
            else if (ScanState.IN_QUOTES == resultState) {
              // A quote while we're in quotes ends the quoted sequence and
              // places us into AFTER_QUOTES.
              result.append(c);
              resultState = ScanState.AFTER_QUOTES;
            }
            else if (ScanState.IN_TAG == resultState) {
              // A quote within an unquoted tag is an error - we accept it
              // by inserting a separator before this quote.
              result.append(SEPARATOR);
              result.append(c);
              resultState = ScanState.IN_QUOTES;
            }
            break;


          case SEPARATOR:
            if (ScanState.AFTER_SEPARATOR == resultState) {
              // If we're already after a separator, ignore this. There's no
              // point in accepting double separators.
            }
            else if (ScanState.AFTER_QUOTES == resultState) {
              // A separator after quotes puts us cleanly into AFTER_SEPARATOR
              // state.
              result.append(c);
              resultState = ScanState.AFTER_SEPARATOR;
            }
            else if (ScanState.IN_QUOTES == resultState) {
              // A separator in quotes is treated as a regular character - no
              // state change, but we allow it.
              result.append(c);
            }
            else if (ScanState.IN_TAG == resultState) {
              // A separator in a tag cleanly ends the tag, and puts us into
              // AFTER_SEPARATOR state.
              result.append(c);
              resultState = ScanState.AFTER_SEPARATOR;
            }
            break;


          default:
            if (ScanState.AFTER_SEPARATOR == resultState) {
              // Any other character after a separator starts a new tag.
              result.append(c);
              resultState = ScanState.IN_TAG;
            }
            else if (ScanState.AFTER_QUOTES == resultState) {
              // Any character after quotes is technically an error - we insert
              // a sperator, and then accept it.
              result.append(SEPARATOR);
              result.append(c);
              resultState = ScanState.IN_TAG;
            }
            else if (ScanState.IN_QUOTES == resultState) {
              // While we're in quotes, any non-special characters are allowed.
              result.append(c);
            }
            else if (ScanState.IN_TAG == resultState) {
              // And the same applies if we're in an unquoted tag.
              result.append(c);
            }
            break;
      }

      return resultState;
    }



    /**
     * Process a character sequence, and update mState/mResult in the
     * process.
     **/
    public void processBuffer(CharSequence original)
    {
      StringBuilder result = new StringBuilder();

      for (int i = 0 ; i < original.length() ; ++i) {
        char c = original.charAt(i);
        mState = processChar(c, mState, result);
      }

      mResult += result.toString();
    }
  }


  /***************************************************************************
   * AutoTagQuoter watches the EditText for changes, and automatically applies
   * proper quoting.
   **/
  private class AutoTagQuoter implements TextWatcher
  {
    private String  mBefore;
    private int     mInsertPosition;
    private int     mReplaceCount;
    private int     mInsertedLength;

    public void afterTextChanged(Editable text)
    {
      CharSequence inserted = text.subSequence(mInsertPosition,
          mInsertPosition + mInsertedLength);

      // Between what we stored in mBefore, and what we read into inserted, we
      // can now reconstruct the insertion character by character. That's
      // useful because it makes it somewhat easier to process quotation marks
      // within the inserted sequence, if a sequence with one or more quotation
      // marks was pasted.

      // Quoter keeps the state necessary to add the chunk of text before the
      // insertion point, the inserted text, and the chunk of text after the
      // replaced text bit by bit.
      Quoter quoter = new Quoter();
      quoter.processBuffer(mBefore.substring(0, mInsertPosition));
      quoter.processBuffer(inserted);
      // The cursor position should always be after the inserted part.
      int cursorPosition = quoter.mResult.length();
      quoter.processBuffer(mBefore.substring(mInsertPosition + mReplaceCount));

      // Only if the processed buffer is different from the original text do we
      // want to update the original text - otherwise we might end up in an
      // infinite loop.
      if (!text.toString().equals(quoter.mResult)) {
        text.replace(0, text.length(), quoter.mResult);
        setSelection(cursorPosition);
      }
    }


    public void beforeTextChanged(CharSequence s, int start, int count, int after)
    {
      mBefore = s.toString();
      mInsertPosition = start;
      mReplaceCount = count;
      mInsertedLength = after;
    }


    public void onTextChanged(CharSequence s, int start, int before, int count)
    {
      // ignore
    }
  }


  /***************************************************************************
   * Data members
   **/
  // Context
  private Context             mContext;


  /***************************************************************************
   * Implementation
   **/
  public EditTags(Context context)
  {
    super(context);
    mContext = context;

    this.addTextChangedListener(new AutoTagQuoter());
  }



  public EditTags(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    mContext = context;

    this.addTextChangedListener(new AutoTagQuoter());
  }



  public EditTags(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs);
    mContext = context;

    this.addTextChangedListener(new AutoTagQuoter());
  }



  /**
   * Return the contents of this edit field, as a list of tags.
   **/
  public List<Tag> getTags()
  {
    List<Tag> results = null;

    CharSequence text = getText();
    if (null != text && 0 != text.length()) {
      results = new LinkedList<Tag>();

      StringBuilder builder = new StringBuilder();
      ScanState state = ScanState.AFTER_SEPARATOR;
      Quoter quoter = new Quoter();
      for (int i = 0 ; i < text.length() ; ++i) {
        char c = text.charAt(i);
        state = quoter.processChar(c, state, builder);
        if (ScanState.AFTER_SEPARATOR == state) {
          String tag = cleanTag(builder.toString(), false);
          if (null != tag) {
            Tag t = new Tag();
            t.mNormalised = tag;
            results.add(t);
          }
          builder = new StringBuilder();
        }
      }

      String tag = cleanTag(builder.toString(), true);
      if (null != tag) {
        Tag t = new Tag();
        t.mNormalised = tag;
        results.add(t);
      }
    }

    return results;
  }



  /**
   * Sets the content as a list of Tag.
   **/
  public void setTags(List<Tag> tags)
  {
    // Normalize tags.
    String sep = String.format("%c", SEPARATOR);
    String tagstring = "";

    for (Tag tag : tags) {
      if (tag.mNormalised.contains(sep)) {
        tagstring += String.format("%c%s%c%c ", QUOTE, tag.mNormalised,
              QUOTE, SEPARATOR);
      }
      else {
        tagstring += String.format("%s%c ", tag.mNormalised, SEPARATOR);
      }
    }

    setText(tagstring);
  }



  /**
   * Cleans a tag by stripping it of a trailing separator and enclosing quotes,
   * if any.
   **/
  private String cleanTag(String tag, boolean stripUnmatchedQuotes)
  {
    // First, tags may *end* in a SEPARATOR char. Strip that.
    String needle = String.format("%c", SEPARATOR);
    if (tag.endsWith(needle)) {
      tag = tag.substring(0, tag.length() - 1);
    }

    // Next, strip leading and trailing whitespaces.
    tag = tag.trim();

    // Last, strip either matched or unmatched quotes.
    needle = String.format("%c", QUOTE);
    if (stripUnmatchedQuotes) {
      if (tag.startsWith(needle)) {
        tag = tag.substring(1);
      }
      if (tag.endsWith(needle)) {
        tag = tag.substring(0, tag.length() - 1);
      }
    }
    else {
      if (tag.startsWith(needle) && tag.endsWith(needle)) {
        tag = tag.substring(1);
        tag = tag.substring(0, tag.length() - 1);
      }
    }

    if (0 == tag.length()) {
      return null;
    }

    return tag;
  }
}

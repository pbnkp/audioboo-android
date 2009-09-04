/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

#include "FLAC/metadata.h"
#include "FLAC/stream_encoder.h"

#include <assert.h>

#include <jni.h>

namespace {

/*****************************************************************************
 * Constants
 **/
static char const * const FLACEncoder_classname   = "fm.audioboo.jni.FLACEncoder";
static char const * const FLACEncoder_mObject     = "mObject";

static char const * const FLACException_classname = "fm.audioboo.jni.FLACEncoder.FLACException";



/*****************************************************************************
 * Helper functions
 **/

/**
 * Retrieve FLAC__StreamEncoder instance from the passed jobject. 
 **/
static FLAC__StreamEncoder * get_encoder(JNIEnv * env, jobject obj)
{
  assert(sizeof(jlong) == sizeof(FLAC__StreamEncoder *));

  // Do the JNI dance for getting the mObject field
  jclass cls = env->FindClass(FLACEncoder_classname);
  jfieldID object_field = env->GetFieldID(cls, FLACEncoder_mObject, "J");
  jlong encoder_value = env->GetLongField(obj, object_field);
  return reinterpret_cast<FLAC__StreamEncoder *>(encoder_value);
}

} // anonymous namespace


/*****************************************************************************
 * JNI Implementation
 **/


extern "C" {

void
Java_fm_audioboo_jni_FLACEncoder_init(JNIEnv * env,
    jobject obj)
{
  FLAC__StreamEncoder * encoder_ptr = NULL;
  encoder_ptr = FLAC__stream_encoder_new();
  if (!encoder_ptr) {
    // Ignore return value of ThrowNew... all we could reasonably do is try and
    // throw another exception, after all.
    jclass exclass = env->FindClass(FLACException_classname);
    env->ThrowNew(exclass, "Could not create FLAC__StreamEncoder!");
    return;
  }

  assert(sizeof(jlong) == sizeof(FLAC__StreamEncoder *));
  jlong encoder_value = reinterpret_cast<jlong>(encoder_ptr);

  // Do the JNI dance for setting the mObject field
  jclass cls = env->FindClass(FLACEncoder_classname);
  jfieldID object_field = env->GetFieldID(cls, FLACEncoder_mObject, "J");
  env->SetLongField(obj, object_field, encoder_value);
}



void
Java_fm_audioboo_jni_FLACEncoder_deinit(JNIEnv * env,
    jobject obj)
{
  FLAC__StreamEncoder * encoder = get_encoder(env, obj);
  if (encoder) {
    FLAC__stream_encoder_delete(encoder);
  }
}

} // extern "C"

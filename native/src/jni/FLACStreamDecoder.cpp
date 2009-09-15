/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

// Define __STDINT_LIMITS to get INT8_MAX and INT16_MAX.
#define __STDINT_LIMITS 1
#include <stdint.h>
#include <assert.h>
#include <string.h>
#include <alloca.h>
#include <limits.h>

#include "FLAC/metadata.h"
#include "FLAC/stream_encoder.h"

#include "util.h"

#include <jni.h>

namespace aj = audioboo::jni;

namespace {

/*****************************************************************************
 * Constants
 **/
static char const * const FLACStreamDecoder_classname   = "fm.audioboo.jni.FLACStreamDecoder";
static char const * const FLACStreamDecoder_mObject     = "mObject";

static char const * const IllegalArgumentException_classname  = "java.lang.IllegalArgumentException";


/*****************************************************************************
 * FLAC callbacks forward declarations
 **/

FLAC__StreamDecoderWriteStatus flac_write_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__Frame const * frame,
    FLAC__int32 const * const buffer[],
    void * client_data);

void flac_metadata_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamMetadata const * metadata,
    void * client_data);

void flac_error_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamDecoderErrorStatus status,
    void * client_data);


/*****************************************************************************
 * Native FLACStreamDecoder representation
 **/

class FLACStreamDecoder
{
public:
  /**
   * Takes ownership of the infile.
   **/
  FLACStreamDecoder(char * infile)
    : m_infile(infile)
    , m_sample_rate(-1)
    , m_channels(-1)
    , m_bits_per_sample(-1)
    , m_min_buffer_size(-1)
    , m_decoder(NULL)
  {
  }


  /**
   * There are no exceptions here, so we need to "construct" outside the ctor.
   * Returns NULL on success, else an error message
   **/
  char const * const init()
  {
    if (!m_infile) {
      return "No file name given!";
    }


    // Try to create the Decoder instance
    m_decoder = FLAC__stream_decoder_new();
    if (!m_decoder) {
      return "Could not create FLAC__StreamDecoder!";
    }

    // Try initializing the file stream.
    FLAC__StreamDecoderInitStatus init_status = FLAC__stream_decoder_init_file(
        m_decoder, m_infile, flac_write_helper, flac_metadata_helper,
        flac_error_helper, this);

    if (FLAC__STREAM_DECODER_INIT_STATUS_OK != init_status) {
      return "Could not initialize FLAC__StreamDecoder for the given file!";
    }

    // Read first frame. That means we also process any metadata.
    FLAC__bool result = FLAC__stream_decoder_process_single(m_decoder);
    if (!result) {
      return "Could not read metadata from FLAC__StreamDecoder!";
    }

    return NULL;
  }



  /**
   * Destroys Decoder instance, releases infile
   **/
  ~FLACStreamDecoder()
  {
    if (m_decoder) {
      FLAC__stream_decoder_finish(m_decoder);
      FLAC__stream_decoder_delete(m_decoder);
      m_decoder = NULL;
    }

    if (m_infile) {
      free(m_infile);
      m_infile = NULL;
    }
  }



  FLAC__StreamDecoderWriteStatus write(
      FLAC__StreamDecoder const * decoder,
      FLAC__Frame const * frame,
      FLAC__int32 const * const buffer[])
  {
    assert(decoder == m_decoder);
    assert(m_buffer);

    if (8 == m_bits_per_sample) {
      return write_internal<int8_t>(frame->header.blocksize, buffer);
    }
    else if (16 == m_bits_per_sample) {
      return write_internal<int16_t>(frame->header.blocksize, buffer);
    }
    else {
      // Should not happen, just return an error.
      return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }
  }



  void metadata(
      FLAC__StreamDecoder const * decoder,
      FLAC__StreamMetadata const * metadata)
  {
    assert(decoder == m_decoder);

    if (!metadata || FLAC__METADATA_TYPE_STREAMINFO != metadata->type) {
      return;
    }

    m_sample_rate = metadata->data.stream_info.sample_rate;
    m_channels = metadata->data.stream_info.channels;
    m_bits_per_sample = metadata->data.stream_info.bits_per_sample;

    // We report the maximum block size, because a buffer that size will hold
    // any block. Yes, that's somewhat lazy, but blocks aren't *that* large.
    m_min_buffer_size = metadata->data.stream_info.max_blocksize
      * (m_bits_per_sample / 8)
      * m_channels;
  }



  void error(
      FLAC__StreamDecoder const * decoder,
      FLAC__StreamDecoderErrorStatus status)
  {
    assert(decoder == m_decoder);

    // FIXME
  }



  /**
   * Reads up to bufsize bytes from the FLAC stream and writes them into buffer.
   * Returns the number of bytes read, or -1 on fatal errors.
   **/
  int read(char * buffer, int bufsize)
  {
    // These are set temporarily - this object does not own the buffer.
    m_buffer = buffer;
    m_buf_size = bufsize / (m_bits_per_sample / 8);
    m_buf_used = 0;

    FLAC__bool result = 0;
    do {
      result = FLAC__stream_decoder_process_single(m_decoder);
    } while (result && m_buf_used < m_buf_size);

    // Clear m_buffer, just to be extra-paranoid that it won't accidentally
    // be freed.
    m_buffer = NULL;
    m_buf_size = 0;
    return result ? m_buf_used * (m_bits_per_sample / 8) : -1;
  }



  int bitsPerSample()
  {
    return m_bits_per_sample;
  }



  int channels()
  {
    return m_channels;
  }



  int sampleRate()
  {
    return m_sample_rate;
  }



  int minBufferSize()
  {
    return m_min_buffer_size;
  }

private:

  /**
   * Copies samples from buffer into m_buffer as sized samples, and interleaved
   * for multi-channel streams.
   **/
  template <typename sized_sampleT>
  FLAC__StreamDecoderWriteStatus
  write_internal(int blocksize, FLAC__int32 const * const buffer[])
  {
    sized_sampleT * outbuf = reinterpret_cast<sized_sampleT *>(m_buffer);
    m_buffer += m_buf_used;

    // We need to interleave the samples for each channel; FLAC on the other
    // hand keeps them in separate buffers.
    for (int i = 0 ; i < blocksize ; ++i) {
      for (int channel = 0 ; channel < m_channels ; ++channel) {
        if (m_buf_used >= m_buf_size) {
          // Should never happen, if the buffer we've been handed via the read()
          // function is large enough.
          return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        *outbuf = buffer[channel][i];
        ++outbuf;
        ++m_buf_used;
      }
    }

    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
  }



  // Configuration values passed to ctor
  char *  m_infile;

  // FLAC Decoder instance
  FLAC__StreamDecoder * m_decoder;

  // Metadata read from file
  int m_sample_rate;
  int m_channels;
  int m_bits_per_sample;
  int m_min_buffer_size;

  // Buffer related data, used by write callback and set by read function
  char *  m_buffer;
  int     m_buf_size;
  int     m_buf_used;
};



/*****************************************************************************
 * FLAC callbacks
 **/

FLAC__StreamDecoderWriteStatus flac_write_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__Frame const * frame,
    FLAC__int32 const * const buffer[],
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->write(decoder, frame, buffer);
}



void flac_metadata_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamMetadata const * metadata,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  dc->metadata(decoder, metadata);
}



void flac_error_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamDecoderErrorStatus status,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  dc->error(decoder, status);
}



/*****************************************************************************
 * Helper functions
 **/

/**
 * Retrieve FLACStreamDecoder instance from the passed jobject.
 **/
static FLACStreamDecoder * get_decoder(JNIEnv * env, jobject obj)
{
  assert(sizeof(jlong) >= sizeof(FLACStreamDecoder *));

  // Do the JNI dance for getting the mObject field
  jclass cls = env->FindClass(FLACStreamDecoder_classname);
  jfieldID object_field = env->GetFieldID(cls, FLACStreamDecoder_mObject, "J");
  jlong decoder_value = env->GetLongField(obj, object_field);

  env->DeleteLocalRef(cls);

  return reinterpret_cast<FLACStreamDecoder *>(decoder_value);
}


/**
 * Store FLACStreamDecoder instance in the passed jobject.
 **/
static void set_decoder(JNIEnv * env, jobject obj, FLACStreamDecoder * decoder)
{
  assert(sizeof(jlong) >= sizeof(FLACStreamDecoder *));

  // Do the JNI dance for setting the mObject field
  jlong decoder_value = reinterpret_cast<jlong>(decoder);
  jclass cls = env->FindClass(FLACStreamDecoder_classname);
  jfieldID object_field = env->GetFieldID(cls, FLACStreamDecoder_mObject, "J");
  env->SetLongField(obj, object_field, decoder_value);
  env->DeleteLocalRef(cls);
}




} // anonymous namespace



/*****************************************************************************
 * JNI Wrappers
 **/

extern "C" {

void
Java_fm_audioboo_jni_FLACStreamDecoder_init(JNIEnv * env, jobject obj,
    jstring infile)
{
  assert(sizeof(jlong) >= sizeof(FLACStreamDecoder *));

  FLACStreamDecoder * decoder = new FLACStreamDecoder(
      aj::convert_jstring_path(env, infile));

  char const * const error = decoder->init();
  if (NULL != error) {
    delete decoder;

    aj::throwByName(env, IllegalArgumentException_classname, error);
    return;
  }

  set_decoder(env, obj, decoder);
}



void
Java_fm_audioboo_jni_FLACStreamDecoder_deinit(JNIEnv * env, jobject obj)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);
  delete decoder;
  set_decoder(env, obj, NULL);
}



jint
Java_fm_audioboo_jni_FLACStreamDecoder_read(JNIEnv * env, jobject obj,
    jbyteArray buffer, jint bufsize)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);

  if (NULL == decoder) {
    aj::throwByName(env, IllegalArgumentException_classname,
        "Called without a valid Decoder instance!");
    return 0;
  }

  jboolean copy = false;
  char * buf = reinterpret_cast<char *>(env->GetByteArrayElements(buffer,
        &copy));

  return decoder->read(buf, bufsize);
}



jint
Java_fm_audioboo_jni_FLACStreamDecoder_bitsPerSample(JNIEnv * env, jobject obj)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);

  if (NULL == decoder) {
    aj::throwByName(env, IllegalArgumentException_classname,
        "Called without a valid Decoder instance!");
    return 0;
  }

  return decoder->bitsPerSample();
}



jint
Java_fm_audioboo_jni_FLACStreamDecoder_channels(JNIEnv * env, jobject obj)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);

  if (NULL == decoder) {
    aj::throwByName(env, IllegalArgumentException_classname,
        "Called without a valid Decoder instance!");
    return 0;
  }

  return decoder->channels();
}



jint
Java_fm_audioboo_jni_FLACStreamDecoder_sampleRate(JNIEnv * env, jobject obj)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);

  if (NULL == decoder) {
    aj::throwByName(env, IllegalArgumentException_classname,
        "Called without a valid Decoder instance!");
    return 0;
  }

  return decoder->sampleRate();
}



jint
Java_fm_audioboo_jni_FLACStreamDecoder_minBufferSize(JNIEnv * env, jobject obj)
{
  FLACStreamDecoder * decoder = get_decoder(env, obj);

  if (NULL == decoder) {
    aj::throwByName(env, IllegalArgumentException_classname,
        "Called without a valid Decoder instance!");
    return 0;
  }

  return decoder->minBufferSize();
}


} // extern "C"

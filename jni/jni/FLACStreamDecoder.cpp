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

#include <sys/stat.h>

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


FLAC__StreamDecoderReadStatus flac_read_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__byte buffer[],
    size_t * bytes,
    void * client_data);

FLAC__StreamDecoderSeekStatus flac_seek_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 absolute_byte_offset,
    void * client_data);

FLAC__StreamDecoderTellStatus flac_tell_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 * absolute_byte_offset,
    void * client_data);

FLAC__StreamDecoderLengthStatus flac_length_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 * stream_length,
    void * client_data);

FLAC__bool flac_eof_helper(
    FLAC__StreamDecoder const * decoder,
    void * client_data);

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
    : m_infile_name(infile)
    , m_infile(NULL)
    , m_sample_rate(-1)
    , m_channels(-1)
    , m_bits_per_sample(-1)
    , m_min_buffer_size(-1)
    , m_decoder(NULL)
    , m_finished(false)
  {
  }


  /**
   * There are no exceptions here, so we need to "construct" outside the ctor.
   * Returns NULL on success, else an error message
   **/
  char const * const init()
  {
    if (!m_infile_name) {
      return "No file name given!";
    }


    // Try to create the Decoder instance
    m_decoder = FLAC__stream_decoder_new();
    if (!m_decoder) {
      return "Could not create FLAC__StreamDecoder!";
    }

    // Open file.
    m_infile = fopen(m_infile_name, "r");
    if (!m_infile) {
      return "Could not open file!";
    }

    // Try initializing the file stream.
    FLAC__StreamDecoderInitStatus init_status = FLAC__stream_decoder_init_stream(
        m_decoder, flac_read_helper, flac_seek_helper, flac_tell_helper,
        flac_length_helper, flac_eof_helper, flac_write_helper,
        flac_metadata_helper, flac_error_helper, this);

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

    if (m_infile_name) {
      free(m_infile_name);
      m_infile_name = NULL;
    }

    if (m_infile) {
      fclose(m_infile);
      m_infile = NULL;
    }
  }



  /**
   * Reads up to bufsize bytes from the FLAC stream and writes them into buffer.
   * Returns the number of bytes read, or -1 on fatal errors.
   **/
  int read(char * buffer, int bufsize)
  {
    // If the decoder is at the end of the stream, exit immediately.
    int ret = checkState();
    if (0 != ret) {
      return ret;
    }

    // These are set temporarily - this object does not own the buffer.
    m_buffer = buffer;
    m_buf_size = bufsize / (m_bits_per_sample / 8);
    m_buf_used = 0;

    FLAC__bool result = 0;
    do {
      result = FLAC__stream_decoder_process_single(m_decoder);
    } while (result && m_buf_used < m_buf_size);
    ret = checkState();

    // Clear m_buffer, just to be extra-paranoid that it won't accidentally
    // be freed.
    m_buffer = NULL;
    m_buf_size = 0;
    return (result ? m_buf_used * (m_bits_per_sample / 8) : ret);
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


  /**
   * Callbacks for FLAC decoder.
   **/ 
  FLAC__StreamDecoderReadStatus cb_read(
      FLAC__StreamDecoder const * decoder,
      FLAC__byte buffer[],
      size_t * bytes)
  {
    size_t expected = *bytes;

    if (expected <= 0) {
      return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
    }

    *bytes = fread(buffer, sizeof(FLAC__byte), expected, m_infile);

    if (ferror(m_infile)) {
      return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
    }
    else if (feof(m_infile)) {
      m_finished = true;
      return FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM;
    }

    return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
  }



  FLAC__StreamDecoderSeekStatus cb_seek(
      FLAC__StreamDecoder const * decoder,
      FLAC__uint64 absolute_byte_offset)
  {
    if (0 > fseeko(m_infile, static_cast<off_t>(absolute_byte_offset), SEEK_SET)) {
      return FLAC__STREAM_DECODER_SEEK_STATUS_ERROR;
    }

    return FLAC__STREAM_DECODER_SEEK_STATUS_OK;
  }



  FLAC__StreamDecoderTellStatus cb_tell(
      FLAC__StreamDecoder const * decoder,
      FLAC__uint64 * absolute_byte_offset)
  {
    off_t pos = 0;

    if (0 > (pos = ftello(m_infile))) {
      return FLAC__STREAM_DECODER_TELL_STATUS_ERROR;
    }

    *absolute_byte_offset = static_cast<FLAC__uint64>(pos);

    return FLAC__STREAM_DECODER_TELL_STATUS_OK;
  }



  FLAC__StreamDecoderLengthStatus cb_length(
      FLAC__StreamDecoder const * decoder,
      FLAC__uint64 * stream_length)
  {
    struct stat filestats;

    if (0 != fstat(fileno(m_infile), &filestats)) {
      return FLAC__STREAM_DECODER_LENGTH_STATUS_ERROR;
    }
    else {
      *stream_length = static_cast<FLAC__uint64>(filestats.st_size);
      return FLAC__STREAM_DECODER_LENGTH_STATUS_OK;
    }
  }



  FLAC__bool cb_eof(
      FLAC__StreamDecoder const * decoder)
  {
    if (feof(m_infile)) {
      m_finished = true;
    }
    return m_finished;
  }



  FLAC__StreamDecoderWriteStatus cb_write(
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



  void cb_metadata(
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



  void cb_error(
      FLAC__StreamDecoder const * decoder,
      FLAC__StreamDecoderErrorStatus status)
  {
    assert(decoder == m_decoder);
    m_finished = true;
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



  /**
   * Translate decoder state into something we can report as a return
   * value from read()
   **/
  int checkState()
  {
    if (m_finished) {
      return -1;
    }

    FLAC__StreamDecoderState state = FLAC__stream_decoder_get_state(m_decoder);
    switch (state) {
      case FLAC__STREAM_DECODER_SEARCH_FOR_METADATA:
      case FLAC__STREAM_DECODER_READ_METADATA:
      case FLAC__STREAM_DECODER_SEARCH_FOR_FRAME_SYNC:
      case FLAC__STREAM_DECODER_READ_FRAME:
        return 0;

      case FLAC__STREAM_DECODER_END_OF_STREAM:
        m_finished = true;
        return -2;

      case FLAC__STREAM_DECODER_OGG_ERROR:
        return -3;

      case FLAC__STREAM_DECODER_SEEK_ERROR:
        return -4;

      case FLAC__STREAM_DECODER_ABORTED:
        return -5;

      case FLAC__STREAM_DECODER_MEMORY_ALLOCATION_ERROR:
        return -6;

      case FLAC__STREAM_DECODER_UNINITIALIZED:
        return -7;

      default:
        return -8;
    }
  }


  // Configuration values passed to ctor
  char *  m_infile_name;

  // FILE pointer we're reading.
  FILE *  m_infile;

  // FLAC Decoder instance
  FLAC__StreamDecoder * m_decoder;

  // Metadata read from file
  int m_sample_rate;
  int m_channels;
  int m_bits_per_sample;
  int m_min_buffer_size;

  bool m_finished;

  // Buffer related data, used by write callback and set by read function
  char *  m_buffer;
  int     m_buf_size;
  int     m_buf_used;
};



/*****************************************************************************
 * FLAC callbacks
 **/
FLAC__StreamDecoderReadStatus flac_read_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__byte buffer[],
    size_t * bytes,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_read(decoder, buffer, bytes);
}



FLAC__StreamDecoderSeekStatus flac_seek_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 absolute_byte_offset,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_seek(decoder, absolute_byte_offset);
}



FLAC__StreamDecoderTellStatus flac_tell_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 * absolute_byte_offset,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_tell(decoder, absolute_byte_offset);
}



FLAC__StreamDecoderLengthStatus flac_length_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__uint64 * stream_length,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_length(decoder, stream_length);
}



FLAC__bool flac_eof_helper(
    FLAC__StreamDecoder const * decoder,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_eof(decoder);
}



FLAC__StreamDecoderWriteStatus flac_write_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__Frame const * frame,
    FLAC__int32 const * const buffer[],
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  return dc->cb_write(decoder, frame, buffer);
}



void flac_metadata_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamMetadata const * metadata,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  dc->cb_metadata(decoder, metadata);
}



void flac_error_helper(
    FLAC__StreamDecoder const * decoder,
    FLAC__StreamDecoderErrorStatus status,
    void * client_data)
{
  FLACStreamDecoder * dc = static_cast<FLACStreamDecoder *>(client_data);
  dc->cb_error(decoder, status);
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

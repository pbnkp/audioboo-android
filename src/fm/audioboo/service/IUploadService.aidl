/**
 * This file is part of AudioBoo, an android program for audio blogging.
 * Copyright (C) 2011 BestBefore Media Ltd. All rights reserved.
 *
 * Author: Jens Finkhaeuser <jens@finkhaeuser.de>
 *
 * $Id$
 **/

package fm.audioboo.service;

import fm.audioboo.data.BooData;


/**
 * Service interface for UploadService.
 **/
interface IUploadService
{
  /**
   * Upload the given Boo. Note that the Boo is placed into an upload queue,
   * but may not be uploaded immediately.
   * Returns true if the upload was successfully scheduled, false otherwise.
   **/
  boolean upload(in BooData boo);
}

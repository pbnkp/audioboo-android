/**
 * This file is part of Audioboo, an android program for audio blogging.
 * Copyright (C) 2011 Audioboo Ltd. All rights reserved.
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
   * Signal the service to start processing the upload queue. If the service is
   * already processing, the upload queue will be refreshed.
   **/
  void processQueue();
}

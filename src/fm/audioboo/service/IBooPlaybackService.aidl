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
 * Service interface for BooPlaybackService.
 **/
interface IBooPlaybackService
{
  void play(in BooData boo);

  void stop();
  void pause();
  void resume();

  int getState();

  // FIXME progress listener

//  /**
//   * Resolve dependencies for the given package name. That encompasses:
//   * - Scanning the package for dependency information
//   * - Querying the system for unmet dependencies
//   * - Querying data sources for packages that would meet those dependencies
//   * - Displaying the results, and offering them to the user for installation.
//   **/
//  void resolveDependencies(String packageName);
//
//
//  /**
//   * Returns the list of Intents specified as mandatory dependencies in the
//   * named package, or null if no such information was found.
//   **/
//  List<Intent> scanPackageForDependencies(String packageName);
//
//
//  /**
//   * Accepts a list of Intents, and filters out those that can currently be
//   * served by the system. It's just a thin wrapper around PackageManager, but
//   * honours the de.finkhaeuser.dm.extras.COMPONENT_TYPE extra as set by
//   * scanPackageForDependencies, if present. Returns the remaining Intents that
//   * cannot be served by the system at the moment.
//   **/
//  List<Intent> removeResolvableIntents(in List<Intent> intents);
//
//
//  /**
//   * Displays a dialog with a choice of packages that would serve the specified
//   * intents.
//   **/
//  void displayChoicesForIntents(in List<Intent> intents);
}

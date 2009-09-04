#! /bin/bash
#
# This file is part of AudioBoo, an android program for audio blogging.
# Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
#
# Author: Jens Finkhaeuser <jens@finkhaeuser.de>
#
# $Id$

# Build native stuff. By default, build the release version, but if the
# a command line target "debug" is provided, build the debug version
BUILD_TARGET="release"
if [ ! -z "$1" ] ; then
  if [ "$1" = "debug" ] ; then
    BUILD_TARGET="debug"
  elif [ "$1" = "release" ] ; then
    true
  else
    echo "The only acceptable parameters for this script are 'debug' or 'release'" >&2
    exit 4
  fi
fi

COMMAND="$2"

cd ndk && make ${COMMAND} APP="AudioBoo-${BUILD_TARGET}" V=1

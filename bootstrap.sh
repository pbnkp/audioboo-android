#! /bin/bash
#
# This file is part of Audioboo, an android program for audio blogging.
# Copyright (C) 2011 Audioboo Ltd. All rights reserved.
#
# Author: Jens Finkhaeuser <jens@finkhaeuser.de>
#
# $Id: bootstrap.sh 1272 2010-11-01 14:07:29Z unwesen $

# 1. Make sure we're in the project root. We'll look for AndroidManifest.xml,
#    and check whether that includes a reference to Audioboo.
PROJECT_PATH=$(pwd)
MANIFEST="${PROJECT_PATH}/AndroidManifest.xml"
if [ ! -f "${MANIFEST}" ] ; then
  echo "No AndroidManifest.xml file found!" >&2
  echo "You need to run this script in the project root." >&2
  exit 1
fi

if grep -qi audioboo "${MANIFEST}" ; then
  true
else
  echo "No mention of Audioboo found in AndroidManifest.xml!" >&2
  echo "You need to run this script in the project root." >&2
  exit 2
fi

# 2. Make sure that there's an NDK link in the project dire
NDK_LINK="${PROJECT_PATH}/ndk"
if [ ! -L "${NDK_LINK}" ] ; then
  echo "Please create a symbolic link to the Android NDK in the project root," >&2
  echo "i.e. run: ln -snf <ndk path> ndk" >&2
  exit 3
fi

# 3. Build native stuff. Force rebuilding; if you don't want to rebuild, run
#    ndk/ndk-build manually without the -B parameter:
#    $ ndk/ndk-build V=1
ndk/ndk-build -B "$@"

# 4. Build external libs. The artefacts end up in the libs subdirectory.
./build-externals.sh "$@"

#! /bin/bash
#
# This file is part of AudioBoo, an android program for audio blogging.
# Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
#
# Author: Jens Finkhaeuser <jens@finkhaeuser.de>
#
# $Id$

# 1. Make sure we're in the project root. We'll look for AndroidManifest.xml,
#    and check whether that includes a reference to AudioBoo.
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
  echo "No mention of AudioBoo found in AndroidManifest.xml!" >&2
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

# 3. In the NDK's directory, create symbolic links back to this project.
ln -snf "${PROJECT_PATH}/native/src" "${NDK_LINK}/sources/AudioBoo"
ln -snf "${PROJECT_PATH}/native/apps/debug" "${NDK_LINK}/apps/AudioBoo-debug"
ln -snf "${PROJECT_PATH}/native/apps/release" "${NDK_LINK}/apps/AudioBoo-release"

# 4. Create Application.mk in the apps directories.
for target in debug release ; do
  cat "${PROJECT_PATH}/native/apps/Application.mk.in" | \
    sed -e "s;@PATH@;${PROJECT_PATH};g" \
        -e "s;@OPTIMIZATION@;${target};g" \
        >"${PROJECT_PATH}/native/apps/${target}/Application.mk"
done

# 5. Build native stuff. By default, build the release version, but if the
#    a command line target "debug" is provided, build the debug version
./build-native.sh "$@"

# 6. Build external libs. The artefacts end up in the libs subdirectory.
./build-externals.sh "$@"

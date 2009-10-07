#! /bin/bash
#
# This file is part of AudioBoo, an android program for audio blogging.
# Copyright (C) 2009 BestBefore Media Ltd. All rights reserved.
#
# Author: Jens Finkhaeuser <jens@finkhaeuser.de>
#
# $Id$

# 1. Build dnssjava
pushd externals/dnsjava >/dev/null
ant
cp *.jar ../../libs
popd >/dev/null

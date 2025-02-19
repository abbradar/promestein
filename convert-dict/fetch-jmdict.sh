#!/bin/sh
set -e

if [ ! -e data/JMdict.gz ]; then
  mkdir -p data
  curl -L ftp://ftp.edrdg.org/pub/Nihongo//JMdict.gz -o data/JMdict.gz
fi

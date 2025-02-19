#!/usr/bin/env nix-shell
#!nix-shell -i bash
set -e

npm install
npx shadow-cljs release app
lein jar

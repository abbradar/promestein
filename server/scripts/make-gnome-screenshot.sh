#!/bin/sh -e

dir="$(mktemp -d promestein-screenshot.XXXXXX)"

cleanup() {
  rm -rf "$dir"
}
trap cleanup EXIT

gnome-screenshot -w -f "$dir/image.png"
curl -F "image=@$dir/image.png" http://localhost:8080/pages/new/image

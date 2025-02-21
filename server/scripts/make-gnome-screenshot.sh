#!/bin/sh -e

host="http://localhost:8080"
if [ -n "$1" ]; then
  host="$1"
fi

dir="$(mktemp --tmpdir -d promestein-screenshot.XXXXXX)"
cleanup() {
  rm -rf "$dir"
}
trap cleanup EXIT

gnome-screenshot -w -f "$dir/image.png"
curl -F "image=@$dir/image.png" "$host/pages/new/image"

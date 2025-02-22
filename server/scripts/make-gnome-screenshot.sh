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

image="$dir/image.png"
gnome-screenshot -w -f "$image"
curl -F "image=@$image" "$host/pages/new/image"

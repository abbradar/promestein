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
gamescopectl screenshot "$image"
# It's async, gamescope is running from root and we don't even have inotifywait.
# This is the best I could do.
for i in $(seq 1 100); do
  size=$(stat --printf="%s" "$image" 2>/dev/null || true)
  # Treat the file as finished when the size is non-zero and stopped growing.
  if [ -n "$size" ] && [ "$size" != "0" ] && [ "$size" = "$old_size" ]; then
    break
  else
    old_size="$size"
    sleep 0.01
  fi
done
curl -F "image=@$image" "$host/pages/new/image"

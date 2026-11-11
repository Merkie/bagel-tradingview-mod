#!/usr/bin/env bash
# Makes a real-time video of the startup animation from the frames IntroGameTest shoots (60 a second of
# animation time, at 1920 x 1080). Run the client game tests first.
#   tools/intro-preview.sh [out.mp4]      default ~/Desktop/bageltv-intro-preview.mp4
set -euo pipefail
cd "$(dirname "$0")/.."
shots=build/run/clientGameTest/screenshots
out=${1:-$HOME/Desktop/bageltv-intro-preview.mp4}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
n=0
for f in "$shots"/*intro-frame-*.png; do
  ln -s "$PWD/$f" "$tmp/$(printf '%04d' $n).png"
  n=$((n + 1))
done
[ "$n" -gt 0 ] || { echo "no intro frames in $shots; run ./gradlew runClientGameTest" >&2; exit 1; }
# Hold the last frame for a second so the end is not cut off by a player's controls.
ffmpeg -y -loglevel error -framerate 60 -i "$tmp/%04d.png" -vf "tpad=stop_mode=clone:stop_duration=1" \
  -c:v libx264 -pix_fmt yuv420p -crf 14 -preset slow -movflags +faststart "$out"
echo "$out ($n frames)"

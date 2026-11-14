#!/usr/bin/env bash
# Refreshes the README's images (docs/images/) from the screenshots the client game tests take: the seven views at
# 1920 x 1080 and the startup animation as a GIF. Run ./gradlew runClientGameTest first.
set -euo pipefail
cd "$(dirname "$0")/.."
shots=build/run/clientGameTest/screenshots
out=docs/images
mkdir -p "$out"
for view in market item screener orders player economy billford; do
  cp "$shots"/*_"$view"-1080.png "$out/$view.png"
done
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
n=0
for f in "$shots"/*intro-frame-*.png; do
  ln -s "$PWD/$f" "$tmp/$(printf '%04d' $n).png"
  n=$((n + 1))
done
[ "$n" -gt 0 ] || { echo "no intro frames in $shots; run ./gradlew runClientGameTest" >&2; exit 1; }
# Half size, 30 frames a second, and two seconds on the open terminal before it loops.
ffmpeg -y -loglevel error -framerate 60 -i "$tmp/%04d.png" -vf "fps=30,scale=960:-1:flags=lanczos,tpad=stop_mode=clone:stop_duration=2,split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" "$out/intro.gif"
ls -l "$out"

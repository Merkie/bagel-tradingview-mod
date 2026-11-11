#!/bin/bash
# Captures the live API's answers into the test fixtures the fake API serves (src/gametest/resources/fixtures).
# Run from anywhere; then run the client game tests and look at the "sparse" screenshots.
set -euo pipefail
base="${BAGELTV_API:-https://bageltv.archers.tools}"
out="$(cd "$(dirname "$0")/.." && pwd)/src/gametest/resources/fixtures"
get() { curl -fsS -m 30 -A "BagelTradingView/fixtures" "$base$2" -o "$out/$1.json"; echo "$1 $(wc -c < "$out/$1.json")"; }
get status /v1/status
get overview "/v1/market/overview?window=24h"
get market_candles "/v1/market/candles?tf=5m&limit=300"
get leaders "/v1/market/leaders?metric=turnover&window=24h&tf=1h&top=10"
get economy "/v1/economy?window=7d"
get items "/v1/items?window=24h&sort=turnover&limit=100"
get orders "/v1/orders?sort=pool&limit=100"
get fills "/v1/orders/fills?limit=20"
get players "/v1/players?sort=trueBalance&limit=100"
get search "/v1/players/search?q=si"
get player_simpinhoe "/v1/players/Simpinhoe"
get item_diamond "/v1/items/diamond?window=24h"
get item_diamond_candles "/v1/items/diamond/candles?tf=5m&limit=2000&fill=1"
get owners "/v1/orders/owners?window=24h&sort=paid&limit=100"
get players_history "/v1/players/history?top=8&window=30d"
get item_diamond_trades "/v1/items/diamond/trades?limit=100"
get item_diamond_book "/v1/items/diamond/book"
# A book of a known kind: thin, and its counts are only a floor.
get item_variant "/v1/items/enchanted_book%5Bmending%3D1%5D?window=24h"
get item_variant_candles "/v1/items/enchanted_book%5Bmending%3D1%5D/candles?tf=5m&limit=2000&fill=1"
get item_variant_trades "/v1/items/enchanted_book%5Bmending%3D1%5D/trades?limit=100"
get item_variant_book "/v1/items/enchanted_book%5Bmending%3D1%5D/book"
# One real head, served by the fake for every player.
curl -fsS -m 30 -A "BagelTradingView/fixtures" "https://bagelsmp.b-cdn.net/api/skin/Simpinhoe?size=32" -o "$out/head.png"; echo "head $(wc -c < "$out/head.png")"

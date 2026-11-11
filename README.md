# BagelTradingView (mod)

A client-only Fabric mod for Minecraft 26.3 (this branch; `main` is 26.2): a market terminal for the BagelSMP economy, drawn in pixel art.
Press **B** in game to open it. It reads the public BagelTV API (live at `https://bageltv.archers.tools`) and never talks to the Minecraft server. It needs only Fabric Loader.

## What is in it

- **Market**: headline tiles, two chart slots that each show any board (market index, top items, top players'
  balances, money supply, players online, money in buy orders), and eight ranked lists.
- **Screener**: every traded item in a table sorted on the server from its headers, with text, category and
  minimum-trades filters.
- **Item**: the price chart for either market (AH sales, filled buy orders, or both as two lines) with volume,
  VWAP and that market's level now (AH price, top order); how the item's money splits between the two; AH sales
  by window; listings and orders by price; recent AH sales; open buy orders and current listings. The panels'
  boundaries drag.
- **Orders**: every open buy order, sortable, with the ones worth filling marked; and **Owners**, who ranks by
  what their orders actually paid out against what they only have locked up.
- **Players** and the player inspector: the rich list, and one player's balance over time, orders, what their
  orders paid out, and auction activity. Players show their heads.
- **Economy**: money supply, players online, money in buy orders.

Every chart is the same widget with the same header: wheel to zoom, drag to pan, Shift-drag or right-drag to zoom
to a box, drag the price axis to stretch it, double click to fit, log scale, maximize, and drawing tools behind
the pencil whose drawings can be selected, moved, reshaped and are saved per chart.

It shows only what the data supports: an item with too few candles draws each sale as a dot; a window longer than
the recorded history says how much it really covers; counts that are only a minimum carry a mark.

## Build and test

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
./gradlew --no-daemon build              # build/libs/bagel-tradingview-<version>.jar
./gradlew --no-daemon runClientGameTest  # a hidden, muted client; screenshots in build/run/clientGameTest/screenshots/
```

A passing test run logs eight `PASS:` lines: Hotkey, Market, Chart, Layout, Tables, Search, Data and Terminal. The tests
never reach the live API: the test mod starts `FakeApi`, a local stand-in, and points the `bageltv.api` system
property at it.

Files it writes, under the game's `config/`: `bagel-tradingview.json` (hotkey, window, size, chart choices, panel sizes) and
`bagel-tradingview-drawings.json` (drawings per chart).

## Layout

```
src/client/java/com/merkie/bageltv/
  BagelTV.java, Config.java, Fmt.java   entry point and hotkey, settings file, money and time formatting
  mixin/KeyboardHandlerMixin.java       the hotkey (no key mapping is registered)
  data/Api.java, Models.java            HTTP, per-path cache and refresh; the API's JSON shapes
  chart/                                the chart engine: Chart, Canvas, Series (Candles, Line, Histogram, Stack),
                                        TimeTicks, ValueFormat, Drawings, Spark
  ui/                                   TerminalScreen (window, rail, search, history), Ui (drawing and input for
                                        one frame, popovers, dropdowns), Theme, Icon, Table, TextField, Splitter,
                                        Items (icons and item colors), Heads (player faces)
  view/                                 one class per view, plus Board (a chart with its own data), Parts (shared
                                        pieces) and Pager (paged lists)
src/gametest/                           FakeApi, Harness, the eight tests, fixtures captured from the live API
tools/capture-fixtures.sh               refreshes the fixtures
docs/images/                            screenshots from the tests
```

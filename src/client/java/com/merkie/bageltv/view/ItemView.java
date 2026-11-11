package com.merkie.bageltv.view;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Candles;
import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.chart.Histogram;
import com.merkie.bageltv.chart.Line;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.ItemRow;
import com.merkie.bageltv.data.Models.OrderRow;
import com.merkie.bageltv.data.Models.Trade;
import com.merkie.bageltv.ui.Icon;
import com.merkie.bageltv.ui.Splitter;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One item: its price chart, stats by window, listings and orders by price, trades tape and open buy orders. The
 * auction house and buy orders are two markets with their own prices and are never blended into one.
 */
public final class ItemView extends View {
    private static final String[] TFS = {"5m", "15m", "1h", "4h", "1d"};
    private static final String[] STAT_ROWS = {"Trades", "Volume", "Turnover", "VWAP", "Median", "Low", "High",
            "Change", "Sold", "Sells in"};

    private final String key;
    private final Chart chart;
    private final Table<Trade> tape = new Table<>(Theme.LINE);
    private final Table<OrderRow> orders = new Table<>(Theme.LINE);
    private String builtShape = "";
    private final List<OrderRow> orderRows = new ArrayList<>();
    private Object ordersFor;
    private final Table<Models.Listing> listings = new Table<>(Theme.LINE);
    private boolean showListings;
    private boolean logSeen;
    private boolean salesOnly;
    private final Splitter sideSplit = new Splitter(true, size -> BagelTV.config().itemSide = size);
    private final Splitter lowerSplit = new Splitter(false, size -> BagelTV.config().itemLower = size);
    /** The candle size: picked from the item's own history until the player picks one. */
    private String tf;
    /** Which market the chart shows: 0 the auction house, 1 filled buy orders, 2 both as lines; -1 until known. */
    private int market = -1;
    private Object[] builtFrom = new Object[0];
    private static final String[] MARKETS = {"AH", "Orders", "Both"};
    private static final String[] TITLES = {"AH sales, price each", "Filled orders, price each",
            "AH and orders, price each"};

    public ItemView(String key) {
        this.key = key;
        this.chart = new Chart("item:" + key);
        chart.emptyText = "No trades recorded in this range yet";
        chart.histogramFormat = com.merkie.bageltv.chart.ValueFormat.COUNT;

        tape.empty = "No trades recorded yet";
        tape.col("Time", 52, (ui, row, x, y, w, h) -> Table.left(ui, when(row.t), x, y, w, h, Theme.MUTED)).left();
        tape.col("Qty", 30, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.qty), x, y, w, h, Theme.TEXT));
        tape.col("Price", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.unit), x, y, w, h,
                row.outlier ? Theme.FAINT : Theme.TEXT)).tip("Unit price");
        tape.col("Seller > Buyer", 110, (ui, row, x, y, w, h) -> {
            int ty = Ui.textY(y, h);
            int half = (w - 22) / 2;
            int used = Parts.player(ui, row.seller, x + 6, ty, half);
            ui.text(">", x + 6 + used + 4, ty, Theme.FAINT);
            Parts.player(ui, row.buyer, x + 6 + used + 14, ty, w - 26 - used);
        }).left().flex(1);

        listings.empty = "No listings seen";
        listings.col("Seller", 70, (ui, row, x, y, w, h) -> Parts.player(ui, row.seller, x + 4, Ui.textY(y, h),
                w - 8)).left().flex(1);
        listings.col("Each", 54, (ui, row, x, y, w, h) -> Table.right(ui, listed(row, row.unit), x, y, w, h,
                Theme.DOWN));
        listings.col("Qty", 34, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.qty), x, y, w, h,
                Theme.TEXT));
        listings.col("Total", 54, (ui, row, x, y, w, h) -> Table.right(ui, listed(row, row.price), x, y, w, h,
                Theme.MUTED)).drop(1);

        orders.empty = "No open buy orders";
        orders.col("Buyer", 70, (ui, row, x, y, w, h) -> Parts.player(ui, row.owner, x + 4, Ui.textY(y, h), w - 8))
                .left().flex(1);
        orders.col("Pays", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.price), x, y, w, h, Theme.UP));
        orders.col("Left", 44, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.remaining), x, y, w, h,
                Theme.TEXT));
        orders.col("Value", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.value != null ? row.value
                : row.price != null && row.remaining != null ? row.price * row.remaining : null), x, y, w, h,
                Theme.MUTED)).drop(1);
    }

    /** A listing's price, marked when the game menu rounded it down. */
    private static String listed(Models.Listing listing, double amount) {
        return (listing.exact() ? "" : "~") + Fmt.money(amount);
    }

    private static String when(long t) {
        long now = System.currentTimeMillis() / 1000;
        return now - t < 20 * 3600 ? Fmt.clockSeconds(t) : Fmt.day(t);
    }

    private static List<String> tradeTip(Trade trade) {
        List<String> lines = new ArrayList<>();
        lines.add(Fmt.dayClock(trade.t) + ":" + Fmt.clockSeconds(trade.t).substring(6));
        lines.add("Quantity\t" + Fmt.countExact(trade.qty));
        lines.add("Unit price\t" + Fmt.moneyExact(trade.unit));
        lines.add("Total\t" + Fmt.moneyExact(trade.price));
        if (trade.timeToSell != null) lines.add("Listed for\t" + Fmt.duration(trade.timeToSell));
        if (trade.outlier) lines.add("Outlier: left out of candles and stats");
        return lines;
    }

    public Table<Trade> tape() {
        return tape;
    }

    public Chart chart() {
        return chart;
    }

    @Override
    public boolean key(int key) {
        return chart.key(key);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        var config = BagelTV.config();
        // The header's change is over 24 hours; the stats table under it has every window.
        Api.Query<ItemRow> item = Api.get("/v1/items/" + Api.encode(key) + "?window=24h", ItemRow.class);
        header(ui, item.data, x, y, w);
        int top = y + 38;
        String note = coverageNote(item.data);
        if (note != null) {
            ui.text(ui.fit(note, w - 4), x + 1, top - 1, Theme.WARN);
            top += 11;
        }
        int bodyH = y + h - top;
        if (item.data == null && item.error != null) {
            ui.error(x, top, w, bodyH, item.error, item::retry);
            return;
        }
        // Both boundaries drag: the side column's width and the lower tables' height.
        int sideMax = w - 220 - Parts.GAP, lowerMax = bodyH - 90 - Parts.GAP;
        int side = Math.clamp(config.itemSide > 0 ? config.itemSide : w * 34 / 100, 150, Math.max(150, sideMax));
        int lower = Math.clamp(config.itemLower > 0 ? config.itemLower : bodyH * 32 / 100, 40,
                Math.max(40, lowerMax));
        int left = w - side - Parts.GAP;
        int chartH = bodyH - lower - Parts.GAP;

        chart(ui, item.data, x, top, left, chartH);
        Api.Query<Models.Book> book = Api.get("/v1/items/" + Api.encode(key) + "/book", Models.Book.class);
        int tapeW = (left - Parts.GAP) * 56 / 100;
        tape(ui, x, top + chartH + Parts.GAP, tapeW, lower);
        openOrders(ui, book, x + tapeW + Parts.GAP, top + chartH + Parts.GAP, left - tapeW - Parts.GAP, lower);

        int sx = x + left + Parts.GAP;
        int statsH = 14 + STAT_ROWS.length * 10 + 13;
        stats(ui, item, sx, top, side, statsH);
        book(ui, book, sx, top + statsH + Parts.GAP, side, bodyH - statsH - Parts.GAP);
        if (!chart.maximized()) {
            lowerSplit.render(ui, x, top + chartH, left, Parts.GAP, lower, 40, lowerMax);
            sideSplit.render(ui, x + left, top, Parts.GAP, bodyH, side, 150, sideMax);
        }
    }

    private void header(Ui ui, ItemRow row, int x, int y, int w) {
        ui.item(key, x, y + 1, 2F);
        var stack = com.merkie.bageltv.ui.Items.stack(key);
        String name = row != null ? row.label() : stack.isEmpty() ? key : stack.getHoverName().getString();
        String sub = key + (row != null && row.category != null ? "  " + label(row.category) : "");
        int textX = x + 38;
        int nameW = ui.text(name, textX, y + 5, Theme.TEXT);
        int subW = ui.text(sub, textX, y + 18, Theme.FAINT);
        if (row == null) return;
        com.merkie.bageltv.ui.TerminalScreen.NAMES.put(key, name);
        int px = textX + Math.max(nameW, subW) + 18;
        String price = Fmt.money(row.last);
        ui.textScaled(price, px, y + 5, 2, Theme.TEXT);
        int cx = px + ui.width(price) * 2 + 8;
        // A change needs a full day before to compare with; without one, nothing is shown.
        if (row.change != null) {
            ui.text(Fmt.pct(row.change), cx, y + 5, Theme.change(row.change));
            ui.text("vs prior 24h", cx, y + 15, Theme.FAINT);
        }
        if (ui.hover(px, y + 2, cx - px + 70, 24)) {
            ui.tip("Latest sale", "Unit price\t" + Fmt.moneyExact(row.last), "VWAP 24h\t"
                    + Fmt.moneyExact(row.vwap), "Worth (/sell)\t" + Fmt.moneyExact(row.worth));
        }
        // Quick levels, when there is room for them. An order nobody fills is not a price, so what orders paid
        // leads and the top open order sits beside it, muted.
        int qx = cx + 84;
        boolean paid = row.orderVwap != null;
        String[][] quick = {{"AH price", Fmt.money(row.bestAsk)}, {"Orders paid", paid ? Fmt.money(row.orderVwap)
                : null}, {"Top order", row.bestBid == null ? null : Fmt.money(row.bestBid)},
                {"", row.ahShare == null ? null : "AH " + Fmt.share(row.ahShare) + "  Orders "
                        + Fmt.share(1 - row.ahShare)},
                {"Worth", Fmt.money(row.worth)}, {"Listed", row.askListings == null || row.askListings == 0 ? null
                : Fmt.count(row.askListings) + " / " + Fmt.count(row.askUnits)},
                {"Shop", row.shopBuy == null ? null : Fmt.money(row.shopBuy)}};
        int[] colors = {Theme.ACCENT, Theme.ORDERS, Theme.MUTED, Theme.MUTED, Theme.WARN, Theme.TEXT, Theme.MUTED};
        int limit = x + w;
        for (int i = 0; i < quick.length; i++) {
            if (quick[i][1] == null) continue;
            int width = Math.max(ui.width(quick[i][0]), ui.width(quick[i][1])) + 14;
            if (qx + width > limit) break;
            if (i == 3) {
                // Where the item's money moved: the auction house's part, then buy orders'.
                int bar = width - 14, ah = (int) Math.round(bar * Math.clamp(row.ahShare, 0, 1));
                ui.text(quick[i][1], qx, y + 5, Theme.MUTED);
                ui.fill(qx, y + 17, ah, 4, Theme.ACCENT);
                ui.fill(qx + ah, y + 17, bar - ah, 4, Theme.ORDERS);
                ui.mark("split", qx + bar / 2, y + 12);
                if (ui.hover(qx, y + 3, bar, 20)) {
                    ui.tip("Money moved in 24h", "AH sales\t" + Fmt.moneyExact(row.turnover),
                            "Filled orders\t" + Fmt.moneyExact(row.orderTurnover));
                }
            } else {
                ui.text(quick[i][0], qx, y + 5, Theme.FAINT);
                ui.text(quick[i][1], qx, y + 15, colors[i]);
                if (i == 1 && ui.hover(qx, y + 3, width - 14, 22)) {
                    ui.tip("Paid into buy orders, 24h", "Average each\t" + Fmt.moneyExact(row.orderVwap),
                            "Deliveries\t" + Fmt.countExact(row.orderFills),
                            "Items\t" + Fmt.countExact(row.orderVolume),
                            row.orderLastAt == null ? null : "Last\t" + Fmt.dayClock(row.orderLastAt.longValue()));
                }
            }
            qx += width;
        }
    }

    /** What a book's or potion's numbers leave out: the sales whose kind nobody recorded. */
    static String coverageNote(ItemRow row) {
        if (row == null || row.coverage == null || !row.variant() && !row.mixed) return null;
        String material = row.base == null ? "these" : row.base.replace('_', ' ');
        if (row.mixed) {
            return "Kind unknown for " + Fmt.share(1 - row.coverage) + " of " + material + " sales; they are counted "
                    + "here, mixed together";
        }
        return "Kind known for " + Fmt.share(row.coverage) + " of " + material + " sales, so trades, volume and "
                + "turnover are minimums";
    }

    private static String label(String category) {
        return category.isEmpty() ? category : category.charAt(0) + category.substring(1).toLowerCase();
    }

    /**
     * The candle size to start on: the largest that still gives about forty candles with trades, so a thin
     * history is not drawn as one or two candles; five minutes if none does.
     */
    static String pickTf(List<Models.Candle> fiveMinute) {
        for (int i = TFS.length - 1; i > 0; i--) {
            long bucket = (long) MarketView.seconds(TFS[i]);
            java.util.Set<Long> traded = new java.util.HashSet<>();
            for (Models.Candle candle : fiveMinute) if (!candle.empty) traded.add(candle.t / bucket);
            if (traded.size() >= 40) return TFS[i];
        }
        return "5m";
    }

    public String tf() {
        return tf;
    }

    private String candlesPath(boolean orders) {
        return "/v1/items/" + Api.encode(key) + "/candles?limit=2000&fill=1" + (orders ? "&source=orders" : "")
                + "&tf=";
    }

    /** Candles that traded. Fewer than a dozen at five minutes and a market is drawn as single points. */
    private static int traded(Models.Candles data) {
        int traded = 0;
        if (data != null && data.candles != null) for (Models.Candle candle : data.candles) if (!candle.empty) traded++;
        return traded;
    }

    /** Which market the chart shows: 0 the auction house, 1 filled buy orders, 2 both. */
    public int market() {
        return market;
    }

    private void chart(Ui ui, ItemRow row, int x, int y, int w, int h) {
        var config = BagelTV.config();
        // An item that mostly moves through buy orders opens on them; a pick by hand stays.
        if (market < 0 && row != null) market = row.ahShare != null && row.ahShare < 0.5 ? 1 : 0;
        int shown = Math.max(market, 0);
        boolean both = shown == 2;
        String ahPath = candlesPath(false), orderPath = candlesPath(true);
        if (tf == null && market >= 0) {
            Api.Query<Models.Candles> probe = Api.get((shown == 1 ? orderPath : ahPath) + "5m", Models.Candles.class);
            if (probe.data != null) tf = pickTf(probe.data.candles == null ? List.of() : probe.data.candles);
            else if (probe.error != null) tf = "5m";
        }
        String size = tf == null ? "5m" : tf;
        // Candles imply steady trading. A market with fewer than a dozen five-minute candles that traded has each
        // sale or filled bucket drawn as itself, at every candle size, so the choice never flips between sizes.
        Api.Query<Models.Candles> ah = shown != 1 ? Api.get(ahPath + size, Models.Candles.class) : null;
        Api.Query<Models.Candles> ahFinest = shown != 1 ? Api.get(ahPath + "5m", Models.Candles.class) : null;
        Api.Query<Models.Candles> filled = shown != 0 ? Api.get(orderPath + size, Models.Candles.class) : null;
        Api.Query<Models.Candles> filledFinest = shown != 0 ? Api.get(orderPath + "5m", Models.Candles.class) : null;
        Api.Query<Models.Trades> sales = shown == 0 ? Api.get("/v1/items/" + Api.encode(key) + "/trades?limit=100",
                Models.Trades.class) : null;
        boolean ahThin = ahFinest != null && ahFinest.data != null && traded(ahFinest.data) < 12;
        boolean filledThin = filledFinest != null && filledFinest.data != null && traded(filledFinest.data) < 12;
        boolean dots = tf != null && !both && (shown == 0 ? ahThin : filledThin);
        salesOnly = dots;
        Object[] from = both ? new Object[] {ah.data, ahFinest.data, filled.data, filledFinest.data}
                : shown == 0 ? new Object[] {dots ? sales.data : ah.data, ahFinest.data}
                : new Object[] {filled.data, filledFinest.data};
        boolean ready = tf != null;
        for (Object part : from) ready &= part != null;
        String shape = shown + size + config.itemChart + config.itemVwap + dots + ahThin + filledThin;
        if (ready && (!java.util.Arrays.equals(from, builtFrom) || !shape.equals(builtShape))) {
            boolean refit = builtFrom.length == 0 || !builtShape.equals(shape);
            if (both) buildBoth(ahThin ? ahFinest.data : ah.data, ahThin, filledThin ? filledFinest.data
                    : filled.data, filledThin, size);
            else if (!dots) build(shown == 0 ? ah.data : filled.data, size, config.itemChart, config.itemVwap,
                    shown == 0 ? Theme.ACCENT : Theme.ORDERS);
            else if (shown == 0) buildSales(sales.data);
            else buildPoints(filledFinest.data);
            builtFrom = from;
            builtShape = shape;
            if (refit) chart.fit();
        }
        chart.emptyText = shown == 1 ? "No orders filled in this range yet" : "No trades recorded in this range yet";
        // The chart's own button flips its scale; anything else follows the saved setting.
        if (chart.log != logSeen) {
            config.itemLog = chart.log;
            config.save();
        } else {
            chart.log = config.itemLog;
        }
        logSeen = chart.log;
        // Each market's level now: the cheapest listing for the auction house, the highest open order for orders.
        chart.refs.clear();
        if (row != null) {
            if (shown != 1 && row.bestAsk != null) chart.refs.add(new Chart.Ref("AH price", row.bestAsk, Theme.ACCENT));
            if (shown != 0 && row.bestBid != null) chart.refs.add(new Chart.Ref("Top order", row.bestBid,
                    Theme.ORDERS));
        }
        int marketW = ui.segmentsWidth(MARKETS), tfW = ui.segmentsWidth(TFS);
        chart.controlsWidth = 1 + marketW + 1 + tfW + 1 + 26 + 1 + ui.width("VWAP") + 10;
        chart.title = TITLES[shown];
        int slot = 0;
        for (String title : TITLES) slot = Math.max(slot, ui.width(title) + 10);
        chart.titleSlot = slot;
        // Candle sizes mean nothing for single points; candle types and VWAP need one market's candles.
        boolean sizesOff = both ? ahThin && filledThin : dots;
        boolean typesOn = !dots && !both;
        chart.controls = (draw, hx, hy, right) -> {
            draw.vline(hx, hy, 13, Theme.BORDER);
            hx++;
            draw.segments(hx, hy, MARKETS, shown, picked -> market = picked, false);
            hx += marketW;
            draw.vline(hx, hy, 13, Theme.BORDER);
            hx++;
            if (sizesOff) {
                // The sizes keep their place, switched off.
                int sx = hx;
                for (String label : TFS) {
                    draw.text(label, sx + 4, hy + 3, Theme.FAINT);
                    sx += draw.width(label) + 9;
                }
            } else {
                draw.segments(hx, hy, TFS, Parts.index(TFS, size), picked -> tf = TFS[picked], false);
            }
            hx += tfW;
            draw.vline(hx, hy, 13, Theme.BORDER);
            hx++;
            hx = type(draw, Icon.CANDLES, "candles", "Candles", hx, hy, typesOn);
            hx = type(draw, Icon.LINE, "line", "Line", hx, hy, typesOn);
            draw.vline(hx, hy, 13, Theme.BORDER);
            hx++;
            int vw = draw.width("VWAP") + 10;
            boolean over = typesOn && draw.click(hx, hy, vw, 13, () -> {
                config.itemVwap = !config.itemVwap;
                config.save();
            });
            if (typesOn && (over || config.itemVwap)) draw.fill(hx, hy, vw, 13, over ? Theme.HOVER : Theme.SELECTED);
            draw.text("VWAP", hx + 5, hy + 3, !typesOn ? Theme.FAINT : config.itemVwap ? Theme.WARN : over
                    ? Theme.TEXT : Theme.MUTED);
            draw.mark("vwap", hx + vw / 2, hy + 6);
            if (over) draw.tip("Volume-weighted average price per candle");
            draw.vline(hx + vw, hy, 13, Theme.BORDER);
            return hx + vw + 1;
        };
        chart.render(ui, x, y, w, h);
        if (!ready) {
            Api.Query<?> failed = null;
            for (Api.Query<?> query : new Api.Query<?>[] {ah, ahFinest, filled, filledFinest, sales}) {
                if (query != null && query.data == null && query.error != null) failed = query;
            }
            if (failed != null) ui.error(x, y + Chart.HEADER, w, h - Chart.HEADER, failed.error, failed::retry);
            else ui.spinner(x + w / 2 - 3, y + h / 2);
        }
    }

    private int type(Ui ui, Icon icon, String type, String label, int x, int y, boolean enabled) {
        var config = BagelTV.config();
        ui.mark("type:" + type, x + 6, y + 6);
        ui.iconButton(icon, x, y, 13, 13, enabled && config.itemChart.equals(type), enabled, label, () -> {
            config.itemChart = type;
            config.save();
        });
        return x + 13;
    }

    /** Whether the chart is showing single sales because there are too few candles. For tests. */
    public boolean salesOnly() {
        return salesOnly;
    }

    /** Every recorded sale as a dot at its time and unit price, joined by steps: no trend is implied. */
    private void buildSales(Models.Trades data) {
        List<Trade> trades = new ArrayList<>();
        if (data.trades != null) for (Trade trade : data.trades) if (!trade.outlier) trades.add(trade);
        int n = trades.size();
        double[] t = new double[n], v = new double[n];
        for (int i = 0; i < n; i++) {
            // Newest first in the API; oldest first on the chart.
            Trade trade = trades.get(n - 1 - i);
            t[i] = trade.t;
            v[i] = trade.unit;
        }
        chart.candles = null;
        chart.histogram = null;
        chart.lines.clear();
        chart.lineBucket = 0;
        Line sale = new Line("Sale", Theme.ACCENT, t, v);
        sale.step = true;
        sale.dots = true;
        chart.lines.add(sale);
        chart.tagLines = true;
    }

    /** A market with few fills: each five-minute bucket that had deliveries as a dot at its last price. */
    private void buildPoints(Models.Candles data) {
        chart.candles = null;
        chart.histogram = null;
        chart.lines.clear();
        chart.lineBucket = 0;
        Line line = points("Filled", Theme.ORDERS, data, true);
        if (line != null) chart.lines.add(line);
        chart.tagLines = true;
    }

    /** The two markets as two lines, each in its own color: never one price. */
    private void buildBoth(Models.Candles ah, boolean ahThin, Models.Candles filled, boolean filledThin, String tf) {
        chart.candles = null;
        chart.histogram = null;
        chart.lines.clear();
        chart.lineBucket = ahThin || filledThin ? 0 : MarketView.seconds(tf);
        Line sale = points("AH", Theme.ACCENT, ah, ahThin);
        Line order = points("Orders", Theme.ORDERS, filled, filledThin);
        if (sale != null) chart.lines.add(sale);
        if (order != null) chart.lines.add(order);
        chart.tagLines = true;
    }

    /** Closing prices as a line; for a thin market only the buckets that traded, as dots joined by steps. */
    private static Line points(String name, int color, Models.Candles data, boolean thin) {
        List<Models.Candle> list = new ArrayList<>();
        if (data.candles != null) for (Models.Candle candle : data.candles) if (!thin || !candle.empty) list.add(candle);
        if (list.isEmpty()) return null;
        double[] t = new double[list.size()], v = new double[list.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = list.get(i).t;
            v[i] = list.get(i).c;
        }
        Line line = new Line(name, color, t, v);
        line.step = thin;
        line.dots = thin;
        return line;
    }

    private void build(Models.Candles data, String tf, String type, boolean vwap, int color) {
        List<Models.Candle> list = data.candles == null ? List.of() : data.candles;
        int n = list.size();
        double bucket = MarketView.seconds(tf);
        double[] t = new double[n], o = new double[n], hi = new double[n], lo = new double[n], c = new double[n],
                volume = new double[n], average = new double[n];
        boolean[] up = new boolean[n], empty = new boolean[n];
        boolean anyEmpty = false;
        for (int i = 0; i < n; i++) {
            Models.Candle candle = list.get(i);
            empty[i] = candle.empty;
            anyEmpty |= candle.empty;
            t[i] = candle.t;
            o[i] = candle.o;
            hi[i] = candle.h;
            lo[i] = candle.l;
            c[i] = candle.c;
            volume[i] = candle.volume == null ? 0 : candle.volume;
            average[i] = candle.vwap == null ? Double.NaN : candle.vwap;
            up[i] = candle.c >= candle.o;
        }
        chart.lines.clear();
        chart.candles = null;
        chart.lineBucket = bucket;
        if (type.equals("candles")) {
            chart.candles = new Candles(t, o, hi, lo, c, bucket);
            if (anyEmpty) chart.candles.empty = empty;
        } else {
            Line price = new Line("Price", color, t, c);
            chart.lines.add(price);
        }
        if (vwap) chart.lines.add(new Line("VWAP", Theme.WARN, t, average));
        chart.tagLines = !type.equals("candles");
        chart.histogram = new Histogram("Vol", t, volume, up, bucket);
    }

    private void stats(Ui ui, Api.Query<ItemRow> query, int x, int y, int w, int h) {
        int top = Parts.card(ui, x, y, w, h, "AH sales by window");
        if (!ui.ready(query, x + 1, top, w - 2, y + h - top - 1)) return;
        ItemRow row = query.data;
        int labelW = 58;
        // The windows our history covers, then one column for everything we have, under its real span. Windows
        // longer than the history would all repeat that column, so they are left out.
        List<String> heads = new ArrayList<>();
        List<Models.WindowStats> columns = new ArrayList<>();
        boolean partial = false;
        for (String window : Parts.WINDOWS) {
            Models.WindowStats stats = row.windows == null ? null : row.windows.get(window);
            boolean complete = stats == null || stats.complete == null || stats.complete;
            if (complete) {
                heads.add(window);
                columns.add(stats);
            } else if (!partial) {
                partial = true;
                long now = System.currentTimeMillis() / 1000;
                heads.add(row.firstTrade == null ? "All" : "All " + Fmt.span(now - row.firstTrade));
                columns.add(stats);
            }
        }
        int col = Math.min(64, (w - 2 - labelW - 3) / Math.max(1, heads.size()));
        int right = x + w - 4;
        for (int c = 0; c < heads.size(); c++) {
            int cr = right - (heads.size() - 1 - c) * col;
            boolean all = heads.get(c).startsWith("All");
            ui.textRight(heads.get(c), cr, top + 3, all ? Theme.WARN : Theme.FAINT);
        }
        String floor = row.floor();
        for (int r = 0; r < STAT_ROWS.length; r++) {
            int ry = top + 12 + r * 10;
            // A variant's counts are minimums; the mark goes on the row's name, where it fits.
            ui.text(STAT_ROWS[r] + (r < 3 && !floor.isEmpty() ? " " + floor : ""), x + 5, ry + 1, Theme.MUTED);
            for (int c = 0; c < heads.size(); c++) {
                Models.WindowStats stats = columns.get(c);
                int cr = right - (heads.size() - 1 - c) * col;
                String text = "-";
                int color = Theme.TEXT;
                if (stats != null) {
                    switch (r) {
                        case 0 -> text = brief(stats.trades);
                        case 1 -> text = brief(stats.volume);
                        case 2 -> text = Fmt.money(stats.turnover);
                        case 3 -> text = Fmt.money(stats.vwap);
                        case 4 -> text = Fmt.money(stats.median);
                        case 5 -> text = Fmt.money(stats.low);
                        case 6 -> text = Fmt.money(stats.high);
                        case 7 -> {
                            // No earlier window to compare with reads as blank, not as no change.
                            text = stats.change == null ? "" : Fmt.pct(stats.change);
                            color = Theme.change(stats.change);
                        }
                        case 8 -> text = Fmt.share(stats.sellThrough);
                        default -> text = stats.medianTimeToSell == null ? "-" : shortTime(stats.medianTimeToSell);
                    }
                }
                if (text.equals("-")) color = Theme.FAINT;
                ui.textRight(ui.fit(text, col - 2), cr, ry + 1, color);
            }
        }
    }

    /** A count short enough for a narrow column: 4,773 or 30.5K. */
    private static String brief(Double value) {
        if (value == null) return "-";
        return Math.abs(value) < 10_000 ? Fmt.count(value) : Fmt.compact(Math.abs(value), false);
    }

    /** A length of time in one unit, to fit a narrow column. */
    private static String shortTime(double seconds) {
        if (seconds < 100) return Math.round(seconds) + "s";
        if (seconds < 6000) return Math.round(seconds / 60) + "m";
        if (seconds < 86400 * 2) return Math.round(seconds / 3600) + "h";
        return Math.round(seconds / 86400) + "d";
    }

    /** The ladder: listings above the gap, cheapest nearest it, and buy orders below, with bars for depth. */
    private void book(Ui ui, Api.Query<Models.Book> query, int x, int y, int w, int h) {
        int top = Parts.card(ui, x, y, w, h, "Listings and orders");
        if (!ui.ready(query, x + 1, top, w - 2, y + h - top - 1)) return;
        Models.Book book = query.data;
        boolean recent = !"book".equals(book.asksSource);
        String source = recent ? "recent listings only" : book.bookAt == null ? "all listings"
                : "all listings, " + Fmt.ago(book.bookAt, System.currentTimeMillis() / 1000) + " ago";
        ui.textRight(source, x + w - 5, y + 3, recent ? Theme.WARN : Theme.FAINT);
        if (ui.hover(x + w - 10 - ui.width(source), y + 1, ui.width(source) + 9, 12)) {
            if (recent) ui.tip("Unsold listings seen in the last day",
                    "Older listings are missing and canceled ones may still show");
            else ui.tip("From a sweep of the whole auction house");
        }
        List<Models.Bid> bids = book.bids == null ? List.of() : book.bids;
        List<Models.Ask> asks = book.asks == null ? List.of() : book.asks;
        int inner = y + h - 1 - top;
        ui.text("Price", x + 5, top + 2, Theme.FAINT);
        ui.textRight("Units", x + w * 62 / 100, top + 2, Theme.FAINT);
        ui.textRight("Value", x + w - 5, top + 2, Theme.FAINT);
        int listTop = top + 11;
        int rowsEach = Math.max(1, (inner - 11 - 12) / 2 / 10);
        int askCount = Math.min(asks.size(), rowsEach), bidCount = Math.min(bids.size(), rowsEach);
        double most = 1;
        double sum = 0;
        for (int i = 0; i < askCount; i++) most = Math.max(most, sum += asks.get(i).units);
        sum = 0;
        for (int i = 0; i < bidCount; i++) most = Math.max(most, sum += bids.get(i).units);

        // Listings, the cheapest at the bottom next to the gap.
        int askBottom = listTop + rowsEach * 10;
        double running = 0;
        for (int i = 0; i < askCount; i++) {
            Models.Ask ask = asks.get(i);
            running += ask.units;
            int ry = askBottom - (i + 1) * 10;
            level(ui, x, ry, w, ask.unit, ask.units, ask.unit * ask.units, running / most, false,
                    Fmt.count(ask.listings) + (ask.listings == 1 ? " listing" : " listings"), running,
                    ask.exact != null && !ask.exact);
        }
        if (askCount == 0) ui.textCenter("No listings seen", x + w / 2, askBottom - 14, Theme.FAINT);

        // The gap between the cheapest listing and the top order.
        int spreadY = askBottom;
        ui.fill(x + 1, spreadY, w - 2, 12, Theme.RAISED);
        if (!asks.isEmpty() && !bids.isEmpty()) {
            double spread = asks.getFirst().unit - bids.getFirst().price;
            boolean crossed = spread < 0;
            String text = crossed ? "Top order pays " + Fmt.money(-spread) + " more" : "Gap " + Fmt.money(spread) + "  "
                    + Fmt.share(spread / asks.getFirst().unit);
            ui.textCenter(text, x + w / 2, spreadY + 2, crossed ? Theme.ACCENT : Theme.MUTED);
            if (crossed && ui.hover(x + 1, spreadY, w - 2, 12)) {
                ui.tip("A listing is cheaper than the top order pays",
                        "Buy at\t" + Fmt.moneyExact(asks.getFirst().unit),
                        "Sell into\t" + Fmt.moneyExact(bids.getFirst().price));
            }
        }

        int bidTop = spreadY + 12;
        running = 0;
        for (int i = 0; i < bidCount; i++) {
            Models.Bid bid = bids.get(i);
            running += bid.units;
            level(ui, x, bidTop + i * 10, w, bid.price, bid.units, bid.value, running / most, true,
                    Fmt.count(bid.orders) + (bid.orders == 1 ? " order" : " orders"), running, false);
        }
        if (bidCount == 0) ui.textCenter("No buy orders", x + w / 2, bidTop + 4, Theme.FAINT);
    }

    private void level(Ui ui, int x, int y, int w, double price, double units, double value, double depth,
                       boolean bid, String count, double cumulative, boolean atLeast) {
        int bar = (int) Math.round((w - 2) * Math.clamp(depth, 0, 1));
        ui.fill(x + w - 1 - bar, y, bar, 9, bid ? Theme.UP_BG : Theme.DOWN_BG);
        if (ui.hover(x + 1, y, w - 2, 10)) {
            ui.fill(x + 1, y, w - 2, 9, Theme.alpha(bid ? Theme.UP : Theme.DOWN, 0x30));
            ui.tip(bid ? "Buy orders" : "Listings", "Price\t" + (atLeast ? "~" + Fmt.money(price)
                            : Fmt.moneyExact(price)), "Units\t" + Fmt.countExact(units),
                    "Value\t" + (atLeast ? "~" + Fmt.money(value) : Fmt.moneyExact(value)),
                    "At this price\t" + count, "Units to here\t" + Fmt.countExact(cumulative),
                    atLeast ? "The game shows this price rounded down, up to 10% low" : null);
        }
        ui.text((atLeast ? "~" : "") + Fmt.money(price), x + 5, y + 1, bid ? Theme.UP : Theme.DOWN);
        ui.textRight(Fmt.count(units), x + w * 62 / 100, y + 1, Theme.TEXT);
        ui.textRight(Fmt.money(value), x + w - 5, y + 1, Theme.MUTED);
    }

    private void tape(Ui ui, int x, int y, int w, int h) {
        Api.Query<Models.Trades> query = Api.get("/v1/items/" + Api.encode(key) + "/trades?limit=100",
                Models.Trades.class);
        int top = Parts.card(ui, x, y, w, h, "Recent AH sales");
        if (!ui.ready(query, x + 1, top, w - 2, y + h - top - 1)) return;
        List<Trade> trades = query.data.trades == null ? List.of() : query.data.trades;
        tape.render(ui, x, top - 1, w, y + h - top + 1, trades, null, true, null, null, ItemView::tradeTip);
    }

    /** Open buy orders or current listings, switched from the card's header. */
    private void openOrders(Ui ui, Api.Query<Models.Book> query, int x, int y, int w, int h) {
        int top = Parts.card(ui, x, y, w, h, "");
        int tx = x + 1;
        String[] tabs = {"Buy orders", "Listings"};
        for (int i = 0; i < tabs.length; i++) {
            boolean on = showListings == (i == 1);
            int tw = ui.width(tabs[i]) + 10;
            boolean pick = i == 1;
            boolean over = ui.click(tx, y + 1, tw, 12, () -> showListings = pick);
            if (on) ui.fill(tx, y + 1, tw, 12, Theme.SELECTED);
            else if (over) ui.fill(tx, y + 1, tw, 12, Theme.HOVER);
            ui.text(tabs[i], tx + 5, y + 3, on ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED);
            tx += tw;
            ui.vline(tx, y + 1, 12, Theme.BORDER);
            tx++;
        }
        if (!ui.ready(query, x + 1, top, w - 2, y + h - top - 1)) return;
        if (query.data != ordersFor) {
            ordersFor = query.data;
            orderRows.clear();
            if (query.data.orders != null) orderRows.addAll(query.data.orders);
            orderRows.sort(Comparator.comparingDouble((OrderRow row) -> row.price == null ? 0 : row.price).reversed());
        }
        List<Models.Listing> listed = query.data.listings == null ? List.of() : query.data.listings;
        String count = Fmt.count(showListings ? listed.size() : orderRows.size());
        if (tx + ui.width(count) + 8 < x + w) ui.textRight(count, x + w - 5, y + 3, Theme.FAINT);
        if (showListings) {
            listings.render(ui, x, top - 1, w, y + h - top + 1, listed, null, true, null, null, row -> {
                List<String> lines = new ArrayList<>();
                lines.add("Listing by " + row.seller);
                if (row.variant != null) lines.add(row.variant);
                if (row.enchants != null && !row.enchants.isEmpty()) lines.add("Enchants\t" + row.enchants);
                lines.add("Each\t" + (row.exact() ? Fmt.moneyExact(row.unit) : "~" + Fmt.money(row.unit)));
                lines.add("Quantity\t" + Fmt.countExact(row.qty));
                lines.add("Total\t" + (row.exact() ? Fmt.moneyExact(row.price) : "~"
                        + Fmt.money(row.price)));
                if (row.expiresAt != null) lines.add("Expires\t" + Fmt.dayClock(row.expiresAt));
                if (!row.exact()) lines.add("The game shows this price rounded down, up to 10% low");
                return lines;
            });
            return;
        }
        orders.render(ui, x, top - 1, w, y + h - top + 1, orderRows, null, true, null, null, row -> {
            List<String> lines = new ArrayList<>();
            lines.add("Buy order by " + row.owner);
            lines.add("Pays each\t" + Fmt.moneyExact(row.price));
            if (row.remaining != null && row.qty != null) {
                lines.add("Left\t" + Fmt.countExact(row.remaining) + " of " + Fmt.countExact(row.qty));
            }
            if (row.expiresAt != null) lines.add("Expires\t" + Fmt.dayClock(row.expiresAt));
            return lines;
        });
    }
}

package com.merkie.bageltv.view;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Candles;
import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.chart.Histogram;
import com.merkie.bageltv.chart.Line;
import com.merkie.bageltv.chart.Stack;
import com.merkie.bageltv.chart.ValueFormat;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.ui.Items;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A chart with its own data: the market index, the top items, the top players' balances, or one of the economy's
 * series. The dashboard puts any of them in either of its chart slots, and the Economy view uses three.
 */
public abstract class Board {
    public static final String[] KINDS = {"index", "leaders", "players", "supply", "online", "escrow"};
    public static final String[] NAMES = {"Market index", "Top items", "Top players", "Money supply",
            "Players online", "Money in buy orders"};

    public final Chart chart;
    /** Drawn first in the chart's header: the dashboard's chart picker. Null where the chart is fixed. */
    public Chart.Controls picker;
    public int pickerWidth;
    /** An item or player a click on a line asked for, opened on the next frame. */
    protected String openItem, openPlayer;

    protected Board(String key) {
        chart = new Chart(key);
        chart.emptyText = "No history in this range yet";
        chart.controls = (ui, x, y, right) -> {
            if (picker != null) x = picker.draw(ui, x, y, right);
            return controls(ui, x, y, right);
        };
    }

    public static Board create(String kind) {
        return switch (kind) {
            case "leaders" -> new Leaders();
            case "players" -> new Players();
            case "supply", "online", "escrow" -> new Economy(kind);
            default -> new Index();
        };
    }

    public static String name(String kind) {
        return NAMES[Parts.index(KINDS, kind)];
    }

    /** The board's own header controls after the picker. Returns the x after them. */
    protected int controls(Ui ui, int x, int y, int right) {
        return x;
    }

    /** Text in the header after a picker, such as the candle size, when it fits. */
    protected static int note(Ui ui, String text, int x, int y, int right) {
        if (x + ui.width(text) + 6 > right) return x;
        return x + 4 + ui.text(text, x + 4, y + 3, Theme.MUTED) + 4;
    }

    /** Fetches what it needs and draws the chart, and its legend if it has one, in the box. */
    public void render(Ui ui, int x, int y, int w, int h) {
        if (openItem != null) {
            String item = openItem;
            openItem = null;
            ui.nav.item(item);
            return;
        }
        if (openPlayer != null) {
            String player = openPlayer;
            openPlayer = null;
            ui.nav.player(player);
            return;
        }
        chart.controlsWidth = pickerWidth;
        draw(ui, x, y, w, h);
    }

    protected abstract void draw(Ui ui, int x, int y, int w, int h);

    /** The loading or error state over a chart that has no data yet. */
    protected static void state(Ui ui, Api.Query<?> query, int x, int y, int w, int h) {
        if (query.data != null) return;
        if (query.error != null) ui.error(x, y + Chart.HEADER, w, h - Chart.HEADER, query.error, query::retry);
        else ui.spinner(x + w / 2 - 3, y + h / 2);
    }

    /** The market index as candles over a turnover histogram. */
    static final class Index extends Board {
        private Object built;
        private String tf = "";

        Index() {
            super("market:index");
            chart.format = ValueFormat.INDEX;
            chart.histogramFormat = ValueFormat.MONEY;
            chart.canLog = false;
        }

        @Override
        protected int controls(Ui ui, int x, int y, int right) {
            return note(ui, tf + " candles", x, y, right);
        }

        @Override
        protected void draw(Ui ui, int x, int y, int w, int h) {
            String next = MarketView.indexTf(Parts.window());
            Api.Query<Models.Candles> query = Api.get("/v1/market/candles?tf=" + next + "&limit=500",
                    Models.Candles.class);
            if (query.data != null && query.data != built) {
                built = query.data;
                List<Models.Candle> list = query.data.candles == null ? List.of() : query.data.candles;
                int n = list.size();
                double[] t = new double[n], o = new double[n], hi = new double[n], lo = new double[n],
                        c = new double[n], v = new double[n];
                boolean[] up = new boolean[n];
                for (int i = 0; i < n; i++) {
                    Models.Candle candle = list.get(i);
                    t[i] = candle.t;
                    o[i] = candle.o;
                    hi[i] = candle.h;
                    lo[i] = candle.l;
                    c[i] = candle.c;
                    v[i] = candle.turnover == null ? 0 : candle.turnover;
                    up[i] = candle.c >= candle.o;
                }
                chart.candles = new Candles(t, o, hi, lo, c, MarketView.seconds(next));
                chart.histogram = new Histogram("Turnover", t, v, up, MarketView.seconds(next));
                if (!next.equals(tf)) chart.fit();
            }
            tf = next;
            chart.title = picker == null ? "Market index, " + tf + " candles" : "";
            chart.render(ui, x, y, w, h);
            state(ui, query, x, y, w, h);
        }
    }

    /** The ten leading items over the window, each a line in its item's own color; or their ranks; or shares. */
    static final class Leaders extends Board {
        private static final String[] METRICS = {"turnover", "volume", "trades"};
        private static final String[] METRIC_LABELS = {"by turnover", "by volume", "by trades"};
        private static final String[] MODES = {"value", "rank", "share"};
        private static final String[] MODE_LABELS = {"per bucket", "by rank", "as shares"};
        private final Object metricMenu = new Object(), modeMenu = new Object();
        private final Set<String> hidden = new HashSet<>();
        private Object built;
        private String shape = "";
        private Models.Leaders data;

        Leaders() {
            super("market:leaders");
            chart.legendLines = false;
            chart.tagLines = false;
            chart.onLineClick = line -> openItem = line.icon;
        }

        @Override
        protected int controls(Ui ui, int x, int y, int right) {
            var config = BagelTV.config();
            if (x + 60 > right) return x;
            x += ui.dropdown(metricMenu, x, y, METRIC_LABELS, Parts.index(METRICS, config.leadersMetric), picked -> {
                config.leadersMetric = METRICS[picked];
                config.save();
            });
            if (x + 60 > right || thin) return x;
            x += ui.dropdown(modeMenu, x, y, MODE_LABELS, Parts.index(MODES, config.leadersMode), picked -> {
                config.leadersMode = MODES[picked];
                config.save();
            });
            return x;
        }

        @Override
        protected void draw(Ui ui, int x, int y, int w, int h) {
            var config = BagelTV.config();
            String window = Parts.window(), tf = MarketView.leadersTf(window);
            Api.Query<Models.Leaders> query = Api.get("/v1/market/leaders?metric=" + config.leadersMetric
                    + "&window=" + window + "&tf=" + tf + "&top=10", Models.Leaders.class);
            String next = config.leadersMode + hidden;
            if (query.data != null && (query.data != built || !next.equals(shape))) {
                boolean fresh = data == null || !query.data.metric.equals(data.metric)
                        || !query.data.window.equals(data.window) || !next.equals(shape);
                built = query.data;
                shape = next;
                data = query.data;
                // Variants' totals are only a floor, so they do not rank against whole items.
                if (data.series != null) data.series.removeIf(one -> one.item != null && one.item.indexOf('[') >= 0);
                thin = busyBuckets(data) < 6;
                build(query.data, MarketView.seconds(tf));
                if (fresh) chart.fit();
            }
            chart.title = picker == null ? "Top items " + METRIC_LABELS[Parts.index(METRICS, config.leadersMetric)]
                    : "";
            // Lines over time need several buckets with trades; with fewer, the totals as bars say all there is.
            chart.body = thin && data != null ? this::bars : null;
            int rows = w >= 420 ? 1 : 2;
            int legendH = chart.maximized() || data == null || thin ? 0 : rows * 11 + 3;
            chart.render(ui, x, y, w, h - legendH);
            state(ui, query, x, y, w, h - legendH);
            if (legendH > 0) legend(ui, x, y + h - legendH + 2, w, rows);
        }

        private boolean thin;

        /** How many buckets of the window have any trades at all. */
        private static int busyBuckets(Models.Leaders leaders) {
            int n = leaders.buckets == null ? 0 : leaders.buckets.length, busy = 0;
            for (int i = 0; i < n; i++) {
                boolean any = leaders.other != null && i < leaders.other.length && leaders.other[i] > 0;
                if (leaders.series != null) {
                    for (Models.LeaderSeries one : leaders.series) any |= one.values != null && one.values[i] > 0;
                }
                if (any) busy++;
            }
            return busy;
        }

        public boolean thin() {
            return thin;
        }

        /** The window's totals as a ranked row of bars, in place of lines there is too little history for. */
        private void bars(Ui ui, int x, int y, int w, int h) {
            List<Models.LeaderSeries> series = data.series == null ? List.of() : data.series;
            if (series.isEmpty()) {
                ui.textCenter("No trades in this window yet", x + w / 2, y + h / 2 - 4, Theme.FAINT);
                return;
            }
            double most = 1;
            for (Models.LeaderSeries one : series) most = Math.max(most, one.total);
            boolean money = "turnover".equals(data.metric);
            int rowH = Math.clamp((h - 4) / series.size(), 11, Theme.ROW);
            int count = Math.min(series.size(), (h - 4) / rowH);
            int nameW = Math.clamp(w * 30 / 100, 60, 130), valueW = 50;
            for (int i = 0; i < count; i++) {
                Models.LeaderSeries one = series.get(i);
                int ry = y + 2 + i * rowH;
                boolean over = ui.click(x, ry, w, rowH, () -> openItem = one.item);
                if (over) {
                    ui.fill(x, ry, w, rowH, Theme.HOVER);
                    ui.tip(one.name, "Total\t" + (money ? Fmt.moneyExact(one.total) : Fmt.countExact(one.total)));
                }
                boolean icon = rowH >= 17;
                if (icon) ui.item(one.item, x + 2, ry + (rowH - 16) / 2);
                else ui.item(one.item, x + 3, ry + (rowH - 8) / 2, 0.5F);
                int tx = x + (icon ? 21 : 14);
                ui.text(ui.fit(Models.compact(one.name), nameW - (icon ? 21 : 14)), tx, ry + (rowH - 7) / 2,
                        Theme.TEXT);
                int barX = x + nameW + 4, barW = w - nameW - valueW - 10;
                int filled = Math.max(1, (int) Math.round(barW * one.total / most));
                ui.fill(barX, ry + rowH / 2 - 2, filled, 4, Items.color(one.item));
                ui.textRight(money ? Fmt.money(one.total) : Fmt.count(one.total), x + w - 5, ry + (rowH - 7) / 2,
                        Theme.TEXT);
            }
        }

        private void build(Models.Leaders leaders, double bucket) {
            String mode = BagelTV.config().leadersMode;
            chart.lines.clear();
            chart.stack = null;
            chart.lineBucket = bucket;
            chart.invert = false;
            chart.fixedMin = chart.fixedMax = Double.NaN;
            chart.canLog = mode.equals("value");
            if (!chart.canLog) chart.log = false;
            int n = leaders.buckets == null ? 0 : leaders.buckets.length;
            double[] t = new double[n];
            for (int i = 0; i < n; i++) t[i] = leaders.buckets[i];
            List<Models.LeaderSeries> series = leaders.series == null ? List.of() : leaders.series;
            switch (mode) {
                case "share" -> {
                    int layers = series.size() + 1;
                    String[] names = new String[layers];
                    int[] colors = new int[layers];
                    double[][] values = new double[layers][];
                    for (int i = 0; i < series.size(); i++) {
                        names[i] = Models.compact(series.get(i).name);
                        colors[i] = Items.color(series.get(i).item);
                        values[i] = series.get(i).values;
                    }
                    names[layers - 1] = "Everything else";
                    colors[layers - 1] = Theme.FAINT;
                    values[layers - 1] = leaders.other == null ? new double[n] : leaders.other;
                    Stack stack = new Stack(t, names, colors, values);
                    for (int i = 0; i < series.size(); i++) stack.shown[i] = !hidden.contains(series.get(i).item);
                    stack.rebuild();
                    chart.stack = stack;
                    chart.format = ValueFormat.SHARE;
                }
                case "rank" -> {
                    for (Models.LeaderSeries one : series) {
                        double[] v = new double[n];
                        for (int i = 0; i < n; i++) {
                            v[i] = one.rank != null && one.rank[i] > 0 ? one.rank[i] : Double.NaN;
                        }
                        add(one, t, v);
                    }
                    chart.invert = true;
                    chart.fixedMin = 0.4;
                    chart.fixedMax = Math.max(2, series.size()) + 0.6;
                    chart.format = ValueFormat.RANK;
                }
                default -> {
                    for (Models.LeaderSeries one : series) add(one, t, one.values);
                    chart.format = "turnover".equals(leaders.metric) ? ValueFormat.MONEY : ValueFormat.COUNT;
                }
            }
        }

        private void add(Models.LeaderSeries one, double[] t, double[] v) {
            Line line = new Line(Models.compact(one.name), Items.color(one.item), t, v);
            line.icon = one.item;
            line.markers = false;
            line.visible = !hidden.contains(one.item);
            chart.lines.add(line);
        }

        /** The series under the chart: hover one to pick it out, click to hide or show it. */
        private void legend(Ui ui, int x, int y, int w, int rows) {
            List<Models.LeaderSeries> series = data.series == null ? List.of() : data.series;
            if (series.isEmpty()) return;
            int perRow = (series.size() + rows - 1) / rows;
            int cell = w / perRow;
            chart.highlight = -1;
            boolean stacked = chart.stack != null;
            for (int i = 0; i < series.size(); i++) {
                Models.LeaderSeries one = series.get(i);
                int cx = x + i % perRow * cell, cy = y + i / perRow * 11;
                boolean off = hidden.contains(one.item);
                boolean over = ui.click(cx, cy, cell - 1, 11, () -> {
                    if (!hidden.remove(one.item)) hidden.add(one.item);
                });
                if (over) {
                    ui.fill(cx, cy, cell - 1, 11, Theme.HOVER);
                    if (!off && !stacked) chart.highlight = i;
                    ui.tip(one.name, "Total\t" + ("turnover".equals(data.metric) ? Fmt.moneyExact(one.total)
                            : Fmt.countExact(one.total)));
                }
                ui.fill(cx + 2, cy + 1, 2, 8, off ? Theme.BORDER : Items.color(one.item));
                ui.item(one.item, cx + 6, cy + 1, 0.5F);
                boolean lit = !off && (chart.hoveredLine < 0 || chart.hoveredLine == i)
                        && (!stacked || chart.stack.hovered < 0 || chart.stack.hovered == i);
                ui.text(ui.fit(Models.compact(one.name), cell - 19), cx + 17, cy + 1, off ? Theme.FAINT
                        : lit ? Theme.TEXT : Theme.MUTED);
                if (i == 0) ui.mark("legend:first", cx + 20, cy + 5);
            }
        }
    }

    /** The richest players' balances against each other, a head at the end of each line. */
    static final class Players extends Board {
        private Object built;
        private Models.PlayersHistory data;
        private final Set<String> hidden = new HashSet<>();
        private String shape = "";

        Players() {
            super("market:players");
            chart.legendLines = false;
            chart.tagLines = false;
            chart.onLineClick = line -> openPlayer = line.head;
        }

        @Override
        protected int controls(Ui ui, int x, int y, int right) {
            return note(ui, "balances, 30 days", x, y, right);
        }

        @Override
        protected void draw(Ui ui, int x, int y, int w, int h) {
            Api.Query<Models.PlayersHistory> query = Api.get("/v1/players/history?top=8&window=30d",
                    Models.PlayersHistory.class);
            if (query.data != null && (query.data != built || !shape.equals(hidden.toString()))) {
                boolean first = built == null;
                built = query.data;
                data = query.data;
                shape = hidden.toString();
                chart.lines.clear();
                List<Models.PlayerSeries> series = data.series == null ? List.of() : data.series;
                for (int s = 0; s < series.size(); s++) {
                    Models.PlayerSeries one = series.get(s);
                    List<Models.MoneyPoint> points = one.points == null ? List.of() : one.points;
                    List<Models.MoneyPoint> good = new ArrayList<>();
                    for (Models.MoneyPoint point : points) if (point.money != null) good.add(point);
                    double[] t = new double[good.size()], v = new double[good.size()];
                    for (int i = 0; i < t.length; i++) {
                        t[i] = good.get(i).t;
                        v[i] = good.get(i).money;
                    }
                    Line line = new Line(one.name, Theme.PALETTE[s % Theme.PALETTE.length], t, v);
                    line.head = one.name;
                    line.markers = false;
                    line.visible = !hidden.contains(one.name);
                    chart.lines.add(line);
                }
                if (first) chart.fit();
            }
            chart.title = picker == null ? "Top players' balances" : "";
            int rows = w >= 420 ? 1 : 2;
            int legendH = chart.maximized() || data == null ? 0 : rows * 11 + 3;
            chart.render(ui, x, y, w, h - legendH);
            state(ui, query, x, y, w, h - legendH);
            if (legendH == 0) return;
            List<Models.PlayerSeries> series = data.series == null ? List.of() : data.series;
            if (series.isEmpty()) return;
            int perRow = (series.size() + rows - 1) / rows, cell = w / perRow, ly = y + h - legendH + 2;
            chart.highlight = -1;
            for (int i = 0; i < series.size(); i++) {
                Models.PlayerSeries one = series.get(i);
                int cx = x + i % perRow * cell, cy = ly + i / perRow * 11;
                boolean off = hidden.contains(one.name);
                boolean over = ui.click(cx, cy, cell - 1, 11, () -> {
                    if (!hidden.remove(one.name)) hidden.add(one.name);
                });
                if (over) {
                    ui.fill(cx, cy, cell - 1, 11, Theme.HOVER);
                    if (!off) chart.highlight = i;
                    ui.tip(one.name, "Balance\t" + Fmt.moneyExact(one.balance));
                }
                ui.fill(cx + 2, cy + 1, 2, 8, off ? Theme.BORDER : Theme.PALETTE[i % Theme.PALETTE.length]);
                ui.head(one.name, cx + 6, cy + 1, 8);
                boolean lit = !off && (chart.hoveredLine < 0 || chart.hoveredLine == i);
                ui.text(ui.fit(one.name, cell - 19), cx + 17, cy + 1, off ? Theme.FAINT : lit ? Theme.TEXT
                        : Theme.MUTED);
            }
        }
    }

    /** One of the economy's series: all the money there is, players online, or money locked in buy orders. */
    public static final class Economy extends Board {
        private final String kind;
        private Object built;

        public Economy(String kind) {
            super("economy:" + kind);
            this.kind = kind;
            if (kind.equals("online")) chart.format = ValueFormat.COUNT;
        }

        @Override
        protected void draw(Ui ui, int x, int y, int w, int h) {
            Api.Query<Models.EconomyHistory> query = Api.get("/v1/economy?window=" + Parts.window(),
                    Models.EconomyHistory.class);
            if (query.data != null && query.data != built) {
                built = query.data;
                chart.lines.clear();
                chart.lines.add(switch (kind) {
                    case "online" -> sampled("Online", Theme.UP, query.data, sample -> sample.online, false);
                    case "escrow" -> sampled("Locked", Theme.WARN, query.data, sample -> sample.orderEscrow, true);
                    default -> supply(query.data);
                });
            }
            chart.title = picker == null ? name(kind) : "";
            chart.render(ui, x, y, w, h);
            state(ui, query, x, y, w, h);
        }

        /** Bagel's daily totals up to where our own samples start, then ours. */
        private static Line supply(Models.EconomyHistory data) {
            List<Models.Economy> series = data.series == null ? List.of() : data.series;
            List<Models.EconomyDay> daily = data.daily == null ? List.of() : data.daily;
            double firstSample = series.isEmpty() ? Double.POSITIVE_INFINITY : series.getFirst().t;
            List<double[]> points = new ArrayList<>();
            for (Models.EconomyDay day : daily) {
                if (day.totalMoney != null && day.t < firstSample) points.add(new double[] {day.t, day.totalMoney});
            }
            for (Models.Economy sample : series) {
                if (sample.totalMoney != null) points.add(new double[] {sample.t, sample.totalMoney});
            }
            Line line = line("Total", Theme.ACCENT, points);
            line.area = true;
            return line;
        }

        private static Line sampled(String name, int color, Models.EconomyHistory data,
                                    Function<Models.Economy, Double> value, boolean area) {
            List<double[]> points = new ArrayList<>();
            if (data.series != null) {
                for (Models.Economy sample : data.series) {
                    Double one = value.apply(sample);
                    if (one != null) points.add(new double[] {sample.t, one});
                }
            }
            Line line = line(name, color, points);
            line.area = area;
            return line;
        }

        private static Line line(String name, int color, List<double[]> points) {
            double[] t = new double[points.size()], v = new double[points.size()];
            for (int i = 0; i < t.length; i++) {
                t[i] = points.get(i)[0];
                v[i] = points.get(i)[1];
            }
            return new Line(name, color, t, v);
        }
    }
}

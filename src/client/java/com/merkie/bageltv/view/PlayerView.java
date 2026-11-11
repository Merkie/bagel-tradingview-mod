package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.chart.Line;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.OrderRow;
import com.merkie.bageltv.data.Models.Trade;
import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.ui.Splitter;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** One player: balance over time, what they have locked in buy orders, and what they trade. */
public final class PlayerView extends View {
    private static final String[] RANGES = {"7D", "30D", "90D", "All"};
    private static final int[] RANGE_DAYS = {7, 30, 90, 0};

    private final String name;
    private final Chart chart;
    private final Table<OrderRow> orders = new Table<>(Theme.ROW);
    private final Table<Trade> trades = new Table<>(Theme.ROW);
    private final Table<Models.Fill> payouts = new Table<>(Theme.ROW);
    private boolean showPayouts;
    private Object builtFor;
    private int range = 3;
    private double newest;
    private final Splitter sideSplit = new Splitter(true, size -> BagelTV.config().playerSide = size);
    private final Splitter lowerSplit = new Splitter(false, size -> BagelTV.config().playerLower = size);

    public PlayerView(String name) {
        this.name = name;
        this.chart = new Chart("player:" + name.toLowerCase());
        chart.emptyText = "No balance history yet";

        orders.empty = "No open buy orders";
        orders.col("Wants", 100, (ui, row, x, y, w, h) -> Parts.item(ui, row.target(), row.label(), x + 1, y, w - 2))
                .left().flex(1);
        orders.col("Pays", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.price), x, y, w, h,
                Theme.TEXT));
        orders.col("vs worth", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.vsWorth), x, y, w, h,
                row.vsWorth == null ? Theme.FAINT : row.vsWorth >= 1 ? Theme.UP : Theme.DOWN)).drop(2);
        orders.col("Left", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.remaining), x, y, w, h,
                Theme.TEXT));
        orders.col("Locked", 56, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.value), x, y, w, h,
                Theme.WARN));
        orders.col("Paid", 52, (ui, row, x, y, w, h) -> Table.right(ui, row.paid == null || row.paid == 0 ? "-"
                : Fmt.money(row.paid), x, y, w, h, row.paid == null || row.paid == 0 ? Theme.FAINT : Theme.TEXT))
                .tip("Paid out so far for deliveries");
        orders.col("Expires", 46, (ui, row, x, y, w, h) -> Table.right(ui, row.expiresAt == null ? "-"
                : Fmt.day(row.expiresAt), x, y, w, h, Theme.FAINT)).drop(1);

        payouts.empty = "No deliveries into their orders yet";
        payouts.col("When", 44, (ui, row, x, y, w, h) -> Table.left(ui, Fmt.ago(row.t,
                System.currentTimeMillis() / 1000), x, y, w, h, Theme.MUTED)).left();
        payouts.col("Item", 100, (ui, row, x, y, w, h) -> Parts.item(ui, row.target(), Models.compact(row.name), x + 1,
                y, w - 2)).left().flex(1);
        payouts.col("Delivered", 56, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.qty), x, y, w, h,
                Theme.TEXT));
        payouts.col("Each", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.price), x, y, w, h,
                Theme.MUTED));
        payouts.col("Paid", 56, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.value), x, y, w, h,
                Theme.WARN));

        trades.empty = "No auction trades in the last 7 days";
        trades.col("When", 44, (ui, row, x, y, w, h) -> Table.left(ui, Fmt.ago(row.t,
                System.currentTimeMillis() / 1000), x, y, w, h, Theme.MUTED)).left();
        trades.col("", 40, (ui, row, x, y, w, h) -> {
            boolean sold = sold(row);
            Table.left(ui, sold ? "Sold" : "Bought", x, y, w, h, sold ? Theme.DOWN : Theme.UP);
        }).left();
        trades.col("Item", 90, (ui, row, x, y, w, h) -> Parts.item(ui, row.item, row.name, x + 1, y, w - 2))
                .left().flex(1);
        trades.col("Qty", 34, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.qty), x, y, w, h, Theme.TEXT));
        trades.col("Total", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.price), x, y, w, h,
                Theme.TEXT));
        trades.col("With", 76, (ui, row, x, y, w, h) -> Parts.player(ui, sold(row) ? row.buyer : row.seller,
                x + 6, Ui.textY(y, h), w - 10)).left().drop(1);
    }

    private boolean sold(Trade trade) {
        return trade.side != null ? trade.side.startsWith("s") : name.equalsIgnoreCase(trade.seller);
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
        Api.Query<Models.Player> query = Api.get("/v1/players/" + Api.encode(name), Models.Player.class);
        Models.Player player = query.data;
        header(ui, player, x, y, w);
        int top = y + 38;
        if (!ui.ready(query, x, top, w, y + h - top)) return;

        var config = BagelTV.config();
        int sideMax = w - 240 - Parts.GAP;
        int rightW = Math.clamp(config.playerSide > 0 ? config.playerSide : w * 40 / 100, 180, Math.max(180, sideMax));
        int leftW = w - rightW - Parts.GAP;
        tiles(ui, player, x, top, leftW);
        int bodyTop = top + 27 + Parts.GAP;
        int lowerMax = y + h - bodyTop - 80 - Parts.GAP;
        int ordersH = Math.clamp(config.playerLower > 0 ? config.playerLower : (y + h - bodyTop) * 34 / 100,
                14 + 13 + Theme.ROW, Math.max(14 + 13 + Theme.ROW, lowerMax));
        int chartH = y + h - bodyTop - ordersH - Parts.GAP;
        chart(ui, player, x, bodyTop, leftW, chartH);
        int oy = bodyTop + chartH + Parts.GAP;
        int otop = Parts.card(ui, x, oy, leftW, ordersH, "");
        int tx = x + 1;
        String[] tabs = {"Open buy orders", "Order payouts"};
        for (int i = 0; i < tabs.length; i++) {
            boolean on = showPayouts == (i == 1), pick = i == 1;
            int tw = ui.width(tabs[i]) + 10;
            boolean over = ui.click(tx, oy + 1, tw, 12, () -> showPayouts = pick);
            if (on || over) ui.fill(tx, oy + 1, tw, 12, on ? Theme.SELECTED : Theme.HOVER);
            ui.text(tabs[i], tx + 5, oy + 3, on ? Theme.ACCENT : over ? Theme.TEXT : Theme.MUTED);
            ui.mark("tab:" + tabs[i], tx + tw / 2, oy + 6);
            tx += tw;
            ui.vline(tx, oy + 1, 12, Theme.BORDER);
            tx++;
        }
        List<OrderRow> open = player.orders == null ? List.of() : player.orders;
        List<Models.Fill> fills = player.orderFills == null ? List.of() : player.orderFills;
        if (showPayouts) {
            // Bagel records that an order was filled, not by whom, so there is no one to name here.
            String total = player.orderSpend == null || player.orderSpend.paid == null ? ""
                    : Fmt.money(player.orderSpend.paid) + " paid in 7d";
            if (tx + ui.width(total) + 10 < x + leftW) ui.textRight(total, x + leftW - 5, oy + 3, Theme.FAINT);
            payouts.render(ui, x, otop - 1, leftW, oy + ordersH - otop + 1, fills, null, true, null,
                    row -> ui.nav.item(row.target()), row -> List.of(row.name == null ? row.item : row.name,
                            "Delivered\t" + Fmt.countExact(row.qty), "Price each\t" + Fmt.moneyExact(row.price),
                            "Paid\t" + Fmt.moneyExact(row.value), "Recorded\t" + Fmt.dayClock(row.t)));
        } else {
            ui.textRight(Fmt.count(open.size()), x + leftW - 5, oy + 3, Theme.FAINT);
            orders.render(ui, x, otop - 1, leftW, oy + ordersH - otop + 1, open, null, true, null,
                    row -> ui.nav.item(row.target()), OrdersView::tip);
        }

        int rx = x + leftW + Parts.GAP;
        int statsH = stats(ui, player, rx, top, rightW);
        auction(ui, player, rx, top + statsH + Parts.GAP, rightW, y + h - top - statsH - Parts.GAP);
        if (!chart.maximized()) {
            lowerSplit.render(ui, x, bodyTop + chartH, leftW, Parts.GAP, ordersH, 14 + 13 + Theme.ROW, lowerMax);
            sideSplit.render(ui, x + leftW, top, Parts.GAP, y + h - top, rightW, 180, sideMax);
        }
    }

    private void header(Ui ui, Models.Player player, int x, int y, int w) {
        String shown = player != null && player.name != null ? player.name : name;
        ui.head(shown, x, y + 1, 32);
        int nx = x + 38;
        // Bagel+ is a plus before the name, as on Bagel's own pages, not a tag.
        if (player != null && "bagelplus".equalsIgnoreCase(player.rankId)) {
            ui.textScaled("+", nx, y + 3, 2, Theme.PLUS);
            ui.mark("bagelplus", nx + 5, y + 10);
            nx += 14;
        }
        ui.textScaled(shown, nx, y + 3, 2, Theme.TEXT);
        int bx = nx + ui.width(shown) * 2 + 10;
        nx = x + 38;
        if (player == null) return;
        if (player.rankId != null && !player.rankId.isEmpty() && !"bagelplus".equalsIgnoreCase(player.rankId)) {
            bx = badge(ui, player.rankId, bx, y + 4, Theme.ACCENT);
        }
        if (player.team != null && !player.team.isEmpty()) bx = badge(ui, player.team, bx, y + 4, Theme.WARN);
        if (player.type != null && !player.type.isEmpty()) bx = badge(ui, player.type, bx, y + 4, Theme.MUTED);
        long now = System.currentTimeMillis() / 1000;
        int sx = nx;
        int sy = y + 24;
        boolean online = player.online || inTabList(shown);
        ui.fill(sx, sy + 1, 5, 5, online ? Theme.UP : Theme.FAINT);
        String seen = online ? "Online" : player.lastOnline == null ? "Offline"
                : "Last online " + Fmt.ago(player.lastOnline, now) + " ago";
        sx += 9 + ui.text(seen, sx + 9, sy, online ? Theme.UP : Theme.MUTED) + 12;
        if (player.firstJoin != null) ui.text("Joined " + Fmt.dayYear(player.firstJoin), sx, sy, Theme.FAINT);
    }

    /** Whether the game's own player list has the name: someone on the server with you is online, whatever Bagel says. */
    private static boolean inTabList(String name) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfoIgnoreCase(name) != null;
    }

    private static int badge(Ui ui, String text, int x, int y, int color) {
        int width = ui.width(text) + 8;
        ui.box(x, y, width, 12, Theme.mix(Theme.PANEL, color, 0.14F), Theme.mix(Theme.PANEL, color, 0.5F));
        ui.text(text, x + 4, y + 2, color);
        return x + width + 4;
    }

    private void tiles(Ui ui, Models.Player player, int x, int y, int w) {
        Double rank = null;
        if (player.stats != null) for (Models.Stat stat : player.stats) if ("money".equals(stat.key)) rank = stat.rank;
        for (int i = 0; i < 4; i++) {
            int tx = x + i * (w + Parts.GAP) / 4;
            int tw = x + (i + 1) * (w + Parts.GAP) / 4 - Parts.GAP - tx;
            switch (i) {
                case 0 -> Parts.tile(ui, tx, y, tw, "Balance", Fmt.money(player.balance), rank == null ? null
                        : "#" + Fmt.count(rank), Theme.MUTED, "Balance", "Exact\t" + Fmt.moneyExact(player.balance));
                case 1 -> Parts.tile(ui, tx, y, tw, "In orders", Fmt.money(player.escrow), player.openOrders == null
                        ? null : Fmt.count(player.openOrders), Theme.MUTED, "Money locked in open buy orders",
                        "Exact\t" + Fmt.moneyExact(player.escrow), "Open orders\t" + Fmt.count(player.openOrders));
                case 2 -> Parts.tile(ui, tx, y, tw, "True balance", Fmt.money(player.trueBalance), null, 0,
                        "Balance plus the money locked in buy orders",
                        "Exact\t" + Fmt.moneyExact(player.trueBalance));
                default -> Parts.tile(ui, tx, y, tw, "Order spend 7d", player.orderSpend == null ? "-"
                                : Fmt.money(player.orderSpend.paid), player.orderSpend == null
                                || player.orderSpend.units == null ? null : Fmt.count(player.orderSpend.units),
                        Theme.MUTED, "Paid out by their buy orders in the last 7 days",
                        "Paid\t" + (player.orderSpend == null ? "-" : Fmt.moneyExact(player.orderSpend.paid)),
                        "Items received\t" + (player.orderSpend == null ? "-" : Fmt.count(player.orderSpend.units)));
            }
        }
    }

    private void chart(Ui ui, Models.Player player, int x, int y, int w, int h) {
        if (player != builtFor) {
            boolean first = builtFor == null;
            builtFor = player;
            build(player);
            if (first) chart.fit();
        }
        int rangesW = ui.segmentsWidth(RANGES);
        chart.title = "Balance";
        chart.controlsWidth = rangesW + 2;
        chart.controls = (draw, hx, hy, right) -> {
            draw.vline(hx, hy, 13, Theme.BORDER);
            draw.segments(hx + 1, hy, RANGES, chart.fitted() || range < 3 ? range : -1, picked -> {
                range = picked;
                if (RANGE_DAYS[picked] == 0) chart.fit();
                else chart.show(newest - RANGE_DAYS[picked] * 86400.0, newest);
            }, false);
            draw.vline(hx + 1 + rangesW, hy, 13, Theme.BORDER);
            return hx + rangesW + 2;
        };
        chart.render(ui, x, y, w, h);
    }

    /** One balance line from the daily closes and, where we have them, our own samples through the day. */
    private void build(Models.Player player) {
        chart.lines.clear();
        List<Models.MoneyPoint> daily = player.daily == null ? List.of() : player.daily;
        List<Models.MoneyPoint> intraday = player.intraday == null ? List.of() : player.intraday;
        double firstSample = intraday.isEmpty() ? Double.POSITIVE_INFINITY : intraday.getFirst().t;
        List<double[]> points = new ArrayList<>();
        for (Models.MoneyPoint point : daily) {
            if (point.money != null && point.t < firstSample) points.add(new double[] {point.t, point.money});
        }
        for (Models.MoneyPoint point : intraday) {
            if (point.money != null) points.add(new double[] {point.t, point.money});
        }
        double[] t = new double[points.size()], v = new double[points.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = points.get(i)[0];
            v[i] = points.get(i)[1];
        }
        newest = t.length == 0 ? System.currentTimeMillis() / 1000.0 : t[t.length - 1];
        Line balance = new Line("Balance", Theme.ACCENT, t, v);
        balance.area = true;
        // True balance where we know what was locked in orders at the time.
        List<Models.EscrowPoint> escrow = player.escrowHistory == null ? List.of() : player.escrowHistory;
        if (!escrow.isEmpty() && t.length > 0) {
            double[] et = new double[escrow.size()], ev = new double[escrow.size()];
            for (int i = 0; i < et.length; i++) {
                Models.EscrowPoint point = escrow.get(i);
                et[i] = point.t;
                int near = balance.nearest(point.t);
                ev[i] = point.escrow == null ? Double.NaN : v[near] + point.escrow;
            }
            chart.lines.add(new Line("True balance", Theme.WARN, et, ev));
        }
        chart.lines.add(balance);
    }

    private static String statLabel(String key) {
        return switch (key) {
            case "kills" -> "Kills";
            case "deaths" -> "Deaths";
            case "playtime" -> "Playtime";
            case "mobs_killed" -> "Mobs killed";
            case "blocks_broken" -> "Blocks broken";
            case "blocks_placed" -> "Blocks placed";
            case "shards" -> "Shards";
            default -> key.isEmpty() ? key : Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ');
        };
    }

    /** Bagel's own stats and where the player places in each. Returns the height used. */
    private int stats(Ui ui, Models.Player player, int x, int y, int w) {
        List<Models.Stat> stats = new ArrayList<>();
        if (player.stats != null) for (Models.Stat stat : player.stats) if (!"money".equals(stat.key)) stats.add(stat);
        // Two columns when there is room for a label, a value and a place in each.
        int columns = w >= 330 ? 2 : 1;
        int rows = Math.max(1, (stats.size() + columns - 1) / columns);
        int h = 14 + rows * 11 + 3;
        int top = Parts.card(ui, x, y, w, h, "Stats");
        ui.textRight("Place", x + w - 5, y + 3, Theme.FAINT);
        if (stats.isEmpty()) {
            ui.text("No stats", x + 5, top + 3, Theme.FAINT);
            return h;
        }
        int cell = (w - 2) / columns;
        for (int i = 0; i < stats.size(); i++) {
            Models.Stat stat = stats.get(i);
            int cx = x + 1 + (i % columns) * cell, cy = top + 2 + (i / columns) * 11;
            if (i % columns == 1) ui.vline(cx, cy - 1, 11, Theme.GRID);
            ui.text(statLabel(stat.key), cx + 4, cy + 1, Theme.MUTED);
            String place = stat.rank == null ? "" : "#" + Fmt.count(stat.rank);
            String value = stat.value == null ? "-" : "playtime".equals(stat.key) ? Fmt.duration(stat.value)
                    : Fmt.count(stat.value);
            ui.textRight(place, cx + cell - 4, cy + 1, Theme.FAINT);
            ui.textRight(value, cx + cell - 52, cy + 1, Theme.TEXT);
        }
        return h;
    }

    private void auction(Ui ui, Models.Player player, int x, int y, int w, int h) {
        int top = Parts.card(ui, x, y, w, h, "Auction house, last 7 days");
        Models.Auction auction = player.auction;
        if (auction == null) {
            ui.text("No auction activity", x + 5, top + 4, Theme.FAINT);
            return;
        }
        // Totals for each side.
        int third = (w - 2) / 3;
        total(ui, x + 1, top, third, "Sold", auction.sold, Theme.DOWN);
        total(ui, x + 1 + third, top, third, "Bought", auction.bought, Theme.UP);
        ui.text("Listed now", x + 1 + 2 * third + 5, top + 3, Theme.FAINT);
        ui.text(auction.listed == null ? "-" : Fmt.money(auction.listed.value), x + 1 + 2 * third + 5, top + 13,
                Theme.TEXT);
        if (auction.listed != null && auction.listed.listings != null) {
            ui.textRight(Fmt.count(auction.listed.listings), x + w - 5, top + 13, Theme.MUTED);
        }
        ui.vline(x + 1 + third, top, 24, Theme.GRID);
        ui.vline(x + 1 + 2 * third, top, 24, Theme.GRID);
        ui.hline(x + 1, top + 24, w - 2, Theme.BORDER);

        int listTop = top + 25;
        int room = y + h - 1 - listTop;
        int topRows = Math.clamp((room - 13 - 13 - 3 * Theme.ROW) / Theme.ROW, 0, 5);
        boolean any = auction.topSold != null && !auction.topSold.isEmpty()
                || auction.topBought != null && !auction.topBought.isEmpty();
        int tableTop = listTop;
        if (topRows > 0 && any) {
            int half = (w - 2) / 2;
            topItems(ui, x + 1, listTop, half, "Most sold", auction.topSold, topRows);
            topItems(ui, x + 1 + half, listTop, w - 2 - half, "Most bought", auction.topBought, topRows);
            ui.vline(x + 1 + half, listTop, 12 + topRows * Theme.ROW, Theme.GRID);
            tableTop = listTop + 12 + topRows * Theme.ROW;
        }
        List<Trade> recent = auction.recent == null ? List.of() : auction.recent;
        trades.render(ui, x, tableTop, w, y + h - tableTop, recent, null, true, null, row -> ui.nav.item(row.item),
                trade -> List.of((sold(trade) ? "Sold " : "Bought ") + Fmt.countExact(trade.qty) + " " + trade.name,
                        "Total\t" + Fmt.moneyExact(trade.price), "Each\t" + Fmt.moneyExact(trade.price / trade.qty),
                        "When\t" + Fmt.dayClock(trade.t)));
    }

    private static void total(Ui ui, int x, int y, int w, String label, Models.Side side, int color) {
        ui.text(label, x + 5, y + 3, color);
        if (side == null) return;
        ui.text(Fmt.money(side.turnover), x + 5, y + 13, Theme.TEXT);
        if (side.trades != null) {
            String trades = Fmt.count(side.trades);
            if (ui.width(Fmt.money(side.turnover)) + ui.width(trades) + 16 <= w) {
                ui.textRight(trades, x + w - 5, y + 13, Theme.MUTED);
            }
        }
        if (ui.hover(x, y, w, 24)) {
            ui.tip(label + " on the auction house", "Turnover\t" + Fmt.moneyExact(side.turnover),
                    "Trades\t" + Fmt.count(side.trades), "Items\t" + Fmt.count(side.volume));
        }
    }

    private static void topItems(Ui ui, int x, int y, int w, String title, List<Models.TopItem> items, int rows) {
        ui.text(title, x + 5, y + 3, Theme.FAINT);
        if (items == null || items.isEmpty()) {
            ui.text("Nothing", x + 5, y + 17, Theme.FAINT);
            return;
        }
        for (int i = 0; i < Math.min(rows, items.size()); i++) {
            Models.TopItem item = items.get(i);
            int ry = y + 12 + i * Theme.ROW;
            boolean over = ui.click(x, ry, w, Theme.ROW, () -> ui.nav.item(item.item));
            if (over) {
                ui.fill(x, ry, w, Theme.ROW, Theme.HOVER);
                ui.tip(item.name, "Turnover\t" + Fmt.moneyExact(item.turnover), "Items\t" + Fmt.count(item.volume),
                        "Trades\t" + Fmt.count(item.trades));
            }
            String value = Fmt.money(item.turnover);
            Parts.item(ui, item.item, item.name, x + 1, ry, w - ui.width(value) - 10);
            ui.textRight(value, x + w - 5, ry + 6, Theme.TEXT);
        }
    }
}

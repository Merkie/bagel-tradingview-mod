package com.merkie.bageltv.view;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Spark;
import com.merkie.bageltv.chart.ValueFormat;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.ItemRow;
import com.merkie.bageltv.data.Models.OrderRow;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.List;
import java.util.function.Function;

/** The dashboard: headline numbers, two chart slots that each show any of the boards, and ranked lists. */
public final class MarketView extends View {
    private final Board[] boards = new Board[2];
    private final String[] kinds = new String[2];
    private final Object[] pickers = {new Object(), new Object()};

    /** The board in a slot: 0 is the left chart, 1 the right. For tests. */
    public Board board(int slot) {
        return boards[slot];
    }

    /** Candle size for the index, so each window shows a comparable number of candles. */
    static String indexTf(String window) {
        return switch (window) {
            case "1h", "6h" -> "5m";
            case "24h" -> "15m";
            case "7d" -> "1h";
            default -> "4h";
        };
    }

    static String leadersTf(String window) {
        return switch (window) {
            case "1h" -> "5m";
            case "6h" -> "15m";
            case "24h" -> "1h";
            case "7d" -> "4h";
            default -> "1d";
        };
    }

    static double seconds(String tf) {
        return switch (tf) {
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "4h" -> 4 * 3600;
            default -> 86400;
        };
    }

    @Override
    public boolean key(int key) {
        for (Board board : boards) if (board != null && board.chart.key(key)) return true;
        return false;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        String window = Parts.window();
        Api.Query<Models.Overview> overview = Api.get("/v1/market/overview?window=" + window, Models.Overview.class);
        ui.reveal(x, y, w, Parts.TOP - Parts.GAP);
        Parts.title(ui, "Market", null, x, y);
        Parts.windowSelector(ui, x + w, y);
        ui.revealed();
        // What the lists really cover when history is shorter than the window.
        String have = Parts.shortfall();
        String span = have == null ? "" : ", last " + have;
        int top = y + Parts.TOP;
        if (!ui.ready(overview, x, top, w, h - Parts.TOP)) return;
        Models.Overview data = overview.data;

        ui.reveal(x, top, w, 27);
        tiles(ui, data, x, top, w);
        ui.revealed();
        int bodyTop = top + 27 + Parts.GAP;
        int bodyH = y + h - bodyTop;
        // Lists get what the charts can spare: four columns in two bands, or two columns in as many bands as
        // fit under a chart of a useful height, with two to ten rows each.
        int columns = w >= 560 ? 4 : 2;
        int bands = 8 / columns;
        while (bands > 1 && bodyH - bands * (15 + 2 * Theme.ROW + Parts.GAP) < 120) bands--;
        int rows = Math.clamp((bodyH - 150 - bands * (15 + Parts.GAP)) / (bands * Theme.ROW), 2, 10);
        int listH = 15 + rows * Theme.ROW;
        int chartsH = bodyH - bands * (listH + Parts.GAP);
        int leftW = (w - Parts.GAP) * 56 / 100;
        ui.reveal(x, bodyTop, leftW, chartsH);
        slot(ui, 0, x, bodyTop, leftW, chartsH);
        ui.revealed();
        ui.reveal(x + leftW + Parts.GAP, bodyTop, w - leftW - Parts.GAP, chartsH);
        slot(ui, 1, x + leftW + Parts.GAP, bodyTop, w - leftW - Parts.GAP, chartsH);
        ui.revealed();

        int listTop = bodyTop + chartsH + Parts.GAP;
        Models.Lists lists = data.lists;
        for (int i = 0; i < columns * bands; i++) {
            int column = i % columns, band = i / columns;
            int lx = x + column * (w + Parts.GAP) / columns;
            int lw = x + (column + 1) * (w + Parts.GAP) / columns - Parts.GAP - lx;
            int ly = listTop + band * (listH + Parts.GAP);
            ui.reveal(lx, ly, lw, listH);
            switch (i) {
                case 0 -> list(ui, lx, ly, lw, listH, "Most traded: turnover" + span, known(lists.turnover), "No trades yet",
                        row -> Fmt.money(row.turnover), row -> Theme.TEXT);
                case 1 -> list(ui, lx, ly, lw, listH, "Most traded: volume" + span, known(lists.volume), "No trades yet",
                        row -> Fmt.count(row.volume), row -> Theme.TEXT);
                case 2 -> list(ui, lx, ly, lw, listH, "Top gainers", enough(lists.gainers), "Not enough sales yet",
                        row -> Fmt.pct(row.change), row -> Theme.change(row.change));
                case 3 -> list(ui, lx, ly, lw, listH, "Top losers", enough(lists.losers), "Not enough sales yet",
                        row -> Fmt.pct(row.change), row -> Theme.change(row.change));
                case 4 -> list(ui, lx, ly, lw, listH, "Sells fastest" + span, enough(lists.fastest), "Not enough sales yet",
                        row -> Fmt.duration(row.medianTimeToSell), row -> Theme.TEXT);
                case 5 -> list(ui, lx, ly, lw, listH, "Best to sell" + span, enough(lists.sell), "Not enough sales yet",
                        row -> row.sellScore == null ? "-" : Fmt.money(row.sellScore) + "/h", row -> Theme.UP);
                case 6 -> list(ui, lx, ly, lw, listH, "Most wanted", lists.wanted, "No open buy orders",
                        row -> Fmt.money(row.bidValue), row -> Theme.TEXT);
                default -> opportunities(ui, lx, ly, lw, listH, data.opportunities);
            }
            ui.revealed();
        }
    }

    /** A ranking by volume or money leaves out variants, whose counts are only a floor. */
    private static List<ItemRow> known(List<ItemRow> rows) {
        if (rows == null) return List.of();
        return rows.stream().filter(row -> !row.variant()).toList();
    }

    /** A ranking drawn from sales leaves out items with too few of them to mean anything. */
    private static List<ItemRow> enough(List<ItemRow> rows) {
        if (rows == null) return List.of();
        return rows.stream().filter(row -> row.trades != null && row.trades >= 5).toList();
    }

    private void tiles(Ui ui, Models.Overview data, int x, int y, int w) {
        Models.Totals totals = data.totals;
        Models.Economy economy = data.economy;
        Models.Index idx = data.index;
        int count = w >= 620 ? 7 : 4;
        for (int i = 0; i < count; i++) {
            int tx = x + i * (w + Parts.GAP) / count;
            int tw = x + (i + 1) * (w + Parts.GAP) / count - Parts.GAP - tx;
            switch (i) {
                case 0 -> Parts.tile(ui, tx, y, tw, "Turnover", Fmt.money(totals.turnover),
                        totals.turnoverChange == null ? null : Fmt.pct(totals.turnoverChange),
                        Theme.change(totals.turnoverChange), "Money traded on the auction house",
                        "Turnover\t" + Fmt.moneyExact(totals.turnover),
                        "Into buy orders\t" + Fmt.moneyExact(totals.orderTurnover),
                        "Items traded\t" + Fmt.count(totals.items));
                case 1 -> Parts.tile(ui, tx, y, tw, "Volume", Fmt.count(totals.volume), null, 0,
                        "Items traded on the auction house", "Volume\t" + Fmt.countExact(nz(totals.volume)),
                        "Into buy orders\t" + Fmt.countExact(nz(totals.orderVolume)));
                case 2 -> Parts.tile(ui, tx, y, tw, "Trades", Fmt.count(totals.trades), null, 0);
                case 3 -> Parts.tile(ui, tx, y, tw, "Market index", idx == null || idx.value == null ? "-"
                                : ValueFormat.INDEX.format(idx.value), idx == null || idx.change == null ? null
                                : Fmt.pct(idx.change),
                        Theme.change(idx == null ? null : idx.change),
                        "Turnover-weighted price index of every item", "Base\t1,000");
                case 4 -> Parts.tile(ui, tx, y, tw, "Money supply", Fmt.money(economy.totalMoney),
                        economy.totalMoneyChange == null ? null : Fmt.pct(economy.totalMoneyChange),
                        Theme.change(economy.totalMoneyChange),
                        "All the money players hold", "Total\t" + Fmt.moneyExact(economy.totalMoney),
                        "Players\t" + Fmt.count(economy.players));
                case 5 -> Parts.tile(ui, tx, y, tw, "Players online", Fmt.count(economy.online), null, 0);
                default -> Parts.tile(ui, tx, y, tw, "In buy orders", Fmt.money(economy.orderEscrow),
                        economy.openOrders == null ? null : Fmt.count(economy.openOrders), Theme.MUTED,
                        "Money locked in open buy orders", "Locked\t" + Fmt.moneyExact(economy.orderEscrow),
                        "Open orders\t" + Fmt.count(economy.openOrders));
            }
        }
    }

    private static double nz(Double value) {
        return value == null ? 0 : value;
    }

    /** One of the two chart slots, with the picker that chooses what it shows. */
    private void slot(Ui ui, int slot, int x, int y, int w, int h) {
        var config = BagelTV.config();
        String kind = slot == 0 ? config.marketLeft : config.marketRight;
        if (Parts.index(Board.KINDS, kind) == 0) kind = "index";
        if (boards[slot] == null || !kind.equals(kinds[slot])) {
            kinds[slot] = kind;
            Board board = Board.create(kind);
            board.picker = (draw, px, py, right) -> px + draw.dropdown(pickers[slot], px, py, Board.NAMES,
                    Parts.index(Board.KINDS, kinds[slot]), picked -> {
                        if (slot == 0) config.marketLeft = Board.KINDS[picked];
                        else config.marketRight = Board.KINDS[picked];
                        config.save();
                    });
            boards[slot] = board;
        }
        boards[slot].pickerWidth = ui.width(Board.name(kind)) + 17;
        boards[slot].render(ui, x, y, w, h);
    }

    private void list(Ui ui, int x, int y, int w, int h, String title, List<ItemRow> rows, String empty,
                      Function<ItemRow, String> value, Function<ItemRow, Integer> color) {
        int top = Parts.card(ui, x, y, w, h, title);
        if (rows == null || rows.isEmpty()) {
            ui.textCenter(ui.fit(empty, w - 8), x + w / 2, top + Math.min((h - 14) / 2 - 4, 22), Theme.FAINT);
            return;
        }
        int count = Math.min(rows.size(), (y + h - 1 - top) / Theme.ROW);
        boolean spark = w >= 150;
        for (int i = 0; i < count; i++) {
            ItemRow row = rows.get(i);
            int ry = top + i * Theme.ROW;
            boolean over = ui.click(x + 1, ry, w - 2, Theme.ROW, () -> ui.nav.item(row.item));
            if (over) {
                ui.fill(x + 1, ry, w - 2, Theme.ROW, Theme.HOVER);
                ui.tip(Parts.itemTip(row));
            }
            String text = value.apply(row);
            int valueW = Math.max(38, ui.width(text) + 4);
            int nameRoom = w - 6 - valueW - (spark ? 34 : 0);
            Parts.item(ui, row.item, row.label(), x + 2, ry, nameRoom);
            if (spark) Spark.draw(ui, x + w - 5 - valueW - 30, ry + 5, 28, 8, row.spark);
            ui.textRight(text, x + w - 5, ry + 6, color.apply(row));
        }
    }

    /** The buy orders offering the most above /sell. */
    private void opportunities(Ui ui, int x, int y, int w, int h, List<OrderRow> rows) {
        int top = Parts.card(ui, x, y, w, h, "Best buy orders");
        if (rows == null || rows.isEmpty()) {
            ui.textCenter("No open buy orders", x + w / 2, top + Math.min((h - 14) / 2 - 4, 22), Theme.FAINT);
            return;
        }
        int count = Math.min(rows.size(), (y + h - 1 - top) / Theme.ROW);
        for (int i = 0; i < count; i++) {
            OrderRow row = rows.get(i);
            int ry = top + i * Theme.ROW;
            boolean over = ui.click(x + 1, ry, w - 2, Theme.ROW, () -> ui.nav.item(row.target()));
            if (over) {
                ui.fill(x + 1, ry, w - 2, Theme.ROW, Theme.HOVER);
                ui.tip(OrdersView.tip(row));
            }
            String pool = Fmt.money(row.pool);
            String each = Fmt.money(row.price);
            int valueW = Math.max(38, ui.width(pool) + 4);
            boolean showEach = w >= 170;
            Parts.item(ui, row.target(), row.label(), x + 2, ry, w - 6 - valueW - (showEach ? ui.width(each) + 8 : 0));
            if (showEach) ui.textRight(each, x + w - 5 - valueW - 4, ry + 6, Theme.MUTED);
            ui.textRight(pool, x + w - 5, ry + 6, Theme.UP);
        }
    }
}

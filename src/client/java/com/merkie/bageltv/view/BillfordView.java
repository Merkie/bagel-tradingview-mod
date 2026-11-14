package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.BillfordItem;
import com.merkie.bageltv.data.Models.BillfordTrade;
import com.merkie.bageltv.ui.Icon;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Billford, the trader behind /billford: the trade he offers now and every trade he has offered, newest first. Each
 * is rated twice, by what the two sides are worth to /sell and by what they sell for on the auction house, at the
 * prices of the trade's own time. The two can disagree, so neither is folded into the other.
 */
public final class BillfordView extends View {
    private static final int NOW = 62;
    private final Table<BillfordTrade> table = new Table<>(Theme.ROW);

    public BillfordView() {
        table.empty = "No trades recorded yet";
        table.col("Seen", 78, (ui, row, x, y, w, h) -> Table.left(ui, Fmt.dayClock(row.from), x, y, w, h,
                Theme.MUTED)).left();
        table.col("Lasted", 52, (ui, row, x, y, w, h) -> Table.right(ui, row.to == null ? "Now"
                : Fmt.duration(row.to - row.from), x, y, w, h, row.to == null ? Theme.ACCENT : Theme.MUTED));
        table.col("You give", 120, (ui, row, x, y, w, h) -> side(ui, row.give, x + 4, y, w - 6, false)).left().flex(1);
        table.col("You get", 120, (ui, row, x, y, w, h) -> side(ui, row.get, x + 4, y, w - 6, false)).left().flex(1);
        table.col("By worth", 62, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.worthRatio), x, y, w, h,
                color(row.worthRatio))).tip("What you get over what you give, at /sell worth");
        table.col("By AH sales", 72, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.ahRatio), x, y, w, h,
                color(row.ahRatio))).tip("What you get over what you give, at the average auction sale that day");
        table.col("Verdict", 100, (ui, row, x, y, w, h) -> Table.left(ui, verdict(row), x + 6, y, w - 6, h,
                verdictColor(row))).left();
    }

    public Table<BillfordTrade> table() {
        return table;
    }

    /** Whether a trade gives back more than it takes. The two kinds of price each get a say. */
    static String verdict(BillfordTrade trade) {
        Double worth = trade.worthRatio, ah = trade.ahRatio;
        if (worth == null && ah == null) return "-";
        if (worth == null || ah == null || worth >= 1 == ah >= 1) {
            return (worth != null ? worth : ah) >= 1 ? "Worth it" : "Not worth it";
        }
        return worth >= 1 ? "Only by worth" : "Only by AH sales";
    }

    private static int verdictColor(BillfordTrade trade) {
        return switch (verdict(trade)) {
            case "Worth it" -> Theme.UP;
            case "Not worth it" -> Theme.DOWN;
            case "-" -> Theme.FAINT;
            default -> Theme.WARN;
        };
    }

    private static int color(Double ratio) {
        return ratio == null ? Theme.FAINT : ratio >= 1 ? Theme.UP : Theme.DOWN;
    }

    /**
     * One side of a trade in a row 18 px tall: each item's icon, count and name. With links, a name opens its
     * item. Returns the width drawn.
     */
    private static int side(Ui ui, List<BillfordItem> items, int x, int y, int room, boolean links) {
        int cx = x;
        for (int i = 0; i < items.size(); i++) {
            BillfordItem item = items.get(i);
            if (i > 0) cx += ui.text("+", cx, y + 6, Theme.FAINT) + 5;
            String label = ui.fit(Fmt.count(item.qty) + " " + item.label(), Math.max(20, x + room - cx - 20));
            int width = 20 + ui.width(label);
            boolean over = links && ui.click(cx, y, width, 18, () -> ui.nav.item(item.item));
            ui.item(item.item, cx, y + 1);
            ui.text(label, cx + 20, y + 6, over ? Theme.ACCENT : Theme.TEXT);
            if (over) ui.hline(cx + 20, y + 14, width - 21, Theme.ACCENT);
            cx += width + 6;
            if (cx >= x + room) break;
        }
        return cx - 6 - x;
    }

    private static List<String> tip(BillfordTrade trade) {
        List<String> lines = new ArrayList<>();
        lines.add(Fmt.dayClock(trade.from) + (trade.to == null ? " to now" : " to " + Fmt.dayClock(trade.to)));
        for (BillfordItem item : trade.give) lines.add("Give " + Fmt.countExact(item.qty) + " " + item.label()
                + "\t" + prices(item));
        for (BillfordItem item : trade.get) lines.add("Get " + Fmt.countExact(item.qty) + " " + item.label()
                + "\t" + prices(item));
        if (trade.worthRatio != null) lines.add("By worth\t" + Fmt.moneyExact(trade.giveWorth) + " for "
                + Fmt.moneyExact(trade.getWorth));
        if (trade.ahRatio != null) lines.add("By AH sales\t" + Fmt.moneyExact(trade.giveAh) + " for "
                + Fmt.moneyExact(trade.getAh));
        return lines;
    }

    /** An item's two prices each, as they were when its trade was last seen. */
    private static String prices(BillfordItem item) {
        return "worth " + (item.worth == null ? "-" : Fmt.moneyExact(item.worth)) + ", AH sales "
                + (item.ah == null ? "-" : Fmt.moneyExact(item.ah));
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Api.Query<Models.Billford> query = Api.get("/v1/billford", Models.Billford.class);
        long now = System.currentTimeMillis() / 1000;
        String note = query.data == null || query.data.checkedAt == null ? null
                : "Read in game " + Fmt.ago(query.data.checkedAt, now) + " ago";
        int tx = Parts.title(ui, "Billford", note, x, y);
        if (query.loading) ui.spinner(tx, y + 5);
        int top = y + Parts.TOP;
        if (!ui.ready(query, x, top, w, y + h - top)) return;
        List<BillfordTrade> trades = query.data.trades == null ? List.of() : query.data.trades;
        if (!trades.isEmpty() && trades.getFirst().to == null) {
            current(ui, trades.getFirst(), x, top, w, now);
            top += NOW + Parts.GAP;
        }
        table.render(ui, x, top, w, y + h - top, trades, null, false, null,
                row -> ui.nav.item(row.get.getFirst().item), BillfordView::tip);
    }

    /** The trade he offers now: both sides, how each kind of price rates it, and the verdict. */
    private void current(Ui ui, BillfordTrade trade, int x, int y, int w, long now) {
        int cy = Parts.card(ui, x, y, w, NOW, "Trading now");
        int tile = Math.clamp((w - 180) / 3, 76, 146), tiles = tile * 3 + Parts.GAP * 2;
        int room = w - tiles - 22;
        ui.clip(x + 1, cy, room + 8, NOW - 15);
        int cx = x + 8 + side(ui, trade.give, x + 8, cy + 5, room, true) + 8;
        ui.icon(Icon.FORWARD, cx, cy + 10, Theme.MUTED);
        side(ui, trade.get, cx + 15, cy + 5, Math.max(40, x + 8 + room - cx - 15), true);
        ui.text("Since " + Fmt.dayClock(trade.from) + ", " + Fmt.duration(Math.max(0, now - trade.from)) + " ago",
                x + 8, cy + 31, Theme.FAINT);
        ui.unclip();
        int right = x + w - 6 - tiles;
        rating(ui, right, cy + 9, tile, "By worth", trade.worthRatio, trade.giveWorth, trade.getWorth);
        rating(ui, right + tile + Parts.GAP, cy + 9, tile, "By AH sales", trade.ahRatio, trade.giveAh, trade.getAh);
        int vx = right + (tile + Parts.GAP) * 2;
        ui.box(vx, cy + 9, tile, 27, Theme.RAISED, Theme.BORDER);
        ui.text("Verdict", vx + 5, cy + 13, Theme.FAINT);
        ui.text(ui.fit(verdict(trade), tile - 8), vx + 5, cy + 24, verdictColor(trade));
        ui.mark("billford:verdict", vx + tile / 2, cy + 22);
    }

    /** One kind of price's say: get over give, with the two totals behind it. */
    private static void rating(Ui ui, int x, int y, int w, String label, Double ratio, Double give, Double get) {
        ui.box(x, y, w, 27, Theme.RAISED, Theme.BORDER);
        ui.text(label, x + 5, y + 4, Theme.FAINT);
        int used = ui.text(Fmt.ratio(ratio), x + 5, y + 15, color(ratio));
        if (ratio == null) return;
        String totals = Fmt.money(give) + " for " + Fmt.money(get);
        if (used + ui.width(totals) + 14 <= w) ui.textRight(totals, x + w - 5, y + 15, Theme.MUTED);
        if (ui.hover(x, y, w, 27)) ui.tip(label, "You give\t" + Fmt.moneyExact(give), "You get\t" + Fmt.moneyExact(get));
    }
}

package com.merkie.bageltv.view;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.ItemRow;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Pieces the views share: the window selector, tiles, cards, and links to items and players. */
public final class Parts {
    public static final String[] WINDOWS = {"1h", "6h", "24h", "7d", "30d"};
    /** Height of a view's top row of title and controls. */
    public static final int TOP = 17;
    public static final int GAP = 4;

    private Parts() {}

    public static String window() {
        return BagelTV.config().window;
    }

    public static int index(String[] options, String value) {
        for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
        return 0;
    }

    public static double windowSeconds(String window) {
        return switch (window) {
            case "1h" -> 3600;
            case "6h" -> 6 * 3600;
            case "7d" -> 7 * 86400;
            case "30d" -> 30 * 86400;
            default -> 86400;
        };
    }

    /**
     * How much history there really is when it is shorter than the picked window, such as "3h", or null when
     * the window is covered. Sales have only been recorded since the collector started.
     */
    public static String shortfall() {
        Models.Status status = Api.get("/v1/status", Models.Status.class).data;
        if (status == null || status.since == null || status.since.sales == null) return null;
        double have = status.now - status.since.sales;
        return have < windowSeconds(window()) ? Fmt.span(have) : null;
    }

    /**
     * The 1h to 30d selector, right-aligned. When sales history is shorter than the picked window, the real
     * span is written beside it, since that is what the numbers cover. Returns its left edge.
     */
    public static int windowSelector(Ui ui, int right, int y) {
        return windowSelector(ui, right, y, true);
    }

    public static int windowSelector(Ui ui, int right, int y, boolean sales) {
        int width = ui.segmentsWidth(WINDOWS);
        ui.segments(right - width - 1, y + 1, WINDOWS, index(WINDOWS, window()), picked -> {
            BagelTV.config().window = WINDOWS[picked];
            BagelTV.config().save();
        });
        int left = right - width - 2;
        String have = sales ? shortfall() : null;
        if (have != null) {
            String text = "covers last " + have;
            left -= 6 + ui.width(text);
            ui.text(text, left, y + 5, Theme.WARN);
            left -= 2;
        }
        return left;
    }

    /** A view's title at the left of its top row. Returns the x after it. */
    public static int title(Ui ui, String title, String note, int x, int y) {
        int tx = x + 1 + ui.text(title, x + 1, y + 5, Theme.TEXT) + 8;
        if (note != null) tx += ui.text(note, tx, y + 5, Theme.FAINT) + 8;
        return tx;
    }

    /** A framed box with a label along its top. Returns the y where its contents start. */
    public static int card(Ui ui, int x, int y, int w, int h, String title) {
        ui.box(x, y, w, h, Theme.BG, Theme.BORDER);
        ui.fill(x + 1, y + 1, w - 2, 12, Theme.RAISED);
        ui.hline(x + 1, y + 13, w - 2, Theme.BORDER);
        ui.text(ui.fit(title, w - 10), x + 5, y + 3, Theme.MUTED);
        return y + 14;
    }

    /** A headline number: a label, the value, and a change or note beside it. */
    public static void tile(Ui ui, int x, int y, int w, String label, String value, String note, int noteColor,
                            String... tip) {
        ui.box(x, y, w, 27, Theme.RAISED, Theme.BORDER);
        ui.text(ui.fit(label, w - 8), x + 5, y + 4, Theme.FAINT);
        int used = ui.text(value, x + 5, y + 15, Theme.TEXT);
        if (note != null && used + ui.width(note) + 14 <= w) ui.textRight(note, x + w - 5, y + 15, noteColor);
        if (tip.length > 0 && ui.hover(x, y, w, 27)) ui.tip(tip);
    }

    /** A name that opens its player. Returns the width drawn. */
    public static int player(Ui ui, String name, int x, int y, int room) {
        if (name == null || name.isEmpty()) return ui.text("-", x, y, Theme.FAINT);
        String shown = ui.fit(name, room);
        int width = ui.width(shown);
        boolean over = ui.click(x - 1, y - 2, width + 2, 11, () -> ui.nav.player(name));
        ui.text(shown, x, y, over ? Theme.ACCENT : Theme.TEXT);
        if (over) ui.hline(x, y + 8, width - 1, Theme.ACCENT);
        return width;
    }

    /** An item's icon and name in a row 18 px tall, which opens the item. */
    public static void item(Ui ui, String key, String name, int x, int y, int room) {
        ui.item(key, x, y + 1);
        ui.text(ui.fit(name == null ? key : name, room - 20), x + 20, y + 6, Theme.TEXT);
    }

    /** The exact figures behind an item row, for its tooltip. */
    public static List<String> itemTip(ItemRow row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.label());
        if (row.last != null) lines.add("Last\t" + Fmt.moneyExact(row.last));
        if (row.vwap != null) lines.add("VWAP\t" + Fmt.moneyExact(row.vwap));
        if (row.change != null) lines.add("Change\t" + Fmt.pct(row.change));
        if (row.low != null && row.high != null) {
            lines.add("Range\t" + Fmt.moneyExact(row.low) + " - " + Fmt.moneyExact(row.high));
        }
        if (row.turnover != null) lines.add("Turnover\t" + row.floor() + Fmt.moneyExact(row.turnover));
        if (row.volume != null) lines.add("Volume\t" + row.floor() + Fmt.countExact(row.volume));
        if (row.trades != null) lines.add("Trades\t" + row.floor() + Fmt.countExact(row.trades));
        if (row.variant() && row.coverage != null) {
            lines.add("Kind known for " + Fmt.share(row.coverage) + " of sales, so these are minimums");
        }
        if (row.sellThrough != null) lines.add("Sell-through\t" + Fmt.share(row.sellThrough));
        if (row.medianTimeToSell != null) lines.add("Median time to sell\t" + Fmt.duration(row.medianTimeToSell));
        if (row.worth != null) lines.add("Worth (/sell)\t" + Fmt.moneyExact(row.worth));
        if (row.bestAsk != null) lines.add("AH price\t" + Fmt.moneyExact(row.bestAsk));
        if (row.orderVwap != null) lines.add("Orders paid\t" + Fmt.moneyExact(row.orderVwap));
        if (row.bestBid != null) lines.add("Top order\t" + Fmt.moneyExact(row.bestBid));
        if (row.askListings != null && row.askListings > 0) {
            lines.add("Listed now\t" + Fmt.count(row.askListings) + " listings, " + Fmt.count(row.askUnits)
                    + " items");
        }
        return lines;
    }
}

package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Spark;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.ItemRow;
import com.merkie.bageltv.ui.Icon;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.TextField;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.List;

/** Every traded item in one sortable, searchable table. */
public final class ScreenerView extends View {
    private static final String[] CATEGORIES = {"", "BLOCKS", "INGREDIENTS", "UTILITIES", "COMBAT", "TOOLS", "BOOKS",
            "POTIONS", "FOOD"};
    private static final String[] CATEGORY_LABELS = {"All", "Blocks", "Ingredients", "Utilities", "Combat", "Tools",
            "Books", "Potions", "Food"};
    private static final int[] MIN_TRADES = {0, 5, 20, 100};
    private static final String[] MIN_LABELS = {"Any", "5+", "20+", "100+"};

    private final Table<ItemRow> table = new Table<>(Theme.ROW);
    private final Pager<Models.ItemList, ItemRow> pager = new Pager<>(Models.ItemList.class, list -> list.items,
            list -> list.total);
    private final TextField search = new TextField("Filter by item", 32);
    private String sort = "turnover";
    private boolean descending = true;
    private int category;
    private int minTrades;

    public ScreenerView() {
        table.empty = "No items match";
        table.col("Item", 104, (ui, row, x, y, w, h) -> Parts.item(ui, row.item, row.label(), x + 1, y, w - 2))
                .left().flex(1).sort("name");
        table.col("Last", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.last), x, y, w, h, Theme.TEXT))
                .sort("last").tip("Unit price of the latest sale");
        table.col("Change", 48, (ui, row, x, y, w, h) -> Table.right(ui, row.change == null ? ""
                : Fmt.pct(row.change), x, y, w, h, Theme.change(row.change))).sort("change").tip("VWAP against the window before");
        table.col("VWAP", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.vwap), x, y, w, h, Theme.MUTED))
                .sort("vwap").tip("Volume-weighted average price: turnover over volume").drop(8);
        table.col("Turnover", 62, (ui, row, x, y, w, h) -> Table.right(ui, row.floor() + Fmt.money(row.turnover), x,
                y, w, h, Theme.TEXT)).sort("turnover").tip("Money traded");
        table.col("Volume", 46, (ui, row, x, y, w, h) -> Table.right(ui, row.floor() + Fmt.count(row.volume), x, y,
                w, h, Theme.TEXT)).sort("volume").tip("Items traded");
        table.col("Trades", 42, (ui, row, x, y, w, h) -> Table.right(ui, row.floor() + Fmt.count(row.trades), x, y,
                w, h, Theme.MUTED)).sort("trades").tip("Number of sales").drop(5);
        table.col("Sold", 40, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.share(row.sellThrough), x, y, w, h,
                row.sellThrough == null ? Theme.FAINT : row.sellThrough >= 0.5 ? Theme.UP : Theme.MUTED))
                .sort("sellThrough").tip("Sell-through: share of new listings that sold").drop(4);
        table.col("Sells in", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.duration(row.medianTimeToSell), x, y,
                w, h, Theme.MUTED)).sort("medianTimeToSell").tip("Median time from listing to sale").drop(6);
        table.col("Premium", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.premium), x, y, w, h,
                row.premium == null ? Theme.FAINT : row.premium >= 1 ? Theme.UP : Theme.DOWN))
                .sort("premium").tip("VWAP over worth, the /sell value").drop(7);
        table.col("Top order", 56, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.bestBid), x, y, w, h, Theme.ORDERS))
                .sort("bestBid").tip("Highest open buy order").drop(1);
        table.col("AH price", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.bestAsk), x, y, w, h, Theme.ACCENT))
                .sort("bestAsk").tip("Cheapest listing").drop(3);
        table.col("Listed", 44, (ui, row, x, y, w, h) -> Table.right(ui, row.askListings == null
                || row.askListings == 0 ? "-" : Fmt.count(row.askListings), x, y, w, h, row.askListings == null
                || row.askListings == 0 ? Theme.FAINT : Theme.TEXT)).sort("askListings")
                .tip("Listings on the auction house now: supply to weigh against sales").drop(2);
        table.col("Trend", 58, (ui, row, x, y, w, h) -> Spark.draw(ui, x + 6, y + 4, w - 12, h - 8, row.spark))
                .tip("VWAP across the window");
    }

    public Table<ItemRow> table() {
        return table;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        int tx = Parts.title(ui, "Screener", null, x, y);
        int right = Parts.windowSelector(ui, x + w, y);
        int minW = ui.segmentsWidth(MIN_LABELS);
        right -= 6 + minW;
        ui.segments(right, y + 1, MIN_LABELS, minTrades, picked -> minTrades = picked);
        String label = "Trades";
        right -= 5 + ui.width(label);
        ui.text(label, right, y + 5, Theme.FAINT);
        int fieldW = Math.clamp(right - 8 - tx, 60, 150);
        ui.field(search, tx, y + 1, fieldW, Icon.SEARCH);

        // Category chips on their own row.
        int cy = y + Parts.TOP + 1;
        int cx = x + 1;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int index = i;
            int width = ui.width(CATEGORY_LABELS[i]) + 10;
            if (cx + width > x + w) break;
            ui.button(CATEGORY_LABELS[i], cx, cy, category == i, () -> category = index);
            cx += width + 3;
        }

        String path = "/v1/items?window=" + Parts.window() + "&sort=" + sort + "&order=" + (descending ? "desc" : "asc")
                + "&q=" + Api.encode(search.text.trim().toLowerCase().replace(' ', '_'))
                + "&category=" + CATEGORIES[category] + "&minTrades=" + (MIN_TRADES[minTrades] == 0 ? ""
                : MIN_TRADES[minTrades]);
        List<ItemRow> rows = pager.rows(path, table.nearEnd);
        if (pager.restarted) table.scroll = 0;
        String count = pager.first.data == null ? "" : Fmt.count(pager.total) + (pager.total == 1 ? " item" : " items");
        ui.textRight(count, x + w - 1, cy + 3, Theme.FAINT);
        if (pager.loading) ui.spinner(x + w - 12 - ui.width(count), cy + 3);

        int top = cy + 13 + Parts.GAP;
        if (pager.first.data == null && rows.isEmpty()) {
            if (!ui.ready(pager.first, x, top, w, y + h - top)) return;
        }
        table.render(ui, x, top, w, y + h - top, rows, sort, descending, key -> {
            if (key.equals(sort)) descending = !descending;
            else {
                sort = key;
                descending = !key.equals("name") && !key.equals("medianTimeToSell");
            }
        }, row -> ui.nav.item(row.item), Parts::itemTip);
    }
}

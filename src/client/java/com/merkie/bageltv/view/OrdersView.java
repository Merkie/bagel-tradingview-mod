package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.OrderRow;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Every open buy order, sortable, with the ones worth filling picked out. */
public final class OrdersView extends View {
    private final Table<OrderRow> table = new Table<>(Theme.ROW);
    private final Pager<Models.Orders, OrderRow> pager = new Pager<>(Models.Orders.class, list -> list.orders,
            list -> list.total);
    private final Table<Models.Owner> owners = new Table<>(Theme.ROW);
    private final Pager<Models.Owners, Models.Owner> ownerPager = new Pager<>(Models.Owners.class,
            list -> list.owners, list -> list.total);
    private static final String[] TABS = {"Orders", "Owners"};
    private boolean showOwners;
    private String ownerSort = "paid";
    private boolean ownerDescending = true;
    private double mostLocked = 1;
    /** Orders that took a delivery in the window, against orders nobody delivers to. */
    private static final int ACTIVE = Theme.UP, IDLE = Theme.WARN;
    private String sort = "pool";
    private boolean descending = true;

    public OrdersView() {
        ownerColumns();
        table.empty = "No open buy orders";
        table.col("Item", 118, (ui, row, x, y, w, h) -> {
            // A bar down the left marks an order worth a look: emerald pays above the auction price, blue has a
            // cheaper listing to fill it from.
            if (arbitrage(row)) ui.fill(x, y, 2, h, Theme.ACCENT);
            else if (aboveAuction(row)) ui.fill(x, y, 2, h, Theme.UP);
            Parts.item(ui, row.target(), row.label(), x + 3, y, w - 4);
        }).left().flex(1);
        table.col("Buyer", 84, (ui, row, x, y, w, h) -> Parts.player(ui, row.owner, x + 4, Ui.textY(y, h), w - 8))
                .left();
        table.col("Pays", 52, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.price), x, y, w, h, Theme.TEXT))
                .sort("price").tip("Price per item");
        table.col("vs worth", 50, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.vsWorth), x, y, w, h,
                row.vsWorth == null ? Theme.FAINT : row.vsWorth >= 1 ? Theme.UP : Theme.DOWN))
                .sort("vsWorth").tip("Price over worth, the /sell value").drop(5);
        table.col("vs AH", 46, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.ratio(row.vsAh), x, y, w, h,
                row.vsAh == null ? Theme.FAINT : row.vsAh >= 1 ? Theme.UP : Theme.DOWN))
                .sort("vsAh").tip("Price over the 24h auction VWAP").drop(3);
        table.col("Edge", 48, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.pct(row.edge), x, y, w, h,
                Theme.change(row.edge))).sort("edge").tip("Price against the auction VWAP, or worth if it does not "
                + "trade");
        table.col("Left", 76, (ui, row, x, y, w, h) -> {
            double qty = row.qty == null ? 0 : row.qty, remaining = row.remaining == null ? 0 : row.remaining;
            Table.right(ui, Fmt.count(remaining), x, y - 2, w, h, Theme.TEXT);
            // How much of the order is filled already.
            int bar = w - 8;
            int filled = qty <= 0 ? 0 : (int) Math.round(bar * Math.clamp((qty - remaining) / qty, 0, 1));
            ui.fill(x + 4, y + h - 4, bar, 2, Theme.BORDER);
            ui.fill(x + 4, y + h - 4, filled, 2, Theme.UP);
        }).sort("remaining").tip("Items still wanted, and how much is filled");
        table.col("Value", 54, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.value), x, y, w, h,
                Theme.MUTED)).sort("value").tip("Money locked in the order: items left times price").drop(2);
        table.col("Pool", 54, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.money(row.pool), x, y, w, h,
                row.pool != null && row.pool > 0 ? Theme.UP : Theme.MUTED))
                .sort("pool").tip("Money on offer above /sell: (price - worth) x items left");
        table.col("Arbitrage", 56, (ui, row, x, y, w, h) -> Table.right(ui, row.arbitrage == null ? "-"
                        : Fmt.money(row.arbitrage), x, y, w, h, row.arbitrage == null ? Theme.FAINT : Theme.ACCENT))
                .sort("arbitrage").tip("Per item, when a listing is cheaper than this order pays");
        table.col("Filled 1h", 52, (ui, row, x, y, w, h) -> Table.right(ui, row.filledLastHour == null
                        || row.filledLastHour == 0 ? "-" : Fmt.count(row.filledLastHour), x, y, w, h, Theme.MUTED))
                .sort("filledLastHour").tip("Items delivered in the last hour").drop(4);
        table.col("Age", 34, (ui, row, x, y, w, h) -> Table.right(ui, row.createdAt == null ? "-"
                        : Fmt.ago(row.createdAt, System.currentTimeMillis() / 1000), x, y, w, h, Theme.FAINT))
                .sort("createdAt").tip("Time since the order was placed").drop(6);
    }

    private void ownerColumns() {
        owners.empty = "No buy orders in this window";
        owners.col("#", 26, (ui, row, x, y, w, h) -> Table.right(ui, Integer.toString(row.rank), x, y, w, h,
                Theme.FAINT));
        owners.col("Player", 96, (ui, row, x, y, w, h) -> {
            ui.head(row.name, x + 3, y + 4, 8);
            ui.text(ui.fit(row.name, w - 20), x + 15, Ui.textY(y, h), Theme.TEXT);
        }).left().flex(1);
        owners.col("Paid out", 58, (ui, row, x, y, w, h) -> Table.right(ui, row.paid == 0 ? "-"
                : Fmt.money(row.paid), x, y, w, h, row.paid == 0 ? Theme.FAINT : Theme.TEXT))
                .sort("paid").tip("Money their orders paid for deliveries in the window");
        owners.col("Delivered", 56, (ui, row, x, y, w, h) -> Table.right(ui, row.units == 0 ? "-"
                : Fmt.count(row.units), x, y, w, h, Theme.MUTED)).sort("units")
                .tip("Items delivered into their orders in the window").drop(3);
        owners.col("Filled", 40, (ui, row, x, y, w, h) -> Table.right(ui, row.ordersFilled == 0 ? "-"
                : Fmt.count(row.ordersFilled), x, y, w, h, Theme.MUTED))
                .tip("Orders that received a delivery in the window").drop(4);
        owners.col("Top item", 110, (ui, row, x, y, w, h) -> {
            if (row.topItem == null) {
                Table.left(ui, "-", x, y, w, h, Theme.FAINT);
                return;
            }
            String item = row.topItem.item;
            boolean over = ui.click(x + 1, y, w - 2, h, () -> ui.nav.item(item));
            Parts.item(ui, item, Models.compact(row.topItem.name), x + 1, y, w - 2);
            if (over) ui.hline(x + 21, y + 14, Math.min(ui.width(Models.compact(row.topItem.name)), w - 24) - 1,
                    Theme.ACCENT);
        }).left().flex(1).tip("The item their orders paid the most for").drop(2);
        owners.col("Locked in orders", 108, (ui, row, x, y, w, h) -> {
            Table.right(ui, row.locked == 0 ? "-" : Fmt.money(row.locked), x, y - 2, w, h, row.locked == 0
                    ? Theme.FAINT : Theme.TEXT);
            // Money in orders that are getting filled, then money in orders nobody delivers to.
            int bar = w - 8;
            int active = (int) Math.round(bar * row.lockedActive / mostLocked);
            int idle = (int) Math.round(bar * row.lockedIdle / mostLocked);
            if (row.lockedActive > 0) active = Math.max(1, active);
            if (row.lockedIdle > 0) idle = Math.max(1, idle);
            ui.fill(x + 4, y + h - 4, bar, 2, Theme.BORDER);
            ui.fill(x + 4, y + h - 4, Math.min(active, bar), 2, ACTIVE);
            ui.fill(x + 4 + Math.min(active, bar), y + h - 4, Math.min(idle, bar - Math.min(active, bar)), 2, IDLE);
        }).sort("locked").tip("Money in their open orders now");
        owners.col("Active", 54, (ui, row, x, y, w, h) -> Table.right(ui, row.lockedActive == 0 ? "-"
                : Fmt.money(row.lockedActive), x, y, w, h, row.lockedActive == 0 ? Theme.FAINT : ACTIVE))
                .sort("lockedActive").tip("Locked in orders that received a delivery in the window");
        owners.col("Idle", 54, (ui, row, x, y, w, h) -> Table.right(ui, row.lockedIdle == 0 ? "-"
                : Fmt.money(row.lockedIdle), x, y, w, h, row.lockedIdle == 0 ? Theme.FAINT : IDLE))
                .sort("lockedIdle").tip("Locked in orders nobody delivered to in the window");
        owners.col("Open", 36, (ui, row, x, y, w, h) -> Table.right(ui, Fmt.count(row.openOrders), x, y, w, h,
                Theme.MUTED)).sort("openOrders").tip("Open orders").drop(1);
    }

    private static List<String> ownerTip(Models.Owner row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.name);
        lines.add("Paid out in the window\t" + Fmt.moneyExact(row.paid));
        lines.add("Items delivered\t" + Fmt.countExact(row.units));
        lines.add("Deliveries\t" + Fmt.countExact(row.fills) + " into " + Fmt.countExact(row.ordersFilled)
                + (row.ordersFilled == 1 ? " order" : " orders"));
        if (row.topItem != null) {
            lines.add("Most paid for\t" + Models.compact(row.topItem.name) + ", " + Fmt.moneyExact(row.topItem.paid));
        }
        lines.add("Locked in orders now\t" + Fmt.moneyExact(row.locked));
        lines.add("In orders getting filled\t" + Fmt.moneyExact(row.lockedActive));
        lines.add("In orders nobody delivers to\t" + Fmt.moneyExact(row.lockedIdle));
        lines.add("Open orders\t" + Fmt.countExact(row.openOrders));
        return lines;
    }

    public Table<Models.Owner> owners() {
        return owners;
    }

    /** The owners tab: who actually pays out, and who only has money parked. */
    private void owners(Ui ui, int x, int y, int w, int h, int tx) {
        List<Models.Owner> rows = ownerPager.rows("/v1/orders/owners?window=" + Parts.window() + "&sort=" + ownerSort
                + "&order=" + (ownerDescending ? "desc" : "asc"), owners.nearEnd);
        if (ownerPager.restarted) owners.scroll = 0;
        int right = Parts.windowSelector(ui, x + w, y);
        if (ownerPager.first.data != null) {
            tx += ui.text(Fmt.count(ownerPager.total) + " owners", tx, y + 5, Theme.FAINT) + 8;
        }
        if (ownerPager.loading) ui.spinner(tx, y + 5);
        // What the two tones of the bar mean.
        String idle = "Nobody delivers", active = "Getting filled";
        if (tx + ui.width(idle) + ui.width(active) + 60 < right) {
            int lx = right - 6 - ui.width(idle);
            ui.text(idle, lx, y + 5, Theme.MUTED);
            ui.fill(lx - 8, y + 6, 5, 5, IDLE);
            lx -= 18 + ui.width(active);
            ui.text(active, lx, y + 5, Theme.MUTED);
            ui.fill(lx - 8, y + 6, 5, 5, ACTIVE);
        }
        int top = y + Parts.TOP;
        if (ownerPager.first.data == null && rows.isEmpty()) {
            if (!ui.ready(ownerPager.first, x, top, w, y + h - top)) return;
        }
        mostLocked = 1;
        for (Models.Owner row : rows) mostLocked = Math.max(mostLocked, row.locked);
        owners.render(ui, x, top, w, y + h - top, rows, ownerSort, ownerDescending, key -> {
            if (key.equals(ownerSort)) ownerDescending = !ownerDescending;
            else {
                ownerSort = key;
                ownerDescending = true;
            }
        }, row -> ui.nav.player(row.name), OrdersView::ownerTip);
    }

    private static boolean arbitrage(OrderRow row) {
        return row.arbitrage != null && row.arbitrage > 0;
    }

    private static boolean aboveAuction(OrderRow row) {
        return row.vsAh != null && row.vsAh >= 1 || row.vsAh == null && row.vsWorth != null && row.vsWorth >= 2;
    }

    /** The exact figures behind an order, and why it stands out. */
    static List<String> tip(OrderRow row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.label() + (row.owner == null ? "" : " wanted by " + row.owner));
        if (row.enchants != null && !row.enchants.isEmpty()) lines.add("Enchants\t" + row.enchants);
        if (row.potion != null && !row.potion.isEmpty()) lines.add("Potion\t" + row.potion);
        lines.add("Pays each\t" + Fmt.moneyExact(row.price));
        if (row.worth != null) lines.add("Worth (/sell)\t" + Fmt.moneyExact(row.worth));
        if (row.ahVwap != null) lines.add("Auction VWAP 24h\t" + Fmt.moneyExact(row.ahVwap));
        if (row.bestAsk != null) lines.add("Cheapest listing\t" + Fmt.moneyExact(row.bestAsk));
        if (row.remaining != null && row.qty != null) {
            lines.add("Left\t" + Fmt.countExact(row.remaining) + " of " + Fmt.countExact(row.qty));
        }
        if (row.delivered != null && row.qty != null) {
            lines.add("Delivered\t" + Fmt.countExact(row.delivered) + " of " + Fmt.countExact(row.qty));
        }
        if (row.paid != null) lines.add("Paid so far\t" + Fmt.moneyExact(row.paid));
        if (row.value != null) lines.add("Locked\t" + Fmt.moneyExact(row.value));
        if (row.pool != null) lines.add("Above /sell\t" + Fmt.moneyExact(row.pool));
        if (arbitrage(row)) lines.add("Arbitrage each\t" + Fmt.moneyExact(row.arbitrage));
        if (row.expiresAt != null) lines.add("Expires\t" + Fmt.dayClock(row.expiresAt));
        return lines;
    }

    public Table<OrderRow> table() {
        return table;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        // Orders one by one, or their owners ranked by what they paid out and what they have locked up.
        int tabsX = x + 1 + ui.width("Buy orders") + 10;
        ui.text("Buy orders", x + 1, y + 5, Theme.TEXT);
        int after = tabsX + ui.segments(tabsX, y + 2, TABS, showOwners ? 1 : 0, picked -> showOwners = picked == 1) + 10;
        if (showOwners) {
            owners(ui, x, y, w, h, after);
            return;
        }
        String note = null;
        List<OrderRow> rows = pager.rows("/v1/orders?sort=" + sort + "&order=" + (descending ? "desc" : "asc"),
                table.nearEnd);
        if (pager.restarted) table.scroll = 0;
        if (pager.first.data != null) {
            note = Fmt.count(pager.total) + " open";
            if (pager.first.data.sweptAt != null) {
                note += ", checked " + Fmt.ago(pager.first.data.sweptAt, System.currentTimeMillis() / 1000) + " ago";
            }
        }
        int tx = after + (note == null ? 0 : ui.text(note, after, y + 5, Theme.FAINT) + 8);
        if (pager.loading) ui.spinner(tx, y + 5);
        // What the bars down the left of a row mean.
        int lx = x + w - 1;
        String blue = "Cheaper listing exists", green = "Pays above auction";
        if (tx + ui.width(blue) + ui.width(green) + 60 < lx) {
            lx -= ui.width(blue);
            ui.text(blue, lx, y + 5, Theme.MUTED);
            ui.fill(lx - 6, y + 4, 2, 9, Theme.ACCENT);
            lx -= 16 + ui.width(green);
            ui.text(green, lx, y + 5, Theme.MUTED);
            ui.fill(lx - 6, y + 4, 2, 9, Theme.UP);
        }
        int top = y + Parts.TOP;
        if (pager.first.data == null && rows.isEmpty()) {
            if (!ui.ready(pager.first, x, top, w, y + h - top)) return;
        }
        table.render(ui, x, top, w, y + h - top, rows, sort, descending, key -> {
            if (key.equals(sort)) descending = !descending;
            else {
                sort = key;
                descending = true;
            }
        }, row -> ui.nav.item(row.target()), OrdersView::tip);
    }
}

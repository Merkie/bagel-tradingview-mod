package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.data.Models.PlayerRow;
import com.merkie.bageltv.ui.Icon;
import com.merkie.bageltv.ui.Table;
import com.merkie.bageltv.ui.TextField;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** The rich list: balances, money locked in buy orders, and the two together. */
public final class PlayersView extends View {
    private final Table<PlayerRow> table = new Table<>(13);
    private final Pager<Models.Players, PlayerRow> pager = new Pager<>(Models.Players.class, list -> list.players,
            list -> list.total);
    private final TextField lookup = new TextField("Look up any player", 17);
    private String sort = "trueBalance";
    private boolean descending = true;
    private double richest = 1;
    private Ui.Nav nav;

    public PlayersView() {
        lookup.submit = () -> {
            String name = lookup.text.trim();
            if (name.length() >= 3 && nav != null) {
                lookup.text = "";
                nav.player(name);
            }
        };
        table.empty = "No players yet";
        table.col("#", 30, (ui, row, x, y, w, h) -> Table.right(ui, Integer.toString(row.rank), x, y, w, h,
                row.rank <= 3 ? Theme.WARN : Theme.FAINT)).tip("Place by balance");
        table.col("Player", 110, (ui, row, x, y, w, h) -> {
            ui.head(row.name, x + 3, y + 2, 8);
            ui.text(ui.fit(row.name, w - 20), x + 15, Ui.textY(y, h), Theme.TEXT);
        }).left().flex(1);
        table.col("Balance", 70, (ui, row, x, y, w, h) -> Table.right(ui, approx(row) + Fmt.money(row.balance), x, y,
                w, h, Theme.TEXT)).sort("balance").tip("A ~ marks a balance rounded down by the in-game leaderboard");
        table.col("In orders", 70, (ui, row, x, y, w, h) -> Table.right(ui, row.escrow == null || row.escrow == 0
                ? "-" : Fmt.money(row.escrow), x, y, w, h, row.escrow == null || row.escrow == 0 ? Theme.FAINT
                : Theme.WARN)).sort("escrow").tip("Escrow: money locked in open buy orders");
        table.col("True balance", 88, (ui, row, x, y, w, h) -> Table.right(ui, approx(row) + Fmt.money(row.trueBalance), x,
                y, w, h, Theme.TEXT)).sort("trueBalance").tip("Balance plus the money locked in buy orders");
        table.col("", 120, (ui, row, x, y, w, h) -> {
            // Balance and escrow as one bar, against the richest player's true balance.
            int bar = w - 12;
            double balance = row.balance == null ? 0 : row.balance, escrow = row.escrow == null ? 0 : row.escrow;
            int a = (int) Math.round(bar * balance / richest), b = (int) Math.round(bar * escrow / richest);
            ui.fill(x + 6, y + 4, bar, h - 8, Theme.GRID);
            ui.fill(x + 6, y + 4, a, h - 8, Theme.mix(Theme.BG, Theme.ACCENT, 0.75F));
            ui.fill(x + 6 + a, y + 4, Math.min(b, bar - a), h - 8, Theme.mix(Theme.BG, Theme.WARN, 0.8F));
        }).drop(2);
        table.col("24h", 56, (ui, row, x, y, w, h) -> Table.right(ui, row.change == null || row.change == 0 ? "-"
                : Fmt.pct(row.change), x, y, w, h, Theme.change(row.change)))
                .sort("change").tip("Change in balance over 24 hours");
        table.col("Orders", 46, (ui, row, x, y, w, h) -> Table.right(ui, row.openOrders == null || row.openOrders == 0
                ? "-" : Fmt.count(row.openOrders), x, y, w, h, Theme.MUTED)).tip("Open buy orders").drop(1);
    }

    /** A mark for a balance the in-game leaderboard rounded down. */
    private static String approx(PlayerRow row) {
        return row.exact ? "" : "~";
    }

    private static List<String> tip(PlayerRow row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.name);
        lines.add("Balance\t" + (row.exact ? Fmt.moneyExact(row.balance) : "~" + Fmt.money(row.balance)));
        lines.add("In orders\t" + Fmt.moneyExact(row.escrow));
        lines.add("True balance\t" + (row.exact ? Fmt.moneyExact(row.trueBalance) : "~"
                + Fmt.money(row.trueBalance)));
        if (row.change != null) lines.add("24h\t" + Fmt.pct(row.change));
        if (!row.exact) lines.add("Rounded down by the in-game leaderboard");
        return lines;
    }

    public Table<PlayerRow> table() {
        return table;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        nav = ui.nav;
        List<PlayerRow> rows = pager.rows("/v1/players?sort=" + sort + "&order=" + (descending ? "desc" : "asc"),
                table.nearEnd);
        if (pager.restarted) table.scroll = 0;
        String note = null;
        if (pager.first.data != null) {
            note = "baltop".equals(pager.first.data.source) ? "Top " + Fmt.count(pager.total) + " by /baltop"
                    : "Top " + Fmt.count(pager.total) + " from the leaderboard";
        }
        int tx = Parts.title(ui, "Players", note, x, y);
        if (pager.loading) ui.spinner(tx, y + 5);
        int fieldW = Math.min(150, x + w - tx - 20);
        if (fieldW >= 80) ui.field(lookup, x + w - fieldW, y + 1, fieldW, Icon.SEARCH);
        int top = y + Parts.TOP;
        if (pager.first.data == null && rows.isEmpty()) {
            if (!ui.ready(pager.first, x, top, w, y + h - top)) return;
        }
        richest = 1;
        for (PlayerRow row : rows) if (row.trueBalance != null) richest = Math.max(richest, row.trueBalance);
        table.render(ui, x, top, w, y + h - top, rows, sort, descending, key -> {
            if (key.equals(sort)) descending = !descending;
            else {
                sort = key;
                descending = true;
            }
        }, row -> ui.nav.player(row.name), PlayersView::tip);
    }
}

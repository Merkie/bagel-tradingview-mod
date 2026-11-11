package com.merkie.bageltv.view;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.data.Models;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

/** The server's money: how much exists, how many players are on, and how much is locked in buy orders. */
public final class EconomyView extends View {
    private final Board supply = new Board.Economy("supply");
    private final Board online = new Board.Economy("online");
    private final Board escrow = new Board.Economy("escrow");

    @Override
    public boolean key(int key) {
        return supply.chart.key(key) || online.chart.key(key) || escrow.chart.key(key);
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Api.Query<Models.EconomyHistory> query = Api.get("/v1/economy?window=" + Parts.window(),
                Models.EconomyHistory.class);
        Parts.title(ui, "Economy", null, x, y);
        Parts.windowSelector(ui, x + w, y, false);
        int top = y + Parts.TOP;
        if (!ui.ready(query, x, top, w, y + h - top)) return;
        Models.Economy now = query.data.now;
        if (now != null) {
            int count = w >= 520 ? 5 : 3;
            for (int i = 0; i < count; i++) {
                int tx = x + i * (w + Parts.GAP) / count;
                int tw = x + (i + 1) * (w + Parts.GAP) / count - Parts.GAP - tx;
                switch (i) {
                    case 0 -> Parts.tile(ui, tx, top, tw, "Money supply", Fmt.money(now.totalMoney),
                            Fmt.pct(now.totalMoneyChange), Theme.change(now.totalMoneyChange),
                            "All the money players hold", "Total\t" + Fmt.moneyExact(now.totalMoney));
                    case 1 -> Parts.tile(ui, tx, top, tw, "Players online", Fmt.count(now.online), null, 0);
                    case 2 -> Parts.tile(ui, tx, top, tw, "In buy orders", Fmt.money(now.orderEscrow),
                            now.totalMoney == null || now.orderEscrow == null || now.totalMoney == 0 ? null
                                    : Fmt.share(now.orderEscrow / now.totalMoney), Theme.MUTED,
                            "Money locked in open buy orders", "Locked\t" + Fmt.moneyExact(now.orderEscrow),
                            "Share of all money\t" + (now.totalMoney == null || now.orderEscrow == null ? "-"
                                    : Fmt.share(now.orderEscrow / now.totalMoney)));
                    case 3 -> Parts.tile(ui, tx, top, tw, "Open buy orders", Fmt.count(now.openOrders), null, 0);
                    default -> Parts.tile(ui, tx, top, tw, "Players, all time", Fmt.count(now.players), null, 0);
                }
            }
        }
        int bodyTop = top + 27 + Parts.GAP;
        int bodyH = y + h - bodyTop;
        int upper = (bodyH - Parts.GAP) * 55 / 100;
        supply.render(ui, x, bodyTop, w, upper);
        int lowerTop = bodyTop + upper + Parts.GAP;
        int half = (w - Parts.GAP) / 2;
        online.render(ui, x, lowerTop, half, bodyH - upper - Parts.GAP);
        escrow.render(ui, x + half + Parts.GAP, lowerTop, w - half - Parts.GAP, bodyH - upper - Parts.GAP);
    }
}

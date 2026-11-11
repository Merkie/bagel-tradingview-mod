package com.merkie.bageltv;

import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.ui.Heads;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.merkie.bageltv.view.MarketView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import static com.merkie.bageltv.Harness.require;

/**
 * The dashboard: two chart slots whose pickers show any board and are remembered; the top items' metric and mode
 * dropdowns; hiding a series from the legend; opening an item or a player by clicking its line; the players
 * chart's heads; and the window selector.
 */
public final class MarketGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "MarketGameTest", harness -> {
            harness.open();
            MarketView view = harness.view(MarketView.class);
            Chart index = context.computeOnClient(client -> view.board(0).chart);
            int[] plot = context.computeOnClient(client -> index.plot());
            harness.move(plot[0] + plot[2] * 2 / 3, plot[1] + plot[3] / 2);
            harness.shot("market-crosshair");
            require(asked("/v1/market/overview?window=24h"), "The dashboard asks for the 24h overview");

            // The right slot starts on the top items: each line its item's color, hovering picks one out.
            Chart leaders = context.computeOnClient(client -> view.board(1).chart);
            int[] box = context.computeOnClient(client -> leaders.plot());
            require(context.computeOnClient(client -> leaders.lines.size()) >= 8 && leaders.lines.stream()
                    .noneMatch(line -> line.icon.contains("[")), "The leaders are whole items; variants, whose totals "
                    + "are only a floor, are left out: " + leaders.lines.size());
            int[] colors = context.computeOnClient(client -> leaders.lines.stream()
                    .mapToInt(line -> line.color).distinct().toArray());
            require(colors.length >= 5, "Leader lines take their items' colors: " + colors.length + " distinct");
            int[] at = hoverLine(harness, context, leaders, box);
            harness.shot("market-leaders-hover");
            String item = context.computeOnClient(client -> leaders.lines.get(leaders.hoveredLine).icon);
            harness.click(at[0], at[1]);
            harness.settle();
            require(TerminalScreen.route().equals(Route.item(item)), "Clicking a line opens its item: "
                    + TerminalScreen.route());
            harness.go(Route.of(Route.Kind.MARKET));

            // Metric and mode are dropdowns in the chart's header.
            harness.click(harness.mark("menu:by turnover"));
            harness.move(harness.mark("option:by volume"));
            harness.shot("market-dropdown");
            harness.click(harness.mark("option:by volume"));
            harness.settle();
            require(BagelTV.config().leadersMetric.equals("volume") && asked("/v1/market/leaders?metric=volume"),
                    "The metric dropdown asks for leaders by volume");
            harness.pick("per bucket", "by rank");
            require(BagelTV.config().leadersMode.equals("rank") && leaders.invert, "The mode dropdown picks ranks");
            harness.park();
            harness.shot("market-leaders-rank");
            harness.pick("by rank", "as shares");
            require(context.computeOnClient(client -> leaders.stack != null), "Shares stack the series");
            harness.move(box[0] + box[2] / 2, box[1] + box[3] / 2);
            harness.shot("market-leaders-share");
            harness.pick("as shares", "per bucket");
            harness.pick("by volume", "by turnover");

            // The legend under the chart hides and shows a series.
            harness.click(harness.mark("legend:first"));
            harness.settle();
            require(!context.computeOnClient(client -> leaders.lines.getFirst().visible),
                    "Clicking a legend entry hides its line");
            harness.click(harness.mark("legend:first"));
            harness.settle();
            require(context.computeOnClient(client -> leaders.lines.getFirst().visible),
                    "Clicking it again shows the line");

            // Each slot's picker shows any board, and the choice is saved.
            harness.pick("Top items", "Top players");
            harness.settle();
            require(BagelTV.config().marketRight.equals("players") && Config.load().marketRight.equals("players"),
                    "The picker changes the slot and saves it");
            require(asked("/v1/players/history?top=8&window=30d"), "Top players asks for balance histories");
            Chart players = context.computeOnClient(client -> view.board(1).chart);
            require(context.computeOnClient(client -> players.lines.size()) == 8, "Eight players' lines");
            context.waitFor(client -> Heads.loaded() >= 8, 200);
            harness.pick("Market index", "Money supply");
            require(BagelTV.config().marketLeft.equals("supply"), "The left slot has its own picker");
            harness.park();
            harness.shot("market-players-supply");
            int[] playersBox = context.computeOnClient(client -> players.plot());
            // A head sits at the end of its own line, stays there when the chart is dragged, and stands for the line.
            int[][] before = context.computeOnClient(client -> icons(players));
            for (int i = 0; i < 8; i++) require(before[i] != null, "Every player's line ends in a head: " + i);
            harness.move(before[0][0] + 4, before[0][1] + 4);
            context.waitTick();
            int picked = context.computeOnClient(client -> players.hoveredLine);
            require(picked >= 0 && before[picked][1] == before[0][1], "Hovering a head picks its line: " + picked
                    + " " + java.util.Arrays.deepToString(before));
            harness.shot("market-players-head-hover");
            int midX = playersBox[0] + playersBox[2] / 3, midY = playersBox[1] + playersBox[3] / 2;
            int axisX = playersBox[0] + playersBox[2] + 10;
            harness.drag(InputConstants.MOUSE_BUTTON_LEFT, axisX, midY, axisX, midY + 20);
            harness.drag(InputConstants.MOUSE_BUTTON_LEFT, midX, midY, midX, midY + 30);
            harness.settle();
            int[][] after = context.computeOnClient(client -> icons(players));
            int top = playersBox[1], bottom = playersBox[1] + playersBox[3] - 8;
            for (int i = 0; i < 8; i++) {
                if (after[i] == null) continue;
                require(after[i][1] != before[i][1] || after[i][1] <= top || after[i][1] >= bottom,
                        "Head " + i + " moves with its line: " + before[i][1] + " -> " + after[i][1]);
            }
            harness.park();
            harness.shot("market-players-dragged");
            harness.doubleClick(midX, midY);
            harness.settle();
            int[] over = hoverLine(harness, context, players, playersBox);
            String name = context.computeOnClient(client -> players.lines.get(players.hoveredLine).head);
            harness.click(over[0], over[1]);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player(name)), "Clicking a player's line opens the player: "
                    + TerminalScreen.route());
            harness.go(Route.of(Route.Kind.MARKET));
            harness.pick("Money supply", "Players online");
            harness.pick("Top players", "Money in buy orders");
            harness.park();
            harness.shot("market-online-escrow");
            harness.pick("Players online", "Market index");
            harness.pick("Money in buy orders", "Top items");

            // The window selector.
            harness.click(harness.mark("segment:7d"));
            harness.settle();
            require(BagelTV.config().window.equals("7d") && Config.load().window.equals("7d"),
                    "The window selector picks 7d and saves it: " + BagelTV.config().window);
            require(asked("/v1/market/overview?window=7d") && asked("/v1/market/candles?tf=1h"),
                    "The 7d window asks for its overview and hourly index candles");
            harness.park();
            harness.shot("market-7d");
        });
    }

    /** Moves down a chart's middle until a line is under the mouse. */
    private static int[] hoverLine(Harness harness, ClientGameTestContext context, Chart chart, int[] plot) {
        for (int x = plot[0] + plot[2] / 2; x < plot[0] + plot[2] - 20; x += 25) {
            for (int y = plot[1] + 16; y < plot[1] + plot[3] - 4; y += 3) {
                harness.move(x, y);
                context.waitTick();
                if (context.computeOnClient(client -> chart.hoveredLine) >= 0) return new int[] {x, y};
            }
        }
        throw new AssertionError("No line to hover");
    }

    private static int[][] icons(Chart chart) {
        int[][] icons = new int[chart.lines.size()][];
        for (int i = 0; i < icons.length; i++) icons[i] = chart.icon(i);
        return icons;
    }

    private static boolean asked(String part) {
        return FakeApi.requests().stream().anyMatch(request -> request.path().contains(part));
    }
}

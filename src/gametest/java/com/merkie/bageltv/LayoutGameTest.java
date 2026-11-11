package com.merkie.bageltv;

import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.ui.Heads;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.merkie.bageltv.view.ItemView;
import com.merkie.bageltv.view.PlayerView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

import static com.merkie.bageltv.Harness.require;

/**
 * Layout and identity: the Item and Player views' panel boundaries drag and are remembered; a thin history
 * fills its chart and picks a small candle size; a busy one picks a large size; variant items open by their key;
 * and players' heads load once.
 */
public final class LayoutGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "LayoutGameTest", harness -> {
            harness.open();
            harness.go(Route.item("diamond"));
            ItemView item = harness.view(ItemView.class);
            Chart chart = context.computeOnClient(client -> item.chart());
            require("4h".equals(item.tf()), "A week of busy five-minute candles starts on 4h candles: " + item.tf());
            require(FakeApi.requests().stream().anyMatch(r -> r.path().contains("/candles?limit=2000&fill=1&tf=4h")),
                    "Candles are asked for filled");
            require(chart.refs.size() == 1 && chart.refs.getFirst().label().equals("AH price"),
                    "The auction chart marks the cheapest listing, not worth or an order: " + chart.refs);
            require(chart.candles.empty != null, "Buckets with no trades come as empty candles");

            // The boundary between the chart and the tables under it drags up and down.
            int[] before = context.computeOnClient(client -> chart.bounds());
            int gapY = before[1] + before[3] + 2;
            harness.move(before[0] + 100, gapY);
            harness.shot("item-splitter-hover");
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, before[0] + 100, gapY, before[0] + 100, gapY - 60);
            int[] shorter = context.computeOnClient(client -> chart.bounds());
            require(shorter[3] == before[3] - 60, "Dragging the lower boundary up shortens the chart: "
                    + Arrays.toString(shorter));
            require(Config.load().itemLower > 0, "The lower panel's height is saved");
            // The boundary between the left column and the right column drags sideways.
            int gapX = shorter[0] + shorter[2] + 2;
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, gapX, shorter[1] + 50, gapX - 70, shorter[1] + 50);
            int[] narrower = context.computeOnClient(client -> chart.bounds());
            require(narrower[2] == shorter[2] - 70 && Config.load().itemSide > 0,
                    "Dragging the side boundary left widens the side column: " + Arrays.toString(narrower));
            harness.park();
            harness.shot("item-resized");
            // Sane minimums: the chart keeps some room however far the boundary is dragged.
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, gapX - 70, shorter[1] + 50, 20, shorter[1] + 50);
            require(context.computeOnClient(client -> chart.bounds())[2] >= 200, "The chart keeps a minimum width");
            context.runOnClient(client -> {
                BagelTV.config().itemSide = 0;
                BagelTV.config().itemLower = 0;
            });

            // An item whose money moves through buy orders opens on them.
            harness.go(Route.item("cobblestone"));
            harness.settle();
            ItemView bulk = harness.view(ItemView.class);
            require(bulk.market() == 1 && context.computeOnClient(client -> bulk.chart()).title
                    .startsWith("Filled orders"), "An item sold mostly into orders opens on orders");
            harness.park();
            harness.shot("item-orders-default");

            // A variant item opens by its key, brackets and all, and shows its own name and icon.
            harness.go(Route.item("enchanted_book[mending=1]"));
            require(FakeApi.requests().stream().anyMatch(r -> r.path().startsWith(
                    "/v1/items/enchanted_book%5Bmending%3D1%5D?")), "A variant's key is encoded in the path");
            harness.go(Route.item("potion[strong_strength]"));
            harness.park();
            harness.shot("item-variant");
            require(TerminalScreen.NAMES.get("potion[strong_strength]").equals("Strong Strength Potion"),
                    "A variant is named kind first: " + TerminalScreen.NAMES);
            require(!harness.view(ItemView.class).salesOnly(), "A busy variant still gets candles");

            // The player inspector resizes the same way, and heads load.
            harness.go(Route.player("Simpinhoe"));
            PlayerView player = harness.view(PlayerView.class);
            Chart balance = context.computeOnClient(client -> player.chart());
            int[] box = context.computeOnClient(client -> balance.bounds());
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, box[0] + 100, box[1] + box[3] + 2, box[0] + 100,
                    box[1] + box[3] - 38);
            require(context.computeOnClient(client -> balance.bounds())[3] == box[3] - 40
                    && Config.load().playerLower > 0, "The player's chart boundary drags too");
            context.waitFor(client -> Heads.loaded() >= 1, 200);
            harness.park();
            harness.shot("player-head");
            // What their buy orders paid out, delivery by delivery.
            harness.click(harness.mark("tab:Order payouts"));
            harness.park();
            harness.shot("player-payouts");
            harness.click(harness.mark("tab:Open buy orders"));
            harness.go(Route.of(Route.Kind.PLAYERS));
            context.waitFor(client -> Heads.loaded() >= 20, 300);
            long heads = FakeApi.requests().stream().filter(r -> r.path().startsWith("/api/skin/")).count();
            harness.go(Route.of(Route.Kind.MARKET));
            harness.go(Route.of(Route.Kind.PLAYERS));
            require(FakeApi.requests().stream().filter(r -> r.path().startsWith("/api/skin/")).count() == heads,
                    "A head is fetched once, not again on each visit");
            require(FakeApi.requests().stream().filter(r -> r.path().startsWith("/api/skin/"))
                    .anyMatch(r -> r.path().contains("%2E") || r.path().contains("/.")),
                    "A Bedrock name with a leading dot is asked for");
            harness.park();
            harness.shot("players-heads");

            // The live capture: a few hours of trades fill the chart on five-minute candles.
            FakeApi.mode = FakeApi.Mode.SPARSE;
            context.runOnClient(client -> com.merkie.bageltv.data.Api.clear());
            TerminalScreen.reset();
            harness.go(Route.of(Route.Kind.SCREENER));
            harness.go(Route.item("diamond"));
            ItemView thin = harness.view(ItemView.class);
            Chart sparse = context.computeOnClient(client -> thin.chart());
            require("5m".equals(thin.tf()), "A short history starts on 5m candles: " + thin.tf());
            double[] view = context.computeOnClient(client -> sparse.view());
            double[] data = context.computeOnClient(client -> sparse.data());
            require(view[0] >= data[0] - 300 && view[1] <= data[1] + 400 + (view[1] - view[0]) * 0.05,
                    "The time axis is fitted to the data: " + Arrays.toString(view) + " " + Arrays.toString(data));
            harness.park();
            harness.shot("sparse-item-fitted");

            // A book of a known kind, as captured: too few candles to draw candles, so every sale is a dot.
            harness.go(Route.item("enchanted_book[mending=1]"));
            ItemView book = harness.view(ItemView.class);
            Chart sales = context.computeOnClient(client -> book.chart());
            require(book.salesOnly() && sales.candles == null && sales.histogram == null
                    && sales.lines.size() == 1 && sales.lines.getFirst().dots && !sales.lines.getFirst().area,
                    "A thin item draws its sales as dots, not candles or a filled line");
            String kind = BagelTV.config().itemChart;
            harness.click(harness.mark("type:" + (kind.equals("line") ? "candles" : "line")));
            require(BagelTV.config().itemChart.equals(kind), "The chart type buttons are disabled meanwhile");
            harness.park();
            harness.shot("sparse-variant-item");
            int[] plot = context.computeOnClient(client -> sales.plot());
            harness.move(plot[0] + plot[2] / 2, plot[1] + plot[3] / 2);
            harness.shot("sparse-variant-hover");
            harness.go(Route.of(Route.Kind.SCREENER));
            harness.park();
            harness.shot("sparse-screener-variants");
            harness.go(Route.of(Route.Kind.MARKET));
            harness.park();
            harness.shot("sparse-market-honest");
        });
    }
}

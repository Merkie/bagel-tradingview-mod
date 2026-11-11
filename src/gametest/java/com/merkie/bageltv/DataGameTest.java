package com.merkie.bageltv;

import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.ui.Route;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.merkie.bageltv.Harness.require;

/**
 * The data layer: requests name the mod and say nothing about the player; the open view refreshes and a closed
 * terminal asks nothing; one path is never asked twice at once; a refresh that fails keeps the data on screen;
 * and loading, sparse history, a 503 and an unreachable server each have a deliberate look.
 */
public final class DataGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "DataGameTest", harness -> {
            harness.open();
            String player = context.computeOnClient(client -> client.getUser().getName());
            String uuid = context.computeOnClient(client -> client.getUser().getProfileId().toString());
            List<FakeApi.Request> first = FakeApi.requests();
            require(!first.isEmpty(), "Opening the terminal asks for data");
            for (FakeApi.Request request : first) {
                require(("BagelTradingView/" + BagelTV.VERSION).equals(request.userAgent()), "Requests carry the "
                        + "mod's User-Agent: " + request.userAgent());
                String all = (request.path() + request.headers()).toLowerCase();
                require(!all.contains(player.toLowerCase()) && !all.contains(uuid.toLowerCase())
                        && !all.contains(uuid.replace("-", "").toLowerCase()), "Nothing about the player is sent: "
                        + all);
            }
            require(BagelTV.VERSION.matches("\\d+\\.\\d+\\.\\d+\\+.*"), "The version comes from the mod's metadata: "
                    + BagelTV.VERSION);

            // While open, the view on screen asks again once its answers are two seconds old (25 in real use).
            FakeApi.clearRequests();
            context.waitTicks(70);
            long overviews = FakeApi.requests().stream().filter(r -> r.path().startsWith("/v1/market/overview")).count();
            require(overviews >= 1 && overviews <= 2, "The open view refreshes, once per interval: " + overviews);
            require(FakeApi.requests().stream().noneMatch(r -> r.path().startsWith("/v1/items/")
                    || r.path().startsWith("/v1/players")), "Only what is on screen is asked for");

            // One path is never in flight twice, however slow the answer.
            FakeApi.delay = 1500;
            context.runOnClient(client -> Api.clear());
            FakeApi.clearRequests();
            context.waitTicks(8);
            harness.park();
            harness.shot("market-loading");
            context.waitTicks(16);
            Map<String, Integer> counts = new HashMap<>();
            for (FakeApi.Request request : FakeApi.requests()) counts.merge(request.path(), 1, Integer::sum);
            require(!counts.isEmpty() && counts.values().stream().allMatch(count -> count == 1),
                    "No path is asked for twice at once: " + counts);
            FakeApi.delay = 0;
            harness.settle();

            // Closed, it asks nothing.
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            context.waitTicks(5);
            FakeApi.clearRequests();
            context.waitTicks(70);
            require(FakeApi.requests().isEmpty(), "A closed terminal asks nothing: " + FakeApi.requests());
            harness.open();

            // A failed refresh keeps what was on screen.
            FakeApi.mode = FakeApi.Mode.DOWN;
            context.waitTicks(70);
            harness.park();
            harness.shot("market-refresh-failed");
            FakeApi.mode = FakeApi.Mode.DENSE;

            // The API answering 503 from the start: a short error and Retry.
            FakeApi.mode = FakeApi.Mode.DOWN;
            context.runOnClient(client -> Api.clear());
            harness.settle();
            harness.park();
            harness.shot("market-down");
            FakeApi.mode = FakeApi.Mode.DENSE;
            int[] bounds = context.computeOnClient(client -> Harness.screen(client).bounds());
            int cx = bounds[0] + 72 + 2 + (bounds[2] - 74) / 2;
            int cy = bounds[1] + 17 + 3 + 17 + (bounds[3] - 17 - 9 - 17) / 2;
            harness.click(cx, cy + 11);
            harness.settle();
            require(context.computeOnClient(client -> Api.get("/v1/market/overview?window=24h",
                    com.merkie.bageltv.data.Models.Overview.class).data != null), "Retry asks again and gets data");

            // Nothing listening at all.
            System.setProperty("bageltv.api", "http://127.0.0.1:9");
            context.runOnClient(client -> Api.clear());
            harness.settle();
            harness.go(Route.item("diamond"));
            harness.park();
            harness.shot("item-unreachable");
            context.waitTicks(60);
            System.setProperty("bageltv.api", FakeApi.address);

            // The live API as captured: minutes of history, lists still filling.
            FakeApi.mode = FakeApi.Mode.SPARSE;
            context.runOnClient(client -> Api.clear());
            for (Object[] page : new Object[][] {{Route.of(Route.Kind.MARKET), "market"},
                    {Route.item("diamond"), "item"}, {Route.of(Route.Kind.SCREENER), "screener"},
                    {Route.of(Route.Kind.PLAYERS), "players"}, {Route.player("Simpinhoe"), "player"},
                    {Route.of(Route.Kind.ECONOMY), "economy"}, {Route.of(Route.Kind.ORDERS), "orders"}}) {
                harness.go((Route) page[0]);
                harness.park();
                harness.shot("sparse-" + page[1]);
            }
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(5);
            for (Object[] page : new Object[][] {{Route.of(Route.Kind.MARKET), "market"},
                    {Route.item("diamond"), "item"}, {Route.player("Simpinhoe"), "player"}}) {
                harness.go((Route) page[0]);
                harness.park();
                harness.shot("sparse-" + page[1] + "-1080");
            }
            // A small terminal: 640 x 360 terminal pixels.
            FakeApi.mode = FakeApi.Mode.DENSE;
            context.runOnClient(client -> {
                Api.clear();
                BagelTV.config().scale = 3;
            });
            for (Object[] page : new Object[][] {{Route.of(Route.Kind.MARKET), "market"},
                    {Route.item("diamond"), "item"}, {Route.of(Route.Kind.SCREENER), "screener"},
                    {Route.player("Simpinhoe"), "player"}}) {
                harness.go((Route) page[0]);
                harness.park();
                harness.shot("small-" + page[1]);
            }
            context.runOnClient(client -> BagelTV.config().scale = 0);
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(5);
        });
    }
}

package com.merkie.bageltv;

import com.merkie.bageltv.ui.Route;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Every view with the busy market, at the default window and at 1920 x 1080. */
public final class TerminalGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "TerminalGameTest", harness -> {
            harness.open();
            for (int big = 0; big < 2; big++) {
                String suffix = big == 0 ? "" : "-1080";
                if (big == 1) {
                    context.getInput().resizeWindow(1920, 1080);
                    context.waitTicks(5);
                }
                shot(harness, Route.of(Route.Kind.MARKET), "market" + suffix);
                shot(harness, Route.of(Route.Kind.SCREENER), "screener" + suffix);
                shot(harness, Route.item("diamond"), "item" + suffix);
                shot(harness, Route.of(Route.Kind.ORDERS), "orders" + suffix);
                shot(harness, Route.of(Route.Kind.PLAYERS), "players" + suffix);
                shot(harness, Route.player("Simpinhoe"), "player" + suffix);
                shot(harness, Route.of(Route.Kind.ECONOMY), "economy" + suffix);
                shot(harness, Route.of(Route.Kind.BILLFORD), "billford" + suffix);
            }
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(5);
        });
    }

    private static void shot(Harness harness, Route route, String name) {
        harness.go(route);
        harness.park();
        harness.shot(name);
    }
}

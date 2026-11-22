package com.merkie.bageltv;

import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import com.mojang.blaze3d.platform.InputConstants;

import static com.merkie.bageltv.Harness.require;

/**
 * Global search finds items and players, the best match first; Enter opens the first, back, forward and Recent
 * retrace the way, and looking up a name nobody has says so.
 */
public final class SearchGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "SearchGameTest", harness -> {
            harness.open();
            int[] search = context.computeOnClient(client -> Harness.screen(client).control("search"));
            harness.click(search);
            harness.type("di");
            harness.settle();
            harness.move(search[0] + 20, search[1] + 34);
            harness.shot("search-items");
            require(FakeApi.requests().stream().anyMatch(r -> r.path().contains("/v1/items?window=30d")
                    && r.path().contains("q=di")) && FakeApi.requests().stream()
                    .anyMatch(r -> r.path().equals("/v1/players/search?q=di")), "Search asks for items and players");
            harness.key(InputConstants.KEY_RETURN);
            harness.settle();
            Route first = TerminalScreen.route();
            require(first.kind() == Route.Kind.ITEM && first.arg().contains("di"), "Enter opens the first result: "
                    + first);

            // The slash key goes to the search box; a player's name finds the player.
            harness.key(InputConstants.KEY_SLASH);
            harness.type("/Sim");
            harness.settle();
            harness.shot("search-players");
            harness.click(search[0] + 20, search[1] + 18);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player("Simpinhoe")), "Clicking a result opens it: "
                    + TerminalScreen.route());

            // Back and forward.
            int[] bounds = context.computeOnClient(client -> Harness.screen(client).bounds());
            harness.click(bounds[0] + 8, bounds[1] + 8);
            harness.settle();
            require(TerminalScreen.route().equals(first), "The back arrow returns to the item");
            harness.click(bounds[0] + 23, bounds[1] + 8);
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.PLAYER, "The forward arrow returns to the player");
            context.getInput().holdAlt();
            harness.key(InputConstants.KEY_LEFT);
            context.getInput().releaseAlt();
            harness.settle();
            require(TerminalScreen.route().equals(first), "Alt+Left goes back too");

            // Recent lists both in the rail: the item on top now, the player under it.
            harness.click(bounds[0] + 30, bounds[1] + 17 + 3 + 6 * 15 + 4 + 6 + 11 + 13 + 6);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player("Simpinhoe")), "Recent reopens the player: "
                    + TerminalScreen.route());

            // It reopens where it was left.
            harness.key(InputConstants.KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            harness.open();
            require(TerminalScreen.route().equals(Route.player("Simpinhoe")), "Reopening returns to the same view");

            // The best match leads: a player whose name starts with what was typed comes before an item that only
            // contains it, and someone on the server with you before every other name.
            harness.go(Route.of(Route.Kind.MARKET));
            harness.key(InputConstants.KEY_SLASH);
            harness.type("/sim");
            harness.settle();
            harness.key(InputConstants.KEY_RETURN);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player("Simpinhoe")), "Enter opens the player a name starts: "
                    + TerminalScreen.route());
            String me = context.computeOnClient(client -> client.getUser().getName());
            harness.key(InputConstants.KEY_SLASH);
            harness.type("/" + me.substring(0, 3));
            harness.settle();
            harness.shot("search-online");
            harness.key(InputConstants.KEY_RETURN);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player(me)), "A player in the game's list leads the results: "
                    + TerminalScreen.route());

            // Looking up a name nobody has says so, whatever closest name Bagel answers with.
            harness.key(InputConstants.KEY_SLASH);
            harness.type("/Simp");
            harness.settle();
            harness.click(harness.mark("lookup"));
            harness.settle();
            require(TerminalScreen.route().equals(Route.player("Simp")), "Look up opens the name as typed");
            harness.mark("player:none");
            harness.shot("search-no-player");
        });
    }
}

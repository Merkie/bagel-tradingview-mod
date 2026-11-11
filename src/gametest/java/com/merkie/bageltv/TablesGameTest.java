package com.merkie.bageltv;

import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.merkie.bageltv.view.ItemView;
import com.merkie.bageltv.view.OrdersView;
import com.merkie.bageltv.view.PlayerView;
import com.merkie.bageltv.view.PlayersView;
import com.merkie.bageltv.view.ScreenerView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import static com.merkie.bageltv.Harness.require;

/**
 * The table views: the screener sorts on the server from its headers, filters by text and category and opens an
 * item from a row; an item's tape opens a player from a name; orders fetch their next page when scrolled to the
 * end; the rich list sorts and looks up any player; and an unknown item or player says so.
 */
public final class TablesGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "TablesGameTest", harness -> {
            harness.open();
            int[] rail = context.computeOnClient(client -> Harness.screen(client).rail(Route.Kind.SCREENER));
            harness.click(rail);
            harness.settle();
            ScreenerView screener = harness.view(ScreenerView.class);
            require(asked("/v1/items?window=24h&sort=turnover&order=desc"), "The screener starts by turnover");

            harness.click(context.computeOnClient(client -> screener.table().header("Change")));
            harness.settle();
            require(asked("sort=change&order=desc"), "A header click sorts on the server");
            harness.click(context.computeOnClient(client -> screener.table().header("Change")));
            harness.settle();
            require(asked("sort=change&order=asc"), "A second click reverses the order");
            harness.click(context.computeOnClient(client -> screener.table().header("Listed")));
            harness.settle();
            require(asked("sort=askListings&order=desc"), "Listed sorts by listings on the auction house");
            harness.click(context.computeOnClient(client -> screener.table().header("AH price")));
            harness.settle();
            require(asked("sort=bestAsk&order=desc"), "Ask sorts by the cheapest listing");
            harness.park();
            harness.shot("screener-sorted");

            // Category chips sit under the title: All, Blocks, Ingredients, Utilities, Combat...
            int[] chips = context.computeOnClient(client -> {
                int[] bounds = Harness.screen(client).bounds();
                int x = bounds[0] + 72 + 2 + 5 + 1;
                for (String label : new String[] {"All", "Blocks", "Ingredients", "Utilities"}) {
                    x += client.font.width(label) + 13;
                }
                return new int[] {x + 8, bounds[1] + 17 + 3 + 17 + 1 + 6};
            });
            harness.click(chips);
            harness.settle();
            require(asked("category=COMBAT"), "The Combat chip filters by category");

            // The filter box takes typing, the hotkey's letter included, without closing the terminal.
            int[] field = context.computeOnClient(client -> {
                int[] bounds = Harness.screen(client).bounds();
                return new int[] {bounds[0] + 72 + 7 + client.font.width("Screener") + 30, bounds[1] + 17 + 3 + 7};
            });
            harness.click(field);
            harness.type("b");
            harness.key(GLFW.GLFW_KEY_B);
            context.runOnClient(client -> require(client.screen instanceof TerminalScreen,
                    "The hotkey types into a text box instead of closing the terminal"));
            harness.key(GLFW.GLFW_KEY_BACKSPACE);
            harness.type("nether");
            harness.settle();
            require(asked("q=nether&category=COMBAT"), "Typing filters by item");
            harness.park();
            harness.shot("screener-filtered");
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.runOnClient(client -> require(client.screen instanceof TerminalScreen,
                    "Escape leaves the text box, not the terminal"));
            harness.click(context.computeOnClient(client -> screener.table().row(0)));
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.ITEM && TerminalScreen.route().arg().contains("nether"),
                    "A row opens its item: " + TerminalScreen.route());

            // An item's tape: names open players, and the listings tab shows what is for sale.
            ItemView item = harness.view(ItemView.class);
            int[] seller = context.computeOnClient(client -> item.tape().cell(0, "Seller > Buyer"));
            harness.move(seller[0] + 12, seller[1]);
            harness.shot("item-hover-name");
            harness.click(seller[0] + 12, seller[1]);
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.PLAYER, "A seller's name opens that player: "
                    + TerminalScreen.route());
            harness.context().getInput().pressMouse(3);
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.ITEM, "The mouse's back button goes back");
            harness.context().getInput().pressMouse(4);
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.PLAYER, "and its forward button goes forward");

            // An item the API has never seen trade.
            harness.go(Route.item("barrier"));
            harness.park();
            harness.shot("item-unknown");

            // Buy orders: sort, then scroll to the end for the next page.
            harness.go(Route.of(Route.Kind.ORDERS));
            OrdersView orders = harness.view(OrdersView.class);
            require(asked("/v1/orders?sort=pool&order=desc&limit=100&offset=0"), "Orders start by pool");
            harness.click(context.computeOnClient(client -> orders.table().header("Arbitrage")));
            harness.settle();
            require(asked("/v1/orders?sort=arbitrage&order=desc"), "The Arbitrage header sorts by arbitrage");
            harness.park();
            harness.shot("orders-arbitrage");
            harness.click(context.computeOnClient(client -> orders.table().header("Pool")));
            harness.settle();
            int[] middle = context.computeOnClient(client -> orders.table().row(4));
            for (int i = 0; i < 12; i++) harness.scroll(middle[0], middle[1], -20);
            harness.settle();
            require(asked("/v1/orders?sort=pool&order=desc&limit=100&offset=100"),
                    "Scrolling to the end fetches the next page");
            int[] buyer = context.computeOnClient(client -> orders.table().cell(
                    orders.table().scroll / orders.table().rowHeight + 3, "Buyer"));
            harness.click(buyer[0] + 8, buyer[1]);
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.PLAYER, "A buyer's name opens that player");

            // Owners: who actually paid out, and who only has money locked up, one click apart.
            harness.go(Route.of(Route.Kind.ORDERS));
            harness.click(harness.mark("segment:Owners"));
            harness.settle();
            require(asked("/v1/orders/owners?window=24h&sort=paid&order=desc&limit=100&offset=0"),
                    "Owners start ranked by what their orders paid out");
            harness.park();
            harness.shot("owners-paid");
            OrdersView ownersView = harness.view(OrdersView.class);
            harness.click(context.computeOnClient(client -> ownersView.owners().header("Locked in orders")));
            harness.settle();
            require(asked("/v1/orders/owners?window=24h&sort=locked&order=desc"), "Locked sorts by money in orders");
            int[] ownerRow = context.computeOnClient(client -> ownersView.owners().row(1));
            harness.move(ownerRow[0], ownerRow[1]);
            harness.shot("owners-locked");
            harness.click(context.computeOnClient(client -> ownersView.owners().header("Idle")));
            harness.settle();
            require(asked("sort=lockedIdle&order=desc"), "Idle sorts by money in orders nobody delivers to");
            harness.click(context.computeOnClient(client -> ownersView.owners().header("Active")));
            harness.settle();
            require(asked("sort=lockedActive&order=desc"), "Active sorts by money in orders getting filled");
            harness.click(harness.mark("segment:7d"));
            harness.settle();
            require(asked("/v1/orders/owners?window=7d"), "The window selector applies to owners");
            harness.click(harness.mark("segment:24h"));
            harness.settle();
            for (int i = 0; i < 12; i++) harness.scroll(ownerRow[0], ownerRow[1], -20);
            harness.settle();
            require(asked("/v1/orders/owners?window=24h&sort=lockedActive&order=desc&limit=100&offset=100"),
                    "Owners page as they scroll");
            harness.click(context.computeOnClient(client -> ownersView.owners().row(
                    ownersView.owners().scroll / ownersView.owners().rowHeight + 2)));
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.PLAYER, "An owner's row opens that player");
            harness.context().getInput().pressMouse(3);
            harness.settle();
            harness.click(harness.mark("segment:Orders"));

            // The rich list.
            harness.go(Route.of(Route.Kind.PLAYERS));
            PlayersView players = harness.view(PlayersView.class);
            harness.click(context.computeOnClient(client -> players.table().header("In orders")));
            harness.settle();
            require(asked("/v1/players?sort=escrow&order=desc"), "The rich list sorts by money in orders");
            harness.park();
            harness.shot("players-by-escrow");
            int[] lookup = context.computeOnClient(client -> {
                int[] bounds = Harness.screen(client).bounds();
                return new int[] {bounds[0] + bounds[2] - 60, bounds[1] + 17 + 3 + 7};
            });
            harness.click(lookup);
            harness.type("knockoutjs");
            harness.key(GLFW.GLFW_KEY_ENTER);
            harness.settle();
            require(TerminalScreen.route().equals(Route.player("knockoutjs")), "Enter looks a player up: "
                    + TerminalScreen.route());
            PlayerView player = harness.view(PlayerView.class);
            require(context.computeOnClient(client -> player.chart().lines.size()) == 2,
                    "The inspector plots balance and true balance");
            // The 7D button in the chart's header.
            harness.click(harness.mark("segment:7D"));
            context.waitTicks(2);
            double[] week = context.computeOnClient(client -> player.chart().view());
            require(Math.abs((week[1] - week[0]) / 86400 - 7) < 0.2, "7D shows the last week: "
                    + (week[1] - week[0]) / 86400);
            harness.park();
            harness.shot("player-7d");

            harness.go(Route.player("nobody_here"));
            harness.park();
            harness.shot("player-unknown");
        });
    }

    private static boolean asked(String part) {
        return FakeApi.requests().stream().anyMatch(request -> request.path().contains(part));
    }
}

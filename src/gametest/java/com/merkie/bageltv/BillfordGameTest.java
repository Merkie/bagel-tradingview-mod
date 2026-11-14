package com.merkie.bageltv;

import com.merkie.bageltv.data.Models.BillfordTrade;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.merkie.bageltv.view.BillfordView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.List;

import static com.merkie.bageltv.Harness.require;

/**
 * Billford's view: the rail opens it, the trade he offers now sits above his past trades, which run newest first,
 * a row opens the item he gives, and a market with one recorded trade shows just that one.
 */
public final class BillfordGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "BillfordGameTest", harness -> {
            harness.open();
            harness.click(context.computeOnClient(client -> Harness.screen(client).rail(Route.Kind.BILLFORD)));
            harness.settle();
            require(TerminalScreen.route().kind() == Route.Kind.BILLFORD, "The rail opens Billford");
            require(FakeApi.requests().stream().anyMatch(request -> request.path().equals("/v1/billford")),
                    "The view asks for his trades");
            BillfordView view = harness.view(BillfordView.class);
            require(harness.mark("billford:verdict") != null, "The trade he offers now has a verdict");
            harness.move(harness.mark("billford:verdict")[0] - 200, harness.mark("billford:verdict")[1]);
            harness.shot("billford-rating-hover");
            int[] second = context.computeOnClient(client -> view.table().row(1));
            harness.move(second[0], second[1]);
            harness.shot("billford-row-hover");
            harness.click(second);
            harness.settle();
            require(TerminalScreen.route().equals(Route.item("diamond")), "A row opens the item he gives: "
                    + TerminalScreen.route());

            FakeApi.mode = FakeApi.Mode.SPARSE;
            harness.context().runOnClient(client -> com.merkie.bageltv.data.Api.clear());
            harness.go(Route.of(Route.Kind.BILLFORD));
            harness.park();
            harness.shot("sparse-billford");
            FakeApi.mode = FakeApi.Mode.DENSE;
        });
    }
}

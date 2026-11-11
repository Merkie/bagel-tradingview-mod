package com.merkie.bageltv;

import com.merkie.bageltv.ui.Intro;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.merkie.bageltv.view.MarketView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import static com.merkie.bageltv.Harness.require;

/**
 * The first open after a login: the terminal starts from its blank default state behind the startup animation,
 * which plays once, can be skipped, and ends on the Market view. Time is held still for the pictures.
 */
public final class IntroGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "IntroGameTest", harness -> {
            // Leave something behind, as an earlier session would.
            harness.open();
            harness.go(Route.item("diamond"));
            require(TerminalScreen.recent() == 1, "An opened item is remembered");
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);

            Intro.enabled = true;
            Intro.frozen = 0.0;
            context.runOnClient(client -> TerminalScreen.newSession());
            harness.open();
            require(TerminalScreen.route().kind() == Route.Kind.MARKET && TerminalScreen.recent() == 0,
                    "A new login starts on the Market view with nothing remembered");
            require(phase(context) != null, "and the animation plays");

            // Every frame of it at 60 a second, at the size it is played at, for the preview video.
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(4);
            harness.settle();
            for (int frame = 0; frame < 560; frame++) {
                Intro.frozen = frame * 1000.0 / 60;
                context.waitTick();
                if (phase(context) == null) break;
                context.takeScreenshot(String.format("intro-frame-%03d", frame));
            }
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(4);
            Intro.frozen = 100000.0;
            context.waitTicks(3);
            require(phase(context) == null, "The animation ends by itself");
            harness.view(MarketView.class);
            harness.park();
            harness.shot("intro-done");

            // A click skips it.
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            Intro.frozen = 300.0;
            context.runOnClient(client -> TerminalScreen.newSession());
            harness.open();
            require("candles".equals(phase(context)), "It plays again on the next login: " + phase(context));
            harness.click(context.computeOnClient(client -> Harness.screen(client).bounds()[0] + 200), 200);
            context.waitTicks(2);
            require(phase(context) == null, "A click skips it");
            harness.view(MarketView.class);

            // And so does a key; the hotkey still closes.
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            context.runOnClient(client -> TerminalScreen.newSession());
            harness.open();
            harness.key(GLFW.GLFW_KEY_SPACE);
            context.waitTicks(2);
            require(phase(context) == null, "A key skips it");
            context.runOnClient(client -> TerminalScreen.newSession());
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            harness.open();
            require(phase(context) != null, "It plays on a new login");
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);

            // The same session again: no animation, and the place is kept.
            Intro.frozen = null;
            harness.open();
            require(phase(context) == null, "A second open in the same session does not replay it");
            harness.go(Route.item("diamond"));
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.gui.screen() == null);
            harness.open();
            require(TerminalScreen.route().equals(Route.item("diamond")), "and reopens where it was");
            Intro.enabled = false;
        });
    }

    private static String phase(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            Intro intro = Harness.screen(client).intro();
            return intro == null ? null : intro.phase();
        });
    }
}

package com.merkie.bageltv;

import com.merkie.bageltv.ui.HotkeyScreen;
import com.merkie.bageltv.ui.TerminalScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import static com.merkie.bageltv.Harness.require;

/**
 * The hotkey and the window: B opens and closes the terminal but never from chat, the settings popover and Mod Menu's
 * settings screen rebind it and turn down keys Minecraft uses, the panel drags by its title bar, and fullscreen and size are remembered.
 */
public final class HotkeyGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "HotkeyGameTest", harness -> {
            // Typing a b in chat types a b.
            context.getInput().pressKey(options -> options.keyChat);
            context.waitForScreen(ChatScreen.class);
            harness.key(GLFW.GLFW_KEY_B);
            context.runOnClient(client -> require(client.screen instanceof ChatScreen,
                    "B opens nothing while chat is open"));
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.screen == null);

            harness.open();
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.screen == null);
            harness.open();
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.screen == null);
            harness.open();

            // The panel floats with a margin and drags by its title bar.
            int[] home = context.computeOnClient(client -> Harness.screen(client).bounds());
            int[] size = context.computeOnClient(client -> new int[] {
                    client.getWindow().getWidth() / Harness.screen(client).scale(),
                    client.getWindow().getHeight() / Harness.screen(client).scale()});
            require(home[0] > 0 && home[1] > 0 && home[2] > size[0] * 0.8 && home[3] > size[1] * 0.8,
                    "The panel fills most of the screen with a margin: " + java.util.Arrays.toString(home));
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, home[0] + 200, home[1] + 8, home[0] + 230, home[1] + 20);
            int[] moved = context.computeOnClient(client -> Harness.screen(client).bounds());
            require(moved[0] == home[0] + 30 && moved[1] == home[1] + 12, "The title bar drags the panel: "
                    + java.util.Arrays.toString(moved));
            require(Config.load().offsetX == 30 && Config.load().offsetY == 12, "Where it was dropped is saved");
            context.runOnClient(client -> {
                BagelTV.config().offsetX = 0;
                BagelTV.config().offsetY = 0;
            });
            context.waitTicks(2);

            // Fullscreen fills the window and is remembered.
            harness.click(context.computeOnClient(client -> Harness.screen(client).control("fullscreen")));
            int[] full = context.computeOnClient(client -> Harness.screen(client).bounds());
            require(full[0] == 0 && full[1] == 0 && full[2] == size[0] && full[3] == size[1],
                    "Fullscreen fills the window: " + java.util.Arrays.toString(full));
            require(Config.load().fullscreen, "Fullscreen is saved");
            harness.settle();
            harness.park();
            harness.shot("fullscreen");
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.screen == null);
            harness.open();
            require(context.computeOnClient(client -> Harness.screen(client).bounds()[2]) == size[0],
                    "It reopens fullscreen");
            harness.click(context.computeOnClient(client -> Harness.screen(client).control("fullscreen")));
            require(!Config.load().fullscreen, "The same button goes back to a window");

            // The settings popover rebinds the key.
            int[] gear = context.computeOnClient(client -> Harness.screen(client).control("settings"));
            harness.click(gear);
            harness.move(gear[0] - 60, gear[1] + 40);
            harness.shot("settings");
            int[] panel = context.computeOnClient(client -> Harness.screen(client).bounds());
            // The key button sits at the popover's right edge, on its first row.
            harness.click(gear[0] - 4, panel[1] + 17 + 13);
            harness.key(GLFW.GLFW_KEY_E);
            require(BagelTV.config().hotkey == GLFW.GLFW_KEY_B, "A key Minecraft uses is turned down");
            harness.shot("settings-key-in-use");
            harness.key(GLFW.GLFW_KEY_J);
            require(BagelTV.config().hotkey == GLFW.GLFW_KEY_J && Config.load().hotkey == GLFW.GLFW_KEY_J,
                    "The new key is saved");
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.runOnClient(client -> require(client.screen instanceof TerminalScreen,
                    "Escape closes the popover, not the terminal"));
            harness.key(GLFW.GLFW_KEY_B);
            context.runOnClient(client -> require(client.screen instanceof TerminalScreen,
                    "The old key no longer closes it"));
            harness.key(GLFW.GLFW_KEY_J);
            context.waitFor(client -> client.screen == null);
            harness.key(GLFW.GLFW_KEY_J);
            context.waitForScreen(TerminalScreen.class);
            harness.key(GLFW.GLFW_KEY_J);
            context.waitFor(client -> client.screen == null);

            // Mod Menu's settings button for the mod opens a screen that rebinds the key without the terminal.
            context.runOnClient(client -> {
                var entry = FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class).stream()
                        .filter(container -> container.getProvider().getMetadata().getId().equals(BagelTV.ID))
                        .findFirst().orElseThrow(() -> new AssertionError("Mod Menu does not see the mod"));
                client.setScreen(entry.getEntrypoint().getModConfigScreenFactory().create(null));
            });
            context.waitForScreen(HotkeyScreen.class);
            harness.shot("modmenu-hotkey");
            // The key button sits just above the middle of the screen.
            double[] button = context.computeOnClient(client -> new double[] {
                    client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight()
                            * (client.getWindow().getGuiScaledHeight() / 2.0 - 20) / client.getWindow().getGuiScaledHeight()});
            context.getInput().setCursorPos(button[0], button[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            harness.key(GLFW.GLFW_KEY_E);
            require(BagelTV.config().hotkey == GLFW.GLFW_KEY_J, "Mod Menu's screen turns down a key Minecraft uses");
            harness.shot("modmenu-hotkey-in-use");
            harness.key(GLFW.GLFW_KEY_K);
            require(BagelTV.config().hotkey == GLFW.GLFW_KEY_K && Config.load().hotkey == GLFW.GLFW_KEY_K,
                    "Mod Menu's screen saves the new key");
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> client.screen == null);
            harness.key(GLFW.GLFW_KEY_K);
            context.waitForScreen(TerminalScreen.class);
            context.runOnClient(client -> {
                BagelTV.config().hotkey = GLFW.GLFW_KEY_B;
                BagelTV.config().save();
            });
        });
    }
}

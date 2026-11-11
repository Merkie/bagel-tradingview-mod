package com.merkie.bageltv;

import com.merkie.bageltv.chart.Drawings;
import com.merkie.bageltv.data.Api;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.ui.TerminalScreen;
import com.merkie.bageltv.view.View;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.function.FailableConsumer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** What the terminal's tests share: a fresh terminal in a world, and a mouse that works in terminal pixels. */
final class Harness {
    static final Logger LOG = LoggerFactory.getLogger("bagel-tradingview-test");
    static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
    private final ClientGameTestContext context;

    private Harness(ClientGameTestContext context) {
        this.context = context;
    }

    /** Runs a test in a new world with default settings, no saved drawings and the busy fake market. */
    static void run(ClientGameTestContext context, String name, FailableConsumer<Harness, Exception> test) {
        FakeApi.mode = FakeApi.Mode.DENSE;
        FakeApi.delay = 0;
        FakeApi.clearRequests();
        com.merkie.bageltv.ui.Intro.enabled = false;
        com.merkie.bageltv.ui.Intro.frozen = null;
        try {
            context.runOnClient(client -> {
                Config fresh = new Config();
                Config current = BagelTV.config();
                for (var field : Config.class.getFields()) field.set(current, field.get(fresh));
                Files.deleteIfExists(CONFIG.resolve("bagel-tradingview-drawings.json"));
                Drawings.reload();
                TerminalScreen.reset();
                Api.clear();
            });
            try (var world = context.worldBuilder().create()) {
                world.getClientWorld().waitForChunksRender();
                test.accept(new Harness(context));
                context.runOnClient(client -> {
                    if (client.screen instanceof TerminalScreen screen) screen.onClose();
                });
            }
        } catch (Exception exception) {
            throw new AssertionError(name + " failed", exception);
        }
        LOG.info("PASS: {}", name);
    }

    ClientGameTestContext context() {
        return context;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    void open() {
        context.getInput().pressKey(GLFW.GLFW_KEY_B);
        context.waitForScreen(TerminalScreen.class);
        settle();
    }

    /** Waits for every request in flight to land and a few frames to draw it. */
    void settle() {
        // One answer can lead to another request, so wait for quiet twice.
        for (int round = 0; round < 2; round++) {
            context.waitTicks(2);
            context.waitFor(client -> !Api.busy(), 200);
            context.waitTicks(3);
        }
    }

    void go(Route route) {
        context.runOnClient(client -> screen(client).go(route));
        settle();
    }

    static TerminalScreen screen(Minecraft client) {
        if (!(client.screen instanceof TerminalScreen screen)) {
            throw new AssertionError("The terminal is not open: " + client.screen);
        }
        return screen;
    }

    @SuppressWarnings("unchecked")
    <V extends View> V view(Class<V> type) {
        return (V) context.computeOnClient(client -> {
            View view = TerminalScreen.view();
            require(type.isInstance(view), "Showing " + view + ", not " + type.getSimpleName());
            return view;
        });
    }

    /** Window coordinates per terminal pixel. */
    private double factor() {
        return context.computeOnClient(client -> (double) screen(client).scale()
                * client.getWindow().getScreenWidth() / client.getWindow().getWidth());
    }

    /** Where a named control was drawn in the last frame. */
    int[] mark(String name) {
        context.waitTick();
        return context.computeOnClient(client -> screen(client).ui().marked(name));
    }

    /** Picks an option from a dropdown showing a label. */
    void pick(String showing, String option) {
        click(mark("menu:" + showing));
        click(mark("option:" + option));
        settle();
    }

    void move(int x, int y) {
        double factor = factor();
        context.getInput().setCursorPos((x + 0.5) * factor, (y + 0.5) * factor);
        context.waitTick();
    }

    void move(int[] at) {
        move(at[0], at[1]);
    }

    void click(int x, int y) {
        move(x, y);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    void click(int[] at) {
        click(at[0], at[1]);
    }

    void doubleClick(int x, int y) {
        move(x, y);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // Long enough that the next press is not taken for a third click.
        context.waitTicks(7);
    }

    /** Presses a button at one point, moves to another in steps, and lets go. */
    void drag(int button, int fromX, int fromY, int toX, int toY) {
        move(fromX, fromY);
        context.getInput().holdMouse(button);
        context.waitTick();
        int steps = 6;
        for (int step = 1; step <= steps; step++) {
            move(fromX + (toX - fromX) * step / steps, fromY + (toY - fromY) * step / steps);
        }
        context.getInput().releaseMouse(button);
        context.waitTick();
    }

    void scroll(int x, int y, double amount) {
        move(x, y);
        context.getInput().scroll(amount);
        context.waitTick();
    }

    void type(String text) {
        context.getInput().typeChars(text);
        context.waitTick();
    }

    void key(int key) {
        context.getInput().pressKey(key);
        context.waitTick();
    }

    /** Takes a screenshot with the mouse parked where it highlights nothing, unless told to leave it. */
    void shot(String name) {
        context.waitTicks(2);
        context.takeScreenshot(name);
    }

    void park() {
        context.getInput().setCursorPos(2, 2);
        context.waitTicks(2);
    }

    static String read(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }
}

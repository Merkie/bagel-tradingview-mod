package com.merkie.bageltv;

import com.merkie.bageltv.ui.TerminalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** BagelTradingView: a market terminal for the BagelSMP economy, opened with a hotkey. */
public final class BagelTV implements ClientModInitializer {
    public static final String ID = "bagel-tradingview";
    public static final String NAME = "BagelTradingView";
    public static final String VERSION = FabricLoader.getInstance().getModContainer(ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private static Config config;

    @Override
    public void onInitializeClient() {
        config = Config.load();
    }

    public static Config config() {
        if (config == null) config = Config.load();
        return config;
    }

    /**
     * Opens the terminal on its hotkey, in game or on the title screen, never over chat or another screen. The
     * terminal closes itself on the same key.
     */
    public static boolean openTerminal(Minecraft client, int key) {
        if (key != config().hotkey || client.gui == null || client.gui.overlay() != null) return false;
        if (client.options.keyDebugModifier.isDown()) return false;
        var current = client.gui.screen();
        if (current != null && !(current instanceof TitleScreen)) return false;
        if (current == null && client.player == null) return false;
        client.gui.setScreen(new TerminalScreen(current));
        return true;
    }
}

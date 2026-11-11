package com.merkie.bageltv.ui;

import com.merkie.bageltv.BagelTV;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/** The key that opens the terminal: its name, and changing it from the terminal's settings or from Mod Menu. */
public final class Hotkey {
    private Hotkey() {}

    public static String name() {
        return name(BagelTV.config().hotkey);
    }

    private static String name(int code) {
        return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }

    /** Takes a key and saves it unless Minecraft already uses it; returns what to tell the player, empty when taken. */
    public static String bind(int code) {
        Minecraft minecraft = Minecraft.getInstance();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            boolean plain = mapping.getCategory() != KeyMapping.Category.DEBUG
                    || mapping == minecraft.options.keyDebugModifier || mapping == minecraft.options.keyDebugOverlay;
            if (plain && mapping.matches(new KeyEvent(code, 0, 0))) return "Minecraft already uses " + name(code);
        }
        BagelTV.config().hotkey = code;
        BagelTV.config().save();
        return "";
    }
}

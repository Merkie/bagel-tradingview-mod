package com.merkie.bageltv.ui;

import com.merkie.bageltv.BagelTV;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's settings in Mod Menu: the key that opens the terminal, for a player whose key is taken and so cannot
 * reach the terminal's own settings. Built from Minecraft's widgets only, so it looks like the screens around it.
 */
public final class HotkeyScreen extends Screen {
    private final Screen parent;
    private boolean capturing;
    private String note = "";

    public HotkeyScreen(Screen parent) {
        super(Component.literal(BagelTV.NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100, y = height / 2 - 30;
        addRenderableWidget(new StringWidget(x, y - 24, 200, 9, title, font));
        addRenderableWidget(Button.builder(Component.literal(capturing ? "Press a key" : "Hotkey: " + Hotkey.name()),
                button -> {
                    capturing = !capturing;
                    note = "";
                    rebuildWidgets();
                }).bounds(x, y, 200, 20).build());
        addRenderableWidget(new StringWidget(x, y + 26, 200, 9, Component.literal(note), font));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y + 48, 200, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!capturing) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) capturing = false;
        else if (event.key() != GLFW.GLFW_KEY_UNKNOWN) {
            note = Hotkey.bind(event.key());
            capturing = !note.isEmpty();
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

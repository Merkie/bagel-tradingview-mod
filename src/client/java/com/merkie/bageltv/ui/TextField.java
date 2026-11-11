package com.merkie.bageltv.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** A one-line text box: type at the end, Backspace, paste, Ctrl+Backspace to clear. */
public final class TextField {
    public String text = "";
    public final String hint;
    public final int max;
    /** Runs when the text changes. */
    public Runnable changed = () -> {};
    /** Runs on Enter. */
    public Runnable submit = () -> {};

    public TextField(String hint, int max) {
        this.hint = hint;
        this.max = max;
    }

    public void set(String value) {
        text = value;
        changed.run();
    }

    void type(String typed) {
        StringBuilder next = new StringBuilder(text);
        typed.codePoints().filter(c -> c >= 32 && c != 127).forEach(next::appendCodePoint);
        if (next.length() > max) next.setLength(max);
        if (!next.toString().equals(text)) set(next.toString());
    }

    boolean key(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (text.isEmpty()) return true;
            if (event.hasControlDownWithQuirk() || event.hasAltDown()) set("");
            else set(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (!text.isEmpty()) set("");
            return true;
        }
        if (event.isPaste()) {
            type(Minecraft.getInstance().keyboardHandler.getClipboard().replace('\n', ' '));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit.run();
            return true;
        }
        return false;
    }
}

package com.merkie.bageltv.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.merkie.bageltv.BagelTV;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import java.util.function.IntConsumer;

/**
 * The boundary between two panels, dragged to resize them. It sits in the gap between the panels, lights up
 * under the mouse, and saves the new size when let go.
 */
public final class Splitter implements Interactive {
    private final boolean sideways;
    private final IntConsumer set;
    private int size, min, max, grab;
    private boolean dragging;

    /**
     * @param sideways true for a bar between a left and a right panel, false for one between upper and lower
     * @param set stores the size of the panel after the bar (the right or the lower one)
     */
    public Splitter(boolean sideways, IntConsumer set) {
        this.sideways = sideways;
        this.set = set;
    }

    /** Draws the bar in its gap. {@code size} is the current size of the panel after it. */
    public void render(Ui ui, int x, int y, int w, int h, int size, int min, int max) {
        this.size = size;
        this.min = min;
        this.max = Math.max(min, max);
        // A little wider than the gap, so it is easy to catch.
        int gx = sideways ? x - 1 : x, gy = sideways ? y : y - 1, gw = sideways ? w + 2 : w, gh = sideways ? h : h + 2;
        ui.interactive(gx, gy, gw, gh, this);
        boolean over = dragging || ui.hover(gx, gy, gw, gh);
        if (!over) return;
        ui.cursor(sideways ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_NS);
        int color = dragging ? Theme.ACCENT : Theme.BORDER_STRONG;
        if (sideways) ui.fill(x + w / 2 - 1, y, 2, h, color);
        else ui.fill(x, y + h / 2 - 1, w, 2, color);
    }

    @Override
    public boolean mouseDown(int px, int py, int button, boolean shift, boolean doubleClick) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        dragging = true;
        grab = (sideways ? px : py) + size;
        return true;
    }

    @Override
    public void mouseDrag(int px, int py) {
        if (dragging) set.accept(Math.clamp(grab - (sideways ? px : py), min, max));
    }

    @Override
    public void mouseUp(int px, int py) {
        dragging = false;
        BagelTV.config().save();
    }
}

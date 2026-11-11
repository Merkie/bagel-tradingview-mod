package com.merkie.bageltv.view;

import com.merkie.bageltv.ui.Ui;

/**
 * One of the terminal's views. To add one: extend this, draw it in {@link #render} from {@code Api.get} entries,
 * add a {@code Route.Kind} for it and create it in {@code TerminalScreen.create}.
 */
public abstract class View {
    /** Draws the view in its box. Called every frame while the view is showing. */
    public abstract void render(Ui ui, int x, int y, int w, int h);

    /** A key press the view may use, such as Delete for a selected drawing. */
    public boolean key(int key) {
        return false;
    }
}

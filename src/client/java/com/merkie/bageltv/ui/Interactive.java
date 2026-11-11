package com.merkie.bageltv.ui;

/** Something that takes the mouse itself, such as a chart or a scroll bar. Coordinates are terminal pixels. */
public interface Interactive {
    /** A press inside its region. Returning true captures the drag until release. */
    boolean mouseDown(int x, int y, int button, boolean shift, boolean doubleClick);

    default void mouseDrag(int x, int y) {}

    default void mouseUp(int x, int y) {}

    default boolean scroll(int x, int y, double dx, double dy) { return false; }
}

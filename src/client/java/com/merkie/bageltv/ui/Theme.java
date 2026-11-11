package com.merkie.bageltv.ui;

/** The terminal's palette and sizes: near-black navy, emerald for up and buy, ruby for down and sell, one blue. */
public final class Theme {
    /** Behind the panel, over the game. */
    public static final int DIM_TOP = 0xA0060912;
    public static final int DIM_BOTTOM = 0xD0060912;
    public static final int SHADOW = 0x80000000;

    /** Chart and table wells. */
    public static final int BG = 0xFF0A0D14;
    /** The panel itself. */
    public static final int PANEL = 0xFF0E121B;
    /** Title bar, tiles, table headers. */
    public static final int RAISED = 0xFF131926;
    public static final int HOVER = 0xFF1A2233;
    public static final int SELECTED = 0xFF16233D;
    public static final int BORDER = 0xFF222B3D;
    public static final int BORDER_STRONG = 0xFF34425E;
    public static final int GRID = 0xFF141A27;
    public static final int GRID_STRONG = 0xFF1C2435;

    public static final int TEXT = 0xFFD6DDEB;
    public static final int MUTED = 0xFF8493AD;
    public static final int FAINT = 0xFF55627C;

    public static final int UP = 0xFF30C988;
    public static final int DOWN = 0xFFEF476F;
    public static final int ACCENT = 0xFF4D90FF;
    public static final int WARN = 0xFFE9B44C;
    /** Buy orders, where they are drawn beside the auction house (which is ACCENT). */
    public static final int ORDERS = 0xFFB78CFF;
    /** The plus before a Bagel+ player's name. */
    public static final int PLUS = 0xFFFFB347;

    public static final int UP_SOFT = 0x5530C988;
    public static final int DOWN_SOFT = 0x55EF476F;
    public static final int UP_BG = 0xFF10261F;
    public static final int DOWN_BG = 0xFF2A131B;
    public static final int ACCENT_SOFT = 0x334D90FF;

    /** Colors for drawings and for series with no color of their own. */
    public static final int[] PALETTE = {
            0xFF4D90FF, 0xFFE9B44C, 0xFF30C988, 0xFFEF476F, 0xFFB388FF, 0xFF4DD0E1, 0xFFFF8A5B, 0xFFD6DDEB,
            0xFF9CCC65, 0xFFF06292
    };

    public static final int TITLE_BAR = 17;
    /** A row with an item icon. */
    public static final int ROW = 18;
    /** A row of text only. */
    public static final int LINE = 11;

    private Theme() {}

    public static int change(Double value) {
        if (value == null || Double.isNaN(value) || Math.abs(value) < 0.00005) return MUTED;
        return value > 0 ? UP : DOWN;
    }

    public static int alpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    /** Mixes two opaque colors; {@code t} of 0 is all {@code a}. */
    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}

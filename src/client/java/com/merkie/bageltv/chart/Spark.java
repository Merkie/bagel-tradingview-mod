package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;

/** A tiny line for a table cell: a couple of dozen points, nulls skipped. */
public final class Spark {
    private Spark() {}

    /** Draws the points across a box, emerald if the last is above the first, ruby if below. */
    public static void draw(Ui ui, int x, int y, int w, int h, Double[] values) {
        if (values == null || values.length == 0) return;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY, first = Double.NaN, last = Double.NaN;
        int points = 0;
        for (Double value : values) {
            if (value == null) continue;
            if (points++ == 0) first = value;
            last = value;
            low = Math.min(low, value);
            high = Math.max(high, value);
        }
        // Two points are not a trend; say nothing rather than draw one.
        if (points < 3) return;
        int color = last > first ? Theme.UP : last < first ? Theme.DOWN : Theme.MUTED;
        int n = values.length;
        int prevX = 0, prevY = 0;
        boolean pen = false;
        for (int i = 0; i < n; i++) {
            if (values[i] == null) continue;
            int px = x + (n == 1 ? w / 2 : Math.round(i * (w - 1) / (float) (n - 1)));
            int py = high == low ? y + h / 2 : y + h - 1 - (int) Math.round((values[i] - low) / (high - low) * (h - 1));
            // Buckets without a sale are skipped, so the line runs from one known price to the next.
            if (pen) segment(ui, prevX, prevY, px, py, color);
            prevX = px;
            prevY = py;
            pen = true;
        }
    }

    /** A line between two points that are a few pixels apart, a column at a time. */
    private static void segment(Ui ui, int ax, int ay, int bx, int by, int color) {
        int width = bx - ax;
        if (width <= 0) {
            ui.fill(ax, Math.min(ay, by), 1, Math.abs(by - ay) + 1, color);
            return;
        }
        int from = ay;
        for (int step = 1; step <= width; step++) {
            int to = ay + Math.round((by - ay) * step / (float) width);
            int top = Math.min(from, to), bottom = Math.max(from, to);
            // Each column covers from where the last one ended, so steep runs stay joined.
            if (to > from) top++;
            else if (to < from) bottom--;
            ui.fill(ax + step, Math.min(top, bottom), 1, Math.abs(bottom - top) + 1, color);
            from = to;
        }
        ui.fill(ax, ay, 1, 1, color);
    }
}

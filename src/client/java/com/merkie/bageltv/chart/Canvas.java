package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Ui;

/**
 * One pane of a chart: where times and values land in pixels, and pixel fills clipped to the pane. Everything a
 * series draws goes through here, so nothing spends a fill outside the plot.
 */
public final class Canvas {
    Ui ui;

    /** The pane, in terminal pixels. */
    public int x, y, w, h;
    /** The visible time range, in seconds. */
    public double t0, t1;
    /** The visible value range after the scale's transform. */
    double f0, f1;
    boolean log;
    boolean invert;

    public double px(double t) {
        return x + (t - t0) / (t1 - t0) * w;
    }

    public int ix(double t) {
        return (int) Math.floor(Math.clamp(px(t), -30000.0, 30000.0));
    }

    public double timeAt(double pixel) {
        return t0 + (pixel - x) / w * (t1 - t0);
    }

    double transform(double v) {
        return log ? Math.log(Math.max(v, 1e-9)) : v;
    }

    double untransform(double f) {
        return log ? Math.exp(f) : f;
    }

    public double py(double v) {
        double share = (transform(v) - f0) / (f1 - f0);
        return invert ? y + share * (h - 1) : y + (1 - share) * (h - 1);
    }

    public int iy(double v) {
        return (int) Math.round(Math.clamp(py(v), -30000.0, 30000.0));
    }

    public double valueAt(double pixel) {
        double share = (pixel - y) / (h - 1);
        return untransform(invert ? f0 + share * (f1 - f0) : f1 - share * (f1 - f0));
    }

    public double pxPerSecond() {
        return w / (t1 - t0);
    }

    /** A vertical run of pixels from ya to yb inclusive, in either order. */
    public void col(int cx, int ya, int yb, int color) {
        col(cx, ya, yb, 1, color);
    }

    public void col(int cx, int ya, int yb, int width, int color) {
        int top = Math.max(Math.min(ya, yb), y), bottom = Math.min(Math.max(ya, yb), y + h - 1);
        int left = Math.max(cx, x), right = Math.min(cx + width, x + w);
        if (bottom < top || right <= left) return;
        ui.g.fill(left, top, right, bottom + 1, color);
    }

    /** A horizontal run of pixels from xa to xb inclusive. */
    public void row(int xa, int xb, int cy, int color) {
        row(xa, xb, cy, 1, color);
    }

    public void row(int xa, int xb, int cy, int height, int color) {
        int left = Math.max(Math.min(xa, xb), x), right = Math.min(Math.max(xa, xb), x + w - 1);
        int top = Math.max(cy, y), bottom = Math.min(cy + height, y + h);
        if (right < left || bottom <= top) return;
        ui.g.fill(left, top, right + 1, bottom, color);
    }

    /** A dashed horizontal line across the pane. */
    public void dashedRow(int cy, int on, int off, int color) {
        if (cy < y || cy >= y + h) return;
        for (int cx = x; cx < x + w; cx += on + off) row(cx, Math.min(cx + on - 1, x + w - 1), cy, color);
    }

    public void dashedCol(int cx, int on, int off, int color) {
        if (cx < x || cx >= x + w) return;
        for (int cy = y; cy < y + h; cy += on + off) col(cx, cy, Math.min(cy + on - 1, y + h - 1), color);
    }

    /**
     * A straight line, clipped to the pane and stepped pixel by pixel, with each run of pixels on one row or
     * column drawn as a single fill.
     */
    public void line(double ax, double ay, double bx, double by, int color, int thickness) {
        // Liang-Barsky: keep the part inside the pane, a little past its edges so thick lines meet them.
        double minX = x - 2, maxX = x + w + 1, minY = y - 2, maxY = y + h + 1;
        double dx = bx - ax, dy = by - ay;
        double enter = 0, leave = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {ax - minX, maxX - ax, ay - minY, maxY - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return;
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0) enter = Math.max(enter, r);
                else leave = Math.min(leave, r);
            }
        }
        if (enter > leave) return;
        int x0 = (int) Math.round(ax + enter * dx), y0 = (int) Math.round(ay + enter * dy);
        int x1 = (int) Math.round(ax + leave * dx), y1 = (int) Math.round(ay + leave * dy);
        steps(x0, y0, x1, y1, color, thickness);
    }

    private void steps(int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        if (dx >= dy) {
            if (x0 > x1) {
                int swap = x0;
                x0 = x1;
                x1 = swap;
                swap = y0;
                y0 = y1;
                y1 = swap;
            }
            int step = y1 >= y0 ? 1 : -1;
            int error = dx / 2, cy = y0, start = x0;
            for (int cx = x0; cx <= x1; cx++) {
                error -= dy;
                if (error < 0) {
                    row(start, cx, cy, thickness, color);
                    start = cx + 1;
                    cy += step;
                    error += dx;
                }
            }
            if (start <= x1) row(start, x1, cy, thickness, color);
        } else {
            if (y0 > y1) {
                int swap = x0;
                x0 = x1;
                x1 = swap;
                swap = y0;
                y0 = y1;
                y1 = swap;
            }
            int step = x1 >= x0 ? 1 : -1;
            int error = dy / 2, cx = x0, start = y0;
            for (int cy = y0; cy <= y1; cy++) {
                error -= dx;
                if (error < 0) {
                    col(cx, start, cy, thickness, color);
                    start = cy + 1;
                    cx += step;
                    error += dy;
                }
            }
            if (start <= y1) col(cx, start, y1, thickness, color);
        }
    }

    /** A small square centered on a point. */
    public void dot(int cx, int cy, int size, int color) {
        int half = size / 2;
        for (int row = 0; row < size; row++) row(cx - half, cx - half + size - 1, cy - half + row, color);
    }
}

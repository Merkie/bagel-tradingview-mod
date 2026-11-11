package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Theme;

/** A line through points in time, optionally filled underneath. NaN values are gaps. */
public final class Line extends Series {
    public double[] v;
    /** Fills under the line. */
    public boolean area;
    /** Draws the line as steps that hold each value until the next point. */
    public boolean step;
    /** Marks each point when there are few of them, far apart. */
    public boolean markers = true;
    /** Marks every point, however many: each is one event, such as a sale. */
    public boolean dots;
    /** An item key whose icon marks the line's end, or null. */
    public String icon;
    /** A player whose head marks the line's end, or null. */
    public String head;
    /** Where the line's last visible point was drawn, for its icon and hit tests. */
    int endX = Integer.MIN_VALUE, endY;

    public Line(String name, int color, double[] t, double[] v) {
        this.name = name;
        this.color = color;
        this.t = t;
        this.v = v;
    }

    @Override
    public void range(double t0, double t1, double[] range) {
        int first = Math.max(0, lowerBound(t0) - 1);
        for (int i = first; i < t.length; i++) {
            widen(range, v[i]);
            if (t[i] > t1) break;
        }
    }

    @Override
    public double value(int index) {
        return v[index];
    }

    /** The line's height in pixels at a pixel column, between its points, or NaN in a gap. */
    double yAt(Canvas canvas, double pixelX) {
        double time = canvas.timeAt(pixelX);
        int next = lowerBound(time);
        if (next >= t.length) return next > 0 && canvas.px(t[next - 1]) >= pixelX - 2 ? canvas.py(v[next - 1])
                : Double.NaN;
        if (next == 0) return canvas.px(t[0]) <= pixelX + 2 ? canvas.py(v[0]) : Double.NaN;
        double a = v[next - 1], b = v[next];
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;
        if (step) return canvas.py(a);
        double share = (time - t[next - 1]) / (t[next] - t[next - 1]);
        return canvas.py(a) + (canvas.py(b) - canvas.py(a)) * share;
    }

    @Override
    public void draw(Canvas canvas, int color, boolean emphasized) {
        int n = t.length;
        endX = Integer.MIN_VALUE;
        if (n == 0) return;
        int first = Math.max(0, lowerBound(canvas.t0) - 1);
        int last = Math.min(n - 1, lowerBound(canvas.t1) + 1);
        int thickness = emphasized ? 2 : 1;
        if (area) fill(canvas, first, last, color);

        boolean pen = false, alone = true;
        int column = 0, low = 0, high = 0, lastY = 0;
        int visiblePoints = 0;
        for (int i = first; i <= last; i++) {
            double value = v[i];
            if (Double.isNaN(value)) {
                if (pen) finish(canvas, column, low, high, alone, color, thickness);
                pen = false;
                continue;
            }
            visiblePoints++;
            int cx = canvas.ix(t[i]), cy = canvas.iy(value);
            if (cx <= canvas.x + canvas.w + 1) {
                endX = cx;
                endY = cy;
            }
            if (!pen) {
                pen = true;
                alone = true;
                column = cx;
                low = high = lastY = cy;
            } else if (cx == column) {
                low = Math.min(low, cy);
                high = Math.max(high, cy);
                lastY = cy;
            } else {
                if (low != high) canvas.col(column, low, high, thickness, color);
                if (step) {
                    canvas.line(column, lastY, cx, lastY, color, thickness);
                    canvas.line(cx, lastY, cx, cy, color, thickness);
                } else {
                    canvas.line(column, lastY, cx, cy, color, thickness);
                }
                alone = false;
                column = cx;
                low = high = lastY = cy;
            }
        }
        if (pen) finish(canvas, column, low, high, alone, color, thickness);

        // With few points far apart, mark each so a sparse history reads as points, not a ruler line.
        if (dots || markers && visiblePoints > 0 && visiblePoints <= 60 && canvas.w / Math.max(1, visiblePoints) >= 9) {
            for (int i = first; i <= last; i++) {
                if (!Double.isNaN(v[i])) canvas.dot(canvas.ix(t[i]), canvas.iy(v[i]), 3, color);
            }
        }
    }

    private static void finish(Canvas canvas, int column, int low, int high, boolean alone, int color,
                               int thickness) {
        if (low != high) canvas.col(column, low, high, thickness, color);
        else if (alone) canvas.dot(column, low, 3, color);
    }

    /** The fill under the line, one translucent column per pixel. */
    private void fill(Canvas canvas, int first, int last, int color) {
        int tint = Theme.alpha(color, 0x26);
        int bottom = canvas.y + canvas.h - 1;
        for (int i = first; i < last; i++) {
            if (Double.isNaN(v[i]) || Double.isNaN(v[i + 1])) continue;
            double ax = canvas.px(t[i]), bx = canvas.px(t[i + 1]);
            double ay = canvas.py(v[i]), by = canvas.py(v[i + 1]);
            int from = Math.max(canvas.x, (int) Math.floor(ax));
            int to = Math.min(canvas.x + canvas.w - 1, (int) Math.floor(bx) - (i + 1 < last ? 1 : 0));
            for (int cx = from; cx <= to; cx++) {
                double share = bx == ax ? 0 : Math.clamp((cx - ax) / (bx - ax), 0, 1);
                int top = (int) Math.round(step ? ay : ay + (by - ay) * share) + 1;
                if (top <= bottom) canvas.col(cx, top, bottom, tint);
            }
        }
    }
}

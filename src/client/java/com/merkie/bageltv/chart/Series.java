package com.merkie.bageltv.chart;

/**
 * Something a chart plots against time. To add a kind of series, extend this, say where its values are
 * ({@link #range}) and draw it through the {@link Canvas} ({@link #draw}); the chart handles the rest.
 */
public abstract class Series {
    public String name = "";
    public int color;
    public boolean visible = true;
    /** Times in Unix seconds, ascending. */
    public double[] t = new double[0];

    public int size() {
        return t.length;
    }

    /** Widens {@code range} (min, max) to cover this series' values between two times. */
    public abstract void range(double t0, double t1, double[] range);

    public abstract void draw(Canvas canvas, int color, boolean emphasized);

    /** The value shown for the point at an index, or NaN. */
    public abstract double value(int index);

    /** The index of the point nearest a time, or -1 with no points. */
    public int nearest(double time) {
        int n = t.length;
        if (n == 0) return -1;
        int at = lowerBound(time);
        if (at <= 0) return 0;
        if (at >= n) return n - 1;
        return time - t[at - 1] <= t[at] - time ? at - 1 : at;
    }

    /** The first index whose time is at or after {@code time}. */
    public int lowerBound(double time) {
        int low = 0, high = t.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (t[middle] < time) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    static void widen(double[] range, double value) {
        if (Double.isNaN(value)) return;
        if (value < range[0]) range[0] = value;
        if (value > range[1]) range[1] = value;
    }
}

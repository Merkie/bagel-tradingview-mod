package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Theme;

/** Candlesticks: a wick from low to high and a body from open to close, emerald when it closed up, ruby when down. */
public final class Candles extends Series {
    public double[] o, h, l, c;
    /** Seconds each candle covers. A candle is centered on its start time, so its tick label sits under it. */
    public double bucket = 3600;
    /** Buckets with no trades, flat at the last close, drawn as a thin muted tick. Null when there are none. */
    public boolean[] empty;

    public Candles(double[] t, double[] o, double[] h, double[] l, double[] c, double bucket) {
        this.t = t;
        this.o = o;
        this.h = h;
        this.l = l;
        this.c = c;
        this.bucket = bucket;
    }

    @Override
    public void range(double t0, double t1, double[] range) {
        for (int i = Math.max(0, lowerBound(t0 - bucket)); i < t.length && t[i] <= t1 + bucket; i++) {
            widen(range, l[i]);
            widen(range, h[i]);
        }
    }

    @Override
    public double value(int index) {
        return c[index];
    }

    @Override
    public void draw(Canvas canvas, int color, boolean emphasized) {
        double slot = bucket * canvas.pxPerSecond();
        int first = Math.max(0, lowerBound(canvas.t0 - bucket)), n = t.length;
        if (slot < 2) {
            // Narrower than two pixels each: one column per pixel, covering every candle that lands on it.
            int column = Integer.MIN_VALUE;
            double low = 0, high = 0, open = 0, close = 0;
            for (int i = first; i < n && t[i] <= canvas.t1 + bucket; i++) {
                int cx = canvas.ix(t[i]);
                if (cx != column) {
                    if (column != Integer.MIN_VALUE) column(canvas, column, low, high, open, close);
                    column = cx;
                    low = l[i];
                    high = h[i];
                    open = o[i];
                } else {
                    low = Math.min(low, l[i]);
                    high = Math.max(high, h[i]);
                }
                close = c[i];
            }
            if (column != Integer.MIN_VALUE) column(canvas, column, low, high, open, close);
            return;
        }
        int body = width(slot);
        for (int i = first; i < n && t[i] <= canvas.t1 + bucket; i++) {
            int cx = canvas.ix(t[i]);
            if (empty != null && empty[i]) {
                canvas.col(cx - body / 2, canvas.iy(c[i]), canvas.iy(c[i]), body, Theme.FAINT);
                continue;
            }
            boolean up = c[i] >= o[i];
            int tone = up ? Theme.UP : Theme.DOWN;
            canvas.col(cx, canvas.iy(h[i]), canvas.iy(l[i]), tone);
            if (body >= 3) {
                int top = canvas.iy(Math.max(o[i], c[i])), bottom = canvas.iy(Math.min(o[i], c[i]));
                if (canvas.invert) {
                    int swap = top;
                    top = bottom;
                    bottom = swap;
                }
                canvas.col(cx - body / 2, top, Math.max(bottom, top), body, tone);
            }
        }
    }

    /**
     * How wide a candle or bar is drawn in a slot of so many pixels: the slot less a gap, as wide as the zoom
     * makes it, and odd so the wick sits in the middle.
     */
    public static int width(double slot) {
        int gap = slot >= 12 ? (int) Math.round(slot * 0.22) : slot >= 5 ? 2 : 1;
        int body = Math.max(1, (int) Math.floor(slot) - gap);
        return body % 2 == 0 ? body - 1 : body;
    }

    private static void column(Canvas canvas, int cx, double low, double high, double open, double close) {
        canvas.col(cx, canvas.iy(high), canvas.iy(low), close >= open ? Theme.UP : Theme.DOWN);
    }
}

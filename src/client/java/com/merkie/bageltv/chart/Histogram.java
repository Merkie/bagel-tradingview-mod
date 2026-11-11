package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Theme;

/** Bars rising from the bottom of the pane under a chart, such as volume or turnover per candle. */
public final class Histogram extends Series {
    public double[] v;
    /** Whether each bar's candle closed up, which picks its color; null draws every bar in the series color. */
    public boolean[] up;
    public double bucket = 3600;
    private static final int UP = Theme.mix(Theme.BG, Theme.UP, 0.5F);
    private static final int DOWN = Theme.mix(Theme.BG, Theme.DOWN, 0.5F);

    public Histogram(String name, double[] t, double[] v, boolean[] up, double bucket) {
        this.name = name;
        this.t = t;
        this.v = v;
        this.up = up;
        this.bucket = bucket;
        this.color = Theme.mix(Theme.BG, Theme.ACCENT, 0.5F);
    }

    @Override
    public void range(double t0, double t1, double[] range) {
        widen(range, 0);
        for (int i = Math.max(0, lowerBound(t0 - bucket)); i < t.length && t[i] <= t1 + bucket; i++) {
            widen(range, v[i]);
        }
    }

    @Override
    public double value(int index) {
        return v[index];
    }

    @Override
    public void draw(Canvas canvas, int color, boolean emphasized) {
        double slot = bucket * canvas.pxPerSecond();
        int bottom = canvas.y + canvas.h - 1;
        int first = Math.max(0, lowerBound(canvas.t0 - bucket)), n = t.length;
        if (slot < 2) {
            int column = Integer.MIN_VALUE;
            double high = 0;
            boolean rising = true;
            for (int i = first; i < n && t[i] <= canvas.t1 + bucket; i++) {
                int cx = canvas.ix(t[i]);
                if (cx != column) {
                    if (column != Integer.MIN_VALUE && high > 0) canvas.col(column, canvas.iy(high), bottom,
                            tone(rising, color));
                    column = cx;
                    high = 0;
                }
                if (v[i] >= high) {
                    high = v[i];
                    rising = up == null || up[i];
                }
            }
            if (column != Integer.MIN_VALUE && high > 0) canvas.col(column, canvas.iy(high), bottom,
                    tone(rising, color));
            return;
        }
        int bar = Candles.width(slot);
        for (int i = first; i < n && t[i] <= canvas.t1 + bucket; i++) {
            if (!(v[i] > 0)) continue;
            int cx = canvas.ix(t[i]);
            canvas.col(cx - bar / 2, Math.min(canvas.iy(v[i]), bottom), bottom, bar, tone(up == null || up[i], color));
        }
    }

    private int tone(boolean rising, int color) {
        return up == null ? color : rising ? UP : DOWN;
    }
}

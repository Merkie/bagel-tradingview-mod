package com.merkie.bageltv.chart;

import com.merkie.bageltv.ui.Theme;

/** Layers stacked to a whole, each a share of the total at every time: who makes up the market, and how it shifts. */
public final class Stack extends Series {
    public final String[] names;
    public final int[] colors;
    public final boolean[] shown;
    /** Per layer, its raw value at each time. */
    public final double[][] values;
    /** Running shares: {@code tops[layer][i]} is the top edge of a layer at a time, from 0 to 1. */
    private double[][] tops;
    /** The layer under the mouse, or -1. */
    public int hovered = -1;

    public Stack(double[] t, String[] names, int[] colors, double[][] values) {
        this.t = t;
        this.names = names;
        this.colors = colors;
        this.values = values;
        this.shown = new boolean[names.length];
        java.util.Arrays.fill(shown, true);
        rebuild();
    }

    /** Works the shares out again after layers are shown or hidden. */
    public void rebuild() {
        int layers = values.length, n = t.length;
        tops = new double[layers][n];
        for (int i = 0; i < n; i++) {
            double total = 0;
            for (int layer = 0; layer < layers; layer++) if (shown[layer]) total += Math.max(0, values[layer][i]);
            double running = 0;
            for (int layer = 0; layer < layers; layer++) {
                if (shown[layer] && total > 0) running += Math.max(0, values[layer][i]) / total;
                tops[layer][i] = total > 0 ? running : Double.NaN;
            }
        }
    }

    @Override
    public void range(double t0, double t1, double[] range) {
        widen(range, 0);
        widen(range, 1);
    }

    @Override
    public double value(int index) {
        return Double.NaN;
    }

    /** A layer's share of the whole at an index. */
    public double share(int layer, int index) {
        double top = tops[layer][index];
        if (Double.isNaN(top) || !shown[layer]) return Double.NaN;
        double below = 0;
        for (int under = layer - 1; under >= 0; under--) {
            if (shown[under]) {
                below = tops[under][index];
                break;
            }
        }
        return top - below;
    }

    /** The layer drawn at a pixel, or -1. */
    int layerAt(Canvas canvas, int px, int py) {
        double time = canvas.timeAt(px);
        int next = lowerBound(time);
        if (next <= 0 || next >= t.length) return -1;
        double share = (time - t[next - 1]) / (t[next] - t[next - 1]);
        double value = canvas.valueAt(py);
        for (int layer = 0; layer < tops.length; layer++) {
            if (!shown[layer]) continue;
            double a = tops[layer][next - 1], b = tops[layer][next];
            if (Double.isNaN(a) || Double.isNaN(b)) return -1;
            if (value <= a + (b - a) * share) return layer;
        }
        return -1;
    }

    @Override
    public void draw(Canvas canvas, int color, boolean emphasized) {
        int n = t.length;
        if (n < 2) return;
        int first = Math.max(0, lowerBound(canvas.t0) - 1), last = Math.min(n - 1, lowerBound(canvas.t1) + 1);
        int layers = tops.length;
        int bottom = canvas.iy(0);
        for (int i = first; i < last; i++) {
            if (Double.isNaN(tops[layers - 1][i]) || Double.isNaN(tops[layers - 1][i + 1])) continue;
            double ax = canvas.px(t[i]), bx = canvas.px(t[i + 1]);
            int from = Math.max(canvas.x, (int) Math.ceil(ax)), to = Math.min(canvas.x + canvas.w - 1,
                    (int) Math.ceil(bx) - 1);
            for (int cx = from; cx <= to; cx++) {
                double share = bx == ax ? 0 : (cx - ax) / (bx - ax);
                int floor = bottom + 1;
                for (int layer = 0; layer < layers; layer++) {
                    if (!shown[layer]) continue;
                    double top = tops[layer][i] + (tops[layer][i + 1] - tops[layer][i]) * share;
                    int cy = canvas.iy(top);
                    if (cy < floor) {
                        boolean lit = hovered < 0 || hovered == layer;
                        // The top pixel of each layer is its full color, the body a shade under it.
                        canvas.col(cx, cy, cy, lit ? colors[layer] : Theme.mix(Theme.BG, colors[layer], 0.45F));
                        if (cy + 1 <= floor - 1) canvas.col(cx, cy + 1, floor - 1,
                                Theme.mix(Theme.BG, colors[layer], lit ? 0.62F : 0.28F));
                        floor = cy;
                    }
                }
            }
        }
    }
}

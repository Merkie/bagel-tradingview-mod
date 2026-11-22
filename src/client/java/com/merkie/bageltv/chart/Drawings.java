package com.merkie.bageltv.chart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the player drew on each chart of the page they are on, under the chart's key (such as
 * {@code item:water_bucket:price} or {@code market:index}). Points are a time and a value, so a drawing stays on
 * its candles however the chart is panned or zoomed. Nothing is saved: the terminal clears them when the player
 * goes to another page.
 */
public final class Drawings {
    private static final Map<String, List<Drawing>> ALL = new HashMap<>();

    private Drawings() {}

    public enum Kind { PENCIL, TREND, ARROW, HLINE, RECT }

    public static final class Drawing {
        public Kind kind;
        public int color;
        /** Times in seconds and values, one pair per point: many for a pencil stroke, two for the rest, one for a line across. */
        public double[] t;
        public double[] v;
    }

    /** The drawings for a chart, a list the chart edits in place. */
    public static List<Drawing> of(String key) {
        return ALL.computeIfAbsent(key, ignored -> new ArrayList<>());
    }

    /** Removes every drawing, as when the player leaves the page. */
    public static void clear() {
        ALL.clear();
    }
}

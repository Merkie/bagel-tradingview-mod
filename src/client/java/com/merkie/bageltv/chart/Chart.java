package com.merkie.bageltv.chart;

import com.merkie.bageltv.Fmt;
import com.merkie.bageltv.chart.Drawings.Drawing;
import com.merkie.bageltv.chart.Drawings.Kind;
import com.merkie.bageltv.ui.Icon;
import com.merkie.bageltv.ui.Interactive;
import com.merkie.bageltv.ui.Theme;
import com.merkie.bageltv.ui.Ui;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The one chart every view uses, drawn only with pixel fills. It plots candles, lines, a stack and a histogram
 * pane against time; has a time axis in local time and a value axis with round ticks; and takes the mouse like a
 * trading terminal: the wheel zooms time around the cursor, dragging pans, Shift-drag or right-drag (or the zoom
 * tool) zooms both axes to a box, dragging the value axis stretches it, and a double click fits everything again.
 * Every chart has the same header: what it shows, the view's own controls, then cursor, box zoom, the drawing
 * tools behind the pencil, log scale, reset and maximize. Drawings live in data space, so they stay on their
 * candles, can be selected, moved and reshaped, and are saved per chart key.
 */
public final class Chart implements Interactive {
    public enum Tool {
        CURSOR(Icon.CURSOR, "Cursor"), ZOOM(Icon.ZOOM, "Zoom to a box"), PENCIL(Icon.PENCIL, "Pencil"),
        TREND(Icon.TREND, "Line"), ARROW(Icon.ARROW, "Arrow"), HLINE(Icon.HLINE, "Horizontal line"),
        RECT(Icon.RECT, "Rectangle"), ERASER(Icon.ERASER, "Eraser");

        final Icon icon;
        final String label;

        Tool(Icon icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        boolean draws() {
            return this != CURSOR && this != ZOOM;
        }
    }

    /** A level marked across the chart, such as the cheapest listing, named and valued in a tag on the axis. */
    public record Ref(String label, double value, int color) {}

    /** A view's own controls in a chart's header, drawn from x and returning the x after them. */
    public interface Controls {
        int draw(Ui ui, int x, int y, int right);
    }

    /** Something a view draws in place of the plot when the data cannot support a chart. */
    public interface Body {
        void draw(Ui ui, int x, int y, int w, int h);
    }

    private enum Mode { NONE, PAN, BOX, DRAW, SCALE, MOVE, RESHAPE }

    /** Height of the header strip, its bottom border included: 13 rows of strip that a button fills exactly. */
    public static final int HEADER = 14;
    private static final int TIME_AXIS = 11;
    private static final int MAX_TICKS = 40;
    private static final Tool[] DRAW_TOOLS = {Tool.PENCIL, Tool.TREND, Tool.ARROW, Tool.HLINE, Tool.RECT};
    /** White first, so a drawing reads as separate from the data. */
    private static final int[] DRAW_COLORS = {0xFFFFFFFF, 0xFF4D90FF, 0xFFE9B44C, 0xFF30C988, 0xFFEF476F,
            0xFFB388FF};
    /** The color new drawings get, shared by every chart. */
    private static int drawColor;
    /** The chart filling the terminal's content area, or null. */
    private static Chart maximized;

    // What it shows. Views replace these when their data changes.
    public Candles candles;
    public final List<Line> lines = new ArrayList<>();
    public Stack stack;
    public Histogram histogram;
    public final List<Ref> refs = new ArrayList<>();
    public ValueFormat format = ValueFormat.MONEY;
    public ValueFormat histogramFormat = ValueFormat.COUNT;
    /** What the chart shows, written in its header, or at the start of the legend when the header is full. */
    public String title = "";
    public String emptyText = "No data yet";
    /** Where its drawings are saved. */
    public String key;
    /** Drawn under the header instead of the plot, or null for the chart itself. */
    public Body body;
    /** The view's controls for the header, or null. */
    public Controls controls;
    /** Pixels the controls need. */
    public int controlsWidth;
    /** Pixels kept for the title when it changes, so the controls after it stay put; 0 for the title's own. */
    public int titleSlot;
    public boolean log;
    public boolean canLog = true;
    /** Puts small values at the top, for rankings. */
    public boolean invert;
    /** A fixed value range instead of fitting to the data, or NaN. */
    public double fixedMin = Double.NaN, fixedMax = Double.NaN;
    /** Seconds per point for line charts on a regular grid, which sets how far the view can zoom in. */
    public double lineBucket;
    /** Lists each line's value in the legend. */
    public boolean legendLines = true;
    /** Tags each line's last value on the axis. */
    public boolean tagLines = true;
    /** The line to emphasize, set by a legend, or -1. */
    public int highlight = -1;
    /** The line under the mouse, or -1. */
    public int hoveredLine = -1;
    public Consumer<Line> onLineClick;
    /** The most candle slots the fitted view shows; longer histories start zoomed to the newest. */
    public int defaultSlots = 160;

    // The view.
    private boolean fitX = true, fitY = true;
    private double t0, t1, v0, v1;
    private double dataT0, dataT1;
    /** The furthest the view may reach: the data and a little room around it. */
    private double limitT0, limitT1;
    private boolean hasData;

    // Layout, kept for input between frames.
    private final Canvas main = new Canvas();
    private final Canvas sub = new Canvas();
    private int x, y, w, h, axisX, axisW, plotBottom;
    private boolean titleInLegend;

    // Ticks, rebuilt only when the view changes.
    private final TimeTicks timeTicks = new TimeTicks();
    private final double[] tickValues = new double[MAX_TICKS];
    private final String[] tickLabels = new String[MAX_TICKS];
    private int tickCount;
    private double tickStep;
    private double ticksV0 = Double.NaN, ticksV1;
    private int ticksH;
    private boolean ticksLog;
    private ValueFormat ticksFormat;

    // Tools and drawings.
    private Tool tool = Tool.CURSOR;
    private Mode mode = Mode.NONE;
    private Drawing draft;
    private Drawing selected;
    private int handle;
    private double[] grabT, grabV;
    private int pressX, pressY, dragX, dragY;
    private boolean moved;
    private double grabT0, grabT1, grabV0, grabV1;
    /** Owns the drawing tools' popover among the terminal's menus. */
    private final Object drawMenu = new Object();
    private final int[] toolX = new int[Tool.values().length];
    private final int[] toolYs = new int[Tool.values().length];
    private final java.util.Map<String, int[]> buttons = new java.util.HashMap<>();

    // Cached read-outs.
    private int legendIndex = -2;
    private Object legendFor;
    private int legendRefs;
    private final List<String> legendParts = new ArrayList<>();
    private final List<Integer> legendColors = new ArrayList<>();
    /** Whether a legend part is a label that sits close to the value after it. */
    private final List<Boolean> legendLabels = new ArrayList<>();
    private String crossTime = "";
    private double crossTimeFor = Double.NaN;
    private final double[] range = new double[2];
    private final int[] tagY = new int[16];
    private final int[] tagColor = new int[16];
    private final String[] tagText = new String[16];
    private final boolean[] tagFilled = new boolean[16];
    private final double[] tagValue = new double[16];
    private final int[] tagOrder = new int[16];
    /** Whether a tag's value is off the top (-1) or the bottom (1) of the pane. */
    private final int[] tagAway = new int[16];
    private int tags;
    private int[] iconX = new int[16];
    private int[] iconY = new int[16];
    private double histogramTop = 1;
    private String histogramLabel = "";

    public Chart(String key) {
        this.key = key;
    }

    /** Fits both axes to the data again. */
    public void fit() {
        fitX = true;
        fitY = true;
    }

    public Tool tool() {
        return tool;
    }

    public void tool(Tool next) {
        tool = next;
        draft = null;
        mode = Mode.NONE;
    }

    /** Shows a time range, leaving the value axis fitting itself. */
    public void show(double from, double to) {
        t0 = from;
        t1 = to;
        fitX = false;
        fitY = true;
    }

    public boolean fitted() {
        return fitX && fitY;
    }

    public boolean maximized() {
        return maximized == this;
    }

    /** Puts any maximized chart back in its place, as when the view changes. */
    public static void restore() {
        maximized = null;
    }

    /** The visible time and value range, for tests. */
    public double[] view() {
        return new double[] {t0, t1, v0, v1};
    }

    /** The first and last time of the data, for tests. */
    public double[] data() {
        return new double[] {dataT0, dataT1};
    }

    /** The plot's rectangle in terminal pixels (x, y, width, height), for tests. */
    public int[] plot() {
        return new int[] {main.x, main.y, main.w, main.h};
    }

    /** The whole chart's rectangle as last drawn, for tests. */
    public int[] bounds() {
        return new int[] {x, y, w, h};
    }

    /**
     * Where a header button is, in terminal pixels, for tests: a tool, or "draw", "reset", "log", "maximize",
     * "color", "trash". Drawing tools, colors and the trash are in the pencil's popover, so open that first.
     */
    public int[] button(Object which) {
        if (which instanceof Tool t) return new int[] {toolX[t.ordinal()], toolYs[t.ordinal()]};
        return buttons.get(which.toString());
    }

    /** The value axis strip's middle, for tests. */
    public int[] axis() {
        return new int[] {axisX + axisW / 2, main.y + main.h / 2};
    }

    public List<Drawing> drawings() {
        return key == null ? List.of() : Drawings.of(key);
    }

    public Drawing selected() {
        return selected;
    }

    private double bucket() {
        if (candles != null) return candles.bucket;
        if (histogram != null) return histogram.bucket;
        return lineBucket;
    }

    // Drawing the chart

    /** Draws the chart in a box, or over the whole content area while it is maximized. */
    public void render(Ui ui, int x, int y, int w, int h) {
        if (maximized == this) {
            ui.box(x, y, w, h, Theme.BG, Theme.BORDER);
            ui.later(() -> {
                ui.g.nextStratum();
                ui.fill(ui.contentX - 5, ui.contentY - 3, ui.contentW + 10, ui.contentH + 8, Theme.PANEL);
                ui.shield(ui.contentX - 5, ui.contentY - 3, ui.contentW + 10, ui.contentH + 8);
                draw(ui, ui.contentX, ui.contentY, ui.contentW, ui.contentH);
            });
            return;
        }
        draw(ui, x, y, w, h);
    }

    private void draw(Ui ui, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        main.ui = ui;
        sub.ui = ui;
        ui.box(x, y, w, h, Theme.BG, Theme.BORDER);
        ui.fill(x + 1, y + 1, w - 2, HEADER - 1, Theme.RAISED);
        ui.hline(x + 1, y + HEADER, w - 2, Theme.BORDER);
        int top = y + HEADER + 1;
        measureData();
        plotBottom = y + h - 1 - TIME_AXIS;
        int plotH = plotBottom - top;
        int subH = histogram != null && plotH >= 60 ? Math.clamp(Math.round(plotH * 0.24F), 16, 90) : 0;
        int mainH = plotH - subH - (subH > 0 ? 1 : 0);
        header(ui);
        if (body != null) {
            body.draw(ui, x + 1, top, w - 2, y + h - 1 - top);
            return;
        }
        if (mainH < 12 || w < 60) return;

        fitView(mainH, Math.max(40, w - 2 - 44));
        valueTicks(mainH);
        collectTags();
        // The gutter fits the widest thing written in it, on either pane.
        axisW = 26;
        for (int i = 0; i < tickCount; i++) axisW = Math.max(axisW, ui.width(tickLabels[i]) + 8);
        for (int i = 0; i < tags; i++) axisW = Math.max(axisW, ui.width(tagText[i]) + 14);
        if (subH > 0 && hasData) {
            range[0] = 0;
            range[1] = 0;
            histogram.range(t0, t1, range);
            histogramTop = range[1] > 0 ? range[1] : 1;
            histogramLabel = histogramFormat.format(histogramTop);
            axisW = Math.max(axisW, ui.width(histogramLabel) + 8);
        }
        axisW = Math.min(axisW, Math.max(26, w / 2));
        axisX = x + w - 1 - axisW;

        main.x = x + 1;
        main.y = top;
        main.w = axisX - main.x;
        main.h = mainH;
        main.t0 = t0;
        main.t1 = t1;
        main.log = log;
        main.invert = invert;
        main.f0 = main.transform(v0);
        main.f1 = main.transform(v1);
        sub.x = main.x;
        sub.w = main.w;
        sub.y = top + mainH + 1;
        sub.h = subH;
        sub.t0 = t0;
        sub.t1 = t1;
        sub.log = false;
        sub.invert = false;

        ui.vline(axisX, top, plotBottom - top, Theme.BORDER);
        ui.hline(x + 1, plotBottom, w - 2, Theme.BORDER);
        if (!hasData) {
            ui.textCenter(ui.fit(emptyText, main.w - 8), main.x + main.w / 2, main.y + main.h / 2 - 4, Theme.FAINT);
            return;
        }

        timeTicks.update(t0, t1, main.w, 54);
        grid(ui);
        boolean over = mode == Mode.NONE ? ui.hover(main.x, main.y, main.w, plotBottom - main.y)
                : mode != Mode.SCALE;
        int mouseX = mode == Mode.NONE ? ui.mx : dragX, mouseY = mode == Mode.NONE ? ui.my : dragY;

        ui.clip(main.x, main.y, main.w, main.h);
        series(ui, over, mouseX, mouseY);
        references();
        Drawing near = drawings(ui, over, mouseX, mouseY);
        ui.unclip();
        if (subH > 0) histogramPane(ui);
        endIcons(ui);
        placeTags();
        axisLabels(ui);

        int index = -1;
        Series primary = primary();
        int snapX = mouseX;
        if (over && primary != null && primary.size() > 0) {
            index = primary.nearest(main.timeAt(mouseX));
            int px = main.ix(primary.t[index]);
            if (Math.abs(px - mouseX) <= Math.max(6, bucket() * main.pxPerSecond())) snapX = px;
            else index = -1;
        }
        drawTags(ui);
        boolean busy = mode == Mode.BOX || mode == Mode.MOVE || mode == Mode.RESHAPE;
        if (over && !busy) crosshair(ui, snapX, mouseY, index >= 0 && primary != null
                ? primary.t[index] : main.timeAt(mouseX));
        legend(ui, primary, index);
        if (over && mode == Mode.NONE && index >= 0) {
            // With many series, say which one the mouse is on.
            if (stack != null && stack.hovered >= 0) {
                ui.tip(stack.names[stack.hovered], "Share\t" + Fmt.share(stack.share(stack.hovered, index)),
                        Fmt.dayClock((long) stack.t[index]));
            } else if (hoveredLine >= 0 && lines.size() > 3) {
                Line line = lines.get(hoveredLine);
                int at = line.nearest(main.timeAt(mouseX));
                ui.tip(line.name, (invert ? "Place" : "Value") + "\t" + (Double.isNaN(line.v[at]) ? "-"
                        : format.format(line.v[at])), Fmt.dayClock((long) line.t[at]));
            }
        }
        if (mode == Mode.BOX) {
            int bx = Math.min(pressX, dragX), by = Math.min(pressY, dragY);
            int bw = Math.abs(dragX - pressX) + 1, bh = Math.abs(dragY - pressY) + 1;
            ui.clip(main.x, main.y, main.w, main.h);
            ui.fill(bx, by, bw, bh, Theme.ACCENT_SOFT);
            ui.outline(bx, by, bw, bh, Theme.ACCENT);
            ui.unclip();
        }

        ui.interactive(x, top, w, y + h - top, this);
        if (ui.hover(axisX, main.y, axisW, main.h)) ui.cursor(CursorTypes.RESIZE_NS);
        else if (over && (tool != Tool.CURSOR || mode == Mode.BOX)) ui.cursor(CursorTypes.CROSSHAIR);
        else if (over && (near != null || mode == Mode.MOVE || mode == Mode.RESHAPE)) ui.cursor(CursorTypes.RESIZE_ALL);
        else if (over && hoveredLine >= 0 && onLineClick != null) ui.cursor(CursorTypes.POINTING_HAND);
    }

    private void measureData() {
        double first = Double.POSITIVE_INFINITY, last = Double.NEGATIVE_INFINITY;
        if (candles != null && candles.size() > 0) {
            first = Math.min(first, candles.t[0]);
            last = Math.max(last, candles.t[candles.size() - 1]);
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.size() == 0) continue;
            first = Math.min(first, line.t[0]);
            last = Math.max(last, line.t[line.size() - 1]);
        }
        if (stack != null && stack.size() > 0) {
            first = Math.min(first, stack.t[0]);
            last = Math.max(last, stack.t[stack.size() - 1]);
        }
        hasData = first <= last;
        dataT0 = first;
        dataT1 = last;
    }

    private Series primary() {
        if (candles != null && candles.size() > 0) return candles;
        if (stack != null && stack.size() > 0) return stack;
        int target = hoveredLine >= 0 ? hoveredLine : highlight;
        if (target >= 0 && target < lines.size() && lines.get(target).visible) return lines.get(target);
        Series longest = null;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.visible && line.size() > 0 && (longest == null || line.size() > longest.size())) longest = line;
        }
        return longest;
    }

    /**
     * Fits the time axis to the data, so however short the history is it spans the plot, and sets how far the
     * view may ever reach: the data's extent and a little room either side.
     */
    private void fitView(int mainH, int plotW) {
        if (!hasData) return;
        double bucket = bucket();
        double span = dataT1 - dataT0;
        double before, after;
        if (bucket > 0) {
            // A candle is centered on its start, so half a bucket either side is the candle itself.
            before = bucket * 0.75;
            after = bucket * 1.25;
        } else {
            before = after = span > 0 ? span * 0.02 : 1800;
        }
        limitT0 = dataT0 - before;
        limitT1 = dataT1 + after;
        // Room past the newest point for the icons at the ends of lines.
        boolean icons = false;
        for (int i = 0; i < lines.size(); i++) icons |= lines.get(i).icon != null || lines.get(i).head != null;
        if (icons) limitT1 += (limitT1 - limitT0) * 14 / plotW;
        if (fitX) {
            t0 = limitT0;
            t1 = limitT1;
            if (bucket > 0 && (t1 - t0) / bucket > defaultSlots) t0 = t1 - defaultSlots * bucket;
        } else {
            clampTime();
        }
        if (!Double.isNaN(fixedMin)) {
            if (fitY) {
                v0 = fixedMin;
                v1 = fixedMax;
            }
            return;
        }
        if (!fitY) return;
        if (stack != null) {
            v0 = 0;
            v1 = 1;
            return;
        }
        range[0] = Double.POSITIVE_INFINITY;
        range[1] = Double.NEGATIVE_INFINITY;
        if (candles != null) candles.range(t0, t1, range);
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).visible) lines.get(i).range(t0, t1, range);
        if (range[0] > range[1]) {
            // Nothing in view: hold the whole data's range so the axis still reads sensibly.
            if (candles != null) candles.range(dataT0, dataT1, range);
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).visible) lines.get(i).range(dataT0, dataT1, range);
            if (range[0] > range[1]) {
                range[0] = 0;
                range[1] = 1;
            }
        }
        double low = range[0], high = range[1];
        // A level close to the data is brought into view; one far away is tagged at the edge instead.
        double reach = Math.max(high - low, Math.abs(high) * 0.02) * 0.3;
        double dataLow = low, dataHigh = high;
        for (int i = 0; i < refs.size(); i++) {
            double value = refs.get(i).value();
            if (value > dataHigh && value - dataHigh <= reach) high = Math.max(high, value);
            if (value < dataLow && dataLow - value <= reach) low = Math.min(low, value);
        }
        if (log) {
            if (high <= 0) high = 1;
            if (low <= 0) low = high / 1000;
            double f0 = Math.log(low), f1 = Math.log(high);
            double fspan = f1 - f0;
            if (fspan < 1e-9) fspan = 0.02;
            f1 += fspan * 16 / Math.max(30, mainH - 24);
            f0 -= fspan * 8 / Math.max(30, mainH - 24);
            v0 = Math.exp(f0);
            v1 = Math.exp(f1);
        } else {
            double height = high - low;
            if (height < 1e-9) height = Math.max(Math.abs(high) * 0.02, 1e-6);
            v1 = high + height * 16 / Math.max(30, mainH - 24);
            v0 = low - height * 8 / Math.max(30, mainH - 24);
            if (low >= 0 && v0 < 0) v0 = 0;
            if (high == low) {
                v0 = low - height;
                v1 = high + height;
            }
        }
    }

    private void valueTicks(int mainH) {
        if (v0 == ticksV0 && v1 == ticksV1 && mainH == ticksH && log == ticksLog && format == ticksFormat) return;
        ticksV0 = v0;
        ticksV1 = v1;
        ticksH = mainH;
        ticksLog = log;
        ticksFormat = format;
        tickCount = 0;
        if (!hasData || !(v1 > v0)) return;
        double unit = format.unit();
        double low = v0 / unit, high = v1 / unit;
        if (log && low > 0 && Math.log10(high / low) >= 1.3) {
            double pixelsPerLn = (mainH - 1) / (Math.log(high) - Math.log(low));
            double lastLn = Double.NEGATIVE_INFINITY;
            int[] mantissas = {1, 2, 5};
            for (int power = (int) Math.floor(Math.log10(low)); power <= Math.ceil(Math.log10(high)); power++) {
                for (int mantissa : mantissas) {
                    double value = mantissa * Math.pow(10, power);
                    if (value < low || value > high || tickCount >= MAX_TICKS) continue;
                    double ln = Math.log(value);
                    if ((ln - lastLn) * pixelsPerLn < 16) continue;
                    lastLn = ln;
                    tickValues[tickCount] = value * unit;
                    tickLabels[tickCount++] = format.axis(value * unit, value * unit);
                }
            }
            tickStep = 0;
            return;
        }
        double rough = (high - low) / Math.max(2, mainH / 26.0);
        double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
        double fraction = rough / magnitude;
        double step = (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10)
                * magnitude;
        if (invert) step = Math.max(1, Math.ceil(step));
        tickStep = step * unit;
        for (double value = Math.ceil(low / step - 1e-9) * step; value <= high + step * 1e-9
                && tickCount < MAX_TICKS; value += step) {
            double raw = value * unit;
            tickValues[tickCount] = raw;
            tickLabels[tickCount++] = format.axis(raw, tickStep);
        }
    }

    private void grid(Ui ui) {
        for (int i = 0; i < tickCount; i++) {
            int gy = main.iy(tickValues[i]);
            if (gy > main.y + 1 && gy < main.y + main.h - 1) ui.hline(main.x, gy, main.w, Theme.GRID);
        }
        int bottom = sub.h > 0 ? sub.y + sub.h : main.y + main.h;
        for (int i = 0; i < timeTicks.count; i++) {
            int gx = main.ix(timeTicks.times[i]);
            if (gx > main.x && gx < main.x + main.w - 1) {
                ui.vline(gx, main.y, bottom - main.y, timeTicks.strong[i] ? Theme.GRID_STRONG : Theme.GRID);
            }
        }
    }

    private void series(Ui ui, boolean over, int mouseX, int mouseY) {
        hoveredLine = -1;
        if (stack != null) {
            stack.hovered = over && mode == Mode.NONE && mouseY < main.y + main.h ? stack.layerAt(main, mouseX, mouseY)
                    : -1;
            stack.draw(main, 0, false);
        }
        if (over && mode == Mode.NONE && tool == Tool.CURSOR && lines.size() > 1 && mouseY < main.y + main.h) {
            double best = 5.5;
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (!line.visible) continue;
                double distance = Math.abs(line.yAt(main, mouseX) - mouseY);
                if (distance < best) {
                    best = distance;
                    hoveredLine = i;
                }
            }
        }
        placeIcons();
        if (over && mode == Mode.NONE && tool == Tool.CURSOR) {
            int icon = iconAt(mouseX, mouseY);
            if (icon >= 0) hoveredLine = icon;
        }
        int target = hoveredLine >= 0 ? hoveredLine : highlight;
        if (target >= lines.size() || target >= 0 && !lines.get(target).visible) target = -1;
        // Last to first: a chart lists its leading line first, and that one belongs on top.
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (!line.visible || i == target) continue;
            line.draw(main, target >= 0 ? Theme.mix(Theme.BG, line.color, 0.32F) : line.color, false);
        }
        if (candles != null) candles.draw(main, 0, false);
        if (target >= 0) lines.get(target).draw(main, lines.get(target).color, true);
    }

    private void references() {
        for (int i = 0; i < refs.size(); i++) {
            Ref ref = refs.get(i);
            int ry = main.iy(ref.value());
            if (ry >= main.y && ry < main.y + main.h) main.dashedRow(ry, 3, 3, Theme.mix(Theme.BG, ref.color(), 0.75F));
        }
    }

    private void histogramPane(Ui ui) {
        ui.hline(main.x, sub.y - 1, main.w, Theme.BORDER);
        sub.f0 = 0;
        sub.f1 = histogramTop * 1.12;
        ui.clip(sub.x, sub.y, sub.w, sub.h);
        histogram.draw(sub, histogram.color, false);
        ui.unclip();
    }

    /**
     * Where each line's icon or head goes: beside the last point of the line that is inside the plot, so it stays on
     * the line however the chart is panned or zoomed. Icons may cover one another.
     */
    private void placeIcons() {
        if (iconX.length < lines.size()) {
            iconX = new int[lines.size()];
            iconY = new int[lines.size()];
        }
        int right = main.x + main.w - 1, bottom = main.y + main.h - 1;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            iconX[i] = Integer.MIN_VALUE;
            if (!line.visible || line.icon == null && line.head == null || line.size() == 0) continue;
            int from = (int) Math.min(right, Math.floor(main.px(line.t[line.size() - 1])));
            for (int column = from; column >= main.x; column--) {
                double at = line.yAt(main, column);
                if (Double.isNaN(at) || at < main.y || at > bottom) continue;
                iconX[i] = Math.min(column + 3, right - 8);
                iconY[i] = Math.clamp((int) Math.round(at) - 4, main.y, bottom - 7);
                break;
            }
        }
    }

    /** The line whose icon the mouse is on, the nearest if several overlap, or -1. */
    private int iconAt(int mouseX, int mouseY) {
        int found = -1, best = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            if (iconX[i] == Integer.MIN_VALUE || mouseX < iconX[i] - 1 || mouseX > iconX[i] + 8
                    || mouseY < iconY[i] - 1 || mouseY > iconY[i] + 8) continue;
            int distance = Math.abs(mouseX - iconX[i] - 4) + Math.abs(mouseY - iconY[i] - 4);
            if (distance < best) {
                best = distance;
                found = i;
            }
        }
        return found;
    }

    /** Where a line's icon is, in terminal pixels, for tests, or null when it has none on screen. */
    public int[] icon(int line) {
        return line >= lines.size() || iconX[line] == Integer.MIN_VALUE ? null : new int[] {iconX[line], iconY[line]};
    }

    /** Item icons or player heads at the ends of lines, the emphasized line's on top. */
    private void endIcons(Ui ui) {
        // Lowest on the chart first, so where heads overlap the line on top keeps its head on top.
        int target = hoveredLine >= 0 ? hoveredLine : highlight;
        int drawn = Integer.MAX_VALUE, drawnIndex = -1;
        for (;;) {
            int next = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (i == target || iconX[i] == Integer.MIN_VALUE) continue;
                boolean after = iconY[i] < drawn || iconY[i] == drawn && i > drawnIndex;
                if (after && (next < 0 || iconY[i] > iconY[next])) next = i;
            }
            if (next < 0) break;
            endIcon(ui, next);
            drawn = iconY[next];
            drawnIndex = next;
        }
        if (target >= 0 && target < lines.size()) endIcon(ui, target);
    }

    private void endIcon(Ui ui, int index) {
        if (iconX[index] == Integer.MIN_VALUE) return;
        Line line = lines.get(index);
        if (line.head != null) ui.head(line.head, iconX[index], iconY[index], 8);
        else ui.item(line.icon, iconX[index], iconY[index], 0.5F);
        if (index == 0) ui.mark("endicon:first", iconX[index] + 4, iconY[index] + 4);
    }

    private void axisLabels(Ui ui) {
        for (int i = 0; i < tickCount; i++) {
            int ly = main.iy(tickValues[i]);
            if (ly < main.y + 4 || ly > main.y + main.h - 4) continue;
            boolean covered = false;
            for (int j = 0; j < tags; j++) covered |= Math.abs(tagY[j] - ly) < 10;
            if (!covered) ui.text(tickLabels[i], axisX + 4, ly - 3, Theme.MUTED);
        }
        int lastRight = Integer.MIN_VALUE;
        for (int i = 0; i < timeTicks.count; i++) {
            int lx = main.ix(timeTicks.times[i]);
            int half = ui.width(timeTicks.labels[i]) / 2;
            if (lx - half < main.x + 1 || lx + half > main.x + main.w - 1 || lx - half < lastRight + 6) continue;
            ui.text(timeTicks.labels[i], lx - half, plotBottom + 3, timeTicks.strong[i] ? Theme.TEXT : Theme.MUTED);
            lastRight = lx + half;
        }
        if (sub.h > 0) ui.text(histogramLabel, axisX + 4, sub.y + 2, Theme.FAINT);
    }

    private void tag(double value, int color, boolean filled, String label) {
        if (tags >= tagY.length) return;
        tagValue[tags] = value;
        tagColor[tags] = color;
        tagFilled[tags] = filled;
        tagText[tags] = label;
        tags++;
    }

    /** The newest candle that traded, whose direction colors the last price. */
    private int lastTraded() {
        int last = candles.size() - 1;
        while (last > 0 && candles.empty != null && candles.empty[last]) last--;
        return last;
    }

    /** What gets tagged on the value axis: levels as named, outlined tags; last values filled in their color. */
    private void collectTags() {
        tags = 0;
        if (!hasData) return;
        double step = tickStep > 0 ? tickStep / 10 : 0;
        for (int i = 0; i < refs.size(); i++) {
            Ref ref = refs.get(i);
            tag(ref.value(), ref.color(), false, ref.label() + " " + format.axis(ref.value(), step(step, ref.value())));
        }
        if (tagLines && stack == null) {
            int target = hoveredLine >= 0 ? hoveredLine : highlight;
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (!line.visible || line.size() == 0 || target >= 0 && target != i) continue;
                double last = lastValue(line);
                if (!Double.isNaN(last)) tag(last, line.color, true, format.axis(last, step(step, last)));
            }
        }
        if (candles != null && candles.size() > 0) {
            int last = candles.size() - 1, traded = lastTraded();
            tag(candles.c[last], candles.c[traded] >= candles.o[traded] ? Theme.UP : Theme.DOWN, true,
                    format.axis(candles.c[last], step(step, candles.c[last])));
        }
    }

    /** Puts each tag at its value, marks the ones off the top or bottom, and moves them apart. */
    private void placeTags() {
        for (int i = 0; i < tags; i++) {
            int ty = main.iy(tagValue[i]);
            tagAway[i] = ty < main.y ? -1 : ty > main.y + main.h - 1 ? 1 : 0;
            tagY[i] = Math.clamp(ty, main.y + 5, main.y + main.h - 6);
            tagOrder[i] = i;
        }
        for (int i = 1; i < tags; i++) {
            for (int j = i; j > 0 && tagY[tagOrder[j]] < tagY[tagOrder[j - 1]]; j--) {
                int swap = tagOrder[j];
                tagOrder[j] = tagOrder[j - 1];
                tagOrder[j - 1] = swap;
            }
        }
        for (int i = 1; i < tags; i++) {
            tagY[tagOrder[i]] = Math.max(tagY[tagOrder[i]], tagY[tagOrder[i - 1]] + 11);
        }
        int floor = main.y + main.h - 6;
        for (int i = tags - 1; i >= 0; i--) {
            tagY[tagOrder[i]] = Math.min(tagY[tagOrder[i]], floor);
            floor = tagY[tagOrder[i]] - 11;
        }
    }

    private void drawTags(Ui ui) {
        if (candles != null && candles.size() > 0) {
            int last = candles.size() - 1, traded = lastTraded();
            int tone = candles.c[traded] >= candles.o[traded] ? Theme.UP : Theme.DOWN;
            int ly = main.iy(candles.c[last]);
            if (ly >= main.y && ly < main.y + main.h) main.dashedRow(ly, 1, 2, Theme.mix(Theme.BG, tone, 0.6F));
        }
        for (int i = 0; i < tags; i++) {
            int ty = tagY[i] - 5;
            if (tagFilled[i]) {
                ui.fill(axisX + 1, ty, axisW - 1, 11, tagColor[i]);
                ui.text(tagText[i], axisX + 4, ty + 2, Theme.BG);
            } else {
                ui.box(axisX + 1, ty, axisW - 1, 11, Theme.BG, Theme.mix(Theme.BG, tagColor[i], 0.8F));
                ui.text(tagText[i], axisX + 4, ty + 2, tagColor[i]);
            }
            if (tagAway[i] != 0) {
                // A small arrow says the value is past this edge of the chart.
                int ax = axisX + axisW - 7, ay = ty + (tagAway[i] < 0 ? 4 : 6), color = tagFilled[i] ? Theme.BG
                        : tagColor[i];
                ui.fill(ax + 2, ay, 1, 1, color);
                ui.fill(ax + 1, ay - tagAway[i], 3, 1, color);
                ui.fill(ax, ay - 2 * tagAway[i], 5, 1, color);
            }
        }
    }

    private static double lastValue(Line line) {
        for (int i = line.size() - 1; i >= 0; i--) if (!Double.isNaN(line.v[i])) return line.v[i];
        return Double.NaN;
    }

    private void crosshair(Ui ui, int cx, int cy, double time) {
        int bottom = sub.h > 0 ? sub.y + sub.h : main.y + main.h;
        if (cx >= main.x && cx < main.x + main.w) {
            for (int dy = main.y; dy < bottom; dy += 4) ui.vline(cx, dy, Math.min(2, bottom - dy), Theme.FAINT);
            if (time != crossTimeFor) {
                crossTimeFor = time;
                crossTime = bucket() >= 86400 ? Fmt.dayYear((long) time) : Fmt.dayClock((long) time);
            }
            int half = ui.width(crossTime) / 2 + 3;
            int left = Math.clamp(cx - half, x + 1, Math.max(x + 1, x + w - 1 - half * 2));
            ui.fill(left, plotBottom + 1, half * 2, TIME_AXIS - 1, Theme.BORDER_STRONG);
            ui.text(crossTime, left + 3, plotBottom + 3, Theme.TEXT);
        }
        if (cy >= main.y && cy < main.y + main.h) {
            for (int dx = main.x; dx < main.x + main.w; dx += 4) {
                ui.hline(dx, cy, Math.min(2, main.x + main.w - dx), Theme.FAINT);
            }
            double value = main.valueAt(cy);
            String label = format.axis(invert ? Math.rint(value) : value, tickStep > 0 ? tickStep / 10
                    : Math.abs(value) / 100);
            int ty = Math.clamp(cy - 5, main.y, main.y + main.h - 11);
            ui.fill(axisX + 1, ty, axisW - 1, 11, Theme.BORDER_STRONG);
            ui.text(ui.fit(label, axisW - 4), axisX + 4, ty + 2, Theme.TEXT);
        }
    }

    /**
     * The read-out along the top of the plot: the candle or the points under the crosshair, or the latest, and
     * what each marked level is.
     */
    private void legend(Ui ui, Series primary, int index) {
        if (primary == null) return;
        int at = index >= 0 ? index : primary.size() - 1;
        Object version = primary.t;
        if (at != legendIndex || version != legendFor || legendRefs != refs.hashCode()) {
            legendIndex = at;
            legendFor = version;
            legendRefs = refs.hashCode();
            legendParts.clear();
            legendColors.clear();
            legendLabels.clear();
            if (titleInLegend && !title.isEmpty()) part(title, Theme.TEXT);
            double step = tickStep > 0 ? tickStep / 10 : 0;
            if (candles != null && candles.size() > 0) {
                if (candles.empty != null && candles.empty[at]) {
                    part("No trades", Theme.FAINT);
                    label("C", Theme.FAINT);
                    part(format.axis(candles.c[at], step(step, candles.c[at])), Theme.MUTED);
                } else {
                    int tone = candles.c[at] >= candles.o[at] ? Theme.UP : Theme.DOWN;
                    label("O", Theme.FAINT);
                    part(format.axis(candles.o[at], step(step, candles.o[at])), tone);
                    label("H", Theme.FAINT);
                    part(format.axis(candles.h[at], step(step, candles.h[at])), tone);
                    label("L", Theme.FAINT);
                    part(format.axis(candles.l[at], step(step, candles.l[at])), tone);
                    label("C", Theme.FAINT);
                    part(format.axis(candles.c[at], step(step, candles.c[at])), tone);
                    if (candles.o[at] != 0) part(Fmt.pct(candles.c[at] / candles.o[at] - 1), tone);
                }
            }
            if (legendLines && stack == null) {
                double time = primary.t[at];
                for (int i = 0; i < lines.size(); i++) {
                    Line line = lines.get(i);
                    if (!line.visible || line.size() == 0) continue;
                    int near = line.nearest(time);
                    double value = Math.abs(line.t[near] - time) <= Math.max(bucket(), 1) * 1.01 || line == primary
                            ? line.v[near] : Double.NaN;
                    label(line.name, Theme.mix(Theme.BG, line.color, 0.8F));
                    part(Double.isNaN(value) ? "-" : format.axis(value, step(step, value)), line.color);
                }
            }
            if (histogram != null && histogram.size() > 0) {
                int near = histogram.nearest(primary.t[at]);
                if (Math.abs(histogram.t[near] - primary.t[at]) <= Math.max(bucket(), 1) * 0.51) {
                    label(histogram.name, Theme.FAINT);
                    part(histogramFormat.format(histogram.v[near]), Theme.MUTED);
                }
            }
            for (int i = 0; i < refs.size(); i++) {
                Ref ref = refs.get(i);
                label(ref.label(), Theme.mix(Theme.BG, ref.color(), 0.8F));
                part(format.axis(ref.value(), step(step, ref.value())), ref.color());
            }
        }
        int lx = main.x + 4, right = main.x + main.w - 4;
        for (int i = 0; i < legendParts.size(); i++) {
            String part = legendParts.get(i);
            int width = ui.width(part);
            if (lx + width > right) break;
            ui.text(part, lx, main.y + 3, legendColors.get(i));
            lx += width + (legendLabels.get(i) ? 3 : 6);
        }
    }

    private static double step(double step, double value) {
        return step > 0 ? step : Math.abs(value) / 100;
    }

    private void part(String text, int color) {
        legendParts.add(text);
        legendColors.add(color);
        legendLabels.add(false);
    }

    /** A legend label, drawn close to the value that follows it. */
    private void label(String text, int color) {
        part(text, color);
        legendLabels.set(legendLabels.size() - 1, true);
    }

    // Header

    /** The title, the view's controls, and the same six buttons on every chart. */
    private void header(Ui ui) {
        int by = y + 1;
        if (body != null) {
            // No plot, so no tools: only what the chart shows and the view's controls.
            if (controls != null) controls.draw(ui, x + 1, by, x + w - 2);
            else if (!title.isEmpty()) ui.text(title, x + 5, by + 3, Theme.TEXT);
            return;
        }
        int count = canLog ? 6 : 5;
        int bx = x + w - 1 - count * 13;
        int left = x + 1, room = bx - 3 - left;
        ui.vline(bx - 1, by, 13, Theme.BORDER);
        // The title leads if it fits beside the controls; otherwise it starts the legend.
        int titleW = title.isEmpty() ? 0 : Math.max(ui.width(title) + 10, titleSlot);
        titleInLegend = titleW + controlsWidth > room;
        if (!titleInLegend && titleW > 0) {
            ui.text(title, left + 4, by + 3, Theme.TEXT);
            left += titleW;
        }
        if (controls != null && controlsWidth <= room) controls.draw(ui, left, by, bx - 3);

        bx = toolButton(ui, Tool.CURSOR, bx, by, true);
        bx = toolButton(ui, Tool.ZOOM, bx, by, true);
        boolean open = ui.openMenu == drawMenu;
        buttons.put("draw", new int[] {bx + 6, by + 6});
        int drawX = bx;
        ui.iconButton(tool.draws() ? tool.icon : Icon.PENCIL, bx, by, 13, 13, open || tool.draws(), true,
                "Drawing tools", () -> ui.openMenu = open ? null : drawMenu);
        bx += 13;
        if (canLog) {
            buttons.put("log", new int[] {bx + 6, by + 6});
            ui.iconButton(Icon.LOG, bx, by, log, "Log scale", () -> {
                log = !log;
                fitY = true;
            });
            bx += 13;
        }
        buttons.put("reset", new int[] {bx + 6, by + 6});
        ui.iconButton(Icon.RESET, bx, by, 13, 13, false, !fitted(), "Reset zoom", this::fit);
        bx += 13;
        buttons.put("maximize", new int[] {bx + 6, by + 6});
        boolean big = maximized == this;
        ui.iconButton(big ? Icon.RESTORE : Icon.MAXIMIZE, bx, by, 13, 13, big, true, big ? "Restore" : "Maximize",
                () -> maximized = big ? null : this);
        if (open) ui.later(() -> drawPopover(ui, drawX, by + 14));
    }

    private int toolButton(Ui ui, Tool which, int bx, int by, boolean toggles) {
        toolX[which.ordinal()] = bx + 6;
        toolYs[which.ordinal()] = by + 6;
        ui.iconButton(which.icon, bx, by, tool == which, which.label, () -> {
            tool(toggles && tool == which ? Tool.CURSOR : which);
            ui.openMenu = null;
        });
        return bx + 13;
    }

    /** The drawing tools, the colors, and the eraser and clear-all, under the pencil button. */
    private void drawPopover(Ui ui, int px, int py) {
        int pw = 6 * 13 + 2, ph = 3 * 13 + 4;
        px = Math.min(px, ui.width - pw - 2);
        ui.closer(() -> ui.openMenu = null);
        ui.popup(px, py, pw, ph, true);
        int bx = px + 1, by = py + 1;
        for (Tool which : DRAW_TOOLS) bx = toolButton(ui, which, bx, by, false);
        ui.hline(px + 1, by + 13, pw - 2, Theme.BORDER);
        by += 14;
        for (int i = 0; i < DRAW_COLORS.length; i++) {
            int index = i, cx = px + 1 + i * 13;
            boolean over = ui.click(cx, by, 13, 13, () -> {
                drawColor = index;
                if (selected != null) {
                    selected.color = DRAW_COLORS[index];
                    Drawings.save();
                }
            });
            if (over) ui.fill(cx, by, 13, 13, Theme.HOVER);
            if (i == drawColor) ui.outline(cx + 1, by + 1, 11, 11, Theme.ACCENT);
            ui.fill(cx + 3, by + 3, 7, 7, DRAW_COLORS[i]);
            if (i == 1) buttons.put("color", new int[] {cx + 6, by + 6});
        }
        ui.hline(px + 1, by + 13, pw - 2, Theme.BORDER);
        by += 14;
        bx = toolButton(ui, Tool.ERASER, px + 1, by, false);
        boolean any = !drawings().isEmpty();
        buttons.put("trash", new int[] {bx + 6, by + 6});
        ui.iconButton(Icon.TRASH, bx, by, 13, 13, false, any, selected != null ? "Delete selected drawing"
                : "Clear all drawings", () -> {
            if (selected != null) drawings().remove(selected);
            else drawings().clear();
            selected = null;
            Drawings.save();
        });
    }

    // Drawings

    private Drawing drawings(Ui ui, boolean over, int mouseX, int mouseY) {
        if (key == null) return null;
        List<Drawing> list = drawings();
        Drawing near = over && mode == Mode.NONE && (tool == Tool.ERASER || tool == Tool.CURSOR)
                ? drawingAt(mouseX, mouseY) : null;
        for (int i = 0; i < list.size(); i++) {
            Drawing drawing = list.get(i);
            draw(drawing, drawing == near && drawing != selected);
        }
        if (draft != null) draw(draft, false);
        if (selected != null && list.contains(selected)) selection(selected);
        else selected = null;
        return near != null ? near : over && selected != null && handleAt(mouseX, mouseY) >= 0 ? selected : null;
    }

    private void draw(Drawing drawing, boolean emphasized) {
        int color = drawing.color;
        int thickness = emphasized ? 2 : 1;
        switch (drawing.kind) {
            case HLINE -> main.row(main.x, main.x + main.w - 1, main.iy(drawing.v[0]), thickness, color);
            case RECT -> {
                if (drawing.t.length < 2) return;
                int ax = main.ix(drawing.t[0]), bx = main.ix(drawing.t[1]);
                int ay = main.iy(drawing.v[0]), by = main.iy(drawing.v[1]);
                int left = Math.min(ax, bx), right = Math.max(ax, bx), top = Math.min(ay, by),
                        bottom = Math.max(ay, by);
                int from = Math.max(top + 1, main.y), to = Math.min(bottom, main.y + main.h);
                if (to > from) main.row(left + 1, right - 1, from, to - from, Theme.alpha(color, 0x22));
                main.row(left, right, top, thickness, color);
                main.row(left, right, bottom, thickness, color);
                main.col(left, top, bottom, thickness, color);
                main.col(right, top, bottom, thickness, color);
            }
            default -> {
                for (int i = 1; i < drawing.t.length; i++) {
                    main.line(main.px(drawing.t[i - 1]), main.py(drawing.v[i - 1]), main.px(drawing.t[i]),
                            main.py(drawing.v[i]), color, thickness);
                }
                if (drawing.kind == Kind.ARROW && drawing.t.length == 2) arrowHead(drawing, color, thickness);
            }
        }
    }

    /** The selected drawing's pixel box: left, top, right, bottom. */
    private int[] box(Drawing drawing) {
        if (drawing.kind == Kind.HLINE) {
            int ly = main.iy(drawing.v[0]);
            return new int[] {main.x + 2, ly, main.x + main.w - 3, ly};
        }
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int i = 0; i < drawing.t.length; i++) {
            int px = main.ix(drawing.t[i]), py = main.iy(drawing.v[i]);
            left = Math.min(left, px);
            right = Math.max(right, px);
            top = Math.min(top, py);
            bottom = Math.max(bottom, py);
        }
        return new int[] {left, top, right, bottom};
    }

    /** A dashed box around the selected drawing, with a handle on each point that can be dragged. */
    private void selection(Drawing drawing) {
        int[] box = box(drawing);
        int left = box[0] - 4, top = box[1] - 4, right = box[2] + 4, bottom = box[3] + 4;
        int color = Theme.ACCENT;
        for (int cx = left; cx <= right; cx += 4) {
            main.row(cx, Math.min(cx + 1, right), top, color);
            main.row(cx, Math.min(cx + 1, right), bottom, color);
        }
        for (int cy = top; cy <= bottom; cy += 4) {
            main.col(left, cy, Math.min(cy + 1, bottom), color);
            main.col(right, cy, Math.min(cy + 1, bottom), color);
        }
        if (drawing.kind == Kind.PENCIL) {
            int[][] corners = {{left, top}, {right, top}, {left, bottom}, {right, bottom}};
            for (int[] corner : corners) handle(corner[0], corner[1]);
        } else if (drawing.kind == Kind.HLINE) {
            handle(main.x + main.w / 2, box[1]);
        } else {
            for (int i = 0; i < drawing.t.length; i++) handle(main.ix(drawing.t[i]), main.iy(drawing.v[i]));
        }
    }

    private void handle(int cx, int cy) {
        main.dot(cx, cy, 5, Theme.ACCENT);
        main.dot(cx, cy, 3, Theme.TEXT);
    }

    /** The point of the selected drawing whose handle is at a pixel, or -1. */
    private int handleAt(int px, int py) {
        if (selected == null || selected.kind == Kind.PENCIL || selected.kind == Kind.HLINE) return -1;
        for (int i = 0; i < selected.t.length; i++) {
            if (Math.abs(main.px(selected.t[i]) - px) <= 3 && Math.abs(main.py(selected.v[i]) - py) <= 3) return i;
        }
        return -1;
    }

    private void arrowHead(Drawing drawing, int color, int thickness) {
        double ax = main.px(drawing.t[0]), ay = main.py(drawing.v[0]);
        double bx = main.px(drawing.t[1]), by = main.py(drawing.v[1]);
        double length = Math.hypot(bx - ax, by - ay);
        if (length < 3) return;
        double ux = (ax - bx) / length, uy = (ay - by) / length;
        for (int side = -1; side <= 1; side += 2) {
            double angle = side * 0.45;
            double rx = ux * Math.cos(angle) - uy * Math.sin(angle), ry = ux * Math.sin(angle) + uy * Math.cos(angle);
            main.line(bx, by, bx + rx * 7, by + ry * 7, color, thickness);
        }
    }

    /** The drawing within a few pixels of a point, the newest first, or null. */
    private Drawing drawingAt(int px, int py) {
        if (key == null) return null;
        List<Drawing> list = drawings();
        for (int i = list.size() - 1; i >= 0; i--) {
            Drawing drawing = list.get(i);
            switch (drawing.kind) {
                case HLINE -> {
                    if (Math.abs(main.py(drawing.v[0]) - py) <= 3) return drawing;
                }
                case RECT -> {
                    double ax = main.px(drawing.t[0]), bx = main.px(drawing.t[1]);
                    double ay = main.py(drawing.v[0]), by = main.py(drawing.v[1]);
                    double left = Math.min(ax, bx), right = Math.max(ax, bx), top = Math.min(ay, by),
                            bottom = Math.max(ay, by);
                    if (px >= left - 3 && px <= right + 3 && py >= top - 3 && py <= bottom + 3) return drawing;
                }
                default -> {
                    for (int j = 1; j < drawing.t.length; j++) {
                        if (distance(px, py, main.px(drawing.t[j - 1]), main.py(drawing.v[j - 1]),
                                main.px(drawing.t[j]), main.py(drawing.v[j])) <= 3.5) return drawing;
                    }
                }
            }
        }
        return null;
    }

    private static double distance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double share = lengthSquared == 0 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / lengthSquared, 0, 1);
        return Math.hypot(px - (ax + share * dx), py - (ay + share * dy));
    }

    // Mouse and keys

    private boolean inPlot(int px, int py) {
        return px >= main.x && px < main.x + main.w && py >= main.y && py < plotBottom;
    }

    @Override
    public boolean mouseDown(int px, int py, int button, boolean shift, boolean doubleClick) {
        if (!hasData) return false;
        pressX = dragX = px;
        pressY = dragY = py;
        moved = false;
        grabT0 = t0;
        grabT1 = t1;
        grabV0 = v0;
        grabV1 = v1;
        if (px >= axisX && py >= main.y && py < main.y + main.h) {
            if (doubleClick) {
                fitY = true;
                return true;
            }
            if (!Double.isNaN(fixedMin) || stack != null) return true;
            mode = Mode.SCALE;
            return true;
        }
        if (!inPlot(px, py)) {
            // The time axis pans like the plot.
            if (py >= plotBottom && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mode = Mode.PAN;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (shift || tool == Tool.ZOOM)) {
            mode = Mode.BOX;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            mode = Mode.PAN;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        double time = main.timeAt(px), value = main.valueAt(Math.clamp(py, main.y, main.y + main.h - 1));
        switch (tool) {
            case CURSOR -> {
                int grabbed = handleAt(px, py);
                Drawing hit = grabbed >= 0 ? selected : drawingAt(px, py);
                if (hit != null) {
                    // A drawing is picked up to move, or one of its points to reshape it.
                    selected = hit;
                    handle = grabbed;
                    grabT = hit.t.clone();
                    grabV = hit.v.clone();
                    mode = grabbed >= 0 ? Mode.RESHAPE : Mode.MOVE;
                } else if (doubleClick) {
                    selected = null;
                    fit();
                } else {
                    selected = null;
                    mode = Mode.PAN;
                }
            }
            case ERASER -> {
                Drawing hit = drawingAt(px, py);
                if (hit != null) {
                    drawings().remove(hit);
                    if (selected == hit) selected = null;
                    Drawings.save();
                }
            }
            case HLINE -> {
                Drawing line = start(Kind.HLINE, time, value);
                drawings().add(line);
                selected = line;
                Drawings.save();
                tool = Tool.CURSOR;
            }
            case PENCIL -> {
                draft = start(Kind.PENCIL, time, value);
                mode = Mode.DRAW;
            }
            case TREND, ARROW, RECT -> {
                draft = start(tool == Tool.TREND ? Kind.TREND : tool == Tool.ARROW ? Kind.ARROW : Kind.RECT, time,
                        value);
                draft.t = new double[] {time, time};
                draft.v = new double[] {value, value};
                mode = Mode.DRAW;
            }
            default -> {}
        }
        return true;
    }

    private static Drawing start(Kind kind, double time, double value) {
        Drawing drawing = new Drawing();
        drawing.kind = kind;
        drawing.color = DRAW_COLORS[drawColor];
        drawing.t = new double[] {time};
        drawing.v = new double[] {value};
        return drawing;
    }

    @Override
    public void mouseDrag(int px, int py) {
        dragX = px;
        dragY = py;
        if (Math.abs(px - pressX) > 2 || Math.abs(py - pressY) > 2) moved = true;
        switch (mode) {
            case PAN -> {
                if (!moved) return;
                double shift = (px - pressX) * (grabT1 - grabT0) / main.w;
                t0 = grabT0 - shift;
                t1 = grabT1 - shift;
                fitX = false;
                clampTime();
                if (!fitY && Double.isNaN(fixedMin) && stack == null) {
                    double f0 = main.transform(grabV0), f1 = main.transform(grabV1);
                    double lift = (py - pressY) * (f1 - f0) / (main.h - 1) * (invert ? -1 : 1);
                    v0 = main.untransform(f0 + lift);
                    v1 = main.untransform(f1 + lift);
                }
            }
            case SCALE -> {
                double f0 = main.transform(grabV0), f1 = main.transform(grabV1);
                double middle = (f0 + f1) / 2, half = (f1 - f0) / 2 * Math.exp((py - pressY) * 0.012);
                v0 = main.untransform(middle - half);
                v1 = main.untransform(middle + half);
                fitY = false;
            }
            case MOVE -> {
                if (!moved || selected == null) return;
                // Every point moves by the same pixels, so the shape holds on a log scale too.
                for (int i = 0; i < grabT.length; i++) {
                    if (selected.kind != Kind.HLINE) selected.t[i] = main.timeAt(main.px(grabT[i]) + px - pressX);
                    selected.v[i] = main.valueAt(main.py(grabV[i]) + py - pressY);
                }
            }
            case RESHAPE -> {
                if (!moved || selected == null) return;
                selected.t[handle] = main.timeAt(main.px(grabT[handle]) + px - pressX);
                selected.v[handle] = main.valueAt(main.py(grabV[handle]) + py - pressY);
            }
            case DRAW -> {
                if (draft == null) return;
                double time = main.timeAt(Math.clamp(px, main.x, main.x + main.w - 1));
                double value = main.valueAt(Math.clamp(py, main.y, main.y + main.h - 1));
                if (draft.kind == Kind.PENCIL) {
                    int n = draft.t.length;
                    double lastX = main.px(draft.t[n - 1]), lastY = main.py(draft.v[n - 1]);
                    if (n < 4000 && Math.hypot(px - lastX, py - lastY) >= 2) {
                        draft.t = java.util.Arrays.copyOf(draft.t, n + 1);
                        draft.v = java.util.Arrays.copyOf(draft.v, n + 1);
                        draft.t[n] = time;
                        draft.v[n] = value;
                    }
                } else {
                    draft.t[1] = time;
                    draft.v[1] = value;
                }
            }
            default -> {}
        }
    }

    @Override
    public void mouseUp(int px, int py) {
        Mode was = mode;
        mode = Mode.NONE;
        switch (was) {
            case PAN -> {
                if (!moved && tool == Tool.CURSOR && hoveredLine >= 0 && onLineClick != null
                        && hoveredLine < lines.size()) onLineClick.accept(lines.get(hoveredLine));
            }
            case MOVE, RESHAPE -> {
                if (moved) Drawings.save();
            }
            case BOX -> {
                int left = Math.max(main.x, Math.min(pressX, px)), right = Math.min(main.x + main.w - 1,
                        Math.max(pressX, px));
                int top = Math.max(main.y, Math.min(pressY, py)), bottom = Math.min(main.y + main.h - 1,
                        Math.max(pressY, py));
                if (right - left >= 4 && bottom - top >= 4) {
                    double a = main.timeAt(left), b = main.timeAt(right + 1);
                    double va = main.valueAt(top), vb = main.valueAt(bottom);
                    t0 = a;
                    t1 = b;
                    fitX = false;
                    clampTime();
                    if (Double.isNaN(fixedMin) && stack == null) {
                        v0 = Math.min(va, vb);
                        v1 = Math.max(va, vb);
                        fitY = false;
                    }
                }
                if (tool == Tool.ZOOM) tool = Tool.CURSOR;
            }
            case DRAW -> {
                Drawing done = draft;
                draft = null;
                if (done == null) return;
                boolean real = done.kind == Kind.PENCIL ? done.t.length >= 2 : moved;
                if (real) {
                    drawings().add(done);
                    selected = done.kind == Kind.PENCIL ? null : done;
                    Drawings.save();
                }
                if (tool != Tool.PENCIL) tool = Tool.CURSOR;
            }
            default -> {}
        }
    }

    @Override
    public boolean scroll(int px, int py, double dx, double dy) {
        if (!hasData) return false;
        if (px >= axisX) {
            if (!Double.isNaN(fixedMin) || stack != null || dy == 0) return true;
            double f0 = main.transform(v0), f1 = main.transform(v1);
            double anchor = main.transform(main.valueAt(Math.clamp(py, main.y, main.y + main.h - 1)));
            double factor = Math.exp(-Math.clamp(dy, -4, 4) * 0.18);
            v0 = main.untransform(anchor - (anchor - f0) * factor);
            v1 = main.untransform(anchor + (f1 - anchor) * factor);
            fitY = false;
            return true;
        }
        double span = t1 - t0;
        if (dy != 0) {
            // A trackpad sends many small steps and a wheel a few whole ones; both zoom by the same curve.
            double factor = Math.exp(-Math.clamp(dy, -4, 4) * 0.22);
            double anchor = main.timeAt(Math.clamp(px, main.x, main.x + main.w - 1));
            double next = Math.clamp(span * factor, minSpan(), limitT1 - limitT0);
            double share = (anchor - t0) / span;
            t0 = anchor - next * share;
            t1 = t0 + next;
        }
        if (dx != 0) {
            double shift = -dx * 24 * (t1 - t0) / main.w;
            t0 += shift;
            t1 += shift;
        }
        fitX = false;
        clampTime();
        return true;
    }

    private double minSpan() {
        double bucket = bucket();
        double least = bucket > 0 ? bucket * 6 : Math.max(60, (dataT1 - dataT0) / 400);
        return Math.min(least, limitT1 - limitT0);
    }

    /** Keeps the view no wider than the data's extent and inside it. */
    private void clampTime() {
        double span = Math.clamp(t1 - t0, minSpan(), limitT1 - limitT0);
        double start = Math.clamp(t0, limitT0, limitT1 - span);
        t0 = start;
        t1 = start + span;
    }

    /**
     * Escape restores a maximized chart or puts the tool down; Delete removes the selected drawing. Returns
     * whether the key was used.
     */
    public boolean key(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE && maximized == this) {
            maximized = null;
            return true;
        }
        if ((key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) && selected != null) {
            drawings().remove(selected);
            selected = null;
            Drawings.save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (tool != Tool.CURSOR || draft != null || selected != null)) {
            tool(Tool.CURSOR);
            selected = null;
            return true;
        }
        return false;
    }
}

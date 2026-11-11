package com.merkie.bageltv;

import com.merkie.bageltv.chart.Chart;
import com.merkie.bageltv.chart.Drawings;
import com.merkie.bageltv.ui.Route;
import com.merkie.bageltv.view.ItemView;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

import static com.merkie.bageltv.Harness.require;

/**
 * The chart engine on an item's 2,000 candles: wheel zoom around the cursor, small trackpad steps, drag to pan,
 * box zoom three ways, stretching the value axis, double click and Reset to fit, log scale, chart types, and the
 * drawing tools, whose drawings stay on their candles and are saved per chart.
 */
public final class ChartGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Harness.run(context, "ChartGameTest", harness -> {
            harness.open();
            harness.go(Route.item("diamond"));
            ItemView view = harness.view(ItemView.class);
            Chart chart = context.computeOnClient(client -> view.chart());
            int[] plot = context.computeOnClient(client -> chart.plot());
            int cx = plot[0] + plot[2] / 2, cy = plot[1] + plot[3] / 2;
            require(chart.candles != null && chart.candles.size() > 500, "The dense fixture has 500+ candles: "
                    + (chart.candles == null ? 0 : chart.candles.size()));
            require(chart.fitted(), "A new chart fits its data");
            double[] fit = view(context, chart);

            // Nothing zooms or pans out past the data.
            double[] data = context.computeOnClient(client -> chart.data());
            for (int i = 0; i < 12; i++) harness.scroll(cx, cy, -4);
            double[] widest = view(context, chart);
            double bucket = chart.candles.bucket;
            require(widest[0] >= data[0] - bucket && widest[1] <= data[1] + bucket * 1.3,
                    "Zooming out stops at the data's extent: " + Arrays.toString(widest));
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, cx, cy, cx + 300, cy);
            require(view(context, chart)[0] >= data[0] - bucket, "Panning stops at the first candle");
            harness.click(context.computeOnClient(client -> chart.button("reset")));
            context.waitTicks(8);

            // The wheel zooms time around the cursor.
            double under = timeAt(fit, plot, cx);
            harness.scroll(cx, cy, 3);
            double[] zoomed = view(context, chart);
            require(zoomed[1] - zoomed[0] < (fit[1] - fit[0]) * 0.6, "Scrolling up zooms in: " + Arrays.toString(zoomed));
            require(Math.abs(timeAt(zoomed, plot, cx) - under) < 3600, "The time under the cursor stays put");
            require(!chart.fitted(), "Zooming leaves the fitted view");
            // A trackpad's small steps zoom a little each.
            harness.scroll(cx, cy, -0.2);
            double[] nudged = view(context, chart);
            double ratio = (nudged[1] - nudged[0]) / (zoomed[1] - zoomed[0]);
            require(ratio > 1.01 && ratio < 1.2, "A small scroll step zooms a little: " + ratio);
            for (int i = 0; i < 6; i++) harness.scroll(cx, cy, 3);
            harness.move(cx + 40, cy - 10);
            harness.shot("chart-zoomed-in");
            double[] close = view(context, chart);
            require((close[1] - close[0]) / bucket <= 9, "It zooms in to a handful of candles: "
                    + (close[1] - close[0]) / bucket);
            for (int i = 0; i < 6; i++) harness.scroll(cx, cy, -3);
            nudged = view(context, chart);

            // Dragging pans.
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, cx, cy, cx + 80, cy);
            double[] panned = view(context, chart);
            require(panned[0] < nudged[0] && Math.abs((panned[1] - panned[0]) - (nudged[1] - nudged[0])) < 1,
                    "Dragging right shows earlier candles without zooming");
            // A double click fits again.
            harness.doubleClick(cx, cy);
            require(context.computeOnClient(client -> chart.fitted()), "A double click fits the data again");

            // Shift-drag zooms both axes to the box.
            context.getInput().holdShift();
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, cx - 60, cy - 30, cx + 60, cy + 30);
            context.getInput().releaseShift();
            double[] boxed = view(context, chart);
            require(Math.abs((boxed[1] - boxed[0]) - (fit[1] - fit[0]) * 121.0 / plot[2]) < (fit[1] - fit[0]) * 0.02,
                    "Shift-drag zooms time to the box: " + Arrays.toString(boxed));
            require(boxed[3] - boxed[2] < (fit[3] - fit[2]) * 0.7, "and the value axis with it");
            harness.click(context.computeOnClient(client -> chart.button("reset")));
            require(context.computeOnClient(client -> chart.fitted()), "Reset fits the data again");
            // So does a right-drag.
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_RIGHT, cx - 50, cy - 20, cx + 50, cy + 20);
            require(!context.computeOnClient(client -> chart.fitted()), "Right-drag zooms to a box");
            harness.click(context.computeOnClient(client -> chart.button("reset")));
            // And the zoom tool, which puts itself down after one box.
            harness.click(context.computeOnClient(client -> chart.button(Chart.Tool.ZOOM)));
            require(context.computeOnClient(client -> chart.tool()) == Chart.Tool.ZOOM, "The zoom tool is picked up");
            harness.move(cx - 70, cy - 25);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            harness.move(cx - 20, cy);
            harness.move(cx + 50, cy + 25);
            harness.shot("chart-box-zoom");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            require(!context.computeOnClient(client -> chart.fitted())
                    && context.computeOnClient(client -> chart.tool()) == Chart.Tool.CURSOR,
                    "The zoom tool zooms once and goes back to the cursor");
            harness.click(context.computeOnClient(client -> chart.button("reset")));

            // Dragging the value axis stretches it; a double click there lets it fit again.
            int[] axis = context.computeOnClient(client -> chart.axis());
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, axis[0], axis[1], axis[0], axis[1] + 40);
            double[] stretched = view(context, chart);
            require(stretched[3] - stretched[2] > (fit[3] - fit[2]) * 1.3, "Dragging the axis down widens the range");
            harness.doubleClick(axis[0], axis[1]);
            require(context.computeOnClient(client -> chart.fitted()), "A double click on the axis fits it");

            // Log scale, saved.
            harness.click(context.computeOnClient(client -> chart.button("log")));
            context.waitTicks(2);
            require(chart.log && BagelTV.config().itemLog, "The log button turns on log scale");
            harness.park();
            harness.shot("chart-log");
            harness.click(context.computeOnClient(client -> chart.button("log")));

            // Chart types: candles, line.
            harness.click(harness.mark("type:line"));
            harness.settle();
            require(BagelTV.config().itemChart.equals("line") && chart.candles == null && chart.lines.size() == 2,
                    "The line button draws the close as a line beside VWAP");
            harness.park();
            harness.shot("chart-line");
            harness.click(harness.mark("type:candles"));
            harness.settle();
            require(chart.candles != null, "The candles button goes back to candles");

            // Two markets, never one price: the auction house, filled buy orders, or both as two lines.
            require(view.market() == 0 && chart.title.startsWith("AH sales"),
                    "An item that mostly sells on the auction house opens on it: " + chart.title);
            int[] vwapAt = harness.mark("vwap");
            harness.click(harness.mark("segment:Orders"));
            harness.settle();
            require(view.market() == 1 && chart.candles != null && chart.title.startsWith("Filled orders")
                    && chart.refs.size() == 1 && chart.refs.getFirst().label().equals("Top order"),
                    "Orders draws candles of what filled orders paid and marks the top order: " + chart.refs);
            require(FakeApi.requests().stream().anyMatch(r -> r.path().contains("/candles?")
                    && r.path().contains("source=orders")), "Order candles are asked for by source");
            require(java.util.Arrays.equals(vwapAt, harness.mark("vwap")), "Switching markets moves no control");
            harness.park();
            harness.shot("chart-orders");
            harness.click(harness.mark("segment:Both"));
            harness.settle();
            require(view.market() == 2 && chart.candles == null && chart.lines.size() == 2
                    && chart.lines.get(0).name.equals("AH") && chart.lines.get(1).name.equals("Orders")
                    && chart.lines.get(0).color != chart.lines.get(1).color && chart.refs.size() == 2,
                    "Both draws the two markets as two lines with both levels");
            String kind = BagelTV.config().itemChart;
            harness.click(harness.mark("type:" + (kind.equals("line") ? "candles" : "line")));
            require(BagelTV.config().itemChart.equals(kind), "Chart types are off while both markets show");
            require(java.util.Arrays.equals(vwapAt, harness.mark("vwap")), "The controls still have not moved");
            harness.park();
            harness.shot("chart-both");
            harness.click(harness.mark("segment:AH"));
            harness.settle();
            require(view.market() == 0 && chart.candles != null, "AH goes back to auction candles");

            // Drawing tools.
            draw(harness, context, chart, Chart.Tool.TREND, cx - 120, cy + 30, cx - 20, cy - 20);
            draw(harness, context, chart, Chart.Tool.ARROW, cx - 10, cy - 40, cx + 50, cy - 10);
            draw(harness, context, chart, Chart.Tool.RECT, cx + 60, cy - 30, cx + 110, cy + 10);
            pick(harness, context, chart, Chart.Tool.HLINE);
            harness.click(cx, cy + 45);
            require(chart.drawings().getFirst().color == 0xFFFFFFFF, "Drawings are white unless a color is picked");
            harness.click(context.computeOnClient(client -> chart.button("draw")));
            context.waitTicks(2);
            harness.move(context.computeOnClient(client -> chart.button("color")));
            harness.shot("chart-draw-popover");
            harness.click(context.computeOnClient(client -> chart.button("color")));
            harness.click(context.computeOnClient(client -> chart.button(Chart.Tool.PENCIL)));
            harness.move(cx - 100, cy - 30);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            for (int i = 1; i <= 24; i++) {
                harness.move(cx - 100 + i * 4, cy - 30 - (int) Math.round(12 * Math.sin(i / 3.0)));
            }
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            require(context.computeOnClient(client -> chart.tool()) == Chart.Tool.PENCIL, "The pencil stays in hand");
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            require(context.computeOnClient(client -> chart.tool()) == Chart.Tool.CURSOR
                    && context.computeOnClient(client -> client.screen != null),
                    "Escape puts the tool down without closing the terminal");
            int count = context.computeOnClient(client -> chart.drawings().size());
            require(count == 5, "Five drawings: " + count);
            require(chart.drawings().get(4).t.length > 8 && chart.drawings().get(4).color != chart.drawings().get(0).color,
                    "The pencil stroke keeps its points and the color picked for it");
            harness.park();
            harness.shot("chart-drawings");
            String saved = Harness.read(Harness.CONFIG.resolve("bagel-tradingview-drawings.json"));
            require(saved.contains("\"item:diamond\"") && saved.contains("TREND") && saved.contains("PENCIL"),
                    "Drawings are saved under the chart's key: " + saved.length() + " characters");

            // Drawings are anchored to time and price, so zooming moves them with the candles.
            double anchor = chart.drawings().getFirst().t[0];
            harness.scroll(cx, cy, 2);
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, cx, cy + 60, cx + 40, cy + 60);
            require(chart.drawings().getFirst().t[0] == anchor, "Panning and zooming leave a drawing's anchors alone");
            harness.park();
            harness.shot("chart-drawings-zoomed");
            harness.click(context.computeOnClient(client -> chart.button("reset")));

            // They come back after closing the terminal and reading the file again.
            harness.key(GLFW.GLFW_KEY_B);
            context.waitFor(client -> client.screen == null);
            context.runOnClient(client -> Drawings.reload());
            harness.open();
            ItemView again = harness.view(ItemView.class);
            Chart reopened = context.computeOnClient(client -> again.chart());
            require(context.computeOnClient(client -> reopened.drawings().size()) == 5, "Drawings persist");

            // Clicking a drawing selects it; dragging it moves it; dragging a handle reshapes it.
            int[] box = context.computeOnClient(client -> reopened.plot());
            int mx = box[0] + box[2] / 2, my = box[1] + box[3] / 2;
            harness.click(mx + 85, my - 30);
            require(context.computeOnClient(client -> reopened.selected()) != null
                    && reopened.selected().kind == Drawings.Kind.RECT, "Clicking the rectangle selects it");
            harness.park();
            harness.shot("chart-drawing-selected");
            double[] before = reopened.selected().t.clone();
            double[] values = reopened.selected().v.clone();
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, mx + 85, my - 30, mx + 65, my - 10);
            require(reopened.selected().t[0] < before[0] && reopened.selected().t[1] < before[1]
                    && reopened.selected().v[0] < values[0], "Dragging a drawing moves all of it");
            double width = reopened.selected().t[1] - reopened.selected().t[0];
            require(Math.abs(width - (before[1] - before[0])) < 1, "and keeps its size");
            double corner = reopened.selected().t[1];
            harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, mx + 90, my + 30, mx + 120, my + 40);
            require(reopened.selected().t[1] > corner && Math.abs(reopened.selected().t[0] - (corner - width)) < 1,
                    "Dragging a handle moves that corner only");
            require(Harness.read(Harness.CONFIG.resolve("bagel-tradingview-drawings.json")).contains(
                    Double.toString(reopened.selected().t[1])), "A moved drawing is saved where it was put");
            harness.key(GLFW.GLFW_KEY_ESCAPE);

            // Maximize fills the view's area with the same chart, tools and drawings; Escape puts it back.
            int[] small = context.computeOnClient(client -> reopened.bounds());
            harness.click(context.computeOnClient(client -> reopened.button("maximize")));
            context.waitTicks(3);
            int[] big = context.computeOnClient(client -> reopened.bounds());
            require(reopened.maximized() && big[2] > small[2] + 150 && big[3] > small[3] + 100,
                    "Maximize fills the content area: " + Arrays.toString(big));
            draw(harness, context, reopened, Chart.Tool.ARROW, big[0] + 200, big[1] + 100, big[0] + 300, big[1] + 160);
            require(context.computeOnClient(client -> reopened.drawings().size()) == 6, "It draws while maximized");
            harness.park();
            harness.shot("chart-maximized");
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            harness.key(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(3);
            require(!reopened.maximized() && context.computeOnClient(client -> client.screen != null)
                    && Arrays.equals(context.computeOnClient(client -> reopened.bounds()), small),
                    "Escape puts the chart back without closing the terminal");
            context.runOnClient(client -> reopened.drawings().removeLast());

            // Select and Delete, the eraser, and clear all.
            harness.click(mx + 30, my + 45);
            harness.key(GLFW.GLFW_KEY_DELETE);
            require(context.computeOnClient(client -> reopened.drawings().size()) == 4,
                    "Delete removes the selected line");
            pick(harness, context, reopened, Chart.Tool.ERASER);
            harness.click(mx + 60, my - 10);
            require(context.computeOnClient(client -> reopened.drawings().size()) == 3,
                    "The eraser removes the rectangle it clicks");
            harness.click(context.computeOnClient(client -> reopened.button(Chart.Tool.CURSOR)));
            pick(harness, context, reopened, "trash");
            require(context.computeOnClient(client -> reopened.drawings().isEmpty()), "Clear all removes the rest");
            require(!Harness.read(Harness.CONFIG.resolve("bagel-tradingview-drawings.json")).contains("item:diamond"),
                    "and the file forgets them");
        });
    }

    private static void draw(Harness harness, ClientGameTestContext context, Chart chart, Chart.Tool tool, int ax,
                             int ay, int bx, int by) {
        pick(harness, context, chart, tool);
        harness.drag(GLFW.GLFW_MOUSE_BUTTON_LEFT, ax, ay, bx, by);
        require(context.computeOnClient(client -> chart.tool()) == Chart.Tool.CURSOR, tool
                + " goes back to the cursor after one drawing");
    }

    /** Opens the pencil's popover and picks something in it. */
    private static void pick(Harness harness, ClientGameTestContext context, Chart chart, Object which) {
        harness.click(context.computeOnClient(client -> chart.button("draw")));
        context.waitTicks(2);
        harness.click(context.computeOnClient(client -> chart.button(which)));
    }

    private static double[] view(ClientGameTestContext context, Chart chart) {
        context.waitTicks(2);
        return context.computeOnClient(client -> chart.view());
    }

    private static double timeAt(double[] view, int[] plot, int x) {
        return view[0] + (x - plot[0]) / (double) plot[2] * (view[1] - view[0]);
    }
}

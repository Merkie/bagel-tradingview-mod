package com.merkie.bageltv.ui;

import com.merkie.bageltv.BagelTV;
import com.merkie.bageltv.chart.Candles;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * The startup animation, played the first time the terminal opens after joining a server. There is no window
 * yet: it plays over the lightly dimmed game, on a wide soft shadow.
 *
 * <p>A candlestick chart streams by from the right behind a round opening, the candles fat at first, then
 * thinner and faster as the opening closes in. At full speed the candles stretch to the opening's height and
 * turn white, so it is a disc of stripes rushing left. Those same stripes, still sliding, then become a turning
 * ring drawn as a line halftone: each stripe's ends draw in from the disc's edge to the ring's outline, and its
 * width at every point goes to thick where the ring's surface is lit, thin where it is in shadow and nothing
 * where there is no ring, which is how the hole opens. The stripes only stop once the ring is all there. It
 * turns to rest face on, the stripes fill out into the solid logo, the logo turns blue and flies to its place
 * while the window opens from that corner and its parts fade in.
 *
 * <p>Shapes are whole terminal pixels, as the charts are; a stripe that is between two widths has a fainter
 * edge. When each thing happens is in {@link IntroTiming}. The terminal draws itself unseen from the first
 * frame, so its data is already loading.
 */
public final class Intro {
    /** The longest the reveal waits for the dashboard's data before giving up on animating it. */
    private static final int PATIENCE = 3000;
    /** How dark the game is behind the animation, as a share of the terminal's usual dimming. */
    private static final float DIM = 0.45F;

    /** Parts that are always there: the title bar and the rail. The view's own parts come after them. */
    static final int CHROME = 2;

    // Shapes, in terminal pixels.
    /** A stripe's slot, and the two weights a stripe is drawn at: a candle's wick and its body. */
    private static final int PITCH = 4, THIN = 1, THICK = 3;
    /** Candle colors for this animation only: deeper than the charts', since they sit on the game, not a panel. */
    private static final int EMERALD = 0xFF05D27A, RUBY = 0xFFFF2B5C;
    private static final int COUNT = IntroTiming.CANDLES + 2;
    /** The ring: 144 across, so each of the logo's nine pixels is four stripes. */
    private static final int ACROSS = 36, SIZE = ACROSS * PITCH;
    private static final double RING = 48, TUBE = 24;
    private static final double TURNS = 1.5, TIP = 0.5;
    private static final int WHITE = 0xFFFFFFFF;

    /** Whether the animation plays at all. Tests of everything else turn it off. */
    public static boolean enabled = !Boolean.getBoolean("bageltv.noIntro");
    /** For tests: milliseconds into the animation, instead of the clock. */
    public static Double frozen;

    private final long startedAt = System.currentTimeMillis();
    /** When the view's first part asked to appear, or -1. */
    private double viewAt = -1;
    private double lastEnd;
    private boolean skipped;

    // The price walk the chart shows: made once, the same every time.
    private static final double[] OPEN = new double[COUNT], HIGH = new double[COUNT], LOW = new double[COUNT],
            CLOSE = new double[COUNT];

    static {
        Random random = new Random(11);
        double price = 0;
        for (int i = 0; i < COUNT; i++) {
            // A rising market with pullbacks: a slow swing under the noise, the odd doji and the odd long candle.
            double body = random.nextGaussian() * 0.9 + 0.16 + 0.34 * Math.cos(i / 7.5);
            if (random.nextInt(8) == 0) body *= 0.06;
            else if (random.nextInt(9) == 0) body *= 2.1;
            OPEN[i] = price;
            CLOSE[i] = price + body;
            HIGH[i] = Math.max(price, price + body) + Math.abs(random.nextGaussian()) * 0.6;
            LOW[i] = Math.min(price, price + body) - Math.abs(random.nextGaussian()) * 0.6;
            price += body;
        }
    }

    /** What a stripe shows at a row of the ring: nothing, shadow or light. */
    private static final byte NONE = 0, SHADOW = 1, LIT = 2;
    private final byte[] tone = new byte[(ACROSS + 1) * SIZE];
    /** Where across the ring each stripe looks, in pixels from its middle. */
    private final double[] across = new double[ACROSS + 1];
    private static final Identifier SHADE_TEXTURE = Identifier.fromNamespaceAndPath(BagelTV.ID, "intro_shadow");
    private static final int SHADE_W = 620, SHADE_H = 400;
    private static boolean shadeReady;

    double time() {
        return frozen != null ? frozen : System.currentTimeMillis() - startedAt;
    }

    void skip() {
        skipped = true;
    }

    public boolean done() {
        if (skipped) return true;
        double t = time();
        if (t < IntroTiming.WINDOW_TO) return false;
        return viewAt >= 0 ? t >= lastEnd : t >= IntroTiming.REVEAL_AT + PATIENCE;
    }

    /** What is on screen, for tests. */
    public String phase() {
        double t = time();
        return t < IntroTiming.WHITE_TO ? "candles" : t < IntroTiming.FILL_FROM ? "ring"
                : t < IntroTiming.TRAVEL_FROM ? "logo" : t < IntroTiming.TRAVEL_TO ? "travel" : "reveal";
    }

    /**
     * How far a part of the terminal has appeared, 0 to 1. Parts are numbered in the order they are drawn; the
     * view's parts wait for its data, then follow each other from whenever it arrived.
     */
    float region(int index) {
        double t = time();
        if (t < IntroTiming.REVEAL_AT) return 0;
        double base;
        if (index < CHROME) {
            base = IntroTiming.REVEAL_AT + index * IntroTiming.STAGGER;
        } else {
            if (viewAt < 0) viewAt = Math.max(t, IntroTiming.REVEAL_AT + CHROME * IntroTiming.STAGGER);
            base = viewAt + (index - CHROME) * IntroTiming.STAGGER;
        }
        lastEnd = Math.max(lastEnd, base + IntroTiming.FADE);
        // Quickly in, then a soft landing.
        return (float) IntroTiming.bezier(0.2, 0.7, 0.2, 1, (t - base) / IntroTiming.FADE);
    }

    /** How far the game's dimming has come to the terminal's usual, 0 to 1. */
    private double arrived() {
        return IntroTiming.smoother(IntroTiming.WINDOW_FROM, IntroTiming.WINDOW_TO, time());
    }

    /** How dark the game is behind everything, as a share of the terminal's usual dimming. */
    float dim() {
        double in = IntroTiming.smoother(time() / IntroTiming.DIM_IN);
        return (float) (DIM * in + (1 - DIM * in) * arrived());
    }

    /** The soft shadow the animation plays on, about a middle point. It goes as the window comes. */
    void backdrop(Ui ui, int cx, int cy) {
        double strength = IntroTiming.smoother(time() / IntroTiming.DIM_IN) * (1 - arrived());
        if (strength <= 0.004) return;
        if (!shadeReady) {
            // One wide blot, darkest in the middle and gone well before its edge, with nothing like an outline.
            NativeImage image = new NativeImage(SHADE_W, SHADE_H, false);
            for (int y = 0; y < SHADE_H; y++) {
                for (int x = 0; x < SHADE_W; x++) {
                    double dx = (x + 0.5 - SHADE_W / 2.0) / (SHADE_W / 2.0), dy = (y + 0.5 - SHADE_H / 2.0) / (SHADE_H / 2.0);
                    double far = Math.sqrt(dx * dx + dy * dy);
                    double dark = far >= 1 ? 0 : Math.pow(smooth(1 - far), 1.6);
                    image.setPixel(x, y, (int) Math.round(dark * 0.78 * 255) << 24 | 0x04060C);
                }
            }
            Minecraft.getInstance().getTextureManager().register(SHADE_TEXTURE,
                    new DynamicTexture(() -> "BagelTradingView startup shadow", image));
            shadeReady = true;
        }
        ui.g.blit(RenderPipelines.GUI_TEXTURED, SHADE_TEXTURE, cx - SHADE_W / 2, cy - SHADE_H / 2, 0F, 0F, SHADE_W,
                SHADE_H, SHADE_W, SHADE_H, (int) Math.round(strength * 255) << 24 | 0xFFFFFF);
    }

    /**
     * The part of the window that shows: nothing until the logo is nearly home, then a box that opens from the
     * logo's place to the whole panel. Null once it is the whole panel.
     */
    int[] window(int x, int y, int w, int h, int logoX, int logoY) {
        double t = time();
        if (t < IntroTiming.WINDOW_FROM) return new int[] {x, y, 0, 0};
        if (t >= IntroTiming.WINDOW_TO) return null;
        double open = IntroTiming.window(t);
        int fromX = logoX - 3, fromY = logoY - 3, from = Icon.BAGEL.width + 6;
        int left = (int) Math.round(fromX + (x - fromX) * open), top = (int) Math.round(fromY + (y - fromY) * open);
        int right = (int) Math.round(fromX + from + (x + w - fromX - from) * open);
        int bottom = (int) Math.round(fromY + from + (y + h - fromY - from) * open);
        return new int[] {left, top, right - left, bottom - top};
    }

    /** Draws the animation itself, over everything, about the panel's middle. */
    void draw(Ui ui, int x, int y, int w, int h, int logoX, int logoY) {
        double t = time();
        ui.g.nextStratum();
        int cx = x + w / 2, cy = y + h / 2;
        if (t < IntroTiming.WHITE_TO) {
            chart(ui, t, cx, cy);
        } else if (t < IntroTiming.TRAVEL_FROM) {
            // While the stripes are still sliding they sit a little left of their places.
            double passed = IntroTiming.passed(t);
            int behind = (int) -Math.round(-(passed - Math.floor(passed)) * PITCH) % PITCH;
            double turned = IntroTiming.spin(t), tipped = Math.sin(Math.PI * turned);
            double angle = TURNS * 2 * Math.PI * turned, ends = IntroTiming.ends(t);
            // Until the stripes' ends are home, each shows the ring a little nearer its middle than where it
            // stands: the ring spread to the disc's width, drawing in to its own as it forms. Stripes beyond
            // that drawn-in width look past the ring and are gone.
            double wide = (RING * Math.abs(Math.cos(angle)) + TUBE) / (RING + TUBE);
            double spread = wide / (1 - ends + wide * ends);
            for (int column = 0; column <= ACROSS; column++) {
                across[column] = (column * PITCH + PITCH / 2.0 - SIZE / 2.0 - behind) * spread;
            }
            shade(tone, angle, TIP * tipped * tipped, across);
            if (t < IntroTiming.FILL_FROM) ring(ui, cx, cy, behind, ends, IntroTiming.widths(t));
            else settle(ui, cx, cy, IntroTiming.fill(t), IntroTiming.square(t), IntroTiming.blue(t));
        } else if (t < IntroTiming.TRAVEL_TO) {
            double go = IntroTiming.travel(t), cell = SIZE / 9.0 + (1 - SIZE / 9.0) * IntroTiming.shrink(t);
            double half = Icon.BAGEL.width / 2.0;
            logo(ui, cx + (logoX + half - cx) * go, cy + (logoY + half - cy) * go, cell, Theme.ACCENT);
        } else {
            ui.icon(Icon.BAGEL, logoX, logoY, Theme.ACCENT);
        }
    }

    private static double smooth(double p) {
        p = Math.clamp(p, 0, 1);
        return p * p * (3 - 2 * p);
    }

    // The chart

    /** The lowest and highest price among the candles in view at a moment, as a live chart fits itself. */
    private static void fit(double t, double[] range) {
        double count = IntroTiming.passed(t), slot = IntroTiming.pitch(t);
        int newest = Math.min(COUNT - 1, (int) count);
        int oldest = Math.max(0, (int) Math.floor(count - 2 * IntroTiming.opening(t) / slot));
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int i = oldest; i <= newest; i++) {
            low = Math.min(low, LOW[i]);
            high = Math.max(high, HIGH[i]);
        }
        range[0] += low;
        range[1] += high;
    }

    /**
     * One pixel column of a candle, as much of it as shows through the round opening. {@code across} is where
     * the opening's height is measured: the column itself, moving to its stripe's middle as the candles turn
     * into stripes, so a stripe ends square.
     */
    private static void column(Ui ui, int x, double across, int from, int to, int cx, int cy, double opening,
                               int color) {
        double dx = across - cx, reach = opening * opening - dx * dx;
        if (reach <= 0) return;
        int half = (int) Math.round(Math.sqrt(reach));
        from = Math.max(from, cy - half);
        to = Math.min(to, cy + half - 1);
        if (to >= from) ui.fill(x, from, 1, to - from + 1, color);
    }

    /**
     * The candlestick chart, drawn as the terminal's charts draw candles (a one-pixel wick and a body as wide as
     * its slot less a gap), seen through a round opening. New candles come in at the right and the older ones
     * march left, faster and faster; the price scale follows what is in view. At full speed every candle
     * stretches to the opening's height and turns white.
     */
    private void chart(Ui ui, double t, int cx, int cy) {
        double count = IntroTiming.passed(t), slot = IntroTiming.pitch(t), opening = IntroTiming.opening(t);
        double turn = IntroTiming.white(t);
        // The scale glides: the average fit over the last fifth of a second.
        double[] range = new double[2];
        int samples = 10;
        for (int i = 0; i < samples; i++) fit(Math.max(0, t - i * 20), range);
        double low = range[0] / samples, high = range[1] / samples, span = Math.max(1e-6, high - low);
        // The chart keeps to the middle band of the opening.
        double band = opening * 0.6, scale = 2 * band / span;
        int body = Candles.width(slot);
        double right = cx + opening;
        // Until the chart fills the opening it sits in the middle, growing both ways.
        double room = IntroTiming.smoother(Math.max(0, 2 * opening - count * slot) / (2 * opening));
        int shift = (int) Math.round(room * opening);
        for (int i = Math.min(COUNT - 1, (int) count); i >= 0; i--) {
            int x = (int) Math.round(right - (count - i) * slot) - shift;
            if (x + (int) Math.ceil(slot) < cx - opening) break;
            double forming = IntroTiming.smoother(count - i);
            double open = OPEN[i], close = open + (CLOSE[i] - open) * forming;
            double highest = Math.max(Math.max(open, close), open + (HIGH[i] - open) * forming);
            double lowest = Math.min(Math.min(open, close), open + (LOW[i] - open) * forming);
            int color = Theme.alpha(Theme.mix(CLOSE[i] >= OPEN[i] ? EMERALD : RUBY, WHITE, (float) turn),
                    (int) Math.round(255 * forming));
            double wickTop = cy - band + (high - highest) * scale, wickBottom = cy - band + (high - lowest) * scale;
            double bodyTop = cy - band + (high - Math.max(open, close)) * scale;
            double bodyBottom = cy - band + (high - Math.min(open, close)) * scale;
            if (turn > 0) {
                wickTop += (cy - opening - wickTop) * turn;
                wickBottom += (cy + opening - wickBottom) * turn;
                bodyTop += (cy - opening - bodyTop) * turn;
                bodyBottom += (cy + opening - bodyBottom) * turn;
            }
            double middle = x + PITCH / 2.0;
            int wick = x + body / 2;
            column(ui, wick, wick + 0.5 + (middle - wick - 0.5) * turn, (int) Math.round(wickTop),
                    (int) Math.round(wickBottom), cx, cy, opening, color);
            if (body < 3) continue;
            int bt = (int) Math.round(bodyTop), bb = Math.max(bt, (int) Math.round(bodyBottom));
            for (int c = 0; c < body; c++) {
                column(ui, x + c, x + c + 0.5 + (middle - x - c - 0.5) * turn, bt, bb, cx, cy, opening, color);
            }
        }
    }

    // The ring

    /**
     * What each stripe shows at each row of the ring, turned about its upright axis and tipped toward the
     * player. A stripe's middle looks straight into the scene and walks forward until it meets the ring's
     * surface; there the surface is lit, or not, by a light at the upper left. Two tones only.
     */
    private static void shade(byte[] tones, double turn, double tip, double[] looks) {
        double ct = Math.cos(turn), st = Math.sin(turn), cp = Math.cos(tip), sp = Math.sin(tip);
        double dirX = cp * st, dirY = -sp, dirZ = -cp * ct;
        double lx = -0.45, ly = -0.55, lz = 0.7, ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double start = RING + TUBE + 2;
        for (int column = 0; column <= ACROSS; column++) {
            double wx = looks[column];
            for (int row = 0; row < SIZE; row++) {
                double wy = row + 0.5 - SIZE / 2.0;
                byte found = NONE;
                if (wx * wx + wy * wy <= (RING + TUBE) * (RING + TUBE)) {
                    double y1 = wy * cp + start * sp, z1 = -wy * sp + start * cp;
                    double ox = wx * ct - z1 * st, oz = wx * st + z1 * ct;
                    double walked = 0, nearest = 1e9;
                    for (int step = 0; step < 64 && walked < 2 * start; step++) {
                        double qx = ox + dirX * walked, qy = y1 + dirY * walked, qz = oz + dirZ * walked;
                        double round = Math.sqrt(qx * qx + qy * qy);
                        double away = Math.sqrt((round - RING) * (round - RING) + qz * qz) - TUBE;
                        nearest = Math.min(nearest, away);
                        if (away < 0.05) {
                            double across = (round - RING) / TUBE;
                            double nx = qx / round * across, ny = qy / round * across, nz = qz / TUBE;
                            double sx = nx * ct + nz * st, sz1 = -nx * st + nz * ct;
                            double sy = ny * cp - sz1 * sp, sz = ny * sp + sz1 * cp;
                            found = (sx * lx + sy * ly + sz * lz) / ll > 0.62 ? LIT : SHADOW;
                            break;
                        }
                        walked += away;
                    }
                    // A stripe that only grazes the ring's edge still shows it, in shadow.
                    if (found == NONE && nearest < 0.5) found = SHADOW;
                }
                tones[column * SIZE + row] = found;
            }
        }
    }

    /**
     * A run of rows of one stripe, covering its slot from {@code from} to {@code to} pixels across (0 to 4).
     * Whole pixels are solid; a pixel partly covered is fainter by as much, which is what a stripe between two
     * widths looks like.
     */
    private static void run(Ui ui, int slotX, int y, int rows, double from, double to, int color) {
        if (rows <= 0 || to - from < 0.02) return;
        for (int pixel = 0; pixel < PITCH; pixel++) {
            double cover = Math.min(to, pixel + 1) - Math.max(from, pixel);
            if (cover <= 0.02) continue;
            ui.fill(slotX + pixel, y, 1, rows, cover >= 0.98 ? color
                    : Theme.alpha(color, (int) Math.round(cover * (color >>> 24))));
        }
    }

    /** Where a stripe's edges are in its slot for what the ring shows there: lit, shadow or nothing. */
    private static double edge(byte found, boolean right) {
        return found == LIT ? (right ? THICK : 0) : found == SHADOW ? (right ? 2 : 1) : 1.5;
    }

    /**
     * The stripes, on their way from a plain disc to the ring. {@code ends} takes each stripe's top and bottom
     * from the disc's edge to the ring's outline in its column, the ring's pattern stretched between them
     * meanwhile; {@code widths} takes its width at each point from a whole stripe's to what the ring shows
     * there. At 1 and 1 this is exactly the halftone ring, on the same stripes.
     */
    private void ring(Ui ui, int cx, int cy, int behind, double ends, double widths) {
        int left = cx - SIZE / 2 - behind, top = cy - SIZE / 2;
        for (int column = 0; column <= ACROSS; column++) {
            int x = left + column * PITCH;
            double dx = x + PITCH / 2.0 - cx, reach = (RING + TUBE) * (RING + TUBE) - dx * dx;
            // The ring's outline in this column; a stripe that looks past the ring is not drawn.
            int first = -1, last = -1;
            for (int row = 0; row < SIZE; row++) {
                if (tone[column * SIZE + row] == NONE) continue;
                if (first < 0) first = row;
                last = row;
            }
            if (first < 0) continue;
            double ringTop = first, ringBottom = last + 1;
            double half = reach > 0 ? Math.sqrt(reach) : 0;
            double from = SIZE / 2.0 - half + (ringTop - (SIZE / 2.0 - half)) * ends;
            double to = SIZE / 2.0 + half + (ringBottom - (SIZE / 2.0 + half)) * ends;
            int rowFrom = (int) Math.round(from), rowTo = (int) Math.round(to);
            if (to - from < 3 || rowTo <= rowFrom) continue;
            int start = rowFrom, lastA = -1, lastB = -1;
            for (int row = rowFrom; row <= rowTo; row++) {
                int a = -1, b = -1;
                if (row < rowTo) {
                    // The ring's pattern, stretched over the stripe as it stands.
                    double along = (row + 0.5 - from) / (to - from);
                    int source = Math.clamp((int) Math.floor(ringTop + along * (ringBottom - ringTop)), 0, SIZE - 1);
                    byte found = tone[column * SIZE + source];
                    a = (int) Math.round(edge(found, false) * widths * 8);
                    b = (int) Math.round((THICK + (edge(found, true) - THICK) * widths) * 8);
                }
                if (a != lastA || b != lastB) {
                    if (lastA >= 0) run(ui, x, top + start, row - start, lastA / 8.0, lastB / 8.0, WHITE);
                    start = row;
                    lastA = a;
                    lastB = b;
                }
            }
        }
    }

    private static double box(double x, double y, double halfW, double halfH) {
        double dx = Math.abs(x) - halfW, dy = Math.abs(y) - halfH;
        double outside = Math.sqrt(Math.max(dx, 0) * Math.max(dx, 0) + Math.max(dy, 0) * Math.max(dy, 0));
        return outside + Math.min(Math.max(dx, dy), 0);
    }

    /**
     * The ring at rest filling out into the logo: every stripe widens to its whole slot ({@code fill}), the
     * round outline squares off into the logo's ({@code square}) and white turns to the logo's blue.
     */
    private void settle(Ui ui, int cx, int cy, double fill, double square, double blue) {
        int left = cx - SIZE / 2, top = cy - SIZE / 2;
        double cell = SIZE / 9.0;
        int color = Theme.mix(WHITE, Theme.ACCENT, (float) blue);
        for (int column = 0; column < ACROSS; column++) {
            int x = left + column * PITCH;
            double dx = column * PITCH + PITCH / 2.0 - SIZE / 2.0;
            int start = 0, lastA = -1, lastB = -1;
            for (int row = 0; row <= SIZE; row++) {
                int a = -1, b = -1;
                if (row < SIZE) {
                    double dy = row + 0.5 - SIZE / 2.0;
                    double round = Math.abs(Math.sqrt(dx * dx + dy * dy) - RING) - TUBE;
                    double body = Math.min(box(dx, dy, 2.5 * cell, 4.5 * cell),
                            Math.min(box(dx, dy, 3.5 * cell, 3.5 * cell), box(dx, dy, 4.5 * cell, 2.5 * cell)));
                    double logo = Math.max(body, -box(dx, dy, 1.5 * cell, 1.5 * cell));
                    if (round + (logo - round) * square < 0) {
                        byte found = tone[column * SIZE + row] == SHADOW ? SHADOW : LIT;
                        a = (int) Math.round(edge(found, false) * (1 - fill) * 8);
                        b = (int) Math.round((edge(found, true) + (PITCH - edge(found, true)) * fill) * 8);
                    }
                }
                if (a != lastA || b != lastB) {
                    if (lastA >= 0) run(ui, x, top + start, row - start, lastA / 8.0, lastB / 8.0, color);
                    start = row;
                    lastA = a;
                    lastB = b;
                }
            }
        }
    }

    /** The logo at any size about a middle point; at a cell of one pixel it is exactly the title bar's icon. */
    private static void logo(Ui ui, double middleX, double middleY, double cell, int color) {
        Icon icon = Icon.BAGEL;
        double x0 = middleX - icon.width / 2.0 * cell, y0 = middleY - icon.height / 2.0 * cell;
        for (int row = 0; row < icon.height; row++) {
            String line = icon.rows[row];
            int top = (int) Math.round(y0 + row * cell), bottom = (int) Math.round(y0 + (row + 1) * cell);
            int start = -1;
            for (int column = 0; column <= icon.width; column++) {
                boolean on = column < icon.width && line.charAt(column) == 'X';
                if (on && start < 0) start = column;
                if (!on && start >= 0) {
                    int left = (int) Math.round(x0 + start * cell), right = (int) Math.round(x0 + column * cell);
                    ui.fill(left, top, right - left, bottom - top, color);
                    start = -1;
                }
            }
        }
    }
}

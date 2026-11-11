package com.merkie.bageltv.ui;

/**
 * When everything in the startup animation happens: each moving thing as a function of the milliseconds since
 * it began. Nothing here is linear and nothing changes speed abruptly: every curve starts and ends at rest, and
 * neighbors overlap, one easing in before the last has finished. Tune the animation here.
 *
 * <p>It depends on nothing else, so the curves can be printed and checked on their own
 * ({@code tools/intro-curves.sh}).
 */
public final class IntroTiming {
    private IntroTiming() {}

    // The chart's scroll, in pixels a millisecond: from a crawl up to full speed, held, then down to a stop.
    public static final double CRAWL = 0.15, RUSH = 0.6;
    public static final double RUSH_AT = 2000, SLOW_AT = 2500, STOP_AT = 3800;
    /** A candle's slot in pixels: wide at first, closing to a stripe's. */
    public static final double WIDE = 24, NARROW = 4, NARROW_AT = 1900;
    /** The round opening: its radius at first and at last, and when it closes in. */
    public static final double OPENING = 112, RING_OUTER = 72, CLOSE_FROM = 800, CLOSE_TO = 2600;
    /** The candles stretch into white stripes. */
    public static final double WHITE_FROM = 1900, WHITE_TO = 2600;
    /** The stripes become the ring: their ends first, their widths a moment behind. */
    public static final double ENDS_FROM = 2700, ENDS_TO = 3700, WIDTHS_FROM = 2800, WIDTHS_TO = 3750;
    /** The ring turns: quickly into it, then a long settle. */
    public static final double SPIN_FROM = 2800, SPIN_TO = 5000;
    /** The stripes fill out, the round outline squares off into the logo, and it turns blue. */
    public static final double FILL_FROM = 4850, FILL_TO = 5300, SQUARE_FROM = 5100, SQUARE_TO = 5600;
    public static final double BLUE_FROM = 5150, BLUE_TO = 5650;
    /** The logo flies to the title bar, shrinking a little ahead of its flight. */
    public static final double TRAVEL_FROM = 5650, TRAVEL_TO = 6350;
    /** The window opens from the logo's place, and the game dims to the terminal's usual. */
    public static final double WINDOW_FROM = 6150, WINDOW_TO = 6650;
    /** The terminal's parts appear: when the first starts, the gap to the next, and how long each takes. */
    public static final double REVEAL_AT = 6350, STAGGER = 50, FADE = 280;
    /** The dimming and the shadow come in. */
    public static final double DIM_IN = 600;

    /** Ease in and out, flat at both ends in speed and in acceleration. */
    public static double smoother(double p) {
        p = Math.clamp(p, 0, 1);
        return p * p * p * (p * (6 * p - 15) + 10);
    }

    public static double smoother(double from, double to, double t) {
        return smoother((t - from) / (to - from));
    }

    /** A cubic bezier easing curve, as in CSS and After Effects: the y at which the curve's x is {@code p}. */
    public static double bezier(double x1, double y1, double x2, double y2, double p) {
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        double low = 0, high = 1, s = p;
        for (int i = 0; i < 40; i++) {
            s = (low + high) / 2;
            double x = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s;
            if (x < p) low = s;
            else high = s;
        }
        return 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s;
    }

    /** The scroll's speed in pixels a millisecond, before it is trimmed to stop on a whole stripe. */
    private static double speed(double t) {
        if (t < RUSH_AT) return CRAWL + (RUSH - CRAWL) * smoother(t / RUSH_AT);
        if (t < SLOW_AT) return RUSH;
        return RUSH * (1 - smoother(SLOW_AT, STOP_AT, t));
    }

    public static double pitch(double t) {
        return NARROW + (WIDE - NARROW) * (1 - smoother(t / NARROW_AT));
    }

    // Candles gone by at each millisecond: the speed over the slot's width, added up.
    private static final double[] PASSED = new double[(int) STOP_AT + 2];
    /** How many candles go by in all: a whole number, so the stripes stop exactly in their places. */
    public static final int CANDLES;

    static {
        double sum = 0;
        for (int ms = 1; ms < PASSED.length; ms++) {
            sum += speed(ms - 0.5) / pitch(ms - 0.5);
            PASSED[ms] = sum;
        }
        CANDLES = (int) Math.round(sum);
        for (int ms = 0; ms < PASSED.length; ms++) PASSED[ms] *= CANDLES / sum;
    }

    /** How many candles have gone by. */
    public static double passed(double t) {
        if (t <= 0) return 0;
        if (t >= PASSED.length - 1) return CANDLES;
        int ms = (int) t;
        return PASSED[ms] + (PASSED[ms + 1] - PASSED[ms]) * (t - ms);
    }

    public static double opening(double t) {
        return OPENING + (RING_OUTER - OPENING) * smoother(CLOSE_FROM, CLOSE_TO, t);
    }

    public static double white(double t) {
        return smoother(WHITE_FROM, WHITE_TO, t);
    }

    public static double ends(double t) {
        return smoother(ENDS_FROM, ENDS_TO, t);
    }

    public static double widths(double t) {
        return smoother(WIDTHS_FROM, WIDTHS_TO, t);
    }

    /** How far through its turning the ring is: a quick start and a long, soft landing. */
    public static double spin(double t) {
        return bezier(0.42, 0, 0.12, 1, (t - SPIN_FROM) / (SPIN_TO - SPIN_FROM));
    }

    public static double fill(double t) {
        return smoother(FILL_FROM, FILL_TO, t);
    }

    public static double square(double t) {
        return smoother(SQUARE_FROM, SQUARE_TO, t);
    }

    public static double blue(double t) {
        return smoother(BLUE_FROM, BLUE_TO, t);
    }

    public static double travel(double t) {
        return bezier(0.62, 0, 0.2, 1, (t - TRAVEL_FROM) / (TRAVEL_TO - TRAVEL_FROM));
    }

    public static double shrink(double t) {
        return bezier(0.5, 0, 0.25, 1, (t - TRAVEL_FROM) / (TRAVEL_TO - TRAVEL_FROM));
    }

    public static double window(double t) {
        return bezier(0.45, 0, 0.15, 1, (t - WINDOW_FROM) / (WINDOW_TO - WINDOW_FROM));
    }

    /** Prints each curve and its speed every 50 ms, and the sharpest change of speed in each, for checking. */
    public static void main(String[] args) {
        String[] names = {"passed", "pitch", "opening", "white", "ends", "widths", "spin", "fill", "square", "travel",
                "shrink", "window"};
        java.util.function.DoubleUnaryOperator[] curves = {IntroTiming::passed, IntroTiming::pitch,
                IntroTiming::opening, IntroTiming::white, IntroTiming::ends, IntroTiming::widths, IntroTiming::spin,
                IntroTiming::fill, IntroTiming::square, IntroTiming::travel, IntroTiming::shrink, IntroTiming::window};
        double end = WINDOW_TO + 100;
        System.out.println("candles " + CANDLES);
        StringBuilder head = new StringBuilder("    ms");
        for (String name : names) head.append(String.format(" %9s %8s", name, "/s"));
        System.out.println(head);
        for (double t = 0; t <= end; t += 50) {
            StringBuilder line = new StringBuilder(String.format("%6.0f", t));
            for (var curve : curves) {
                double value = curve.applyAsDouble(t);
                double speed = (curve.applyAsDouble(t + 1) - curve.applyAsDouble(t - 1)) / 2 * 1000;
                line.append(String.format(" %9.3f %8.3f", value, speed));
            }
            System.out.println(line);
        }
        // A kink shows as a jump in speed from one frame to the next that is large beside the curve's top speed.
        System.out.println("largest change of speed between frames, as a share of top speed:");
        for (int i = 0; i < names.length; i++) {
            double top = 0, jump = 0, last = 0;
            double step = 1000.0 / 60;
            for (double t = 0; t <= end; t += step) {
                double speed = curves[i].applyAsDouble(t + step) - curves[i].applyAsDouble(t);
                top = Math.max(top, Math.abs(speed));
                jump = Math.max(jump, Math.abs(speed - last));
                last = speed;
            }
            System.out.printf("  %-8s %.3f%n", names[i], jump / top);
        }
    }
}

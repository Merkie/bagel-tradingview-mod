package com.merkie.bageltv.chart;

import com.merkie.bageltv.Fmt;

import java.util.Locale;

/** How a chart writes its values on the axis, in the legend and in read-outs. */
public interface ValueFormat {
    /** A short label, as in a legend. */
    String format(double value);

    /** A label with enough digits to tell apart values {@code step} apart, for axis ticks and the crosshair. */
    default String axis(double value, double step) {
        return format(value);
    }

    /** How many raw units make one display unit, so ticks land on round display values (100 cents to a dollar). */
    default double unit() {
        return 1;
    }

    ValueFormat MONEY = new ValueFormat() {
        @Override
        public String format(double value) {
            return Fmt.money(value);
        }

        @Override
        public String axis(double value, double step) {
            return "$" + scaled(value / 100, step / 100);
        }

        @Override
        public double unit() {
            return 100;
        }
    };

    ValueFormat COUNT = new ValueFormat() {
        @Override
        public String format(double value) {
            return Fmt.count(value);
        }

        @Override
        public String axis(double value, double step) {
            return scaled(value, step);
        }
    };

    /** A plain number such as the market index, written out in full. */
    ValueFormat INDEX = new ValueFormat() {
        @Override
        public String format(double value) {
            return String.format(Locale.US, "%,.2f", value);
        }

        @Override
        public String axis(double value, double step) {
            int decimals = step <= 0 || Double.isNaN(step) ? 2 : (int) Math.clamp(Math.ceil(-Math.log10(step) - 1e-9),
                    0, 4);
            return String.format(Locale.US, "%,." + decimals + "f", value);
        }
    };

    /** A share from 0 to 1. */
    ValueFormat SHARE = new ValueFormat() {
        @Override
        public String format(double value) {
            return Fmt.share(value);
        }

        @Override
        public String axis(double value, double step) {
            return Math.round(value * 100) + "%";
        }

        @Override
        public double unit() {
            return 0.01;
        }
    };

    /** A place in a ranking: #1 is the top. */
    ValueFormat RANK = value -> "#" + Math.round(value);

    /** A number with a K/M/B/T suffix and as many decimals as {@code step} needs. */
    static String scaled(double value, double step) {
        double abs = Math.abs(value);
        String[] units = {"", "K", "M", "B", "T", "Q"};
        int unit = 0;
        double divisor = 1;
        while (abs / divisor >= 1000 && unit < units.length - 1) {
            divisor *= 1000;
            unit++;
        }
        double shownStep = step / divisor;
        int decimals = shownStep <= 0 || Double.isNaN(shownStep) ? 2
                : (int) Math.clamp(Math.ceil(-Math.log10(shownStep) - 1e-9), 0, 4);
        String digits = String.format(Locale.US, "%." + decimals + "f", value / divisor);
        return digits + units[unit];
    }
}

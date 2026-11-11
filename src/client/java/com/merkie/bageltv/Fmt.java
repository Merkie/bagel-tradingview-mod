package com.merkie.bageltv;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Number and time formatting. Money arrives in cents; times in Unix seconds and show in the local timezone. */
public final class Fmt {
    public static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DAY_HM = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US);
    private static final String[] UNITS = {"", "K", "M", "B", "T", "Q"};

    private Fmt() {}

    /** Cents as short dollars: $0.77, $98.80, $766, $4.9K, $1.2M, $6.08B. */
    public static String money(double cents) {
        if (Double.isNaN(cents)) return "-";
        double dollars = cents / 100.0;
        String sign = dollars < 0 ? "-" : "";
        return sign + "$" + compact(Math.abs(dollars), true);
    }

    public static String money(Double cents) {
        return cents == null ? "-" : money(cents.doubleValue());
    }

    /** Cents as exact dollars with separators: $4,900 or $765.63. */
    public static String moneyExact(double cents) {
        if (Double.isNaN(cents)) return "-";
        double dollars = cents / 100.0;
        String sign = dollars < 0 ? "-" : "";
        dollars = Math.abs(dollars);
        boolean whole = Math.abs(dollars - Math.rint(dollars)) < 0.005;
        return sign + "$" + String.format(Locale.US, whole ? "%,.0f" : "%,.2f", dollars);
    }

    public static String moneyExact(Double cents) {
        return cents == null ? "-" : moneyExact(cents.doubleValue());
    }

    /** A count: 6,296 or 1.35M. */
    public static String count(double value) {
        if (Double.isNaN(value)) return "-";
        double abs = Math.abs(value);
        if (abs < 100_000) {
            boolean whole = Math.abs(abs - Math.rint(abs)) < 0.005;
            return String.format(Locale.US, whole ? "%,.0f" : "%,.1f", value);
        }
        return (value < 0 ? "-" : "") + compact(abs, false);
    }

    public static String count(Double value) {
        return value == null ? "-" : count(value.doubleValue());
    }

    public static String countExact(double value) {
        boolean whole = Math.abs(value - Math.rint(value)) < 0.005;
        return String.format(Locale.US, whole ? "%,.0f" : "%,.2f", value);
    }

    /** Three significant digits with a K/M/B/T suffix. */
    public static String compact(double abs, boolean money) {
        if (abs < 1000) {
            if (!money) return trim(String.format(Locale.US, abs < 10 ? "%.2f" : abs < 100 ? "%.1f" : "%.0f", abs));
            if (abs == 0) return "0";
            if (abs < 0.01) return trim(String.format(Locale.US, "%.4f", abs));
            if (abs < 100) return String.format(Locale.US, "%.2f", abs);
            return String.format(Locale.US, "%.0f", abs);
        }
        int unit = 0;
        while (abs >= 999.5 && unit < UNITS.length - 1) {
            abs /= 1000;
            unit++;
        }
        String digits = String.format(Locale.US, abs < 9.995 ? "%.2f" : abs < 99.95 ? "%.1f" : "%.0f", abs);
        return trim(digits) + UNITS[unit];
    }

    private static String trim(String digits) {
        if (digits.indexOf('.') < 0) return digits;
        int end = digits.length();
        while (digits.charAt(end - 1) == '0') end--;
        if (digits.charAt(end - 1) == '.') end--;
        return digits.substring(0, end);
    }

    /** A fraction as a signed percent: +1.24%, -0.17%. */
    public static String pct(double fraction) {
        if (Double.isNaN(fraction)) return "-";
        double percent = fraction * 100;
        double abs = Math.abs(percent);
        String digits = abs >= 1000 ? compact(abs, false) : String.format(Locale.US, abs >= 100 ? "%.0f" : abs >= 10
                ? "%.1f" : "%.2f", abs);
        return (percent > 0.004 ? "+" : percent < -0.004 ? "-" : "") + digits + "%";
    }

    public static String pct(Double fraction) {
        return fraction == null ? "-" : pct(fraction.doubleValue());
    }

    /** A share from 0 to 1 as a plain percent: 53.5%. */
    public static String share(Double fraction) {
        if (fraction == null) return "-";
        double percent = fraction * 100;
        return trim(String.format(Locale.US, percent >= 99.95 || percent == 0 ? "%.0f" : "%.1f", percent)) + "%";
    }

    /** A ratio such as price over worth: 7.9x. */
    public static String ratio(Double value) {
        if (value == null) return "-";
        double abs = Math.abs(value);
        return (abs >= 1000 ? compact(abs, false) : trim(String.format(Locale.US, abs >= 100 ? "%.0f" : abs >= 10
                ? "%.1f" : "%.2f", abs))) + "x";
    }

    /** Seconds as a short length of time: 84s, 4m 17s, 2h 5m, 3d 4h. */
    public static String duration(double seconds) {
        long s = Math.round(seconds);
        if (s < 100) return s + "s";
        if (s < 3600) return s / 60 + "m " + s % 60 + "s";
        if (s < 86400) return s / 3600 + "h " + s % 3600 / 60 + "m";
        return s / 86400 + "d " + s % 86400 / 3600 + "h";
    }

    public static String duration(Double seconds) {
        return seconds == null ? "-" : duration(seconds.doubleValue());
    }

    /** A length of history in one unit: 45m, 3h, 5d. */
    public static String span(double seconds) {
        if (seconds < 5400) return Math.max(1, Math.round(seconds / 60)) + "m";
        if (seconds < 2 * 86400) return Math.round(seconds / 3600) + "h";
        return Math.round(seconds / 86400) + "d";
    }

    /** How long ago a time was: now, 42s, 5m, 3h, 2d. */
    public static String ago(long then, long now) {
        long s = Math.max(0, now - then);
        if (s < 5) return "now";
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m";
        if (s < 86400) return s / 3600 + "h";
        return s / 86400 + "d";
    }

    public static ZonedDateTime at(long seconds) {
        return Instant.ofEpochSecond(seconds).atZone(ZONE);
    }

    public static String clock(long seconds) { return HM.format(at(seconds)); }
    public static String clockSeconds(long seconds) { return HMS.format(at(seconds)); }
    public static String day(long seconds) { return DAY.format(at(seconds)); }
    public static String dayYear(long seconds) { return DAY_YEAR.format(at(seconds)); }
    public static String dayClock(long seconds) { return DAY_HM.format(at(seconds)); }
    public static String month(long seconds) { return MONTH.format(at(seconds)); }
}

package com.merkie.bageltv.chart;

import com.merkie.bageltv.Fmt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Time axis ticks in the player's timezone: steps from a minute to a year, picked so labels never crowd, landing
 * on round local times (whole hours, local midnight, the first of a month).
 */
final class TimeTicks {
    private static final int[] STEPS = {60, 300, 900, 1800, 3600, 2 * 3600, 3 * 3600, 6 * 3600, 12 * 3600};
    private static final int[] DAY_STEPS = {1, 2, 7, 14};
    private static final int[] MONTH_STEPS = {1, 3, 6, 12};
    private static final int MAX = 64;

    final double[] times = new double[MAX];
    final String[] labels = new String[MAX];
    /** A tick that starts a new day, month or year, drawn brighter. */
    final boolean[] strong = new boolean[MAX];
    int count;
    private double forT0 = Double.NaN, forT1, forWidth;

    /** Works the ticks out again only when the visible range or the width changed. */
    void update(double t0, double t1, int width, int minSpacing) {
        if (t0 == forT0 && t1 == forT1 && width == forWidth) return;
        forT0 = t0;
        forT1 = t1;
        forWidth = width;
        count = 0;
        if (!(t1 > t0) || width <= 0) return;
        double secondsPerLabel = (t1 - t0) / width * minSpacing;
        for (int step : STEPS) {
            if (step >= secondsPerLabel) {
                clock(t0, t1, step);
                return;
            }
        }
        for (int days : DAY_STEPS) {
            if (days * 86400.0 >= secondsPerLabel) {
                days(t0, t1, days);
                return;
            }
        }
        for (int months : MONTH_STEPS) {
            if (months * 30.4 * 86400 >= secondsPerLabel) {
                months(t0, t1, months);
                return;
            }
        }
        months(t0, t1, 12 * (int) Math.ceil(secondsPerLabel / (365.0 * 86400)));
    }

    private void clock(double t0, double t1, int step) {
        long start = (long) Math.floor(t0);
        int offset = Fmt.ZONE.getRules().getOffset(Instant.ofEpochSecond(start)).getTotalSeconds();
        long tick = Math.floorDiv(start + offset, (long) step) * step - offset;
        for (; tick <= t1 && count < MAX; tick += step) {
            if (tick < t0) continue;
            ZonedDateTime local = Fmt.at(tick);
            boolean midnight = local.getHour() == 0 && local.getMinute() == 0;
            add(tick, midnight ? Fmt.day(tick) : Fmt.clock(tick), midnight);
        }
    }

    private void days(double t0, double t1, int every) {
        LocalDate day = Fmt.at((long) Math.floor(t0)).toLocalDate();
        for (int guard = 0; guard < 400 && count < MAX; guard++, day = day.plusDays(1)) {
            long tick = day.atStartOfDay(Fmt.ZONE).toEpochSecond();
            if (tick > t1) return;
            if (tick < t0) continue;
            // Weeks start on Monday; every other day counts from the first of the month, so labels stay put
            // as you pan.
            int inMonth = day.getDayOfMonth();
            boolean on = switch (every) {
                case 1 -> true;
                case 2 -> inMonth % 2 == 1 && inMonth != 31;
                default -> Math.floorMod(day.toEpochDay() - 4, (long) every) == 0;
            };
            if (on) add(tick, Fmt.day(tick), day.getDayOfMonth() == 1);
        }
    }

    private void months(double t0, double t1, int every) {
        LocalDate day = Fmt.at((long) Math.floor(t0)).toLocalDate().withDayOfMonth(1);
        for (int guard = 0; guard < 2400 && count < MAX; guard++, day = day.plusMonths(1)) {
            long tick = day.atStartOfDay(Fmt.ZONE).toEpochSecond();
            if (tick > t1) return;
            if (tick < t0 || (day.getMonthValue() - 1) % Math.min(every, 12) != 0) continue;
            if (every > 12 && day.getYear() % (every / 12) != 0) continue;
            boolean january = day.getMonthValue() == 1;
            add(tick, january ? Integer.toString(day.getYear()) : Fmt.month(tick), january);
        }
    }

    private void add(long tick, String label, boolean isStrong) {
        times[count] = tick;
        labels[count] = label;
        strong[count] = isStrong;
        count++;
    }
}

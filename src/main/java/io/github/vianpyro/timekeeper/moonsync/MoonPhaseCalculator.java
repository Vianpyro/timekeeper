package io.github.vianpyro.timekeeper.moonsync;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Computes the current real-world lunar phase from the Julian date, using the standard ~29.53-day
 * synodic month approximation. No network access required (see PROJECT_SPEC.md "MoonSync").
 *
 * <p>Minecraft indexes moon phases 0-7 starting at the full moon: 0 = full moon, 1 = waning
 * gibbous, 2 = last quarter, 3 = waning crescent, 4 = new moon, 5 = waxing crescent, 6 = first
 * quarter, 7 = waxing gibbous (vanilla computes this as {@code (dayTime / 24000) % 8}).
 */
public final class MoonPhaseCalculator {

    private static final double SYNODIC_MONTH_DAYS = 29.530588853;

    // A well-known reference new moon, commonly used as an epoch for simple phase calculations.
    private static final ZonedDateTime REFERENCE_NEW_MOON =
            ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC);
    private static final double REFERENCE_JULIAN_DATE = toJulianDate(REFERENCE_NEW_MOON);

    private static final String[] PHASE_NAMES = {
            "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
            "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
    };

    private MoonPhaseCalculator() {
    }

    public static int currentPhase(Clock clock) {
        return phaseAt(ZonedDateTime.now(clock));
    }

    public static int phaseAt(ZonedDateTime dateTime) {
        double julianDate = toJulianDate(dateTime.withZoneSameInstant(ZoneOffset.UTC));
        double age = (julianDate - REFERENCE_JULIAN_DATE) % SYNODIC_MONTH_DAYS;
        if (age < 0) {
            age += SYNODIC_MONTH_DAYS;
        }
        int eighth = (int) Math.floor(age / SYNODIC_MONTH_DAYS * 8.0) % 8;
        // Real age 0 (new moon) is Minecraft phase index 4: the two indices are offset by a
        // quarter-cycle (4 eighths), in the same direction the phase advances.
        return (eighth + 4) % 8;
    }

    /** A human-readable name for a Minecraft moon phase index in {@code [0, 7]}. */
    public static String describe(int phase) {
        if (phase < 0 || phase >= PHASE_NAMES.length) {
            throw new IllegalArgumentException("Moon phase must be in [0, 7], got: " + phase);
        }
        return PHASE_NAMES[phase];
    }

    /** Standard Fliegel & Van Flandern Julian Date conversion (proleptic Gregorian calendar). */
    static double toJulianDate(ZonedDateTime utc) {
        LocalDate date = utc.toLocalDate();
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        int a = year / 100;
        int b = 2 - a + a / 4;
        double dayFraction = (utc.getHour() * 3600.0 + utc.getMinute() * 60.0 + utc.getSecond()) / 86400.0;
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5 + dayFraction;
    }
}

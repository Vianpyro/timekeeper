package io.github.vianpyro.timekeeper.timesync;

import io.github.vianpyro.timekeeper.MinecraftTime;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Converts between real wall-clock time and Minecraft's time-of-day tick range {@code [0, 24000)}.
 *
 * <p>Minecraft tick 0 is dawn (~06:00), not midnight: real midnight lands on tick 18000. Both
 * directions of this conversion shift by that same 6000-tick (6h) offset.
 */
public final class RealTimeConverter {

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long DAWN_OFFSET_TICKS = 6000L;
    private static final long TICKS_PER_HOUR = MinecraftTime.TICKS_PER_DAY / 24L;

    private RealTimeConverter() {
    }

    /** The Minecraft time-of-day tick corresponding to "now" on {@code clock}, plus an hour offset. */
    public static long toGameTicks(Clock clock, int offsetHours) {
        return toGameTicks(ZonedDateTime.now(clock), offsetHours);
    }

    public static long toGameTicks(ZonedDateTime dateTime, int offsetHours) {
        long secondOfDay = dateTime.toLocalTime().toSecondOfDay();
        long baseTicks = secondOfDay * MinecraftTime.TICKS_PER_DAY / SECONDS_PER_DAY;
        long shifted = baseTicks - DAWN_OFFSET_TICKS + (long) offsetHours * TICKS_PER_HOUR;
        return Math.floorMod(shifted, MinecraftTime.TICKS_PER_DAY);
    }

    /** Formats a Minecraft time-of-day tick value as the real-world "HH:mm" it corresponds to. */
    public static String formatAsClockTime(long ticksOfDay) {
        long shifted = Math.floorMod(ticksOfDay + DAWN_OFFSET_TICKS, MinecraftTime.TICKS_PER_DAY);
        long totalMinutes = shifted * 24L * 60L / MinecraftTime.TICKS_PER_DAY;
        return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60L, totalMinutes % 60L);
    }
}

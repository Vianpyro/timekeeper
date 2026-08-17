package io.github.vianpyro.timekeeper;

/**
 * Shared constants and helpers for Minecraft's {@code DayTime} encoding.
 *
 * <p>{@code DayTime} is a single {@code long} that packs two independent pieces of information:
 * a day count (whole days elapsed) and a time-of-day (ticks within the current day, in
 * {@code [0, 24000)}). The in-game moon phase is {@code dayCount % 8}. This is deliberately
 * distinct from {@code GameTime} (the world's real age in ticks, used by statistics and
 * advancements), which this mod never reads or writes - see PROJECT_SPEC.md "Ce que le mod ne
 * modifie jamais".
 */
public final class MinecraftTime {

    public static final long TICKS_PER_DAY = 24000L;

    private MinecraftTime() {
    }

    /** The day-count component of a raw {@code dayTime} value. */
    public static long dayCount(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /** The time-of-day component of a raw {@code dayTime} value, always in {@code [0, 24000)}. */
    public static long ticksOfDay(long dayTime) {
        return Math.floorMod(dayTime, TICKS_PER_DAY);
    }

    /** Packs a day count and a time-of-day back into a raw {@code dayTime} value. */
    public static long combine(long dayCount, long ticksOfDay) {
        return dayCount * TICKS_PER_DAY + ticksOfDay;
    }
}

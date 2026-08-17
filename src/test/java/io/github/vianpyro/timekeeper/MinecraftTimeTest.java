package io.github.vianpyro.timekeeper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftTimeTest {

    @Test
    void splitsAndRecombinesDayTime() {
        long dayTime = MinecraftTime.combine(5, 12345);
        assertEquals(5, MinecraftTime.dayCount(dayTime));
        assertEquals(12345, MinecraftTime.ticksOfDay(dayTime));
    }

    @Test
    void handlesNegativeRawDayTime() {
        // dayTime is a plain signed long; dayCount/ticksOfDay must still split it consistently
        // even for values that should not normally occur (defensive, not a real game state).
        long dayTime = -1L;
        assertEquals(-1, MinecraftTime.dayCount(dayTime));
        assertEquals(MinecraftTime.TICKS_PER_DAY - 1, MinecraftTime.ticksOfDay(dayTime));
    }

    @Test
    void ticksOfDayIsAlwaysNonNegative() {
        for (long dayTime = -3 * MinecraftTime.TICKS_PER_DAY; dayTime < 3 * MinecraftTime.TICKS_PER_DAY; dayTime += 137) {
            long ticks = MinecraftTime.ticksOfDay(dayTime);
            assertTrue(ticks >= 0 && ticks < MinecraftTime.TICKS_PER_DAY);
        }
    }
}

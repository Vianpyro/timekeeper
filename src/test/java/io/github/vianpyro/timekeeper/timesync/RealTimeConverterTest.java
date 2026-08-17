package io.github.vianpyro.timekeeper.timesync;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RealTimeConverterTest {

    private static final ZonedDateTime DAWN = ZonedDateTime.of(2026, 8, 17, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime NOON = ZonedDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime MIDNIGHT = ZonedDateTime.of(2026, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void sixAmIsTickZero() {
        assertEquals(0L, RealTimeConverter.toGameTicks(DAWN, 0));
    }

    @Test
    void noonIsTick6000() {
        assertEquals(6000L, RealTimeConverter.toGameTicks(NOON, 0));
    }

    @Test
    void midnightIsTick18000() {
        assertEquals(18000L, RealTimeConverter.toGameTicks(MIDNIGHT, 0));
    }

    @Test
    void positiveOffsetShiftsForward() {
        assertEquals(3000L, RealTimeConverter.toGameTicks(DAWN, 3));
    }

    @Test
    void negativeOffsetWrapsAround() {
        assertEquals(21000L, RealTimeConverter.toGameTicks(DAWN, -3));
    }

    @Test
    void formatAsClockTimeIsTheInverseConversion() {
        assertEquals("06:00", RealTimeConverter.formatAsClockTime(0L));
        assertEquals("12:00", RealTimeConverter.formatAsClockTime(6000L));
        assertEquals("00:00", RealTimeConverter.formatAsClockTime(18000L));
    }
}

package io.github.vianpyro.timekeeper.moonsync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoonPhaseCalculatorTest {

    private static final double SYNODIC_MONTH_DAYS = 29.530588853;
    private static final ZonedDateTime REFERENCE_NEW_MOON =
            ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC);

    @Test
    void julianDateMatchesTheJ2000Epoch() {
        // 2000-01-01 12:00 UTC is JD 2451545.0, a widely-cited reference point.
        ZonedDateTime j2000 = ZonedDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(2451545.0, MoonPhaseCalculator.toJulianDate(j2000), 1e-9);
    }

    @Test
    void referenceNewMoonIsPhaseFour() {
        assertEquals(4, MoonPhaseCalculator.phaseAt(REFERENCE_NEW_MOON));
    }

    @Test
    void midCycleIsFullMoon() {
        // 0.5625 is the midpoint of the [0.5, 0.625) bucket that maps to "full moon" (index 0),
        // chosen to sit well clear of the bucket boundaries rather than exactly on one.
        long millisAfterNewMoon = (long) (SYNODIC_MONTH_DAYS * 0.5625 * 86_400_000L);
        ZonedDateTime midFullMoon = REFERENCE_NEW_MOON.plus(Duration.ofMillis(millisAfterNewMoon));
        assertEquals(0, MoonPhaseCalculator.phaseAt(midFullMoon));
    }

    @Test
    void oneSynodicMonthLaterIsTheSamePhase() {
        // 0.3125 sits safely inside the [0.25, 0.375) bucket, clear of any bucket boundary, so
        // truncating a fractional-day cycle length to whole milliseconds below can't round the
        // second point across into a neighboring bucket the way testing at the boundary itself
        // (offset 0.0, i.e. comparing REFERENCE_NEW_MOON to exactly one cycle later) did.
        long offsetMillis = (long) (SYNODIC_MONTH_DAYS * 0.3125 * 86_400_000L);
        long cycleMillis = (long) (SYNODIC_MONTH_DAYS * 86_400_000L);
        ZonedDateTime reference = REFERENCE_NEW_MOON.plus(Duration.ofMillis(offsetMillis));
        ZonedDateTime oneCycleLater = reference.plus(Duration.ofMillis(cycleMillis));
        assertEquals(MoonPhaseCalculator.phaseAt(reference), MoonPhaseCalculator.phaseAt(oneCycleLater));
    }

    @Test
    void phaseIsAlwaysInRange() {
        ZonedDateTime arbitrary = ZonedDateTime.of(2026, 8, 17, 0, 0, 0, 0, ZoneOffset.UTC);
        int phase = MoonPhaseCalculator.phaseAt(arbitrary);
        assertTrue(phase >= 0 && phase <= 7);
    }

    @Test
    void describeRejectsOutOfRangePhases() {
        assertThrows(IllegalArgumentException.class, () -> MoonPhaseCalculator.describe(8));
        assertThrows(IllegalArgumentException.class, () -> MoonPhaseCalculator.describe(-1));
    }
}

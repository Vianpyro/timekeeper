package io.github.vianpyro.timekeeper.weathersync;

import io.github.vianpyro.timekeeper.SyncModule;
import io.github.vianpyro.timekeeper.config.TimekeeperConfig;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * A probabilistic clear/rain/thunder weather simulation.
 *
 * <p>This is intentionally not backed by any real weather data source for this v1 (see
 * PROJECT_SPEC.md "WeatherSync"): integrating a real API (e.g. Open-Meteo, which needs no API
 * key) is a deliberate future extension point, not a blocking TODO. A future contributor wiring
 * that up only needs to replace {@link #rollNextState}, which is the only place "what comes
 * next" is decided; the rest of the module (timing, applying weather, error handling) is
 * unaffected by where that decision comes from.
 */
public final class WeatherSyncModule implements SyncModule {

    // Duration ranges in ticks (20 ticks = 1 real second). Deliberately loose "credible" values,
    // not tied to any real meteorological model - see class Javadoc.
    private static final long MIN_CLEAR_TICKS = 12_000L;   // 10 minutes
    private static final long MAX_CLEAR_TICKS = 36_000L;   // 30 minutes
    private static final long MIN_RAIN_TICKS = 6_000L;     // 5 minutes
    private static final long MAX_RAIN_TICKS = 18_000L;    // 15 minutes
    private static final long MIN_THUNDER_TICKS = 3_000L;  // 2.5 minutes
    private static final long MAX_THUNDER_TICKS = 9_000L;  // 7.5 minutes

    private static final Map<WeatherState, Map<WeatherState, Double>> TRANSITIONS = buildTransitionTable();

    private final Logger logger;
    private final Random random;

    private boolean enabled;
    private long tickInterval = 20L;
    private volatile String lastError;
    private WeatherState currentState = WeatherState.CLEAR;
    private long ticksUntilTransition;
    private boolean primed;

    public WeatherSyncModule(Logger logger, TimekeeperConfig config) {
        this(logger, new Random(), config);
    }

    WeatherSyncModule(Logger logger, Random random, TimekeeperConfig config) {
        this.logger = logger;
        this.random = random;
        applyConfig(config);
    }

    @Override
    public String getName() {
        return "WeatherSync";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void tick(MinecraftServer server) {
        try {
            if (!primed) {
                beginState(server, WeatherState.CLEAR);
                primed = true;
                return;
            }

            ticksUntilTransition -= tickInterval;
            if (ticksUntilTransition > 0) {
                return;
            }

            beginState(server, rollNextState(currentState));
        } catch (RuntimeException e) {
            lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("WeatherSync tick failed: {}", lastError, e);
        }
    }

    private void beginState(MinecraftServer server, WeatherState state) {
        currentState = state;
        ticksUntilTransition = durationFor(state);
        applyWeather(server, state, ticksUntilTransition);
        lastError = null;
        if (logger.isDebugEnabled()) {
            logger.debug("WeatherSync transitioned to {} for {} ticks", state, ticksUntilTransition);
        }
    }

    private void applyWeather(MinecraftServer server, WeatherState state, long durationTicks) {
        int duration = (int) durationTicks;
        // MinecraftServer#setWeatherParameters (the same method vanilla's own /weather command
        // uses) governs weather server-wide; there is no per-dimension equivalent to target, so
        // unlike TimeSync/MoonSync this ignores syncAllWorlds.
        switch (state) {
            case CLEAR -> server.setWeatherParameters(duration, 0, false, false);
            case RAIN -> server.setWeatherParameters(0, duration, true, false);
            case THUNDER -> server.setWeatherParameters(0, duration, true, true);
        }
    }

    private WeatherState rollNextState(WeatherState from) {
        Map<WeatherState, Double> weights = TRANSITIONS.get(from);
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<WeatherState, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return from; // Guards against floating-point rounding at the tail of the distribution.
    }

    private long durationFor(WeatherState state) {
        return switch (state) {
            case CLEAR -> randomBetween(MIN_CLEAR_TICKS, MAX_CLEAR_TICKS);
            case RAIN -> randomBetween(MIN_RAIN_TICKS, MAX_RAIN_TICKS);
            case THUNDER -> randomBetween(MIN_THUNDER_TICKS, MAX_THUNDER_TICKS);
        };
    }

    private long randomBetween(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min));
    }

    private static Map<WeatherState, Map<WeatherState, Double>> buildTransitionTable() {
        Map<WeatherState, Map<WeatherState, Double>> table = new EnumMap<>(WeatherState.class);
        table.put(WeatherState.CLEAR, weights(0.55, 0.35, 0.10));
        table.put(WeatherState.RAIN, weights(0.35, 0.45, 0.20));
        table.put(WeatherState.THUNDER, weights(0.15, 0.55, 0.30));
        return table;
    }

    private static Map<WeatherState, Double> weights(double clear, double rain, double thunder) {
        Map<WeatherState, Double> weights = new EnumMap<>(WeatherState.class);
        weights.put(WeatherState.CLEAR, clear);
        weights.put(WeatherState.RAIN, rain);
        weights.put(WeatherState.THUNDER, thunder);
        return weights;
    }

    @Override
    public void reload(TimekeeperConfig config) {
        applyConfig(config);
    }

    private void applyConfig(TimekeeperConfig config) {
        this.enabled = config.isWeatherSyncEnabled();
        this.tickInterval = Math.max(1, config.getUpdateIntervalTicks());
    }

    @Override
    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }

    public WeatherState getCurrentState() {
        return currentState;
    }
}

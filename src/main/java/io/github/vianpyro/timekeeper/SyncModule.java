package io.github.vianpyro.timekeeper;

import io.github.vianpyro.timekeeper.config.TimekeeperConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * Common contract implemented by every sync module (TimeSync, MoonSync, WeatherSync).
 *
 * <p>Modules are independent: none of them call into another module, and each is individually
 * enabled/disabled through {@link TimekeeperConfig}. Coordination between modules that touch the
 * same underlying world state (TimeSync and MoonSync both write a world's day time) happens by
 * each module only ever modifying its own slice of that state - see
 * {@link io.github.vianpyro.timekeeper.MinecraftTime}.
 */
public interface SyncModule {

    /** Short, human-readable name used in logs and in {@code /timekeeper status}. */
    String getName();

    /** Whether this module is currently enabled, per the most recently applied config. */
    boolean isEnabled();

    /**
     * Applies this module's effect for the current cycle. Called on the server thread only, at
     * the cadence configured by {@code updateIntervalTicks}.
     */
    void tick(MinecraftServer server);

    /** Re-reads the given config and applies any settings that changed. */
    void reload(TimekeeperConfig config);

    /** Describes the last error raised by {@link #tick} or {@link #reload}, if any. */
    Optional<String> getLastError();
}

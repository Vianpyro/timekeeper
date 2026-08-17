package io.github.vianpyro.timekeeper;

import io.github.vianpyro.timekeeper.config.ConfigException;
import io.github.vianpyro.timekeeper.config.TimekeeperConfig;
import io.github.vianpyro.timekeeper.moonsync.MoonSyncModule;
import io.github.vianpyro.timekeeper.timesync.TimeSyncModule;
import io.github.vianpyro.timekeeper.weathersync.WeatherSyncModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the three sync modules: owns the config, drives {@code tick()}/{@code reload()}
 * calls, and is the single place that decides the shared {@code advance_time}/
 * {@code advance_weather} gamerules, since more than one module's behaviour depends on them.
 *
 * <p>{@link io.github.vianpyro.timekeeper.TimekeeperMod} only wires this class to Fabric's
 * events and commands; all actual behaviour lives here and in the individual modules.
 */
public final class TimekeeperManager {

    private final Logger logger;
    private final Path configPath;

    private final TimeSyncModule timeSync;
    private final MoonSyncModule moonSync;
    private final WeatherSyncModule weatherSync;
    private final List<SyncModule> modules;

    private volatile TimekeeperConfig config;
    private volatile String lastReloadError;
    private int ticksUntilNextRun;

    public TimekeeperManager(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.config = TimekeeperConfig.defaults();
        this.timeSync = new TimeSyncModule(logger, config);
        this.moonSync = new MoonSyncModule(logger, config);
        this.weatherSync = new WeatherSyncModule(logger, config);
        this.modules = List.of(timeSync, moonSync, weatherSync);
    }

    public void onServerStarted(MinecraftServer server) {
        loadConfig();
        applyToModules();
        applyGameRules(server);
        logger.info(
                "Timekeeper ready (enabled={}, timeSync={}, moonSync={}, weatherSync={})",
                config.isModEnabled(), config.isTimeSyncEnabled(), config.isMoonSyncEnabled(),
                config.isWeatherSyncEnabled());
    }

    public void onEndTick(MinecraftServer server) {
        if (!config.isModEnabled()) {
            return;
        }
        if (ticksUntilNextRun-- > 0) {
            return;
        }
        ticksUntilNextRun = Math.max(1, config.getUpdateIntervalTicks()) - 1;

        for (SyncModule module : modules) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.tick(server);
            } catch (RuntimeException e) {
                // A module's own tick() already catches its expected failure modes; this is a
                // last-resort net so one module misbehaving can never take the others down with it.
                logger.error("{} failed unexpectedly: {}", module.getName(), e.getMessage(), e);
            }
            if (config.isDebugLogging()) {
                module.getLastError().ifPresent(err -> logger.warn("{} reported an error: {}", module.getName(), err));
            }
        }
    }

    public void onServerStopping(MinecraftServer server) {
        // The cycle was driven artificially; there is no "natural" state to restore, only the
        // vanilla defaults to release it back to. See PROJECT_SPEC.md "Ne laisser aucune trace...".
        releaseGameRules(server);
    }

    public ReloadResult reload(MinecraftServer server) {
        loadConfig();
        if (lastReloadError != null) {
            return ReloadResult.failure(lastReloadError);
        }
        applyToModules();
        applyGameRules(server);
        ticksUntilNextRun = 0;
        return ReloadResult.success();
    }

    public void setEnabled(boolean enabled, MinecraftServer server) {
        config.setModEnabled(enabled);
        persistConfig();
        if (enabled) {
            applyGameRules(server);
        } else {
            releaseGameRules(server);
        }
    }

    private void loadConfig() {
        try {
            config = TimekeeperConfig.load(configPath);
            lastReloadError = null;
        } catch (ConfigException e) {
            lastReloadError = e.getMessage();
            logger.error("Failed to load {}, keeping the previous configuration: {}", configPath, e.getMessage());
        }
    }

    private void persistConfig() {
        try {
            config.save(configPath);
        } catch (ConfigException e) {
            lastReloadError = e.getMessage();
            logger.error("Failed to persist {}: {}", configPath, e.getMessage());
        }
    }

    private void applyToModules() {
        for (SyncModule module : modules) {
            module.reload(config);
        }
    }

    private void applyGameRules(MinecraftServer server) {
        boolean daylightManaged = config.isModEnabled() && (timeSync.isEnabled() || moonSync.isEnabled());
        boolean weatherManaged = config.isModEnabled() && weatherSync.isEnabled();
        // TimeSync/MoonSync both advance world clocks directly every cycle; the vanilla
        // "advance_time" rule must be off while either is active, or it would fight our writes
        // every tick (see ServerClockManager#tick). Likewise for WeatherSync and "advance_weather".
        GameRules gameRules = server.getGameRules();
        gameRules.set(GameRules.ADVANCE_TIME, !daylightManaged, server);
        gameRules.set(GameRules.ADVANCE_WEATHER, !weatherManaged, server);
    }

    private void releaseGameRules(MinecraftServer server) {
        GameRules gameRules = server.getGameRules();
        gameRules.set(GameRules.ADVANCE_TIME, true, server);
        gameRules.set(GameRules.ADVANCE_WEATHER, true, server);
    }

    public TimekeeperConfig getConfig() {
        return config;
    }

    public TimeSyncModule getTimeSync() {
        return timeSync;
    }

    public MoonSyncModule getMoonSync() {
        return moonSync;
    }

    public WeatherSyncModule getWeatherSync() {
        return weatherSync;
    }

    public List<SyncModule> getModules() {
        return modules;
    }

    public Optional<String> getLastReloadError() {
        return Optional.ofNullable(lastReloadError);
    }

    public record ReloadResult(boolean succeeded, String message) {

        public static ReloadResult success() {
            return new ReloadResult(true, "Timekeeper configuration reloaded.");
        }

        public static ReloadResult failure(String message) {
            return new ReloadResult(false, message);
        }
    }
}

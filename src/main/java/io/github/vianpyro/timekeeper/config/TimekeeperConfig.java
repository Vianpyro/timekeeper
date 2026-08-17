package io.github.vianpyro.timekeeper.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The mod's single flat config file ({@code config/timekeeper.properties}).
 *
 * <p>Every save rewrites the whole file from {@link #render()} rather than patching individual
 * lines in place. This keeps the file consistently commented (see PROJECT_SPEC.md
 * "Configuration") at the cost of discarding any manual formatting changes an operator made
 * between reloads - a deliberate trade-off for a config that is small enough to fully regenerate.
 */
public final class TimekeeperConfig {

    private boolean modEnabled = true;
    private boolean timeSyncEnabled = true;
    private boolean moonSyncEnabled = true;
    private boolean weatherSyncEnabled = true;
    private int offsetHours = 0;
    private boolean syncAllWorlds = false;
    private int updateIntervalTicks = 20;
    private int commandPermissionLevel = 2;
    private boolean debugLogging = false;

    private TimekeeperConfig() {
    }

    public static TimekeeperConfig defaults() {
        return new TimekeeperConfig();
    }

    /**
     * Loads the config from {@code path}, creating it with default values first if it does not
     * exist yet.
     *
     * @throws ConfigException if the file cannot be read, written, or contains an invalid value.
     */
    public static TimekeeperConfig load(Path path) throws ConfigException {
        if (!Files.exists(path)) {
            TimekeeperConfig config = defaults();
            config.save(path);
            return config;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new ConfigException("Failed to read " + path + ": " + e.getMessage(), e);
        }

        TimekeeperConfig config = defaults();
        config.modEnabled = readBoolean(props, "modEnabled", config.modEnabled);
        config.timeSyncEnabled = readBoolean(props, "timeSyncEnabled", config.timeSyncEnabled);
        config.moonSyncEnabled = readBoolean(props, "moonSyncEnabled", config.moonSyncEnabled);
        config.weatherSyncEnabled = readBoolean(props, "weatherSyncEnabled", config.weatherSyncEnabled);
        config.offsetHours = readInt(props, "offsetHours", config.offsetHours);
        config.syncAllWorlds = readBoolean(props, "syncAllWorlds", config.syncAllWorlds);
        config.updateIntervalTicks = readInt(props, "updateIntervalTicks", config.updateIntervalTicks);
        config.commandPermissionLevel = readInt(props, "commandPermissionLevel", config.commandPermissionLevel);
        config.debugLogging = readBoolean(props, "debugLogging", config.debugLogging);

        if (config.updateIntervalTicks < 1) {
            throw new ConfigException("updateIntervalTicks must be at least 1, got: " + config.updateIntervalTicks);
        }
        if (config.commandPermissionLevel < 0 || config.commandPermissionLevel > 4) {
            throw new ConfigException("commandPermissionLevel must be between 0 and 4, got: " + config.commandPermissionLevel);
        }

        return config;
    }

    /** Rewrites the config file at {@code path} with this instance's current values. */
    public void save(Path path) throws ConfigException {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, render());
        } catch (IOException e) {
            throw new ConfigException("Failed to write " + path + ": " + e.getMessage(), e);
        }
    }

    private String render() {
        return """
                # Timekeeper configuration
                #
                # This file is plain and safe to hand-edit. Run "/timekeeper reload" (or restart the
                # server) to apply changes - most settings take effect without a restart.

                # Master switch for the whole mod. When false, all three modules are inactive and
                # the advance_time/advance_weather gamerules are left at their vanilla defaults
                # (true). Toggled at runtime by "/timekeeper on" / "/timekeeper off"; this value
                # is what persists that state across restarts.
                modEnabled=%s

                # --- TimeSync ---

                # Keep the world's time of day in sync with the server's real system clock.
                timeSyncEnabled=%s

                # Shift the synced time by this many hours (may be negative, e.g. -5). Purely
                # additive to the real clock reading; does not affect MoonSync.
                offsetHours=%d

                # If true, TimeSync (and MoonSync's day count) is applied to every loaded
                # dimension. If false, only the Overworld is synced.
                syncAllWorlds=%s

                # --- MoonSync ---

                # Keep the in-game moon phase in sync with the real current lunar phase.
                moonSyncEnabled=%s

                # --- WeatherSync ---

                # Run the probabilistic clear/rain/thunder weather simulation.
                weatherSyncEnabled=%s

                # --- General ---

                # How often (in ticks; 20 ticks = 1 real second) enabled modules re-evaluate and
                # (re-)apply their state. Lower values are more precise but do marginally more
                # work per server tick.
                updateIntervalTicks=%d

                # Minimum operator permission level required to run /timekeeper commands (0-4).
                commandPermissionLevel=%d

                # Log extra detail about each sync cycle to the server console/log file.
                debugLogging=%s
                """.formatted(
                modEnabled, timeSyncEnabled, offsetHours, syncAllWorlds, moonSyncEnabled,
                weatherSyncEnabled, updateIntervalTicks, commandPermissionLevel, debugLogging);
    }

    private static boolean readBoolean(Properties props, String key, boolean fallback) throws ConfigException {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        raw = raw.trim();
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ConfigException("Invalid boolean value for '" + key + "': " + raw);
    }

    private static int readInt(Properties props, String key, int fallback) throws ConfigException {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid integer value for '" + key + "': " + raw);
        }
    }

    public boolean isModEnabled() {
        return modEnabled;
    }

    public void setModEnabled(boolean modEnabled) {
        this.modEnabled = modEnabled;
    }

    public boolean isTimeSyncEnabled() {
        return timeSyncEnabled;
    }

    public boolean isMoonSyncEnabled() {
        return moonSyncEnabled;
    }

    public boolean isWeatherSyncEnabled() {
        return weatherSyncEnabled;
    }

    public int getOffsetHours() {
        return offsetHours;
    }

    public boolean isSyncAllWorlds() {
        return syncAllWorlds;
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public int getCommandPermissionLevel() {
        return commandPermissionLevel;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }
}

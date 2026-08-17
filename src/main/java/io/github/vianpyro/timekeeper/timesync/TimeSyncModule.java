package io.github.vianpyro.timekeeper.timesync;

import io.github.vianpyro.timekeeper.MinecraftTime;
import io.github.vianpyro.timekeeper.SyncModule;
import io.github.vianpyro.timekeeper.config.TimekeeperConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Keeps the synced world clock(s) time-of-day aligned with the server's real system clock.
 *
 * <p>Minecraft 26.x drives time through named, server-wide {@link WorldClock}s (see {@link
 * ServerClockManager}) rather than a per-dimension counter. Only the time-of-day component of a
 * clock's total tick count is touched here; the day-count component is read back and preserved
 * as-is so this module never fights {@link io.github.vianpyro.timekeeper.moonsync.MoonSyncModule}
 * over the same value. See {@link MinecraftTime}.
 */
public final class TimeSyncModule implements SyncModule {

    private final Logger logger;
    private final Clock clock;

    private boolean enabled;
    private int offsetHours;
    private boolean syncAllWorlds;
    private volatile String lastError;
    private volatile long lastSyncedTicksOfDay = -1;

    public TimeSyncModule(Logger logger, TimekeeperConfig config) {
        this(logger, Clock.systemDefaultZone(), config);
    }

    TimeSyncModule(Logger logger, Clock clock, TimekeeperConfig config) {
        this.logger = logger;
        this.clock = clock;
        applyConfig(config);
    }

    @Override
    public String getName() {
        return "TimeSync";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void tick(MinecraftServer server) {
        try {
            long ticksOfDay = RealTimeConverter.toGameTicks(clock, offsetHours);
            ServerClockManager clockManager = server.clockManager();
            for (Holder<WorldClock> worldClock : targetClocks(server)) {
                long dayCount = MinecraftTime.dayCount(clockManager.getTotalTicks(worldClock));
                // Only the time-of-day component is ours; the day count belongs to MoonSync.
                clockManager.setTotalTicks(worldClock, MinecraftTime.combine(dayCount, ticksOfDay));
            }
            lastSyncedTicksOfDay = ticksOfDay;
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("TimeSync tick failed: {}", lastError, e);
        }
    }

    /** The Overworld clock only, or every registered world clock when {@code syncAllWorlds}. */
    private List<? extends Holder<WorldClock>> targetClocks(MinecraftServer server) {
        Registry<WorldClock> registry = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
        if (syncAllWorlds) {
            return registry.listElements().toList();
        }
        return List.of(registry.getOrThrow(WorldClocks.OVERWORLD));
    }

    @Override
    public void reload(TimekeeperConfig config) {
        applyConfig(config);
    }

    private void applyConfig(TimekeeperConfig config) {
        this.enabled = config.isTimeSyncEnabled();
        this.offsetHours = config.getOffsetHours();
        this.syncAllWorlds = config.isSyncAllWorlds();
    }

    @Override
    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }

    public OptionalLong getLastSyncedTicksOfDay() {
        long value = lastSyncedTicksOfDay;
        return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }
}

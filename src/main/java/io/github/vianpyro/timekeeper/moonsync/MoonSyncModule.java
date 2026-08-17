package io.github.vianpyro.timekeeper.moonsync;

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
import java.util.OptionalInt;

/**
 * Keeps the synced world clock(s) moon phase aligned with the real current lunar phase.
 *
 * <p>Minecraft 26.x drives time through named, server-wide {@link WorldClock}s (see {@link
 * ServerClockManager}) rather than a per-dimension counter. Only the day-count component of a
 * clock's total tick count is touched here (set directly to the desired phase index, since only
 * its value modulo 8 ever matters to vanilla); the time-of-day component is read back and
 * preserved as-is so this module never fights {@link
 * io.github.vianpyro.timekeeper.timesync.TimeSyncModule} over the same value. See
 * {@link MinecraftTime}.
 */
public final class MoonSyncModule implements SyncModule {

    private final Logger logger;
    private final Clock clock;

    private boolean enabled;
    private boolean syncAllWorlds;
    private volatile String lastError;
    private volatile int lastSyncedPhase = -1;

    public MoonSyncModule(Logger logger, TimekeeperConfig config) {
        this(logger, Clock.systemDefaultZone(), config);
    }

    MoonSyncModule(Logger logger, Clock clock, TimekeeperConfig config) {
        this.logger = logger;
        this.clock = clock;
        applyConfig(config);
    }

    @Override
    public String getName() {
        return "MoonSync";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void tick(MinecraftServer server) {
        try {
            int phase = MoonPhaseCalculator.currentPhase(clock);
            ServerClockManager clockManager = server.clockManager();
            for (Holder<WorldClock> worldClock : targetClocks(server)) {
                long ticksOfDay = MinecraftTime.ticksOfDay(clockManager.getTotalTicks(worldClock));
                clockManager.setTotalTicks(worldClock, MinecraftTime.combine(phase, ticksOfDay));
            }
            lastSyncedPhase = phase;
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.warn("MoonSync tick failed: {}", lastError, e);
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
        this.enabled = config.isMoonSyncEnabled();
        this.syncAllWorlds = config.isSyncAllWorlds();
    }

    @Override
    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }

    public OptionalInt getLastSyncedPhase() {
        int value = lastSyncedPhase;
        return value < 0 ? OptionalInt.empty() : OptionalInt.of(value);
    }
}

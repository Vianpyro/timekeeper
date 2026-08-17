package io.github.vianpyro.timekeeper.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.vianpyro.timekeeper.SyncModule;
import io.github.vianpyro.timekeeper.TimekeeperManager;
import io.github.vianpyro.timekeeper.config.TimekeeperConfig;
import io.github.vianpyro.timekeeper.moonsync.MoonPhaseCalculator;
import io.github.vianpyro.timekeeper.timesync.RealTimeConverter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Registers {@code /timekeeper reload|status|on|off} (see PROJECT_SPEC.md "Commandes admin"). */
public final class TimekeeperCommand {

    private TimekeeperCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, TimekeeperManager manager) {
        dispatcher.register(Commands.literal("timekeeper")
                .requires(Commands.hasPermission(permissionCheckFor(manager.getConfig().getCommandPermissionLevel())))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource(), manager)))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), manager)))
                .then(Commands.literal("on").executes(context -> setEnabled(context.getSource(), manager, true)))
                .then(Commands.literal("off").executes(context -> setEnabled(context.getSource(), manager, false))));
    }

    /**
     * Maps the config's plain 0-4 operator level (see PROJECT_SPEC.md "Commandes admin") onto the
     * named permission tiers Minecraft 26.x uses in place of the old integer levels - 2
     * ("gamemasters") is the same default vanilla uses for e.g. {@code /time} and {@code /weather}.
     */
    private static PermissionCheck permissionCheckFor(int level) {
        return switch (level) {
            case 0 -> Commands.LEVEL_ALL;
            case 1 -> Commands.LEVEL_MODERATORS;
            case 2 -> Commands.LEVEL_GAMEMASTERS;
            case 3 -> Commands.LEVEL_ADMINS;
            default -> Commands.LEVEL_OWNERS;
        };
    }

    private static int reload(CommandSourceStack source, TimekeeperManager manager) {
        TimekeeperManager.ReloadResult result = manager.reload(source.getServer());
        if (result.succeeded()) {
            source.sendSuccess(() -> Component.literal("Timekeeper configuration reloaded."), true);
            return 1;
        }
        source.sendFailure(Component.literal("Failed to reload Timekeeper configuration: " + result.message()));
        return 0;
    }

    private static int setEnabled(CommandSourceStack source, TimekeeperManager manager, boolean enabled) {
        manager.setEnabled(enabled, source.getServer());
        source.sendSuccess(() -> Component.literal("Timekeeper is now " + (enabled ? "enabled" : "disabled") + "."), true);
        return 1;
    }

    private static int status(CommandSourceStack source, TimekeeperManager manager) {
        TimekeeperConfig config = manager.getConfig();
        source.sendSuccess(() -> Component.literal(
                "Timekeeper is " + (config.isModEnabled() ? "enabled" : "disabled") + "."), false);

        for (SyncModule module : manager.getModules()) {
            String line = " - " + module.getName() + ": " + describe(module, manager);
            source.sendSuccess(() -> Component.literal(line), false);
            module.getLastError().ifPresent(error -> {
                String errorLine = "   last error: " + error;
                source.sendSuccess(() -> Component.literal(errorLine), false);
            });
        }

        manager.getLastReloadError().ifPresent(error -> {
            String errorLine = "Last reload error: " + error;
            source.sendSuccess(() -> Component.literal(errorLine), false);
        });

        return 1;
    }

    private static String describe(SyncModule module, TimekeeperManager manager) {
        if (!module.isEnabled()) {
            return "disabled";
        }
        if (module == manager.getTimeSync()) {
            OptionalLong ticks = manager.getTimeSync().getLastSyncedTicksOfDay();
            return ticks.isPresent()
                    ? "enabled (synced time-of-day: " + RealTimeConverter.formatAsClockTime(ticks.getAsLong()) + ")"
                    : "enabled (not yet synced)";
        }
        if (module == manager.getMoonSync()) {
            OptionalInt phase = manager.getMoonSync().getLastSyncedPhase();
            return phase.isPresent()
                    ? "enabled (moon phase: " + MoonPhaseCalculator.describe(phase.getAsInt()) + ")"
                    : "enabled (not yet synced)";
        }
        if (module == manager.getWeatherSync()) {
            return "enabled (current weather: " + manager.getWeatherSync().getCurrentState().name().toLowerCase(Locale.ROOT) + ")";
        }
        return "enabled";
    }
}

package io.github.vianpyro.timekeeper;

import io.github.vianpyro.timekeeper.command.TimekeeperCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Mod entrypoint. Deliberately thin: it only builds the {@link TimekeeperManager} and wires it to
 * Fabric's events/commands. All actual behaviour lives in {@link TimekeeperManager} and the
 * individual {@link SyncModule} implementations - see PROJECT_SPEC.md "Architecture attendue".
 */
public final class TimekeeperMod implements ModInitializer {

    public static final String MOD_ID = "timekeeper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
        TimekeeperManager manager = new TimekeeperManager(LOGGER, configPath);

        // Registered under "main" (not "server"), so this also runs the integrated server used
        // by singleplayer/LAN worlds, not only dedicated servers - see PROJECT_SPEC.md
        // "Environnement de type : server-side only ... Doit fonctionner tel quel en solo".
        ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(manager::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(manager::onEndTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
                TimekeeperCommand.register(dispatcher, manager));
    }
}

# Timekeeper

A server-side [Fabric](https://fabricmc.net/) mod for Minecraft 26.2 that keeps a world's time of
day, moon phase, and weather in sync with the real world.

Timekeeper is an independent, from-scratch rewrite inspired by the general idea behind
NorthWestTreesGaming's closed-source "RealTimeMod-Reborn" - no code, assets, or decompiled output
from that project were used.

## Features

Three independent modules, each on/off in the config with no hard dependency between them:

- **TimeSync** - matches the world clock's time of day to the server's real system clock, with an
  optional hour offset.
- **MoonSync** - matches the in-game moon phase to the real current lunar phase, computed locally
  from the Julian date (no network access, no API key).
- **WeatherSync** - a probabilistic clear/rain/thunder simulation. Not backed by a real weather
  API in this v1 - see [Extending WeatherSync](#extending-weathersync).

Works out of the box in singleplayer (the integrated server) as well as on a dedicated server; it
has no client-side component at all.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api)
   for Minecraft 26.2.
2. Drop the Timekeeper jar into your `mods/` folder.
3. Start the server (or singleplayer world) once to generate `config/timekeeper.properties`.

## Configuration

Everything lives in one flat, commented file: `config/timekeeper.properties`. Edit it and run
`/timekeeper reload` to apply changes without restarting.

| Key                     | Default | Meaning                                             |
| ------------------------ | ------- | ---------------------------------------------------- |
| `modEnabled`             | `true`  | Master switch; persists `/timekeeper on`/`off`.       |
| `timeSyncEnabled`        | `true`  | Enable TimeSync.                                      |
| `offsetHours`            | `0`     | Hours added to (or subtracted from) the synced time.  |
| `syncAllWorlds`          | `false` | Sync every dimension's clock, not just the Overworld. |
| `moonSyncEnabled`        | `true`  | Enable MoonSync.                                      |
| `weatherSyncEnabled`     | `true`  | Enable WeatherSync.                                   |
| `updateIntervalTicks`    | `20`    | How often enabled modules re-apply their state.       |
| `commandPermissionLevel` | `2`     | Operator level required for `/timekeeper` commands.   |
| `debugLogging`           | `false` | Log extra detail for each sync cycle.                 |

## Commands

All under `/timekeeper`, requiring operator level `commandPermissionLevel` (default `2`):

- `reload` - reload the config file without restarting the server.
- `status` - show whether the mod and each module is on, the last synced time/moon phase/weather,
  and the last error (if any).
- `on` / `off` - toggle every module at once. `off` releases the `advance_time` /
  `advance_weather` gamerules back to vanilla rather than trying to reconstruct a "natural" time -
  the cycle was driven artificially, so there is nothing to restore, only to hand back cleanly.

## Architecture

```
io.github.vianpyro.timekeeper
├── TimekeeperMod        entrypoint: wires everything below to Fabric events/commands, no logic
├── TimekeeperManager     owns the config, drives tick()/reload(), owns the shared gamerules
├── SyncModule            the interface all three modules implement
├── MinecraftTime         shared DayTime <-> (day count, time of day) helpers
├── config/               TimekeeperConfig: load/save config/timekeeper.properties
├── command/              /timekeeper reload|status|on|off
├── timesync/             TimeSyncModule + RealTimeConverter (real clock -> game ticks)
├── moonsync/             MoonSyncModule + MoonPhaseCalculator (Julian date -> moon phase)
└── weathersync/          WeatherSyncModule + WeatherState (the clear/rain/thunder simulation)
```

Each module only implements `tick()`, `reload()`, `isEnabled()`, and reports its own last error;
`TimekeeperManager` is the only place that knows about all three at once.

### `DayTime` vs. `GameTime`

Minecraft persists two independent counters per world: **`GameTime`** (the world's real age in
ticks, used by statistics/advancements) and **`DayTime`** (drives the day/night cycle and the moon
phase, `dayCount % 8`). TimeSync and MoonSync only ever touch `DayTime` - `GameTime` is never
read or written, directly or indirectly, by any part of this mod. See the comment at
`TimekeeperManager#applyGameRules` and the individual modules for where this matters in code.

### No trace left behind

A mod cannot run any code after it has been removed - that is a hard limitation of every mod
loader, not something Timekeeper works around. The only state Timekeeper can leave behind in a
world is the `advance_time` / `advance_weather` gamerules if they're left disabled. Two
safety nets guard against that: running `/timekeeper off` before removing the jar, and a
`ServerLifecycleEvents.SERVER_STOPPING` hook that does the same automatically on every clean
shutdown. **That hook assumes a clean shutdown** - it cannot run after a crash or a `kill -9`, so
if the server goes down that way with Timekeeper active, reset the gamerules manually
(`/gamerule advance_time true` and `/gamerule advance_weather true`) before removing the jar.

### Extending WeatherSync

Wiring up a real weather API (e.g. [Open-Meteo](https://open-meteo.com/), which needs no API key)
is a deliberate future extension, not a blocking TODO for this v1. `WeatherSyncModule.rollNextState`
is the only place "what happens next" is decided - swap its probability table for a real forecast
lookup and the rest of the module (timing, applying weather, error handling, config) needs no
changes.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for building locally, the manual test checklist, and repo
conventions.

## License

[MIT](LICENSE)

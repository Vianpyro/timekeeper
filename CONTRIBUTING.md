# Contributing to Timekeeper

## Building locally

Requires JDK 25 (exactly - see `PROJECT_SPEC.md` for why not 26). `./gradlew` downloads
everything else itself.

```bash
./gradlew build
```

This compiles the mod, runs the unit tests (see [Tests](#tests) below), and produces
`build/libs/timekeeper-<version>.jar`.

To try it in a real singleplayer world or a local dedicated server, drop that jar (not the
`-sources.jar`) into a `mods/` folder alongside [Fabric Loader](https://fabricmc.net/use/) and
[Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.2.

## Project structure

See the "Architecture" section of [README.md](README.md) for the package layout. In short: one
package per module (`timesync`, `moonsync`, `weathersync`), each implementing the `SyncModule`
interface (`tick()` / `reload()` / `isEnabled()` / `getLastError()`) and staying independent of
the other two. `TimekeeperManager` is the only class that coordinates across modules (it owns the
shared `advance_time` / `advance_weather` gamerules, since more than one module's behaviour
depends on them); `TimekeeperMod` itself only wires things up and holds no logic.

## Tests

There is no automated in-game testing for this v1 - Fabric mods don't have a simple way to spin up
a real game loop in CI. What *is* unit-tested is the pure, deterministic math each module is built
on (`RealTimeConverterTest`, `MoonPhaseCalculatorTest`, `MinecraftTimeTest`), since that needs no
Minecraft runtime at all and is exactly where an off-by-one would be easy to ship unnoticed.
`./gradlew build` runs these as part of `check`.

Everything that actually touches the game needs a manual pass after any change to `timesync/`,
`moonsync/`, `weathersync/`, `command/`, or `config/`:

- [ ] Start a server with all three modules enabled. Confirm the in-game time roughly matches
      your real clock within a few seconds (accounting for `offsetHours` if you set one).
- [ ] Set a non-zero `offsetHours`, `/timekeeper reload`, and confirm the synced time shifts by
      that many hours.
- [ ] Use `/time set` and an admin tool (or patience) to check the moon phase over a simulated
      week - it should track the real lunar phase, not vanilla's 8-day cycle.
- [ ] `/timekeeper reload` with the server running: no crash, no console errors, config changes
      take effect.
- [ ] `/timekeeper reload` with a deliberately broken config value (e.g. `offsetHours=abc`): the
      command reports failure, the server keeps running on the last-good config, and
      `/timekeeper status` shows the error.
- [ ] `/timekeeper off` then check `/gamerule advance_time` and `/gamerule advance_weather`: both
      back to `true`.
- [ ] `/timekeeper on` re-enables all three modules and re-disables those gamerules.
- [ ] Stop the server normally with modules still enabled, then check `advance_time` /
      `advance_weather` in the saved `level.dat` (or just restart and query them): both `true`,
      confirming the `SERVER_STOPPING` safety net worked.
- [ ] Load a singleplayer world (integrated server) with no special config: all three modules
      behave the same as on a dedicated server.
- [ ] Watch WeatherSync for a while: it actually transitions between clear/rain/thunder rather
      than getting stuck in one state.

## Branch and commit conventions

- Branch names: `<category>/<slug>`, e.g. `feature/moon-phase-sync`, `fix/weather-lockup`.
  Allowed categories: `feature`, `fix`, `chore`, `docs`, `refactor`, `perf`, `test`, `build`,
  `ci`, `style`, `revert`, `hotfix`, `security` (checked by `pr-hygiene.yml`).
- PR titles follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat: `, `fix: `, `chore: `, ...) - also checked by `pr-hygiene.yml`. `main` uses squash
  merges, so the PR title becomes the commit message on `main`, which is what `cd.yml` reads to
  decide the next version and changelog.
- `main` is protected: PRs only, CI must be green.

## Release process

Mostly automated once a release PR is merged - see the "CI/CD" section of `PROJECT_SPEC.md` for
the full decide/propose/publish design. As a maintainer:

1. Merge PRs to `main` as normal, using Conventional Commit titles.
2. `cd.yml` opens or updates the `chore(release): publish vX.Y.Z` PR. **It's pushed by the
   default `GITHUB_TOKEN`, so GitHub schedules its CI/pr-hygiene checks in an
   "approval-required" state rather than starting them immediately** - open the PR and click
   *Approve and run workflows* in the banner GitHub shows there. This is the one manual step
   this pipeline has; see "Why there's a manual click" below for why it's there on purpose
   instead of removed with a token.
3. Once checks are green, review the version bump and changelog `cd.yml` computed, then merge.
4. That merge tags the release and fires a `repository_dispatch` to `release.yml`, which takes
   it from there (build, draft GitHub release, publish to Modrinth, undraft) with no further
   action needed - this part needs no approval click, since `repository_dispatch` is one of the
   two event types GitHub always lets a `GITHUB_TOKEN` trigger (see the comment at the top of
   `cd-tag-release.sh`).

### Why there's a manual click

`cd.yml` uses the repository's own default `GITHUB_TOKEN` throughout, on purpose, so this
project needs no standing secret with write access beyond what Actions already provides. The
cost: GitHub's anti-recursion safeguard holds `pull_request` workflow runs it creates for a
`GITHUB_TOKEN`-authenticated PR pending approval, rather than starting them immediately - that's
the click in step 2 above. If that becomes annoying, the fix is a repository secret holding a
token with a real (non-bot) identity - e.g. a fine-grained
[personal access token](https://github.com/settings/personal-access-tokens) scoped to this repo
with Contents + Pull requests read/write - used in place of `github.token` for `cd.yml`'s
checkout and `GH_TOKEN`. That removes the click entirely (a PAT-authenticated PR isn't subject to
the same hold) at the cost of a token to create and eventually rotate.

### One-time repository setup

A few things GitHub doesn't let you express as committed files - set these up once:

- Branch protection on `main`: require a pull request, require the `CI / Build` check, disallow
  direct pushes.
- Merge method: squash merge only (`cd.yml`'s release detection relies on the squash commit
  subject matching the release PR title).
- Repository secret `MODRINTH_TOKEN`: a Modrinth API token with permission to publish versions
  to the project below.
- Repository variable `MODRINTH_PROJECT_ID`: the Modrinth project's ID, once it exists (create
  the project on Modrinth first - `release.yml` publishes versions to an existing project, it
  doesn't create one).

### If a release gets stuck

If a release PR was merged and the tag exists but `release.yml` never ran (check the Actions tab
for a matching run) - most likely the `repository_dispatch` call in `cd-tag-release.sh` failed or
was skipped for some reason worth looking into first - re-run it manually without needing
another version bump:

```bash
gh workflow run release.yml -f tag=vX.Y.Z
```

or from the Actions tab: *Release* → *Run workflow* → enter the tag. It checks out that exact
tag's commit regardless of which branch is selected in the dropdown, so this is safe to use even
if `main` has moved on since.

# Spike notes — Part 1 (Claims / territory layer)

Per spec §6's spike instructions: "if a spec §6 assumption fails, STOP and report — do
not improvise a replacement design." This doc records what could and could not be
verified in this sandbox, and the design decisions made as a result. **Nothing here
should be treated as confirmed until a maintainer compiles `:mod` and boots a server
with the real MineColonies/OPAC jars.**

## 1. Network sandbox findings

This sandbox's outbound network allowlist blocks every host needed to resolve the two
soft-dependency jars and the NeoForge toolchain itself:

| Host | Needed for | Result |
| --- | --- | --- |
| `maven.neoforged.net` | NeoForge / ModDevGradle artifacts | `403 host_not_allowed` |
| `cursemaven.com` / `www.cursemaven.com` | MineColonies (`curse.maven:minecolonies-245506:8186694`) | `403 host_not_allowed` |
| `api.modrinth.com` | Open Parties and Claims (`maven.modrinth:open-parties-and-claims:b16WHzyv`) | `403 host_not_allowed` |
| `maven.ldtteam.com` | MineColonies's own dependency chain | `403 host_not_allowed` |
| `maven.minecraftforge.net`, `libraries.minecraft.net`, `piston-meta.mojang.com` | Minecraft/Forge libraries | `403 host_not_allowed` |

Allowed: `github.com`, `repo.maven.apache.org` (Maven Central), `services.gradle.org`,
`plugins.gradle.org`.

**Consequence**: `:mod` cannot be compiled, tested, or even have its dependencies
resolved in this sandbox. This is why the project is split into `:core` (pure Java,
zero Minecraft/NeoForge/MineColonies/OPAC deps, builds and tests against Maven Central
only) and `:mod` (the NeoForge mod, depends on `:core`, only buildable in CI/on a
maintainer box with full internet).

`./gradlew :core:test` passes (20 tests). `./gradlew :mod:build` / `./gradlew build`
have **not** been run successfully here — `.github/workflows/build.yml` runs them with
full CI network access, and that result is the first real compile signal.

**Lesson from the first playtest — a compiling jar can still be unloadable.** The
spike scaffold omitted the MDK's resource-expansion step, so the built jar's
`neoforge.mods.toml` still contained literal `${...}` placeholders. FML rejected it
(`Error during pre-loading phase: File mods\pcmc_territory.jar is not a valid mod
file`) and the client then died in an unrelated-looking Quark
`Where is minecraft???!` render-init crash — the usual mask when any mod fails
pre-loading (first error in `latest.log` is the real one). Fixed by expanding the
metadata in `:mod`'s `processResources`; `build.yml` now validates the **packaged**
jar (placeholder grep, TOML parse, modId + dependency assertions, bundled `:core`
classes present), so "compiles but won't load" packaging failures turn CI red
instead of surfacing on a playtester's machine.

## 2. MineColonies API (compile-verified)

`mod/src/main/java/com/theasshats/pcmcterritory/integration/minecolonies/MineColoniesColonyLookup.java`
calls:

```java
IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, pos)
```

returning an `IColony` whose `getID()` is the colony id bound via `/realm found`. This
is the documented 1.21.1 `IColonyManager` entry point, and CI now compiles it clean
against the real `minecolonies-1.1.1327-1.21.1-snapshot` jar (CurseForge project
245506, file 8186694) — the spike's guess survived contact. Runtime behavior (does
standing in a colony actually resolve its id?) still needs the docs/PLAYTESTING.md
pass.

If the method/package name differs on the real jar, `MineColoniesColonyLookup` is the
**only** file that needs to change — `ColonyLookup` (the `:core` interface) and every
caller are insulated from this.

**Also unverified**: whether MineColonies fires a claim/border-change event suitable
for cache invalidation, and whether a per-chunk capability exposes the owning colony id
directly (an O(1) alternative to the position lookup above — flagged in the file as a
perf upgrade to check once this compiles). Part 1 does not depend on either; see §4.

## 3. Open Parties and Claims API (compile-verified)

`mod/src/main/java/com/theasshats/pcmcterritory/integration/opac/OpacClaimLookup.java`
calls:

```java
OpenPACServerAPI.get(server).getServerClaimsManager()
    .get(ResourceLocation.parse(levelId), chunkX, chunkZ)
```

returning an `IPlayerChunkClaimAPI` whose `getPlayerId()` becomes the `ClaimKey` an
entity binds.

**Maven coordinate (lesson):** the spike originally wrote the Modrinth coordinate as
`0.26.2-neoforge`, guessing a `<version>-<loader>` suffix format. CI *could* reach
Modrinth maven and got a clean "not found" — OPAC's actual version numbers embed
loader and MC version as a *prefix* (`neoforge-1.21.1-0.26.2`, matching the jar
filename). The dependency is now pinned by Modrinth version *id* (`b16WHzyv`, from
the pack repo's `mods/open-parties-and-claims.pw.toml`), which is immutable and
can't be mis-formatted. When adding a Modrinth dep without API access, take the
version id straight from a packwiz manifest or the version page URL.

**API surface (lesson):** the spike's guessed packages were wrong, and the first real
CI compile against the `neoforge-1.21.1-0.26.2` jar caught all of it — exactly the
"one file to fix" failure mode this layout was designed for. The corrected surface,
cross-checked against the OPAC `1.21` branch source
(github.com/thexaero/open-parties-and-claims, `minecraft_version=1.21.1`):

- `OpenPACServerAPI` lives in `xaero.pac.common.server.api` (not `server.claims.api`);
  static `get(MinecraftServer)`.
- `IPlayerChunkClaimAPI` lives in `xaero.pac.common.claims.player.api`; its
  `getPlayerId()` is documented **not-null** — server claims carry a dedicated owner
  UUID (`PlayerConfig.SERVER_CLAIM_UUID`) rather than a null owner.
- `IServerClaimsManagerAPI` (in `xaero.pac.common.server.claims.api`, as guessed) is
  **generic-free** on 1.21 — the old `<?, ?, ?>` wildcard form doesn't compile.
- `get(ResourceLocation dimension, int x, int z)` takes **chunk** coordinates (a
  `ChunkPos` overload exists alongside it).

If a future OPAC version moves these types, `OpacClaimLookup` is the one file to fix;
`ClaimLookup` and its callers are unaffected. Runtime behavior (does a claimed chunk
actually resolve?) still needs the docs/PLAYTESTING.md pass.

**Also unverified**: OPAC's claim-change event/listener API. Part 1 does not depend on
it; see §4.

**For a future Part 2 spike**: whether OPAC exposes (a) a settable per-player claim
limit, or (b) a cancellable claim-creation event. Either would let a future
"government-issued claim allowance" feature (README's "Looking ahead" section)
*prevent* an over-allowance claim outright instead of just declining to bind it to a
realm. Part 1 does not depend on either.

## 4. Cache invalidation design — TTL instead of an unverifiable event hook

Spec §6 calls for the chunk→entity resolver to be "event-driven and position-local."
The spike could not pin down concrete MineColonies/OPAC claim-change event classes
(§2/§3), and improvising names for events that may not exist (or may have changed
across versions) would be exactly the kind of unverified replacement design the spike
instructions say to avoid.

Instead, `TerritoryResolver` (`:core`) uses **two independent invalidation triggers**:

1. **Registry edits** (`/realm found`, member/name changes, entity removal) call
   `invalidateAll()` immediately via `RegistryListener` — these are rare admin actions,
   so a full clear is cheap and exact, and this part has no external-API dependency at
   all.
2. **External claim changes** (a MineColonies colony border moves, an OPAC claim is
   made/abandoned) are handled by a short per-entry TTL
   (`TerritoryResolver.DEFAULT_TTL_TICKS = 20`, i.e. ~1 second at 20 TPS, wired to
   `ServerLevel#getGameTime()` in `RealmsSavedData`). A cache entry older than the TTL
   is treated as a miss and recomputed on the *next lookup* — there is no background
   tick scan, so this stays position-local and only costs anything when a position is
   already being queried (combat, block break, commands).

This is **strictly more robust** than a hand-guessed event hook: it works identically
regardless of whether/how MineColonies or OPAC signal claim changes, degrades to "1
second of staleness" in the worst case, and the existing TTL-expiry test
(`TerritoryResolverTest#cacheEntryExpiresAfterTtlAndPicksUpExternalClaimChange`)
demonstrates the recompute-on-expiry behavior with a fake clock. If a maintainer later
confirms a real change-event API exists, it can be added as a **third**, immediate
trigger (call `invalidate(chunk)` from the event handler) without removing the TTL
fallback — the TTL is the floor, not a replacement for a faster signal.

Negative results ("wilderness", no colony/claim) are cached and TTL'd identically, so
repeated lookups over ungoverned land don't repeatedly call into MineColonies/OPAC.

## 5. `pcmc-killfeed` reference

The task asked to follow `theasshats/pcmc-killfeed`'s naming conventions, but that repo
is outside this session's configured GitHub access (`Access denied: repository
"theasshats/pcmc-killfeed" is not configured for this session`), and an anonymous
`git ls-remote` also failed (no credentials). Standard NeoForge 1.21.1/ModDevGradle
conventions were used instead: `gradle.properties`-driven `neoforge.mods.toml`
templating, `${mod_id}`/`${mod_version}` placeholders, `javafml` mod loader, package
root `com.theasshats.pcmcterritory`. A maintainer comparing against `pcmc-killfeed`
should flag any divergence worth aligning.

## 6. What this means for "done"

- `:core` is the part of spec §6 that's fully implemented *and* verified
  (`./gradlew :core:test`, 18/18 passing): the registry, entity model, reverse indexes,
  and the TTL-based resolver/cache.
- `:mod` (NeoForge glue, MineColonies/OPAC adapters, commands, SavedData, public API,
  events) is implemented per the documented API surfaces above but **compiles for the
  first time in CI**, not here. Any compile error surfaced by `.github/workflows/build.yml`
  on this branch is the first real signal and should be fixed there rather than
  papered over.
- Everything runtime (does `/realm found` actually bind a MineColonies colony id? does
  `/realm whogoverns` answer correctly inside a real OPAC claim? what's the actual TPS
  cost?) is on the maintainer playtest checklist in `README.md` — green CI is not
  in-game verification.

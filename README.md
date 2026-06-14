# PCMC Territory

The territory substrate for Project Commonwealth's governance system (spec §6, Part
1 of the [governance mod plan](https://github.com/theasshats/project-commonwealth)).
Given a `(level, chunkPos)`, resolves which political entity — if any — governs that
chunk.

**Scope fence (Part 1 only)**: no tiers, laws, federation, treasury, or Numismatics
dependency. Just the chunk → entity resolver, a minimal entity registry, and three
debug commands. Part 2 (`pcmc-realms`) builds government on top of this via the
public `api` package.

This mod **reads** MineColonies colony borders and Open Parties and Claims chunk
claims to determine territory ownership. It **owns and creates zero claims of its
own** — it never calls into either mod's claim-creation APIs.

## Looking ahead (Part 2+)

Part 1 deliberately stops at "read MineColonies/OPAC, resolve a chunk to an entity."
A few directions discussed for Part 2 (`pcmc-realms`) and beyond are recorded here so
they aren't lost before that work is scoped:

- **Combined city/faction borders.** The data model already supports this: a
  `RealmEntity` can hold both `colonyIds` and bound `claimKeys`. A chunk inside a
  colony resolves to that colony's realm, and an OPAC-claimed chunk outside every
  colony resolves to the realm that bound it — colony borders take strict precedence
  and shield their chunks from outside claims. So a city's MineColonies border plus
  any OPAC claims its members bind to it already resolve as one combined territory
  today — Part 1 is missing only a player-facing bind command (`/realm debug
  bindclaim` is the op + OFFICER-only stand-in for exercising that path).
- **Tier-gated claim allowances.** Citizens start with a small OPAC claim allowance;
  MineColonies city-tier growth raises it; confederation/faction leaders hold a
  larger pool they can grant to members as "charters" (e.g. to start an outpost or
  mine). This is a Part 2 governance feature, not Part 1 scope.
- **Hard-limiting claims by government.** A stronger version of the above, where the
  government can *prevent* an over-allowance OPAC claim outright rather than just
  decline to count it as territory. This needs OPAC to expose either a settable
  per-player claim limit or a cancellable claim-creation event — **both unverified**
  (see `docs/SPIKE-PART1.md` §3). Until one of those is confirmed against the real
  jar, the fallback is enforcing the allowance at the *binding* layer: an
  over-allowance claim can still exist in OPAC, it just doesn't bind to (and thus
  doesn't expand) a realm's territory. OPAC stays a **soft dependency** for Part 1
  either way; making it hard would only be worth revisiting if the hard-limit
  approach pans out.

## Modules

- **`:core`** — pure Java, no Minecraft/NeoForge/MineColonies/OPAC dependencies.
  The entity model (`RealmEntity`, `RealmsRegistry`), the chunk→entity resolver
  (`TerritoryResolver`), and the `ColonyLookup`/`ClaimLookup` interfaces the mod
  module implements. Fully unit-tested with JUnit 5 — `./gradlew :core:test`.
- **`:mod`** — the NeoForge 1.21.1 mod. SavedData persistence, Brigadier commands,
  the public `api` package, NeoForge events, and the MineColonies/OPAC adapters.
  Requires NeoForge/CurseMaven/Modrinth Maven access to build (CI only — see
  `docs/SPIKE-PART1.md`).

## Soft dependencies

| Mod | Version | Mod ID | If absent |
| --- | --- | --- | --- |
| MineColonies | 1.1.1327-1.21.1-snapshot | `minecolonies` | Colony resolution no-ops (`ColonyLookup.NOOP`); `/realm found` reports MineColonies as absent. |
| Open Parties and Claims | neoforge-1.21.1-0.26.2 | `openpartiesandclaims` | Claim resolution no-ops (`ClaimLookup.NOOP`). |

Either or both can be missing without the mod crashing — territory resolution simply
degrades to "no colonies/claims known," and chunks resolve as ungoverned.

## Known limitation: sub-level (airship) claims

Territory resolution is **chunk-based** — it answers "who governs this `(level,
chunkPos)`?". A claim bound to a Valkyrien Skies **sub-level**, such as an
[`aeroclaims`](https://modrinth.com/mod/CwZ8q37q) airship claim, is *not* a chunk in
the parent level, so a player standing on a claimed airship resolves by the **ground
chunk beneath the ship** (often ungoverned wilderness) rather than by the ship's own
claim. This is **by design in Part 1, not a bug** — the resolver has no sub-level
source yet. Adding one, and deciding the precedence rule when a claimed ship sits over
another faction's territory, is tracked in issues #4 and #5 (see `docs/SPIKE-PART1.md`
§7); until then, ship-borne territory resolves to the ground beneath it.

## Commands

- `/realm found <name>` — binds the colony at your current position to a political
  entity named `<name>` (creating it if it doesn't exist). Requires MineColonies and
  standing inside a colony's claim. Requires OFFICER+ to rebind an existing entity.
- `/realm info [name]` — shows an entity's id, members, bound colony ids, and bound
  claim keys. Defaults to the entity governing your current chunk.
- `/realm whogoverns` — debug command reporting which entity (if any) governs your
  current chunk.
- `/realm debug bindclaim <name>` — **op + OFFICER-only** playtest helper: binds the OPAC claim
  covering your current chunk to the named entity. Part 1 has no player-facing claim
  binding (that arrives with Part 2); this exists so the OPAC resolution path can be
  exercised in-game at all.

## Building

```sh
./gradlew build       # builds :core and :mod
./gradlew :core:test  # runs the :core unit tests (no Minecraft toolchain needed)
```

`:mod` requires resolving NeoForge, MineColonies (CurseMaven), and Open Parties and
Claims (Modrinth Maven) — see `docs/SPIKE-PART1.md` for why that can't happen in this
sandbox and what's been verified vs. assumed.

## Releases

`.github/workflows/build.yml` builds the mod jar on every push/PR. On a `v*` tag, it
additionally prints the jar's sha1 and attaches it to a GitHub release — the
mod-mirror pattern described in `project-commonwealth`'s `docs/CUSTOM-MODS.md`.

## Maintainer playtest checklist

CI green (`./gradlew build` succeeding) means `:mod` **compiles** against the real
MineColonies/OPAC jars. It does **not** mean any of the following have been verified —
this sandbox cannot launch Minecraft, so all of the below needs a real client/server.
**Step-by-step setup and a scenario walkthrough covering every item live in
[`docs/PLAYTESTING.md`](docs/PLAYTESTING.md).**

- [ ] Client boots to the main menu with this mod + MineColonies + OPAC installed.
- [ ] `/realm found <name>` while standing inside a MineColonies colony successfully
      binds that colony to a new (or existing, with OFFICER+) entity.
- [ ] `/realm whogoverns` reports the correct entity name while standing inside that
      colony's claimed chunks.
- [ ] `/realm whogoverns` reports the correct entity while standing inside an Open
      Parties and Claims claim bound via `/realm debug bindclaim` (no MineColonies
      colony there).
- [ ] `/realm whogoverns` reports "ungoverned" in wilderness (no colony, no claim).
- [ ] Unclaiming the OPAC chunk flips `/realm whogoverns` to "ungoverned" within
      ~1–2 seconds, with no restart (the resolver's TTL picking up the external
      change — `docs/SPIKE-PART1.md` §4).
- [ ] Entities, members, and bindings survive a save-and-quit / server restart
      (SavedData round-trip).
- [ ] `/realm info [name]` prints sensible header/members/colonies/claims for a bound
      entity.
- [ ] No measurable TPS impact from repeated `/realm whogoverns` calls or normal block
      break / combat in governed and ungoverned chunks (spark profile before/after).
- [ ] Removing MineColonies (keeping OPAC) — server starts without crashing, colony
      resolution degrades to "none," `/realm found` reports MineColonies absent.
- [ ] Removing Open Parties and Claims (keeping MineColonies) — server starts without
      crashing, claim-only territory resolves as ungoverned.
- [ ] Removing both — server starts without crashing; all chunks resolve as
      ungoverned; `/realm found` reports MineColonies absent.

**Green CI is not in-game verification.** CI compiles `MineColoniesColonyLookup` and
`OpacClaimLookup` against the real dependency jars (see `docs/SPIKE-PART1.md` §2–3 for
the verified API surfaces), but until the checklist above is run on a real server,
their runtime behavior — does standing in a colony or claim actually resolve? — is
still unverified.

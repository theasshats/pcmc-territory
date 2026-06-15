# Playtesting Part 1

Step-by-step instructions for running the maintainer playtest checklist in
`README.md`. This sandbox cannot launch Minecraft, so every item below is a
human-on-a-real-instance task. Green CI means `:mod` compiled against the real
MineColonies/OPAC jars — nothing more.

The adapters' API surfaces are compile-verified (`docs/SPIKE-PART1.md` §2–3), but the
first playtest is the moment their *runtime* assumptions meet reality: if
`/realm found` or `/realm whogoverns` misbehaves, suspect
`MineColoniesColonyLookup` / `OpacClaimLookup` first.

## 1. Get the jar

Either:

- **From CI** (no toolchain needed): open the latest green run of the **Build**
  workflow on this branch → download the `pcmc_territory-jar` artifact (kept 14
  days), or
- **Locally**: `./gradlew build` → `mod/build/libs/pcmc_territory-<version>.jar`.

## 2. Set up the test instance

A NeoForge **1.21.1** instance (Prism or any launcher), NeoForge **21.1.233**,
with these in `mods/`:

| Mod | Version | Source |
| --- | --- | --- |
| `pcmc_territory` | this build | step 1 |
| MineColonies | 1.1.1327-1.21.1-snapshot | CurseForge project 245506, file 8186694 |
| MineColonies's required libraries | matching 1.21.1 builds | Structurize, BlockUI, Domum Ornamentum, Multi-Piston — the launch error will name any that are missing |
| Open Parties and Claims | 0.26.2 (neoforge) | Modrinth `gF3BGWvG` |
| Xaero's Minimap or World Map *(optional)* | any recent 1.21.1 | gives OPAC its map claiming UI; otherwise claim via OPAC's commands |

For the server-side items, the same set on a NeoForge 21.1.233 server works; a
single-player world also exercises the integrated server and is fine for
everything except the TPS measurement.

> Quick partial alternative: `./gradlew :mod:runClient` boots a dev client
> **without** MineColonies/OPAC (they're compile-only), which is exactly the
> "both deps absent" degradation configuration of scenario 6 — useful as a fast
> smoke test, but the full matrix needs the real jars in a real instance.

## 3. Scenario walkthrough

Run in order; later scenarios reuse the realm created in scenario 2.

### Scenario 1 — boot sanity

1. Launch the client with the full mod set. It must reach the main menu.
2. Create a fresh world (default settings). It must load without errors.
3. Check the log for the wiring lines:
   `[pcmc_territory] MineColonies found - colony resolution enabled`,
   `[pcmc_territory] Open Parties and Claims found - claim resolution enabled`, and —
   once the world finishes loading (it's logged at server-started, not server-starting) —
   `[pcmc_territory] OPAC claim auto-binding enabled - …`.

### Scenario 2 — colony path (`/realm found`)

1. Found a MineColonies colony: place the Supply Camp (or Supply Ship), take the
   Town Hall from it, place it, and right-click → create new colony.
2. Standing inside the colony's border: `/realm found "Riverside"` → expect
   `Founded realm 'Riverside', bound to colony #<id>.`
3. `/realm whogoverns` → `This chunk is governed by 'Riverside'.` Repeat a few
   chunks away but still inside the colony border — same answer.
4. `/realm info` (no name) and `/realm info "Riverside"` → header with the
   entity UUID, you as LEADER, the colony id listed, claims `-`.
5. Negative checks: run `/realm found "Elsewhere"` while standing **outside**
   any colony → `You must be standing inside a MineColonies colony…`; run
   `/realm found "Other"` back inside the same colony → `Colony #<id> is
   already governed by 'Riverside'.`

### Scenario 3 — OPAC path (`/realm debug bindclaim`)

1. Travel well outside the colony border. Claim the chunk you're standing in
   with OPAC. Easiest without a map mod: press **`'`** (apostrophe) to open
   OPAC's claims screen and claim the current chunk from the grid (the key is
   rebindable under Controls). Alternatives: the Xaero's map claims screen if a
   Xaero map mod is installed, or `/openpac-claims` + tab-completion.
2. `/realm whogoverns` → still **ungoverned** (a raw OPAC claim with no realm
   binding is not territory).
3. As an op: `/realm debug bindclaim "Riverside"` → `Bound claim owner <uuid>
   to realm 'Riverside'.`
4. `/realm whogoverns` → governed by `Riverside`. `/realm info "Riverside"` now
   lists the claim owner UUID under Claims.
5. Walk into an **adjacent unclaimed** chunk: ungoverned. Back into the claimed
   chunk: governed — confirms resolution is per-chunk, not radius-based.

### Scenario 3b — colony borders shield an overlapping claim *(new in PR #1; optional, needs a second colony)*

Validates the precedence rule added in this PR: a colony's border always wins over
an OPAC claim, and an *unfounded* colony resolves as ungoverned rather than falling
through to a claim that overlaps it.

1. Place a **second** colony (Supply Camp → Town Hall → Create Colony) but **do
   not** `/realm found` it — leave it unfounded.
2. Inside that second colony's border, OPAC-claim the chunk you're standing in,
   then as op `/realm debug bindclaim "Riverside"` to bind that claim to the realm
   from scenario 2.
3. `/realm whogoverns` there → must report **ungoverned**. Pre-PR behavior would
   have fallen through to the bound claim and reported **'Riverside'** — if you see
   that, the shield logic in `TerritoryResolver.resolveLeaf` regressed (it must
   consult OPAC only for chunks outside every colony).

### Scenario 3c — auto-bind a member's claim *(new in this PR; issue #7)*

Validates that a realm member's OPAC claims bind to their realm with no manual command.

1. As the LEADER of `Riverside` (the founder from scenario 2), and a member of **no
   other** realm, travel outside every colony border and OPAC-claim a fresh chunk.
2. Immediately — no command, no restart — expect the chat message
   *"Your land claims now belong to realm 'Riverside'."* (the one-time confirmation).
3. `/realm whogoverns` there → governed by `Riverside`. `/realm info "Riverside"` lists
   your username under Claims.
4. Claim a **second** chunk elsewhere: `/realm whogoverns` there is `Riverside` too,
   with **no** second confirmation — binding is keyed on the claim owner, so it covers
   all of your claims at once.
5. Unclaim the first chunk: after ~1–2 s it flips to ungoverned (TTL), but `/realm info`
   still lists you under Claims and your other claim stays governed — unclaiming a chunk
   does not unbind the owner.
6. Negative: have a second player who is in **no** realm (or an alt) OPAC-claim a chunk →
   it stays ungoverned and they get no message. Auto-bind only acts for a member of
   exactly one realm; the op-only `/realm debug bindclaim` remains the path for everyone
   else.

### Scenario 4 — wilderness and the TTL

1. In land with no colony and no claim: `/realm whogoverns` →
   `This chunk is ungoverned (wilderness).`
2. Unclaim the OPAC chunk from scenario 3, then stand in it and run
   `/realm whogoverns` again after ~1–2 seconds → must flip to ungoverned
   **without any restart or command** — this is the resolver's 20-tick TTL
   picking up an external claim change (`docs/SPIKE-PART1.md` §4). If it stays
   governed indefinitely, the TTL wiring (`RealmsSavedData` →
   `ServerLevel#getGameTime`) is broken.

### Scenario 5 — persistence

1. Save and quit (or stop the server), relaunch, rejoin the world.
2. `/realm info "Riverside"` → entity, members, and colony binding survived
   (SavedData round-trip). `/realm whogoverns` inside the colony → still
   governed.

### Scenario 6 — soft-dep degradation

Each configuration must reach the main menu / server-ready without crashing:

1. **Remove MineColonies** (and its libraries), keep OPAC: world loads; log
   says colony resolution disabled; `/realm found x` → `MineColonies is not
   installed…`; the old colony chunks resolve as ungoverned; an OPAC-bound
   claim still resolves as governed.
2. **Remove OPAC**, restore MineColonies: world loads; `/realm debug bindclaim`
   → OPAC-absent message; colony chunks still governed.
3. **Remove both**: world loads; everything resolves as ungoverned; `/realm
   info "Riverside"` still prints the stored entity (the registry is ours, not
   theirs).
4. Restore both: previous bindings resolve again with no manual repair.

### Scenario 7 — performance

On a real (dedicated) server with the full set: profile with spark
(`/spark profiler --timeout 60`) while players break blocks / fight inside and
outside governed chunks. `pcmc_territory` frames should be absent or negligible
— resolution is cache-served and only runs on lookups, never on a tick scan. A
measurable cost here is a bug, not a tuning knob.

## 4. Reporting results

Tick the checklist in `README.md` (or the PR's playtest section) per item. For
any failure, capture the log/crash report and note which scenario step — a
failure in scenarios 2–3 most likely means one of the two adapter files
(`MineColoniesColonyLookup`, `OpacClaimLookup`) is calling the right API with the
wrong runtime assumption; fixing it is a one-file change by design.

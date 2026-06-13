package com.theasshats.pcmcterritory.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The chunk -&gt; entity resolver (spec §6), the hot path everything above Part 1
 * eventually calls.
 *
 * <p><b>Invalidation strategy.</b> Two independent triggers:
 * <ul>
 *   <li><b>Registry edits</b> (entity created/removed/changed) invalidate the whole
 *       cache immediately via {@link RegistryListener} — these are rare admin
 *       actions, so a full clear is cheap and exact.</li>
 *   <li><b>External claim changes</b> (MineColonies colony borders, OPAC claims)
 *       use a short per-entry TTL ({@link #ttlTicks}) instead of a change-event
 *       hook: a cache entry older than the TTL is treated as a miss and
 *       recomputed on the next lookup. This is still position-local and
 *       event-driven in the sense that matters for perf — the check happens only
 *       when a position is already being queried, never a background scan — and
 *       it works regardless of exactly how/whether MineColonies or OPAC signal a
 *       claim change, which spec §6 spike couldn't fully pin down without the
 *       real jars (see docs/SPIKE-PART1.md). Default TTL is {@link #DEFAULT_TTL_TICKS}
 *       (1 second) — short enough that a moved claim border is reflected almost
 *       immediately, long enough that hot-path lookups (combat, block break) stay
 *       cache-served.</li>
 * </ul>
 *
 * <p>Negative results ("wilderness") are cached too, subject to the same TTL, so
 * repeated lookups over ungoverned land don't repeatedly call into MineColonies/OPAC.
 *
 * <p><b>Threading.</b> Not thread-safe. Every call — {@link #resolveLeaf}/{@link
 * #resolveChain}, the {@code set*Lookup} re-points, and {@link RegistryListener}
 * invalidation — must run on the server thread, which is the only thread on which the
 * backing {@link RealmsRegistry} and the MineColonies/OPAC lookups may be touched. A
 * lock-free variant allowing off-thread reads is tracked as a follow-up.
 */
public final class TerritoryResolver implements RegistryListener {

    public static final long DEFAULT_TTL_TICKS = 20L; // ~1 second at 20 TPS

    private static final UUID UNGOVERNED = new UUID(0L, 0L);

    private record CacheEntry(UUID entityOrSentinel, long cachedAtTick) {}

    private final RealmsRegistry registry;
    private ColonyLookup colonyLookup;
    private ClaimLookup claimLookup;
    private final LongSupplier currentTick;
    private final long ttlTicks;
    private final Map<TerritoryChunk, CacheEntry> cache = new HashMap<>();

    public TerritoryResolver(RealmsRegistry registry, ColonyLookup colonyLookup, ClaimLookup claimLookup) {
        this(registry, colonyLookup, claimLookup, () -> 0L, DEFAULT_TTL_TICKS);
    }

    public TerritoryResolver(RealmsRegistry registry, ColonyLookup colonyLookup, ClaimLookup claimLookup,
                              LongSupplier currentTick, long ttlTicks) {
        this.registry = registry;
        this.colonyLookup = colonyLookup;
        this.claimLookup = claimLookup;
        this.currentTick = currentTick;
        this.ttlTicks = ttlTicks;
        registry.addListener(this);
    }

    /** Re-points the colony lookup, e.g. once MineColonies finishes loading. */
    public void setColonyLookup(ColonyLookup colonyLookup) {
        this.colonyLookup = colonyLookup;
        invalidateAll();
    }

    /** Re-points the claim lookup, e.g. once OPAC finishes loading. */
    public void setClaimLookup(ClaimLookup claimLookup) {
        this.claimLookup = claimLookup;
        invalidateAll();
    }

    public ColonyLookup colonyLookup() {
        return colonyLookup;
    }

    public ClaimLookup claimLookup() {
        return claimLookup;
    }

    /**
     * Resolves the leaf entity governing {@code chunk}, if any.
     *
     * <p>A MineColonies colony's borders take strict precedence over OPAC claims and
     * <em>shield</em> the chunk: if the chunk is inside any colony, the result is that
     * colony's bound realm, or empty ("ungoverned") when the colony has not been
     * founded as a realm — it never falls through to an overlapping OPAC claim. An OPAC
     * claim is consulted only for chunks outside every colony. (This shield-over-
     * fall-through choice is a deliberate Part 1 decision — see the "combined borders"
     * note in README.md.)
     */
    public Optional<UUID> resolveLeaf(TerritoryChunk chunk) {
        long now = currentTick.getAsLong();
        CacheEntry cached = cache.get(chunk);
        if (cached != null && now - cached.cachedAtTick() < ttlTicks) {
            return cached.entityOrSentinel().equals(UNGOVERNED) ? Optional.empty() : Optional.of(cached.entityOrSentinel());
        }

        OptionalInt colonyId = colonyLookup.colonyIdAt(chunk);
        UUID resolved = colonyId.isPresent()
                // Inside a colony: resolve to its realm if founded, else ungoverned. A
                // colony's borders shield the chunk from any overlapping outside claim.
                ? registry.entityIdForColony(colonyId.getAsInt()).orElse(null)
                // Outside every colony: fall back to an OPAC claim, if one is bound.
                : claimLookup.claimAt(chunk).flatMap(registry::entityIdForClaim).orElse(null);

        cache.put(chunk, new CacheEntry(resolved != null ? resolved : UNGOVERNED, now));
        return Optional.ofNullable(resolved);
    }

    /**
     * Resolves the full leaf-&gt;root chain for {@code chunk}. Part 1 entities have
     * no parent, so this is trivially a singleton (or empty for wilderness) —
     * Part 2 extends this by walking {@code parentId} from the leaf.
     */
    public List<UUID> resolveChain(TerritoryChunk chunk) {
        return resolveLeaf(chunk)
                .map(List::of)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Invalidates a single chunk immediately. Currently unused — registry edits go
     * through {@link #invalidateAll()} and external claim changes rely on the TTL —
     * but kept as the hook for a future immediate claim-change signal, should one be
     * confirmed against the real MineColonies/OPAC jars (docs/SPIKE-PART1.md §4).
     */
    public void invalidate(TerritoryChunk chunk) {
        cache.remove(chunk);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public int cacheSize() {
        return cache.size();
    }

    @Override
    public void onEntityCreated(RealmEntity entity) {
        invalidateAll();
    }

    @Override
    public void onEntityRemoved(RealmEntity entity) {
        invalidateAll();
    }

    @Override
    public void onEntityChanged(RealmEntity entity) {
        invalidateAll();
    }
}

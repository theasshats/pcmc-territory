package com.theasshats.pcmcterritory.core;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The chunk -&gt; entity resolver (spec §6), the hot path everything above Part 1
 * eventually calls. Backed by a cache that is invalidated whenever the registry
 * changes (entity created/removed/edited) — entity edits are rare admin actions,
 * so a full-cache clear on those is cheap and keeps invalidation simple and correct.
 * Per-chunk invalidation ({@link #invalidate}) is used for claim-change signals,
 * which can be position-targeted.
 *
 * <p>{@link #resolveLeaf} also caches negative results (no governing entity —
 * "wilderness") so repeated lookups over ungoverned land don't repeatedly call
 * into MineColonies/OPAC.
 */
public final class TerritoryResolver implements RegistryListener {

    private static final UUID UNGOVERNED = new UUID(0L, 0L);

    private final RealmsRegistry registry;
    private volatile ColonyLookup colonyLookup;
    private volatile ClaimLookup claimLookup;
    private final ConcurrentMap<TerritoryChunk, UUID> cache = new ConcurrentHashMap<>();

    public TerritoryResolver(RealmsRegistry registry, ColonyLookup colonyLookup, ClaimLookup claimLookup) {
        this.registry = registry;
        this.colonyLookup = colonyLookup;
        this.claimLookup = claimLookup;
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

    /**
     * Resolves the leaf entity governing {@code chunk}, if any. MineColonies is
     * consulted before OPAC: a colony's borders are the more specific claim.
     */
    public Optional<UUID> resolveLeaf(TerritoryChunk chunk) {
        UUID cached = cache.get(chunk);
        if (cached != null) {
            return cached.equals(UNGOVERNED) ? Optional.empty() : Optional.of(cached);
        }

        UUID resolved = colonyLookup.colonyIdAt(chunk)
                .stream()
                .boxed()
                .flatMap(colonyId -> registry.entityIdForColony(colonyId).stream())
                .findFirst()
                .or(() -> claimLookup.claimAt(chunk)
                        .flatMap(registry::entityIdForClaim))
                .orElse(null);

        cache.put(chunk, resolved != null ? resolved : UNGOVERNED);
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

    /** Invalidates a single chunk, e.g. on an OPAC/MineColonies claim-change signal. */
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

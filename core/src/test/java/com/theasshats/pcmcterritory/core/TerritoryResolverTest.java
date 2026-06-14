package com.theasshats.pcmcterritory.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryResolverTest {

    private static final String OVERWORLD = "minecraft:overworld";

    /** In-memory fake standing in for {@code IColonyManager} (colonyId -> chunk). */
    private static final class FakeColonyLookup implements ColonyLookup {
        final Map<TerritoryChunk, Integer> colonies = new HashMap<>();

        @Override
        public OptionalInt colonyIdAt(TerritoryChunk chunk) {
            Integer colonyId = colonies.get(chunk);
            return colonyId == null ? OptionalInt.empty() : OptionalInt.of(colonyId);
        }
    }

    /** In-memory fake standing in for OPAC's claims manager (claim owner -> chunk). */
    private static final class FakeClaimLookup implements ClaimLookup {
        final Map<TerritoryChunk, ClaimKey> claims = new HashMap<>();

        @Override
        public Optional<ClaimKey> claimAt(TerritoryChunk chunk) {
            return Optional.ofNullable(claims.get(chunk));
        }
    }

    @Test
    void resolvesViaMineColoniesColony() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        registry.bindColony(entity.id(), 7);

        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 10, 20);
        colonyLookup.colonies.put(chunk, 7);

        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, ClaimLookup.NOOP);

        assertEquals(entity.id(), resolver.resolveLeaf(chunk).orElseThrow());
        assertEquals(1, resolver.cacheSize());
    }

    @Test
    void resolvesViaOpacClaimWhenNoColony() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());
        registry.bindClaim(entity.id(), claimKey);

        FakeClaimLookup claimLookup = new FakeClaimLookup();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 5, 5);
        claimLookup.claims.put(chunk, claimKey);

        TerritoryResolver resolver = new TerritoryResolver(registry, ColonyLookup.NOOP, claimLookup);

        assertEquals(entity.id(), resolver.resolveLeaf(chunk).orElseThrow());
    }

    @Test
    void colonyTakesPrecedenceOverClaim() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity colonyEntity = registry.createEntity("ColonyTown", UUID.randomUUID(), 0L);
        RealmEntity claimEntity = registry.createEntity("ClaimTown", UUID.randomUUID(), 0L);
        registry.bindColony(colonyEntity.id(), 1);
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());
        registry.bindClaim(claimEntity.id(), claimKey);

        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 0, 0);
        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        colonyLookup.colonies.put(chunk, 1);
        FakeClaimLookup claimLookup = new FakeClaimLookup();
        claimLookup.claims.put(chunk, claimKey);

        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, claimLookup);

        assertEquals(colonyEntity.id(), resolver.resolveLeaf(chunk).orElseThrow());
    }

    @Test
    void unfoundedColonyShieldsChunkFromOutsideClaim() {
        RealmsRegistry registry = new RealmsRegistry();
        // An OPAC claim is bound to a realm...
        RealmEntity claimEntity = registry.createEntity("ClaimTown", UUID.randomUUID(), 0L);
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());
        registry.bindClaim(claimEntity.id(), claimKey);

        // ...on a chunk that also sits inside colony #1, which nobody has founded.
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 0, 0);
        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        colonyLookup.colonies.put(chunk, 1);
        FakeClaimLookup claimLookup = new FakeClaimLookup();
        claimLookup.claims.put(chunk, claimKey);

        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, claimLookup);

        // The colony's borders shield the chunk: ungoverned, NOT handed to the claim's realm.
        assertTrue(resolver.resolveLeaf(chunk).isEmpty(),
                "an unfounded colony must not fall through to an overlapping outside claim");
    }

    @Test
    void wildernessResolvesEmptyAndIsCached() {
        RealmsRegistry registry = new RealmsRegistry();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 100, 100);

        TerritoryResolver resolver = new TerritoryResolver(registry, ColonyLookup.NOOP, ClaimLookup.NOOP);

        assertTrue(resolver.resolveLeaf(chunk).isEmpty());
        assertEquals(1, resolver.cacheSize());
        // second lookup is served from cache, still empty
        assertTrue(resolver.resolveLeaf(chunk).isEmpty());
        assertEquals(1, resolver.cacheSize());
    }

    @Test
    void entityEditInvalidatesCache() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        registry.bindColony(entity.id(), 7);

        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 10, 20);
        colonyLookup.colonies.put(chunk, 7);

        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, ClaimLookup.NOOP);
        resolver.resolveLeaf(chunk);
        assertEquals(1, resolver.cacheSize());

        registry.rename(entity.id(), "Renamed");

        assertEquals(0, resolver.cacheSize());
    }

    @Test
    void resolveChainIsLeafOnlyInPart1() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        registry.bindColony(entity.id(), 7);

        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 10, 20);
        colonyLookup.colonies.put(chunk, 7);

        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, ClaimLookup.NOOP);

        assertEquals(java.util.List.of(entity.id()), resolver.resolveChain(chunk));

        TerritoryChunk wilderness = TerritoryChunk.of(OVERWORLD, 999, 999);
        assertTrue(resolver.resolveChain(wilderness).isEmpty());
    }

    @Test
    void cacheEntryExpiresAfterTtlAndPicksUpExternalClaimChange() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        registry.bindColony(entity.id(), 7);

        FakeColonyLookup colonyLookup = new FakeColonyLookup();
        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 10, 20);
        colonyLookup.colonies.put(chunk, 7);

        long[] tick = {0L};
        TerritoryResolver resolver = new TerritoryResolver(registry, colonyLookup, ClaimLookup.NOOP,
                () -> tick[0], 20L);

        assertEquals(entity.id(), resolver.resolveLeaf(chunk).orElseThrow());

        // MineColonies abandons the colony "externally" — no registry event, so the
        // cache entry is still present but stale.
        colonyLookup.colonies.remove(chunk);
        assertEquals(entity.id(), resolver.resolveLeaf(chunk).orElseThrow(), "still served from cache within TTL");

        // advance past the TTL: the next lookup recomputes and observes the change.
        tick[0] = 21L;
        assertTrue(resolver.resolveLeaf(chunk).isEmpty(), "recomputed after TTL expiry");
    }

    @Test
    void softDependencyNoopsNeverThrow() {
        RealmsRegistry registry = new RealmsRegistry();
        TerritoryResolver resolver = new TerritoryResolver(registry, ColonyLookup.NOOP, ClaimLookup.NOOP);

        TerritoryChunk chunk = TerritoryChunk.of(OVERWORLD, 1, 1);

        assertTrue(resolver.resolveLeaf(chunk).isEmpty());
        assertTrue(resolver.resolveChain(chunk).isEmpty());
    }
}

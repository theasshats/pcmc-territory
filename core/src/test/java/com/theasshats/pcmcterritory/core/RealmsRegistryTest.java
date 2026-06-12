package com.theasshats.pcmcterritory.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealmsRegistryTest {

    @Test
    void createEntityMakesFounderLeader() {
        RealmsRegistry registry = new RealmsRegistry();
        UUID founder = UUID.randomUUID();

        RealmEntity entity = registry.createEntity("Riverside", founder, 0L);

        assertEquals("Riverside", entity.name());
        assertEquals(Role.LEADER, entity.roleOf(founder).orElseThrow());
        assertTrue(entity.hasAtLeast(founder, Role.LEADER));
    }

    @Test
    void bindColonyCreatesReverseLookup() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);

        registry.bindColony(entity.id(), 42);

        assertEquals(entity.id(), registry.entityIdForColony(42).orElseThrow());
        assertTrue(entity.colonyIds().contains(42));
    }

    @Test
    void bindColonyTwiceToDifferentEntitiesConflicts() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity a = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        RealmEntity b = registry.createEntity("Hilltop", UUID.randomUUID(), 0L);

        registry.bindColony(a.id(), 42);

        assertThrows(RealmConflictException.class, () -> registry.bindColony(b.id(), 42));
    }

    @Test
    void bindColonyTwiceToSameEntityIsIdempotent() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);

        registry.bindColony(entity.id(), 42);
        registry.bindColony(entity.id(), 42);

        assertEquals(1, entity.colonyIds().size());
    }

    @Test
    void bindClaimCreatesReverseLookup() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());

        registry.bindClaim(entity.id(), claimKey);

        assertEquals(entity.id(), registry.entityIdForClaim(claimKey).orElseThrow());
    }

    @Test
    void removeEntityClearsIndexes() {
        RealmsRegistry registry = new RealmsRegistry();
        RealmEntity entity = registry.createEntity("Riverside", UUID.randomUUID(), 0L);
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());
        registry.bindColony(entity.id(), 42);
        registry.bindClaim(entity.id(), claimKey);

        assertTrue(registry.remove(entity.id()));

        assertTrue(registry.entityIdForColony(42).isEmpty());
        assertTrue(registry.entityIdForClaim(claimKey).isEmpty());
        assertTrue(registry.get(entity.id()).isEmpty());
    }

    @Test
    void findByNameIsCaseInsensitive() {
        RealmsRegistry registry = new RealmsRegistry();
        registry.createEntity("Riverside", UUID.randomUUID(), 0L);

        assertTrue(registry.findByName("riverside").isPresent());
        assertFalse(registry.findByName("nowhere").isPresent());
    }

    @Test
    void restoreEntityRebuildsIndexes() {
        RealmsRegistry registry = new RealmsRegistry();
        UUID id = UUID.randomUUID();
        UUID founder = UUID.randomUUID();
        ClaimKey claimKey = new ClaimKey(UUID.randomUUID());

        registry.restoreEntity(id, "Riverside", 100L,
                Map.of(founder, Role.LEADER), Set.of(7), Set.of(claimKey));

        assertEquals("Riverside", registry.get(id).orElseThrow().name());
        assertEquals(id, registry.entityIdForColony(7).orElseThrow());
        assertEquals(id, registry.entityIdForClaim(claimKey).orElseThrow());
        assertEquals(Role.LEADER, registry.get(id).orElseThrow().roleOf(founder).orElseThrow());
    }

    @Test
    void restoreEntityConflictingColonyThrows() {
        RealmsRegistry registry = new RealmsRegistry();
        registry.restoreEntity(UUID.randomUUID(), "A", 0L, Map.of(), Set.of(7), Set.of());

        assertThrows(RealmConflictException.class,
                () -> registry.restoreEntity(UUID.randomUUID(), "B", 0L, Map.of(), Set.of(7), Set.of()));
    }

    @Test
    void renameAndMemberManagement() {
        RealmsRegistry registry = new RealmsRegistry();
        UUID founder = UUID.randomUUID();
        UUID citizen = UUID.randomUUID();
        RealmEntity entity = registry.createEntity("Riverside", founder, 0L);

        registry.rename(entity.id(), "New Riverside");
        registry.setMember(entity.id(), citizen, Role.CITIZEN);

        assertEquals("New Riverside", entity.name());
        assertEquals(Role.CITIZEN, entity.roleOf(citizen).orElseThrow());
        assertFalse(entity.hasAtLeast(citizen, Role.OFFICER));

        assertTrue(registry.removeMember(entity.id(), citizen));
        assertTrue(entity.roleOf(citizen).isEmpty());
    }
}

package com.theasshats.pcmcterritory.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory entity registry plus the colonyId/claimKey reverse indexes from
 * spec §6. The mod module persists this to {@code RealmsSavedData} (NBT); this
 * class has no knowledge of persistence or Minecraft types so it can be
 * exercised directly in unit tests.
 */
public final class RealmsRegistry {

    private final Map<UUID, RealmEntity> entities = new LinkedHashMap<>();
    private final Map<Integer, UUID> colonyIndex = new HashMap<>();
    private final Map<ClaimKey, UUID> claimIndex = new HashMap<>();
    private final List<RegistryListener> listeners = new ArrayList<>();

    public void addListener(RegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RegistryListener listener) {
        listeners.remove(listener);
    }

    public RealmEntity createEntity(String name, UUID founder, long createdTick) {
        RealmEntity entity = new RealmEntity(UUID.randomUUID(), name, createdTick);
        entity.putMember(founder, Role.LEADER);
        entities.put(entity.id(), entity);
        listeners.forEach(l -> l.onEntityCreated(entity));
        return entity;
    }

    /**
     * Reconstructs an entity (and its reverse indexes) from persisted state without
     * firing listeners — used by {@code RealmsSavedData} on world load. Throws
     * {@link RealmConflictException} if {@code id} duplicates or
     * {@code colonyIds}/{@code claimKeys} collide with an already-loaded entity
     * (should not happen for well-formed save data). All-or-nothing: the load path
     * survives a corrupt entity by skipping it, so a failed restore must not leave
     * index entries pointing at an entity that never made it into the registry —
     * everything is validated before anything is committed.
     */
    public RealmEntity restoreEntity(UUID id, String name, long createdTick,
                                      Map<UUID, Role> members, Set<Integer> colonyIds, Set<ClaimKey> claimKeys) {
        if (entities.containsKey(id)) {
            throw new RealmConflictException("Duplicate entity id " + id);
        }
        for (int colonyId : colonyIds) {
            UUID existing = colonyIndex.get(colonyId);
            if (existing != null && !existing.equals(id)) {
                throw new RealmConflictException(
                        "Colony " + colonyId + " is bound to both " + existing + " and " + id);
            }
        }
        for (ClaimKey claimKey : claimKeys) {
            UUID existing = claimIndex.get(claimKey);
            if (existing != null && !existing.equals(id)) {
                throw new RealmConflictException(
                        "Claim " + claimKey + " is bound to both " + existing + " and " + id);
            }
        }

        RealmEntity entity = new RealmEntity(id, name, createdTick);
        members.forEach(entity::putMember);
        for (int colonyId : colonyIds) {
            colonyIndex.put(colonyId, id);
            entity.addColonyId(colonyId);
        }
        for (ClaimKey claimKey : claimKeys) {
            claimIndex.put(claimKey, id);
            entity.addClaimKey(claimKey);
        }
        entities.put(id, entity);
        return entity;
    }

    public boolean remove(UUID entityId) {
        RealmEntity entity = entities.remove(entityId);
        if (entity == null) {
            return false;
        }
        for (Integer colonyId : entity.colonyIds()) {
            colonyIndex.remove(colonyId);
        }
        for (ClaimKey claimKey : entity.claimKeys()) {
            claimIndex.remove(claimKey);
        }
        listeners.forEach(l -> l.onEntityRemoved(entity));
        return true;
    }

    public Optional<RealmEntity> get(UUID entityId) {
        return Optional.ofNullable(entities.get(entityId));
    }

    public Optional<RealmEntity> findByName(String name) {
        return entities.values().stream()
                .filter(e -> e.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public Collection<RealmEntity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    /**
     * Returns every entity {@code playerId} is a member of, in registry (creation)
     * order. Part 1 has no member-management beyond founding, so in practice this is
     * the realm(s) the player founded — usually empty or a single element. Used by
     * the OPAC claim auto-binder to decide which realm a member's new claim joins.
     */
    public List<RealmEntity> entitiesForMember(UUID playerId) {
        List<RealmEntity> result = new ArrayList<>();
        for (RealmEntity entity : entities.values()) {
            if (entity.members().containsKey(playerId)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Binds a MineColonies colony to an entity. Throws {@link RealmConflictException}
     * if the colony is already bound to a different entity.
     */
    public void bindColony(UUID entityId, int colonyId) {
        RealmEntity entity = requireEntity(entityId);
        UUID existing = colonyIndex.get(colonyId);
        if (existing != null && !existing.equals(entityId)) {
            throw new RealmConflictException(
                    "Colony " + colonyId + " is already bound to entity " + existing);
        }
        entity.addColonyId(colonyId);
        colonyIndex.put(colonyId, entityId);
        listeners.forEach(l -> l.onEntityChanged(entity));
    }

    public void unbindColony(int colonyId) {
        UUID entityId = colonyIndex.remove(colonyId);
        if (entityId == null) {
            return;
        }
        get(entityId).ifPresent(entity -> {
            entity.removeColonyId(colonyId);
            listeners.forEach(l -> l.onEntityChanged(entity));
        });
    }

    /**
     * Binds an OPAC claim owner to an entity. Throws {@link RealmConflictException}
     * if the claim is already bound to a different entity.
     */
    public void bindClaim(UUID entityId, ClaimKey claimKey) {
        RealmEntity entity = requireEntity(entityId);
        UUID existing = claimIndex.get(claimKey);
        if (existing != null && !existing.equals(entityId)) {
            throw new RealmConflictException(
                    "Claim " + claimKey + " is already bound to entity " + existing);
        }
        entity.addClaimKey(claimKey);
        claimIndex.put(claimKey, entityId);
        listeners.forEach(l -> l.onEntityChanged(entity));
    }

    public void unbindClaim(ClaimKey claimKey) {
        UUID entityId = claimIndex.remove(claimKey);
        if (entityId == null) {
            return;
        }
        get(entityId).ifPresent(entity -> {
            entity.removeClaimKey(claimKey);
            listeners.forEach(l -> l.onEntityChanged(entity));
        });
    }

    public Optional<UUID> entityIdForColony(int colonyId) {
        return Optional.ofNullable(colonyIndex.get(colonyId));
    }

    public Optional<UUID> entityIdForClaim(ClaimKey claimKey) {
        return Optional.ofNullable(claimIndex.get(claimKey));
    }

    public void rename(UUID entityId, String newName) {
        RealmEntity entity = requireEntity(entityId);
        entity.setName(newName);
        listeners.forEach(l -> l.onEntityChanged(entity));
    }

    public void setMember(UUID entityId, UUID playerId, Role role) {
        RealmEntity entity = requireEntity(entityId);
        entity.putMember(playerId, role);
        listeners.forEach(l -> l.onEntityChanged(entity));
    }

    public boolean removeMember(UUID entityId, UUID playerId) {
        RealmEntity entity = requireEntity(entityId);
        boolean removed = entity.removeMember(playerId);
        if (removed) {
            listeners.forEach(l -> l.onEntityChanged(entity));
        }
        return removed;
    }

    private RealmEntity requireEntity(UUID entityId) {
        RealmEntity entity = entities.get(entityId);
        if (entity == null) {
            throw new IllegalArgumentException("No such entity: " + entityId);
        }
        return entity;
    }
}

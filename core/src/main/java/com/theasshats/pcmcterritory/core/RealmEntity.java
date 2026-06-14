package com.theasshats.pcmcterritory.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Part 1's minimal political entity: an id, a name, members with roles, and
 * references to the colonies/claims it governs. No tier, parent, laws, or
 * treasury — those are Part 2/3 (spec §8).
 */
public final class RealmEntity {

    private final UUID id;
    private final long createdTick;
    private String name;
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    private final Set<Integer> colonyIds = new LinkedHashSet<>();
    private final Set<ClaimKey> claimKeys = new LinkedHashSet<>();

    public RealmEntity(UUID id, String name, long createdTick) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.createdTick = createdTick;
    }

    public UUID id() {
        return id;
    }

    public long createdTick() {
        return createdTick;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public Map<UUID, Role> members() {
        return Collections.unmodifiableMap(members);
    }

    public Optional<Role> roleOf(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public boolean hasAtLeast(UUID playerId, Role required) {
        Role role = members.get(playerId);
        return role != null && role.atLeast(required);
    }

    public void putMember(UUID playerId, Role role) {
        members.put(Objects.requireNonNull(playerId, "playerId"), Objects.requireNonNull(role, "role"));
    }

    public boolean removeMember(UUID playerId) {
        return members.remove(playerId) != null;
    }

    public Set<Integer> colonyIds() {
        return Collections.unmodifiableSet(colonyIds);
    }

    void addColonyId(int colonyId) {
        colonyIds.add(colonyId);
    }

    void removeColonyId(int colonyId) {
        colonyIds.remove(colonyId);
    }

    public Set<ClaimKey> claimKeys() {
        return Collections.unmodifiableSet(claimKeys);
    }

    void addClaimKey(ClaimKey key) {
        claimKeys.add(key);
    }

    void removeClaimKey(ClaimKey key) {
        claimKeys.remove(key);
    }
}

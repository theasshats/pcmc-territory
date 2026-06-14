package com.theasshats.pcmcterritory.api;

import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.RealmEntity;
import com.theasshats.pcmcterritory.core.Role;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, point-in-time view of a {@code RealmEntity} for consumers outside
 * this mod (Part 2 — government). Part 1 entities have no tier/parent/children;
 * those fields arrive with Part 2 and will extend this record rather than
 * replace it, so the leaf-&gt;root {@link TerritoryApi#resolve} contract stays stable.
 */
public record EntitySnapshot(
        UUID id,
        String name,
        Map<UUID, Role> members,
        Set<Integer> colonyIds,
        Set<ClaimKey> claimKeys
) {
    public static EntitySnapshot of(RealmEntity entity) {
        return new EntitySnapshot(
                entity.id(),
                entity.name(),
                Map.copyOf(entity.members()),
                Set.copyOf(entity.colonyIds()),
                Set.copyOf(entity.claimKeys())
        );
    }
}

package com.theasshats.pcmcterritory.api;

import com.theasshats.pcmcterritory.core.TerritoryChunk;
import com.theasshats.pcmcterritory.data.RealmsSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API boundary (spec §8). Part 2 consumes only this package: it must not
 * reach into {@code data}/{@code integration}/{@code core} directly — those are
 * implementation details that can change without notice.
 */
public final class TerritoryApi {

    private TerritoryApi() {}

    /**
     * Resolves the governing entity chain for {@code pos} in {@code level}, leaf
     * first. In Part 1 this is trivially empty (wilderness) or a single-element
     * list (one entity, no hierarchy yet) — Part 2 extends the chain by walking
     * parent links above the leaf.
     */
    public static List<UUID> resolve(ServerLevel level, ChunkPos pos) {
        RealmsSavedData data = RealmsSavedData.get(level.getServer().overworld());
        return data.resolver().resolveChain(toTerritoryChunk(level, pos));
    }

    public static Optional<EntitySnapshot> getEntity(ServerLevel level, UUID entityId) {
        return RealmsSavedData.get(level.getServer().overworld())
                .registry().get(entityId)
                .map(EntitySnapshot::of);
    }

    public static Optional<EntitySnapshot> getEntityByName(ServerLevel level, String name) {
        return RealmsSavedData.get(level.getServer().overworld())
                .registry().findByName(name)
                .map(EntitySnapshot::of);
    }

    public static TerritoryChunk toTerritoryChunk(ServerLevel level, ChunkPos pos) {
        return TerritoryChunk.of(level.dimension().location().toString(), pos.x, pos.z);
    }
}

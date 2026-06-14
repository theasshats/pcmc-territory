package com.theasshats.pcmcterritory.core;

import java.util.Objects;

/**
 * A chunk position, keyed for the resolver cache. {@code levelId} is the dimension's
 * resource location (e.g. {@code "minecraft:overworld"}) as a string, and
 * {@code packedChunkPos} mirrors Minecraft's {@code ChunkPos.asLong()} packing so the
 * mod module can hand the cache real chunk coordinates without this module depending
 * on Minecraft classes.
 */
public record TerritoryChunk(String levelId, long packedChunkPos) {
    public TerritoryChunk {
        Objects.requireNonNull(levelId, "levelId");
    }

    public static long pack(int chunkX, int chunkZ) {
        return (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
    }

    public static int unpackX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int unpackZ(long packed) {
        return (int) (packed >>> 32);
    }

    public static TerritoryChunk of(String levelId, int chunkX, int chunkZ) {
        return new TerritoryChunk(levelId, pack(chunkX, chunkZ));
    }
}

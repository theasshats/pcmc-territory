package com.theasshats.pcmcterritory.integration.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.theasshats.pcmcterritory.core.ColonyLookup;
import com.theasshats.pcmcterritory.core.TerritoryChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

/**
 * Position -&gt; MineColonies colony id (spec §6).
 *
 * <p><b>Compile-verified, not yet runtime-verified (see docs/SPIKE-PART1.md §2):</b>
 * the entry point below — {@code IMinecoloniesAPI.getInstance().getColonyManager()
 * .getColonyByPosFromWorld(Level, BlockPos)} — is the documented 1.21.1 API surface,
 * and CI compiles this class against the real
 * {@code curse.maven:minecolonies-245506:8186694} jar (the spike's guess survived
 * contact). In-game behavior still needs the docs/PLAYTESTING.md pass. If a future
 * version moves the method or package, this is the one file to fix;
 * {@link ColonyLookup} callers are unaffected. MineColonies also attaches a per-chunk
 * capability recording the owning colony id, which would be an O(1) alternative to
 * this position lookup if available — worth checking on the box as a perf upgrade.
 */
public final class MineColoniesColonyLookup implements ColonyLookup {

    private final MinecraftServer server;

    public MineColoniesColonyLookup(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public OptionalInt colonyIdAt(TerritoryChunk chunk) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(chunk.levelId()));
        ServerLevel level = server.getLevel(levelKey);
        if (level == null) {
            return OptionalInt.empty();
        }

        int chunkX = TerritoryChunk.unpackX(chunk.packedChunkPos());
        int chunkZ = TerritoryChunk.unpackZ(chunk.packedChunkPos());
        BlockPos pos = new BlockPos((chunkX << 4) + 8, level.getSeaLevel(), (chunkZ << 4) + 8);

        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, pos);
        return colony == null ? OptionalInt.empty() : OptionalInt.of(colony.getID());
    }
}

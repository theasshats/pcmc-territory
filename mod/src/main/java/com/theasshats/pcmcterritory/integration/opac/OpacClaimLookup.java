package com.theasshats.pcmcterritory.integration.opac;

import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.ClaimLookup;
import com.theasshats.pcmcterritory.core.TerritoryChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.claims.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.api.OpenPACServerAPI;

import java.util.Optional;

/**
 * Position -&gt; OPAC claim owner (spec §6).
 *
 * <p><b>SPIKE — unverified against the real jar (see docs/SPIKE-PART1.md):</b> this
 * sandbox cannot resolve {@code maven.modrinth:open-parties-and-claims:0.26.2-neoforge}
 * (Modrinth maven is network-blocked here), so the {@code xaero.pac.common.server.claims.api}
 * package/types below are based on the published javadoc surface
 * (thexaero.github.io/open-parties-and-claims) but have not been compiled against the
 * actual jar. If the package/method names differ, this is the one file to fix;
 * {@link ClaimLookup} callers are unaffected.
 *
 * <p>{@link IPlayerChunkClaimAPI#getPlayerId()} returns the claim's "owner" — a real
 * player UUID for a personal claim, or a party's fake-player UUID for a party claim.
 * Either way it becomes the {@link ClaimKey} an entity binds via {@code /realm found}
 * in a later part; Part 1 only needs the reverse lookup to exist.
 */
public final class OpacClaimLookup implements ClaimLookup {

    private final MinecraftServer server;

    public OpacClaimLookup(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Optional<ClaimKey> claimAt(TerritoryChunk chunk) {
        IServerClaimsManagerAPI<?, ?, ?> claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();

        int chunkX = TerritoryChunk.unpackX(chunk.packedChunkPos());
        int chunkZ = TerritoryChunk.unpackZ(chunk.packedChunkPos());
        IPlayerChunkClaimAPI claim = claimsManager.get(ResourceLocation.parse(chunk.levelId()), chunkX, chunkZ);

        if (claim == null) {
            return Optional.empty();
        }
        return Optional.of(new ClaimKey(claim.getPlayerId()));
    }
}

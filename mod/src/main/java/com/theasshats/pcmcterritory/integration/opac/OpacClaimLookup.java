package com.theasshats.pcmcterritory.integration.opac;

import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.ClaimLookup;
import com.theasshats.pcmcterritory.core.TerritoryChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import java.util.Optional;
import java.util.UUID;

/**
 * Position -&gt; OPAC claim owner (spec §6).
 *
 * <p><b>Compile-verified, not yet runtime-verified (see docs/SPIKE-PART1.md §3):</b>
 * the spike's guessed package names failed the first real CI compile; the imports
 * below were then corrected against the OPAC 1.21 branch source
 * (github.com/thexaero/open-parties-and-claims) and CI compiles this class against
 * the real {@code neoforge-1.21.1-0.26.2} jar. In-game behavior still needs the
 * docs/PLAYTESTING.md pass. If a future OPAC version moves these types, this is the
 * one file to fix; {@link ClaimLookup} callers are unaffected.
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
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();

        int chunkX = TerritoryChunk.unpackX(chunk.packedChunkPos());
        int chunkZ = TerritoryChunk.unpackZ(chunk.packedChunkPos());
        IPlayerChunkClaimAPI claim = claimsManager.get(ResourceLocation.parse(chunk.levelId()), chunkX, chunkZ);

        if (claim == null) {
            return Optional.empty();
        }
        UUID ownerId = claim.getPlayerId();
        if (ownerId == null) {
            // The 1.21 API documents getPlayerId() as not-null — even server claims
            // carry a dedicated owner (PlayerConfig.SERVER_CLAIM_UUID). Kept as a
            // zero-cost guard against a future version regressing that contract.
            return Optional.empty();
        }
        return Optional.of(new ClaimKey(ownerId));
    }
}

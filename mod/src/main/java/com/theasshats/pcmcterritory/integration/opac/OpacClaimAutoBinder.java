package com.theasshats.pcmcterritory.integration.opac;

import com.mojang.logging.LogUtils;
import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.RealmEntity;
import com.theasshats.pcmcterritory.core.RealmsRegistry;
import com.theasshats.pcmcterritory.data.RealmsSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.List;
import java.util.UUID;

/**
 * Auto-binds a realm member's Open Parties and Claims claim to their realm (issue #7).
 *
 * <p>Registered on OPAC's claims-manager tracker at {@code ServerStartedEvent}.
 * {@link #onChunkChange} fires synchronously on the server thread for every claim and
 * unclaim — verified against the OPAC 1.21 branch source: the listener API lives in
 * {@code xaero.pac.common.claims.tracker.api} and the tracker is reachable through the
 * public {@link OpenPACServerAPI}. Server-thread delivery means the (server-thread-only)
 * registry can be mutated directly here, no marshalling required.
 *
 * <p><b>Soft dependency:</b> this class names OPAC API types directly and is therefore
 * only loaded when OPAC is present, gated through
 * {@link com.theasshats.pcmcterritory.integration.TerritoryIntegrations} exactly like
 * {@link OpacClaimLookup}.
 *
 * <p><b>Binding rule.</b> A realm binds a claim by its <em>owner</em> id, so a member's
 * first claim binds their owner id and every later claim of theirs is already covered.
 * To stay unambiguous, auto-binding only acts when the owner is a member of
 * <em>exactly one</em> realm and isn't already bound; the zero-realm, multi-realm, and
 * already-bound cases are left to the manual {@code /realm debug bindclaim} binder.
 */
public final class OpacClaimAutoBinder implements IClaimsManagerListenerAPI {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;

    private OpacClaimAutoBinder(MinecraftServer server) {
        this.server = server;
    }

    /** Registers a fresh auto-binder on OPAC's claim tracker for {@code server}. */
    public static void register(MinecraftServer server) {
        OpenPACServerAPI.get(server).getServerClaimsManager().getTracker()
                .register(new OpacClaimAutoBinder(server));
    }

    @Override
    public void onChunkChange(ResourceLocation dimension, int chunkX, int chunkZ, IPlayerChunkClaimAPI claim) {
        if (claim == null) {
            return; // an unclaim — nothing to bind
        }
        UUID ownerId = claim.getPlayerId();
        if (ownerId == null) {
            return; // documented not-null, but guard rather than risk a NPE on the hot path
        }

        ClaimKey key = new ClaimKey(ownerId);
        RealmsSavedData data = RealmsSavedData.get(server.overworld());
        RealmsRegistry registry = data.registry();

        if (registry.entityIdForClaim(key).isPresent()) {
            return; // owner already bound (manually or earlier) — first bind wins
        }

        List<RealmEntity> realms = registry.entitiesForMember(ownerId);
        if (realms.size() != 1) {
            return; // not a member, or a member of several realms — ambiguous, leave it to manual binding
        }

        RealmEntity realm = realms.get(0);
        registry.bindClaim(realm.id(), key);
        data.setDirty();
        LOGGER.debug("Auto-bound claim owner {} to realm '{}' ({})", ownerId, realm.name(), realm.id());

        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    "commands.pcmc_territory.autobind.notify", realm.name()));
        }
    }

    @Override
    public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
        // OPAC documents this as client-only; no server-side territory effect.
    }

    @Override
    public void onDimensionChange(ResourceLocation dimension) {
        // OPAC documents this as client-only; no server-side territory effect.
    }
}

package com.theasshats.pcmcterritory.data;

import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.ClaimLookup;
import com.theasshats.pcmcterritory.core.ColonyLookup;
import com.theasshats.pcmcterritory.core.RealmEntity;
import com.theasshats.pcmcterritory.core.RealmsRegistry;
import com.theasshats.pcmcterritory.core.Role;
import com.theasshats.pcmcterritory.core.TerritoryResolver;
import com.theasshats.pcmcterritory.event.RealmEventBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-level {@link SavedData} attached to the overworld (spec §2). Wraps the
 * engine-agnostic {@link RealmsRegistry}/{@link TerritoryResolver} from {@code :core}
 * and (de)serializes entities to NBT.
 *
 * <p>The resolver starts with {@link ColonyLookup#NOOP}/{@link ClaimLookup#NOOP} —
 * {@code TerritoryIntegrations} swaps in real lookups once it has determined which
 * of MineColonies/OPAC are loaded (spec §9 soft-dep degradation).
 */
public final class RealmsSavedData extends SavedData {

    public static final String DATA_NAME = "pcmc_territory";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final RealmsRegistry registry = new RealmsRegistry();
    private final TerritoryResolver resolver;

    private RealmsSavedData(ServerLevel overworld) {
        // overworld::getGameTime drives the resolver's TTL-based cache expiry
        // (core.TerritoryResolver) so a stale MineColonies/OPAC claim is recomputed
        // on the next lookup after DEFAULT_TTL_TICKS, even with no registry edit.
        this.resolver = new TerritoryResolver(registry, ColonyLookup.NOOP, ClaimLookup.NOOP,
                overworld::getGameTime, TerritoryResolver.DEFAULT_TTL_TICKS);
        // TerritoryResolver already listens for cache invalidation (core.RegistryListener);
        // this second listener bridges the same callbacks to the public api events.
        registry.addListener(new RealmEventBridge());
    }

    /** Always fetched from the overworld, regardless of which dimension a chunk is in (spec §2). */
    public static RealmsSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new RealmsSavedData(overworld),
                        (tag, provider) -> load(tag, provider, overworld), null),
                DATA_NAME);
    }

    public RealmsRegistry registry() {
        return registry;
    }

    public TerritoryResolver resolver() {
        return resolver;
    }

    private static RealmsSavedData load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel overworld) {
        RealmsSavedData data = new RealmsSavedData(overworld);
        ListTag entitiesTag = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entitiesTag.size(); i++) {
            CompoundTag entityTag = entitiesTag.getCompound(i);
            try {
                UUID id = entityTag.getUUID("id");
                String name = entityTag.getString("name");
                long createdTick = entityTag.getLong("createdTick");

                Map<UUID, Role> members = new LinkedHashMap<>();
                ListTag membersTag = entityTag.getList("members", Tag.TAG_COMPOUND);
                for (int m = 0; m < membersTag.size(); m++) {
                    CompoundTag memberTag = membersTag.getCompound(m);
                    members.put(memberTag.getUUID("player"), Role.valueOf(memberTag.getString("role")));
                }

                Set<Integer> colonyIds = new LinkedHashSet<>();
                for (int colonyId : entityTag.getIntArray("colonyIds")) {
                    colonyIds.add(colonyId);
                }

                Set<ClaimKey> claimKeys = new LinkedHashSet<>();
                ListTag claimsTag = entityTag.getList("claimKeys", Tag.TAG_COMPOUND);
                for (int c = 0; c < claimsTag.size(); c++) {
                    claimKeys.add(new ClaimKey(claimsTag.getCompound(c).getUUID("owner")));
                }

                data.registry.restoreEntity(id, name, createdTick, members, colonyIds, claimKeys);
            } catch (RuntimeException e) {
                // A corrupted entry or an incompatible save from a future/older version
                // shouldn't take the whole world down with it - skip just this entity.
                LOGGER.error("Skipping malformed entity at index {} in {} save data: {}", i, DATA_NAME, e.toString());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entitiesTag = new ListTag();
        for (RealmEntity entity : registry.all()) {
            CompoundTag entityTag = new CompoundTag();
            entityTag.putUUID("id", entity.id());
            entityTag.putString("name", entity.name());
            entityTag.putLong("createdTick", entity.createdTick());

            ListTag membersTag = new ListTag();
            entity.members().forEach((playerId, role) -> {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("player", playerId);
                memberTag.putString("role", role.name());
                membersTag.add(memberTag);
            });
            entityTag.put("members", membersTag);

            entityTag.putIntArray("colonyIds",
                    entity.colonyIds().stream().mapToInt(Integer::intValue).toArray());

            ListTag claimsTag = new ListTag();
            for (ClaimKey claimKey : entity.claimKeys()) {
                CompoundTag claimTag = new CompoundTag();
                claimTag.putUUID("owner", claimKey.ownerId());
                claimsTag.add(claimTag);
            }
            entityTag.put("claimKeys", claimsTag);

            entitiesTag.add(entityTag);
        }
        tag.put("entities", entitiesTag);
        return tag;
    }
}

package com.theasshats.pcmcterritory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theasshats.pcmcterritory.api.EntitySnapshot;
import com.theasshats.pcmcterritory.api.TerritoryApi;
import com.theasshats.pcmcterritory.core.ClaimKey;
import com.theasshats.pcmcterritory.core.RealmEntity;
import com.theasshats.pcmcterritory.core.Role;
import com.theasshats.pcmcterritory.core.TerritoryChunk;
import com.theasshats.pcmcterritory.data.RealmsSavedData;
import com.theasshats.pcmcterritory.integration.TerritoryIntegrations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Part 1 command surface (spec §7/§8): {@code /realm found}, {@code /realm info},
 * {@code /realm whogoverns}. Server-authoritative — all checks run against the
 * overworld-attached {@link RealmsSavedData}, never trusting the client.
 */
public final class RealmCommand {

    private RealmCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("realm")
                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> found(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("whogoverns")
                        .executes(ctx -> whoGoverns(ctx.getSource())))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("bindclaim")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> bindClaim(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))));
    }

    private static int found(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!TerritoryIntegrations.mineColoniesPresent()) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.found.minecolonies_absent"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        RealmsSavedData data = data(level);
        TerritoryChunk chunk = chunkAt(level, player);

        OptionalInt colonyId = data.resolver().colonyLookup().colonyIdAt(chunk);
        if (colonyId.isEmpty()) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.found.no_colony"));
            return 0;
        }

        Optional<RealmEntity> existingByName = data.registry().findByName(name);
        Optional<UUID> existingOwner = data.registry().entityIdForColony(colonyId.getAsInt());

        if (existingByName.isPresent()) {
            RealmEntity entity = existingByName.get();

            if (!entity.hasAtLeast(player.getUUID(), Role.OFFICER)) {
                source.sendFailure(Component.translatable("commands.pcmc_territory.no_permission"));
                return 0;
            }
            if (existingOwner.isPresent() && !existingOwner.get().equals(entity.id())) {
                String ownerName = entityName(data, existingOwner.get());
                source.sendFailure(Component.translatable(
                        "commands.pcmc_territory.found.colony_already_bound", colonyId.getAsInt(), ownerName));
                return 0;
            }

            data.registry().bindColony(entity.id(), colonyId.getAsInt());
            data.setDirty();
            int boundColonyId = colonyId.getAsInt();
            source.sendSuccess(() -> Component.translatable(
                    "commands.pcmc_territory.found.success", entity.name(), boundColonyId), true);
            return 1;
        }

        if (existingOwner.isPresent()) {
            String ownerName = entityName(data, existingOwner.get());
            source.sendFailure(Component.translatable(
                    "commands.pcmc_territory.found.colony_already_bound", colonyId.getAsInt(), ownerName));
            return 0;
        }

        RealmEntity entity = data.registry().createEntity(name, player.getUUID(), level.getGameTime());
        data.registry().bindColony(entity.id(), colonyId.getAsInt());
        data.setDirty();

        int boundColonyId = colonyId.getAsInt();
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.found.success", entity.name(), boundColonyId), true);
        return 1;
    }

    private static int info(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        RealmsSavedData data = data(level);

        EntitySnapshot entity;
        if (name != null) {
            Optional<EntitySnapshot> byName = data.registry().findByName(name).map(EntitySnapshot::of);
            if (byName.isEmpty()) {
                source.sendFailure(Component.translatable("commands.pcmc_territory.info.not_found", name));
                return 0;
            }
            entity = byName.get();
        } else {
            ServerPlayer player = source.getPlayerOrException();
            TerritoryChunk chunk = chunkAt(level, player);
            Optional<EntitySnapshot> here = data.resolver().resolveLeaf(chunk)
                    .flatMap(data.registry()::get)
                    .map(EntitySnapshot::of);
            if (here.isEmpty()) {
                source.sendFailure(Component.translatable("commands.pcmc_territory.info.none_here"));
                return 0;
            }
            entity = here.get();
        }

        sendInfo(source, entity);
        return 1;
    }

    private static void sendInfo(CommandSourceStack source, EntitySnapshot entity) {
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.info.header", entity.name(), entity.id().toString()), false);

        String members = entity.members().entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.info.members", members.isEmpty() ? "-" : members), false);

        String colonies = entity.colonyIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.info.colonies", colonies.isEmpty() ? "-" : colonies), false);

        String claims = entity.claimKeys().stream()
                .map(ClaimKey::ownerId)
                .map(UUID::toString)
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.info.claims", claims.isEmpty() ? "-" : claims), false);
    }

    private static int whoGoverns(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        RealmsSavedData data = data(level);
        TerritoryChunk chunk = chunkAt(level, player);

        Optional<UUID> leaf = data.resolver().resolveLeaf(chunk);
        if (leaf.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.pcmc_territory.whogoverns.ungoverned"), false);
            return 1;
        }

        String name = entityName(data, leaf.get());
        source.sendSuccess(() -> Component.translatable("commands.pcmc_territory.whogoverns.governed", name), false);
        return 1;
    }

    /**
     * Op-only playtest/debug helper: binds the OPAC claim covering the player's
     * current chunk to an existing entity. Part 1 has no player-facing claim
     * binding (that arrives with Part 2's government commands), but without this
     * the OPAC resolution path — registry {@code bindClaim} + resolver fallback —
     * would be unreachable in-game and the playtest checklist couldn't cover it
     * (see docs/PLAYTESTING.md, scenario 3).
     */
    private static int bindClaim(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!TerritoryIntegrations.opacPresent()) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.debug.opac_absent"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        RealmsSavedData data = data(level);
        TerritoryChunk chunk = chunkAt(level, player);

        Optional<ClaimKey> claim = data.resolver().claimLookup().claimAt(chunk);
        if (claim.isEmpty()) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.debug.no_claim_here"));
            return 0;
        }

        Optional<RealmEntity> entity = data.registry().findByName(name);
        if (entity.isEmpty()) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.info.not_found", name));
            return 0;
        }

        if (!entity.get().hasAtLeast(player.getUUID(), Role.OFFICER)) {
            source.sendFailure(Component.translatable("commands.pcmc_territory.no_permission"));
            return 0;
        }

        Optional<UUID> existing = data.registry().entityIdForClaim(claim.get());
        if (existing.isPresent() && !existing.get().equals(entity.get().id())) {
            String ownerName = entityName(data, existing.get());
            source.sendFailure(Component.translatable("commands.pcmc_territory.debug.claim_already_bound", ownerName));
            return 0;
        }

        data.registry().bindClaim(entity.get().id(), claim.get());
        data.setDirty();

        String claimOwner = claim.get().ownerId().toString();
        String boundEntityName = entity.get().name();
        source.sendSuccess(() -> Component.translatable(
                "commands.pcmc_territory.debug.bindclaim.success", claimOwner, boundEntityName), true);
        return 1;
    }

    /** Fetches the overworld-attached realms data for {@code level} (spec §2). */
    private static RealmsSavedData data(ServerLevel level) {
        return RealmsSavedData.get(level.getServer().overworld());
    }

    /** Resolves the territory chunk {@code player} currently stands in. */
    private static TerritoryChunk chunkAt(ServerLevel level, ServerPlayer player) {
        return TerritoryApi.toTerritoryChunk(level, new ChunkPos(player.blockPosition()));
    }

    /** Looks up an entity's display name, falling back to {@code "?"} if it no longer exists. */
    private static String entityName(RealmsSavedData data, UUID id) {
        return data.registry().get(id).map(RealmEntity::name).orElse("?");
    }
}

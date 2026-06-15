package com.theasshats.pcmcterritory;

import com.mojang.logging.LogUtils;
import com.theasshats.pcmcterritory.command.RealmCommand;
import com.theasshats.pcmcterritory.data.RealmsSavedData;
import com.theasshats.pcmcterritory.integration.TerritoryIntegrations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Mod entry point (spec §8 Part 1). Wires the {@code :core} resolver/registry to
 * Brigadier commands and, on server start, to the MineColonies/OPAC adapters —
 * or their {@code NOOP} fallbacks when those mods aren't loaded (spec §9).
 */
@Mod(PcmcTerritory.MOD_ID)
public final class PcmcTerritory {

    public static final String MOD_ID = "pcmc_territory";

    private static final Logger LOGGER = LogUtils.getLogger();

    public PcmcTerritory(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        RealmCommand.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        RealmsSavedData data = RealmsSavedData.get(event.getServer().overworld());

        boolean mineColonies = TerritoryIntegrations.mineColoniesPresent();
        boolean opac = TerritoryIntegrations.opacPresent();

        data.resolver().setColonyLookup(TerritoryIntegrations.createColonyLookup(event.getServer()));
        data.resolver().setClaimLookup(TerritoryIntegrations.createClaimLookup(event.getServer()));

        LOGGER.info("[{}] MineColonies {} - colony resolution {}", MOD_ID,
                mineColonies ? "found" : "not found", mineColonies ? "enabled" : "disabled");
        LOGGER.info("[{}] Open Parties and Claims {} - claim resolution {}", MOD_ID,
                opac ? "found" : "not found", opac ? "enabled" : "disabled");
    }

    private void onServerStarted(ServerStartedEvent event) {
        // Deferred to ServerStarted (not ServerStarting) so OPAC's claims manager is
        // fully initialised before we attach the claim listener. Auto-binding is a
        // non-essential soft-dep feature, so a registration failure is logged and
        // swallowed rather than allowed to abort server start.
        if (!TerritoryIntegrations.opacPresent()) {
            return;
        }
        try {
            TerritoryIntegrations.registerClaimAutoBinder(event.getServer());
            LOGGER.info("[{}] OPAC claim auto-binding enabled - a member's new claims bind to their realm", MOD_ID);
        } catch (Throwable t) {
            LOGGER.error("[{}] Failed to register the OPAC claim auto-binder; member claims will not auto-bind", MOD_ID, t);
        }
    }
}

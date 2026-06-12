package com.theasshats.pcmcterritory.integration;

import com.theasshats.pcmcterritory.core.ClaimLookup;
import com.theasshats.pcmcterritory.core.ColonyLookup;
import com.theasshats.pcmcterritory.integration.minecolonies.MineColoniesColonyLookup;
import com.theasshats.pcmcterritory.integration.opac.OpacClaimLookup;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * Soft-dependency wiring (spec §9). The {@code minecolonies}/{@code OPAC} adapter
 * classes reference those mods' API types directly, but the JVM only links/loads
 * those classes when {@link #createColonyLookup}/{@link #createClaimLookup}
 * actually instantiate them — gated on {@link ModList#isLoaded}. When a dep is
 * absent, the corresponding {@code core} {@code *Lookup.NOOP} is used instead, so
 * the resolver degrades to "no colonies/claims known" rather than crashing.
 */
public final class TerritoryIntegrations {

    /** MineColonies' mod id (verify against the 1.1.1327-1.21.1 jar — spec spike item (a)). */
    public static final String MINECOLONIES_MOD_ID = "minecolonies";

    /** Open Parties and Claims' mod id (verify against the 0.26.2 neoforge jar — spec spike item (b)). */
    public static final String OPAC_MOD_ID = "openpartiesandclaims";

    private TerritoryIntegrations() {}

    public static boolean mineColoniesPresent() {
        return ModList.get().isLoaded(MINECOLONIES_MOD_ID);
    }

    public static boolean opacPresent() {
        return ModList.get().isLoaded(OPAC_MOD_ID);
    }

    public static ColonyLookup createColonyLookup(MinecraftServer server) {
        return mineColoniesPresent() ? new MineColoniesColonyLookup(server) : ColonyLookup.NOOP;
    }

    public static ClaimLookup createClaimLookup(MinecraftServer server) {
        return opacPresent() ? new OpacClaimLookup(server) : ClaimLookup.NOOP;
    }
}

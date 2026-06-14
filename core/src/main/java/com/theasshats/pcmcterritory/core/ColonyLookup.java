package com.theasshats.pcmcterritory.core;

import java.util.OptionalInt;

/**
 * Position -&gt; MineColonies colony id. The mod module implements this against
 * {@code IColonyManager} when MineColonies is loaded; {@link #NOOP} is used
 * when it is absent (spec §9 soft-dep degradation).
 */
@FunctionalInterface
public interface ColonyLookup {

    ColonyLookup NOOP = chunk -> OptionalInt.empty();

    OptionalInt colonyIdAt(TerritoryChunk chunk);
}

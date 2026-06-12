package com.theasshats.pcmcterritory.core;

import java.util.Optional;

/**
 * Position -&gt; Open Parties and Claims claim owner. The mod module implements
 * this against OPAC's claims manager when it is loaded; {@link #NOOP} is used
 * when it is absent (spec §9 soft-dep degradation).
 */
@FunctionalInterface
public interface ClaimLookup {

    ClaimLookup NOOP = chunk -> Optional.empty();

    Optional<ClaimKey> claimAt(TerritoryChunk chunk);
}

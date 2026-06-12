package com.theasshats.pcmcterritory.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to an Open Parties and Claims claim "owner" (a player or party id)
 * whose claimed chunks an {@link RealmEntity} has bound. The territory mod never
 * creates or owns OPAC claims itself — this is purely a reverse-lookup key.
 */
public record ClaimKey(UUID ownerId) {
    public ClaimKey {
        Objects.requireNonNull(ownerId, "ownerId");
    }
}

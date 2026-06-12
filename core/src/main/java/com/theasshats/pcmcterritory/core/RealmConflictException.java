package com.theasshats.pcmcterritory.core;

/**
 * Thrown when a colony or claim binding would conflict with an existing one
 * (e.g. a colony already bound to a different entity). Callers are expected to
 * pre-check via {@link RealmsRegistry#entityIdForColony} /
 * {@link RealmsRegistry#entityIdForClaim} and report a clean error rather than
 * overwriting another entity's binding; this exception is a safety net for any
 * caller that skips that check.
 */
public final class RealmConflictException extends RuntimeException {
    public RealmConflictException(String message) {
        super(message);
    }
}

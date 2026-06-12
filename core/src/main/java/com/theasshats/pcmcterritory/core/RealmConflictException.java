package com.theasshats.pcmcterritory.core;

/**
 * Thrown when a colony or claim binding would conflict with an existing one
 * (e.g. a colony already bound to a different entity). Commands catch this to
 * report a clean error rather than silently overwriting another entity's claim.
 */
public final class RealmConflictException extends RuntimeException {
    public RealmConflictException(String message) {
        super(message);
    }
}

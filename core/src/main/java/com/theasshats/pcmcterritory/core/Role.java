package com.theasshats.pcmcterritory.core;

/**
 * A member's standing within a {@link RealmEntity}, ordered low-to-high so
 * {@link #atLeast(Role)} can gate mutating commands (found/info edits, etc.).
 */
public enum Role {
    CITIZEN,
    OFFICER,
    LEADER;

    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}

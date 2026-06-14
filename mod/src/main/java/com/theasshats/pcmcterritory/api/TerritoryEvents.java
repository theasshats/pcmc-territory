package com.theasshats.pcmcterritory.api;

import net.neoforged.bus.api.Event;

/**
 * Entity lifecycle events, posted to {@code NeoForge.EVENT_BUS} whenever the
 * realm registry changes. Part 2 (government/laws) listens for these instead of
 * polling the registry — keeps cross-mod coupling to events + {@link TerritoryApi}
 * only (spec §8 "Public surface for the parts above it").
 */
public abstract class TerritoryEvents extends Event {

    private final EntitySnapshot entity;

    protected TerritoryEvents(EntitySnapshot entity) {
        this.entity = entity;
    }

    public EntitySnapshot entity() {
        return entity;
    }

    /** A new entity was founded. */
    public static final class Created extends TerritoryEvents {
        public Created(EntitySnapshot entity) {
            super(entity);
        }
    }

    /** An entity was deleted. */
    public static final class Removed extends TerritoryEvents {
        public Removed(EntitySnapshot entity) {
            super(entity);
        }
    }

    /** An entity's membership, name, or colony/claim bindings changed. */
    public static final class Changed extends TerritoryEvents {
        public Changed(EntitySnapshot entity) {
            super(entity);
        }
    }
}

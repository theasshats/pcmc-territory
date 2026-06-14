package com.theasshats.pcmcterritory.core;

/**
 * Notified on {@link RealmsRegistry} mutations. The mod module's implementation
 * both posts the public {@code api} create/remove/changed events and invalidates
 * the {@link TerritoryResolver} cache (spec §6).
 */
public interface RegistryListener {

    default void onEntityCreated(RealmEntity entity) {}

    default void onEntityRemoved(RealmEntity entity) {}

    default void onEntityChanged(RealmEntity entity) {}
}

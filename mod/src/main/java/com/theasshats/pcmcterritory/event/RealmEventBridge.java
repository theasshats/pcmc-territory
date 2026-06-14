package com.theasshats.pcmcterritory.event;

import com.theasshats.pcmcterritory.api.EntitySnapshot;
import com.theasshats.pcmcterritory.api.TerritoryEvents;
import com.theasshats.pcmcterritory.core.RealmEntity;
import com.theasshats.pcmcterritory.core.RegistryListener;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Bridges {@code :core}'s {@link RegistryListener} callbacks to the public
 * {@link TerritoryEvents} posted on {@code NeoForge.EVENT_BUS}. Registered once
 * per {@code RealmsSavedData} instance.
 */
public final class RealmEventBridge implements RegistryListener {

    @Override
    public void onEntityCreated(RealmEntity entity) {
        NeoForge.EVENT_BUS.post(new TerritoryEvents.Created(EntitySnapshot.of(entity)));
    }

    @Override
    public void onEntityRemoved(RealmEntity entity) {
        NeoForge.EVENT_BUS.post(new TerritoryEvents.Removed(EntitySnapshot.of(entity)));
    }

    @Override
    public void onEntityChanged(RealmEntity entity) {
        NeoForge.EVENT_BUS.post(new TerritoryEvents.Changed(EntitySnapshot.of(entity)));
    }
}

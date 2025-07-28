package org.matsim.maas.preference.events;

import org.matsim.core.events.handler.EventHandler;

/**
 * Handler interface for PreferenceUpdateEvent.
 * Following MATSim event handler conventions.
 */
public interface PreferenceUpdateEventHandler extends EventHandler {
    
    void handleEvent(PreferenceUpdateEvent event);
    
}
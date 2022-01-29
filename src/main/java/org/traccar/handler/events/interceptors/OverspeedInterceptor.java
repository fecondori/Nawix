package org.traccar.handler.events.interceptors;

import org.traccar.database.DeviceManager;
import org.traccar.database.GeofenceManager;
import org.traccar.model.Position;

/**
 * Intercepts the overspeed event handler when its called
 */
public class OverspeedInterceptor implements BaseInterceptor{

    private final GeofenceManager geofenceManager;
    private final DeviceManager deviceManager;

    public OverspeedInterceptor(DeviceManager deviceManager, GeofenceManager geofenceManager){
        this.deviceManager = deviceManager;
        this.geofenceManager = geofenceManager;
    }
    @Override
    public void Invoke(Position position) {

    }
}

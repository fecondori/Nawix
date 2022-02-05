package org.traccar.handler.events.interceptors;

import org.traccar.Context;
import org.traccar.model.Event;
import org.traccar.model.Position;

public class GeofenceEnterInterceptor extends BaseInterceptor{
    @Override
    public String getType() {
        return Event.TYPE_GEOFENCE_ENTER;
    }

    @Override
    public void invoke(Event event, Position position) {

    }
}

package org.traccar.handler.events.interceptors;

import org.traccar.model.Event;
import org.traccar.model.Position;

import java.util.Map;

public abstract class BaseInterceptor {
    public abstract String getType();
    public abstract void invoke(Event event, Position position);
}

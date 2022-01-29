package org.traccar.handler.events.interceptors;

import org.traccar.model.Position;

public interface BaseInterceptor{
     void Invoke(Position position);
}

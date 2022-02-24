package org.traccar.handler.events.filter;

import org.traccar.model.Position;

public abstract class BaseFilter {
    public abstract boolean passFilter(Position position);

    public abstract boolean isActive();
}

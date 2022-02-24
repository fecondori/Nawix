package org.traccar.handler.events.filter;

import org.traccar.model.Position;

import java.util.ArrayList;
import java.util.Collection;

public class OutdatedFilterManager {

    private Collection<BaseFilter> filters = new ArrayList<>();

    public OutdatedFilterManager(){
        filters.add(new OperatorFilter());
        filters.add(new DeltaTimeFilter());
    }

    public boolean passFilters(Position position){
        System.out.println("Attempting to pass filters");
        return filters.stream().allMatch(filter -> filter.isActive() && filter.passFilter(position));
    }
}

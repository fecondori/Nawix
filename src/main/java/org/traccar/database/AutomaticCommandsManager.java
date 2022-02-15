package org.traccar.database;

import org.traccar.model.nawix.AutomaticCommand;

import java.util.Collection;
import java.util.stream.Collectors;

public class AutomaticCommandsManager extends ExtendedObjectManager<AutomaticCommand> {
    public AutomaticCommandsManager(DataManager dataManager) {
        super(dataManager, AutomaticCommand.class);
    }

    public Collection<AutomaticCommand> getCommandsByEventType(String eventType){
        return getItems(getAllItems()).stream().filter(c -> c.getEventType().compareTo(eventType) == 0).collect(Collectors.toList());
    }
}

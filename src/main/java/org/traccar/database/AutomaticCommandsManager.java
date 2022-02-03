package org.traccar.database;

import org.traccar.model.nawix.AutomaticCommand;

public class AutomaticCommandsManager extends ExtendedObjectManager<AutomaticCommand> {
    public AutomaticCommandsManager(DataManager dataManager) {
        super(dataManager, AutomaticCommand.class);
    }
}

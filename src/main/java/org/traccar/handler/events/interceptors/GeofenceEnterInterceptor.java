package org.traccar.handler.events.interceptors;

import net.fortuna.ical4j.model.property.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.database.AutomaticCommandsManager;
import org.traccar.database.GeofenceManager;
import org.traccar.model.Command;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.model.nawix.AutomaticCommand;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GeofenceEnterInterceptor extends BaseInterceptor{
    private Logger LOGGER = LoggerFactory.getLogger(OverspeedInterceptor.class);
    private AutomaticCommandsManager automaticCommandManager;
    private GeofenceManager geofenceManager;

    public  GeofenceEnterInterceptor(){
        automaticCommandManager = Context.getAutomaticCommandManager();
        geofenceManager = Context.getGeofenceManager();
    }

    @Override
    public String getType() {
        return Event.TYPE_GEOFENCE_ENTER;
    }

    @Override
    public void invoke(Event event, Position position) {
        LOGGER.info(String.format("Intercepting geofence enter, device id: %d, event id: %d, position id:", event.getDeviceId(), event.getId(), position.getId()));
        if(automaticCommandManager == null) automaticCommandManager = Context.getAutomaticCommandManager();
        if(geofenceManager == null) geofenceManager = Context.getGeofenceManager();

        Collection<AutomaticCommand> auomaticCommands = automaticCommandManager.getCommandsByEventType(getType());
        Collection<Command> commands = auomaticCommands.stream()
                .filter(cmd -> shouldInvoke(cmd, event))
                .map(cmd -> fromAutomaticCommand(cmd, position.getDeviceId()))
                .collect(Collectors.toList());

        for (Command command : commands){
            LOGGER.info(String.format("Trying to send command %s, device id: %d, event id: %d", command.getString("data"), event.getDeviceId(), event.getId()));
            sendCommand(command);
        }


    }

    private boolean shouldInvoke(AutomaticCommand command, Event event){
        Geofence geofence = geofenceManager.getById(event.getGeofenceId());
        String protocol = Context.getConnectionManager().getActiveDevice(event.getDeviceId()).getProtocol().getName();

        return matchesType(command, geofence)
                && matchesProtocol(command, protocol);
    }

    private boolean matchesType(AutomaticCommand command, Geofence geofence){
        String commandGeofenceType = command.getString("geofenceType");
        if(commandGeofenceType == null) return false;

        String geofenceType = geofence.getString("geofenceType");
        if(geofenceType == null) return false;

        if(commandGeofenceType.toLowerCase().compareTo(geofenceType.toLowerCase()) == 0)
            return true;

        return false;
    }

    private boolean matchesProtocol(AutomaticCommand command, String protocol){
        return command.getProtocol().compareTo(protocol) == 0;
    }

    private Command fromAutomaticCommand(AutomaticCommand automaticCommand, long deviceId) {
        Command command = new Command();
        command.setDeviceId(deviceId);
        command.set("data", automaticCommand.getCommandData());
        command.setType(automaticCommand.getCommandType());
        return command;
    }

    private void sendCommand(Command command){
        try {
            Context.getCommandsManager().sendCommand(command);
        } catch (Exception e) {
            LOGGER.info(e.toString());
        }
    }
}

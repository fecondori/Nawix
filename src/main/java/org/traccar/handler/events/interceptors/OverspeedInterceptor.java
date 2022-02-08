package org.traccar.handler.events.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.database.AutomaticCommandsManager;
import org.traccar.model.Command;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.nawix.AutomaticCommand;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class OverspeedInterceptor extends BaseInterceptor{
    Logger LOGGER = LoggerFactory.getLogger(OverspeedInterceptor.class);
    private AutomaticCommandsManager automaticCommandManager;

    public  OverspeedInterceptor(){
        automaticCommandManager = Context.getAutomaticCommandManager();
    }

    @Override
    public String getType() {
       return Event.TYPE_DEVICE_OVERSPEED;
    }

    @Override
    public void invoke(Event event, Position position) {

        LOGGER.info(String.format("Intercepting event, device id: %d, event id: %d, position id:", event.getDeviceId(), event.getId(), position.getDeviceId()));
        if(automaticCommandManager == null) automaticCommandManager = Context.getAutomaticCommandManager();
        Set<Long> allIds = automaticCommandManager.getAllItems();
        Collection<AutomaticCommand> automaticCommands = automaticCommandManager.getItems(allIds);
        Collection<Command> commands = automaticCommands.stream()
                .filter(cmd -> shouldInvoke(cmd, event))
                .map(cmd -> fromAutomaticCommand(cmd, event.getDeviceId()))
                .collect(Collectors.toList());

        for (Command command : commands){
            LOGGER.info(String.format("Trying to send command %s, device id: %d, event id: %d", command.getString("data"), event.getDeviceId(), event.getId()));
            sendCommand(command);
        }

    }

    private void sendCommand(Command command){

        try {
            Context.getCommandsManager().sendCommand(command);
        } catch (Exception e) {
            LOGGER.info(e.toString());
        }
    }

    private Command fromAutomaticCommand(AutomaticCommand automaticCommand, long deviceId) {
        Command command = new Command();
        command.setDeviceId(deviceId);
        command.set("data", automaticCommand.getCommandData());
        command.setType(automaticCommand.getCommandType());
        return command;
    }

    public boolean shouldInvoke(AutomaticCommand command, Event event) {
        boolean onlyInsideGeofence = event.getInteger("geofenceId") != 0;
        int speedLimit = event.getInteger("speedLimit");
        if(speedLimit == 0) return false;

        String protocol = Context.getConnectionManager().getActiveDevice(event.getDeviceId()).getProtocol().getName();

        return matchesOnlyInsideGeofences(command, onlyInsideGeofence, event.getGeofenceId()) &&
                matchesLowerSpeedLimit(command, speedLimit) &&
                matchesUpperSpeedLimit(command, speedLimit) &&
                matchesProtocol(command, protocol);
    }

    private boolean matchesProtocol(AutomaticCommand command, String protocol){
        return command.getProtocol().compareTo(protocol) == 0;
    }

    private boolean matchesOnlyInsideGeofences(AutomaticCommand command, boolean onlyInsideGeofence, long geofenceId){
        return command.getBoolean("onlyInsideGeofences") && geofenceId != 0 || !onlyInsideGeofence;
    }

    private boolean matchesUpperSpeedLimit(AutomaticCommand command, int speedLimit){
        int cmdUpperSpeedLimit = command.getInteger("upperSpeedLimit");
        return  speedLimit <= cmdUpperSpeedLimit;
    }

    private boolean matchesLowerSpeedLimit(AutomaticCommand command, int speedLimit){
        int cmdLowerSpeedLimit = command.getInteger("lowerSpeedLimit");
        return cmdLowerSpeedLimit < speedLimit;
    }
}

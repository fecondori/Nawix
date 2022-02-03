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
        if(automaticCommandManager == null) automaticCommandManager = Context.getAutomaticCommandManager();
        Set<Long> allIds = automaticCommandManager.getAllItems();
        Collection<AutomaticCommand> commands = automaticCommandManager.getItems(allIds);
        commands = commands.stream().filter(c -> shouldInvoke(c, event)).collect(Collectors.toList());
        LOGGER.info(String.format("Commands to invoke for device %d: %d", position.getDeviceId(), commands.size()));
        for(AutomaticCommand cmd : commands)
            LOGGER.info(String.format("sending command %s", cmd.getCommandData()));
            //sendCommand(cmd, position);

    }

    private void sendCommand(AutomaticCommand cmd, Position position){
        Command command = new Command();
        command.setDeviceId(position.getDeviceId());
        command.setType(cmd.getType());
        command.set("data", cmd.getCommandData());
        try {
            Context.getCommandsManager().sendCommand(command);
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
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
        return  speedLimit < cmdUpperSpeedLimit;
    }

    private boolean matchesLowerSpeedLimit(AutomaticCommand command, int speedLimit){
        int cmdLowerSpeedLimit = command.getInteger("lowerSpeedLimit");
        return cmdLowerSpeedLimit <= speedLimit;
    }
}

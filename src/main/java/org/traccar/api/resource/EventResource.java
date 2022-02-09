/*
 * Copyright 2016 - 2021 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import java.sql.SQLException;
import java.time.*;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import liquibase.pro.packaged.Q;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.api.BaseResource;
import org.traccar.database.DataManager;
import org.traccar.helper.Log;
import org.traccar.model.*;
import org.traccar.model.Calendar;

@Path("events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)

public class EventResource extends BaseResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventResource.class);
    @Path("{id}")
    @GET
    public Event get(@PathParam("id") long id) throws SQLException {
        Event event = Context.getDataManager().getObject(Event.class, id);
        if (event == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        Context.getPermissionsManager().checkDevice(getUserId(), event.getDeviceId());
        if (event.getGeofenceId() != 0) {
            Context.getPermissionsManager().checkPermission(Geofence.class, getUserId(), event.getGeofenceId());
        }
        if (event.getMaintenanceId() != 0) {
            Context.getPermissionsManager().checkPermission(Maintenance.class, getUserId(), event.getMaintenanceId());
        }
        return event;
    }


    @Path("/overspeed")
    @GET
    public Collection<ExtendedEvent> get(
            @QueryParam("groupIds") List<Long> groupIds,
            @QueryParam("deviceIds") List<Long> deviceIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to,
            @QueryParam("geofenceIds") List<Long> geofencesIds,
            @QueryParam("includeOutsideGeofences") boolean includeOutsideGeofences,
            @QueryParam("minGeofenceSpeedLimit") int minGeofenceSpeedLimit,
            @QueryParam("maxGeofenceSpeedLimit") int naxGeofenceSpeedLimit,
            @QueryParam("minDeviceSpeed") int minDeviceSpeed,
            @QueryParam("maxDeviceSpeed") int maxDeviceSpeed,
            @QueryParam("minDeviceSpeedLimit") int minDeviceSpeedLimit,
            @QueryParam("maxDeviceSpeedLimit") int maxDeviceSpeedLimit
            ) throws SQLException {
        String speedUnit = Context.getUsersManager().getById(getUserId()).getString("speedUnit");
        if(speedUnit != null)
            speedUnit = speedUnit.toLowerCase();
        else
            speedUnit = "kn";
        if (from == null) {
            //System.out.println("No from date provided");
            from = Date.from(ZonedDateTime.parse("1990-01-01T12:00:00+01:00").toInstant());
            //LocalDate local = LocalDate.now().minusYears(1);
            //from = java.sql.Date.from(local.atStartOfDay().toInstant(ZoneOffset.UTC));
        }
        if (to == null) {
            //System.out.println("No to date provided");
            LocalDate local = LocalDate.now().plusYears(1);
            to = java.sql.Date.from(local.atStartOfDay().toInstant(ZoneOffset.UTC));
        }

        // Performance can still be optimized but several changes to logic must be done

        // get events from db with the specified params
        Collection<ExtendedEvent> events = Context.getDataManager().getOverspeedEvents(groupIds, deviceIds, from, to, geofencesIds, includeOutsideGeofences, minDeviceSpeed, maxDeviceSpeed, minDeviceSpeedLimit, maxDeviceSpeedLimit, minGeofenceSpeedLimit, naxGeofenceSpeedLimit, speedUnit);

        // get all distinct devices in the events
        Collection<Long> permittedDevices = events.stream().map(e -> e.getDeviceId()).distinct().filter(this::hasDevicePermission).collect(Collectors.toList());

        // get all distinct geofences in the eventes
        Collection<Long> permittedGeofences = events.stream().filter(e->e.getGeofenceId() > 0).map(e -> e.getGeofenceId()).distinct().filter(this::hasGeofencePermission).collect(Collectors.toList());

        //remove all events whose device nor geofence is contained in the previous lists
        events = events.stream().filter(e-> permittedDevices.contains(e.getDeviceId()) && (permittedGeofences.contains(e.getGeofenceId()) || e.getGeofenceId() == 0)).collect(Collectors.toList());
        return events;


        //return events;
    }

    private boolean hasDevicePermission(long deviceId){
        try{
            Context.getPermissionsManager().checkDevice(getUserId(), deviceId);
            LOGGER.info(String.format("Permission granted user id: %d   device id: %d", getUserId(), deviceId));
            return true;
        }
        catch (Exception e){
            LOGGER.info(String.format("Permission denied user id: %d   device id: %d", getUserId(), deviceId));
            return false;
        }
    }

    private boolean hasGeofencePermission(long geofenceId){
        try{
            Context.getGeofenceManager().checkItemPermission(getUserId(), geofenceId);
            LOGGER.info(String.format("Geofence Permission granted user id: %d geofence id: %d", getUserId(), geofenceId));
            return true;
        }
        catch (Exception e){
            LOGGER.info(String.format("Geofence Permission denied user id: %d geofence id: %d", getUserId(), geofenceId));
            return false;
        }
    }
}

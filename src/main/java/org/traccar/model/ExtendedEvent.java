package org.traccar.model;

/**
 * Class used as DTO to send geofence names and extra data required
 */
public class ExtendedEvent extends Event{
    private String geofenceName = "";

    public String getGeofenceName(){ return geofenceName; }

    public void setGeofenceName(String geofenceName){ this.geofenceName = geofenceName; }
}

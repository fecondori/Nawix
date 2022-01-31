package org.traccar.model;

/**
 * Class used as DTO to send geofence names and extra data required
 */
public class ExtendedEvent extends Event{
    private String geofenceName = "";

    public String getGeofenceName(){ return geofenceName; }

    private double latitude;

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    private double longitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    private double altitude; // value in meters

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public void setGeofenceName(String geofenceName){ this.geofenceName = geofenceName; }
}

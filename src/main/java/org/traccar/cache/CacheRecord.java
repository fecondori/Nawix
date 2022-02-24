package org.traccar.cache;

import org.traccar.helper.DateUtil;
import org.traccar.model.Event;
import org.traccar.model.Position;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class CacheRecord implements Comparable<CacheRecord> {
    private Position position;
    private List<Event> events = new LinkedList<>();

    public CacheRecord(Position position, Event event) {
        this.position = position;
        this.events.add(event);
    }

    public CacheRecord(Position position) {
        this.position = position;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    @Override
    public int compareTo(CacheRecord o) {
        Date d = Date.from(Instant.now());
        //System.out.println(String.format("comparing deltas: %d: %d and %d: %d",
        //        position.getId(),
        //        TimeUnit.MILLISECONDS.toSeconds(position.getDeviceTime().getTime()),
        //        o.position.getId(),
        //        TimeUnit.MILLISECONDS.toSeconds(o.getPosition().getDeviceTime().getTime())));
        int result =  (int) DateUtil.getDelta(getPosition().getDeviceTime(), o.getPosition().getDeviceTime());
        //System.out.println("result:" + result);
        return result;
    }



    public void addEvent(Event event) {
        events.add(event);
    }
}

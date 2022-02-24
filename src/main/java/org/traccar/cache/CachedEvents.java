package org.traccar.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.config.Keys;
import org.traccar.helper.DateUtil;
import org.traccar.model.Event;
import org.traccar.model.Position;

import java.util.*;

public class CachedEvents {
    public static final CachedEvents INSTANCE = new CachedEvents();
    private static Collection<CacheListener> listeners = new ArrayList<>();
    private Logger LOGGER = LoggerFactory.getLogger(CachedEvents.class);

    public int getMaxCachedTime(){
        return Context.getConfig().getInteger(Keys.OUTDATED_POSITION_CACHE_TIME_LIMIT);
    }
    private Map<Long, SortedSet<CacheRecord>> cachedRecords = new HashMap<>();

    public void put(Position position, Event event){
        if(cachedRecords.containsKey(position.getDeviceId())) {
            SortedSet<CacheRecord> records = cachedRecords.get(position.getDeviceId());
            for(CacheRecord record : records){
                if(record.getPosition().getId() == position.getId())
                {
                    record.addEvent(event);
                    return;
                }
            }
        }
        put(new CacheRecord(position, event));
    }

    public void put(Position position){
        System.out.println("Putting " + position.getId());
        if(cachedRecords.containsKey(position.getDeviceId())) {
            SortedSet<CacheRecord> records = cachedRecords.get(position.getDeviceId());
            for(CacheRecord record : records){
                if(record.getPosition().getId() == position.getId())
                    return;
            }
        }
        put(new CacheRecord(position));
        LOGGER.info(String.format("Position %d added, cached count: %d", position.getId(), cachedRecords.get(position.getDeviceId()).stream().count()));
        int delta = (int) DateUtil.getDelta(getNewest(position.getDeviceId()).getPosition().getDeviceTime(), getOldest(position.getDeviceId()).getPosition().getDeviceTime());
        System.out.println(delta);
    }

    private void deleteOutdated(SortedSet<CacheRecord> events){
        while(true){
            CacheRecord newest;
            CacheRecord oldest;

            try {
                newest = events.last(); // latest added
                oldest = events.first(); // first added
            }
            catch (Exception err) {
                break;
            }

            if(oldest == null) break;
            if(newest == null) break;
            if(newest == oldest) break;

            int delta = (int) DateUtil.getDelta(newest.getPosition().getDeviceTime(), oldest.getPosition().getDeviceTime());

            if(delta > getMaxCachedTime()){
                LOGGER.info("removing Position %d from cache", oldest.getPosition().getId());
                events.remove(oldest);
            }
            else
                break;
        }

    }

    private void put(CacheRecord record){
        Long id = record.getPosition().getDeviceId();
        if(!cachedRecords.containsKey(id)) {
            cachedRecords.put(id, new TreeSet<>());
            cachedRecords.get(id).add(record);
            return;
        }
        cachedRecords.get(id).add(record);
        //System.out.println(cachedRecords.get(id).add(record));
       // System.out.println(cachedRecords.get(id).stream().count());
        deleteOutdated(cachedRecords.get(id));


    }

    /**
     * Gets the CacheRecord added most recently
     * @param deviceId
     * @return
     */
    public CacheRecord getNewest(long deviceId){
        if(!cachedRecords.containsKey(deviceId))
            return null;
        if(cachedRecords.get(deviceId).isEmpty())
            return null;
        return cachedRecords.get(deviceId).last();
    }

    /**
     * Gets the first CacheRecord added to the cache
     * @param deviceId
     * @return
     */
    public CacheRecord getOldest(long deviceId){
        if(!cachedRecords.containsKey(deviceId))
            return null;
        if(cachedRecords.get(deviceId).isEmpty())
            return null;
        return cachedRecords.get(deviceId).first();
    }

    public void clearCache(long deviceId){
        if(cachedRecords.containsKey(deviceId))
            cachedRecords.get(deviceId).clear();
    }

    public void fireEvents(Position lastValidPosition){
        long deviceId = lastValidPosition.getDeviceId();
        if(!cachedRecords.containsKey(deviceId)) return;
        TreeSet<CacheRecord> eventsFired = new TreeSet<>(cachedRecords.get(deviceId));
        clearCache(deviceId);
        deleteOutdated(eventsFired);
        listeners.forEach(l->l.onCachedEventsFired(eventsFired));
    }

    public void addListener(CacheListener listener) {
        listeners.add(listener);
    }
}
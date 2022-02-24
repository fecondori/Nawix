package org.traccar.handler.events.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.cache.CacheListener;
import org.traccar.cache.CacheRecord;
import org.traccar.cache.CachedEvents;
import org.traccar.model.Event;
import org.traccar.model.Position;

import java.util.*;

public class InterceptorManager {
    Logger LOGGER = LoggerFactory.getLogger(InterceptorManager.class);
    private Map<String, BaseInterceptor> interceptors = new HashMap<>();

    public InterceptorManager(){
        initInterceptors();
        CachedEvents.INSTANCE.addListener(new CacheListener() {
            @Override
            public void onCachedEventsFired(Set<CacheRecord> records) {
                for (CacheRecord record : records) {
                    record.getEvents().forEach(e -> invokeInterceptor(e, record.getPosition()));
                }

            }
        });
    }

    public void interceptEvents(Map<Event, Position> events){
        events.forEach((event, position) -> interceptEvent(event, position));
    }

    private void interceptEvent(Event event, Position position) {
        if (position.getOutdated()) {
            LOGGER.info(String.format("Intercepted Postion %d is outdated, Event: %d", position.getId(), event.getId()));
        }
        if (!position.getValid()) {
            LOGGER.info(String.format("Intercepted Postion %d is invalid, Event: %d", position.getId(), event.getId()));
        }
        if (!position.getPassOutdatedFilters()) {
            LOGGER.info(String.format("Intercepted Position %d doesn't pass outdated filters, position: %d"));
            CachedEvents.INSTANCE.put(position, event);
            return;
        }
        LOGGER.info("Firing events, source position: %d", position.getId());
        CachedEvents.INSTANCE.fireEvents(position);
        invokeInterceptor(event, position);
    }

    private void invokeInterceptor(Event event, Position position){
        if (interceptors.containsKey(event.getType())) {
            BaseInterceptor interceptor = interceptors.get(event.getType());
            interceptor.invoke(event, position);
        }
    }

    private void initInterceptors(){

        addInterceptor(new OverspeedInterceptor());
        addInterceptor(new GeofenceEnterInterceptor());
    }

    public boolean addInterceptor(BaseInterceptor interceptor){
        if(interceptors.containsKey(interceptor.getType())){
           return false;
        }
        interceptors.put(interceptor.getType(), interceptor);
        return true;
    }
}

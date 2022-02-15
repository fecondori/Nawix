package org.traccar.handler.events.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.Event;
import org.traccar.model.Position;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class InterceptorManager {
    Logger LOGGER = LoggerFactory.getLogger(InterceptorManager.class);
    private Map<String, BaseInterceptor> interceptors = new HashMap<>();

    public InterceptorManager(){
        initInterceptors();
    }

    public void interceptEvents(Map<Event, Position> events){
        events.forEach((event, position) -> interceptEvent(event, position));
    }

    private void interceptEvent(Event event, Position position) {
        if(position.getOutdated()){
            LOGGER.info(String.format("Intercepted Postion %d is outdated, Event: %d", position.getId(), event.getId()));
        }
        if(!position.getValid()){
            LOGGER.info(String.format("Intercepted Postion %d is invalid, Event: %d", position.getId(), event.getId()));
        }
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

package org.traccar.handler.events.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.cache.CacheRecord;
import org.traccar.cache.CachedEvents;
import org.traccar.config.Keys;
import org.traccar.helper.DateUtil;
import org.traccar.model.Position;

public class DeltaTimeFilter extends BaseFilter{
    private Logger LOGGER = LoggerFactory.getLogger(DeltaTimeFilter.class);
    private boolean isActive;
    private int MAX_DELTA;
    private int MIN_DELTA;

    public DeltaTimeFilter(){
        MAX_DELTA = Context.getConfig().getInteger(Keys.OUTDATED_POSITION_BY_TIME_DELTA_MAX_DELTA, 5);
        MIN_DELTA = Context.getConfig().getInteger(Keys.OUTDATED_POSITION_BY_TIME_DELTA_MIN_DELTA, 1);
        isActive = Context.getConfig().getBoolean(Keys.OUTDATED_POSITION_BY_TIME_DELTA);
        LOGGER.info("DeltaTimeFilter instantiated, is active: {} MinDelta: {} MaxDelta: {}", isActive, MIN_DELTA, MAX_DELTA);
    }

    @Override
    public boolean passFilter(Position position) {
        CacheRecord newest = CachedEvents.INSTANCE.getNewest(position.getDeviceId());
        if(newest == null) return false;
        long serverDelta = DateUtil.getDelta(position.getServerTime(), newest.getPosition().getServerTime());
        long deviceDelta = DateUtil.getDelta(position.getDeviceTime(), newest.getPosition().getDeviceTime());
        long delta = Math.abs(serverDelta - deviceDelta);
        LOGGER.info("MAX DELTA {}  {}", position.getId(), newest.getPosition().getId() );
        if(delta <= MAX_DELTA && delta >= MIN_DELTA) {
            LOGGER.info("DeltaTimeFilter accepted for {}", position.getId());
            return true;
        }
        LOGGER.info("DeltaTimeFilter rejected for {}", position.getId());
       return false;
    }



    @Override
    public boolean isActive() {
        return isActive;
    }
}
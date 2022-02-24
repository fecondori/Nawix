package org.traccar.handler.events.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.config.Keys;
import org.traccar.model.Position;

public class OperatorFilter extends BaseFilter {
    Logger LOGGER = LoggerFactory.getLogger(OperatorFilter.class);
    @Override
    public boolean passFilter(Position position) {
        if(position.getString("Operator") == null || position.getInteger("Operator") != 0){
            LOGGER.info("OperatorFilter accepted for position {}, operator: {}", position.getId(), position.getString("Operator"));
            return true;
        }

        LOGGER.info("OperatorFilter rejected for {}", position.getId());
        return false;
    }

    @Override
    public boolean isActive() {
        return Context.getConfig().getBoolean(Keys.OUTDATED_POSITION_BY_OPERATOR);
    }
}

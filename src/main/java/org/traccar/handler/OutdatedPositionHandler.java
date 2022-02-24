package org.traccar.handler;

import io.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseDataHandler;
import org.traccar.handler.events.filter.OutdatedFilterManager;
import org.traccar.model.Position;

@ChannelHandler.Sharable
public class OutdatedPositionHandler extends BaseDataHandler {
    Logger LOGGER = LoggerFactory.getLogger(OutdatedPositionHandler.class);
    private OutdatedFilterManager filterManager = new OutdatedFilterManager();
    @Override
    protected Position handlePosition(Position position) {
        boolean isOutdated = !filterManager.passFilters(position);
        position.setPassOutdatedFilters(isOutdated);
        return position;
    }
}

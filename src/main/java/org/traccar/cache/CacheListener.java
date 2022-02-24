package org.traccar.cache;

import java.util.Set;

public interface CacheListener {
    void onCachedEventsFired (Set<CacheRecord> events);
}
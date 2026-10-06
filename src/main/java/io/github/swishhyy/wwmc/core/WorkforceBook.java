package io.github.swishhyy.wwmc.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared station slots expire; resource blocks still use exclusive reservations. */
public final class WorkforceBook<K> {
    private final Map<K,Map<UUID,Long>> crews=new HashMap<>();
    public boolean claim(K station,UUID worker,long now,int duration,int capacity) {
        Map<UUID,Long> crew=crews.computeIfAbsent(station,k -> new HashMap<>());
        crew.values().removeIf(until -> until<=now);
        if(capacity<=0 || !crew.containsKey(worker) && crew.size()>=capacity) return false;
        crew.put(worker,now+duration); return true;
    }
    public int count(K station,long now) {
        Map<UUID,Long> crew=crews.get(station);
        if(crew==null) return 0;
        crew.values().removeIf(until -> until<=now); return crew.size();
    }
    public void release(K station,UUID worker) {
        Map<UUID,Long> crew=crews.get(station);
        if(crew!=null) { crew.remove(worker); if(crew.isEmpty()) crews.remove(station); }
    }
    public void prune(long now) {
        crews.values().forEach(crew -> crew.values().removeIf(until -> until<=now));
        crews.values().removeIf(Map::isEmpty);
    }
}

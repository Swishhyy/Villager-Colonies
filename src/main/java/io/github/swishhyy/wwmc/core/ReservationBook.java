package io.github.swishhyy.wwmc.core;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.UUID;

/** Expiring exclusive claims prevent two workers harvesting the same block. */
public final class ReservationBook<K> {
    private record Lease(UUID owner, long until) {}
    private final Map<K,Lease> leases = new HashMap<>();
    public boolean available(K key,UUID owner,long now) {
        Lease current=leases.get(key);
        return current==null || current.until()<=now || current.owner().equals(owner);
    }
    public boolean claim(K key, UUID owner, long now, int duration) {
        Lease current=leases.get(key);
        if(current!=null && current.until()>now && !current.owner().equals(owner)) return false;
        leases.put(key,new Lease(owner,now+duration));
        return true;
    }
    public void release(K key, UUID owner) {
        Lease current=leases.get(key);
        if(current!=null && current.owner().equals(owner)) leases.remove(key);
    }
    public boolean claimAll(Collection<K> keys,UUID owner,long now,int duration) {
        for(K key:keys) {
            Lease current=leases.get(key);
            if(current!=null && current.until()>now && !current.owner().equals(owner)) return false;
        }
        for(K key:keys) leases.put(key,new Lease(owner,now+duration));
        return true;
    }
    public void prune(long now) { leases.values().removeIf(lease -> lease.until()<=now); }
}

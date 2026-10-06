package io.github.swishhyy.wwmc.settlement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

/** Share detected resource locations for one second. Callers still validate each location against the live world. */
public final class StationResourceCache {
    private record Key(UUID town,Station station) {}
    private record Scan(long until,int radius,List<Station> stations,List<BlockPos> positions) {}
    private final Map<Key,Scan> scans=new HashMap<>();
    public List<BlockPos> positions(Settlement town,Station station,long now,Supplier<List<BlockPos>> detect) {
        Key key=new Key(town.id,station);
        Scan cached=scans.get(key);
        if(cached==null || now>=cached.until() || cached.radius()!=town.radius || !cached.stations().equals(town.stations)) {
            List<BlockPos> positions=detect.get().stream().map(BlockPos::immutable).toList();
            cached=new Scan(now+20,town.radius,List.copyOf(town.stations),positions);
            scans.put(key,cached);
        }
        return cached.positions();
    }
    public void refresh(UUID town) { scans.keySet().removeIf(key -> key.town().equals(town)); }
    public void prune(long now) { scans.values().removeIf(scan -> scan.until()<=now); }
}

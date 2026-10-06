package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.WorkCadence;
import io.github.swishhyy.wwmc.settlement.Settlement;
import io.github.swishhyy.wwmc.settlement.Station;
import io.github.swishhyy.wwmc.settlement.StationResourceCache;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

public final class PerformanceChecks {
    @Test void thirtyRecruitsKeepTheirWorkRateWithoutUpdatingTogether() {
        int[] updates=new int[30];
        for(long tick=0;tick<100;tick++) {
            int active=0;
            for(int citizen=0;citizen<30;citizen++) if(WorkCadence.due(tick,1000+citizen,10)) {
                active++; updates[citizen]++;
            }
            assertEquals(3,active,"A cohort of 30 uses all ten tick slots instead of one shared spike");
        }
        for(int count:updates) assertEquals(10,count,"Every worker still updates twice per second");
        for(int citizen:new int[]{Integer.MIN_VALUE,-1,0,Integer.MAX_VALUE}) {
            int due=0;
            for(long tick=Long.MAX_VALUE-19;tick<Long.MAX_VALUE;tick++) if(WorkCadence.due(tick,citizen,10)) due++;
            assertTrue(due>=1 && due<=2,"Long-running clocks and any entity ID keep a valid phase");
        }
    }
    @Test void unreachableCandidatesCannotTriggerAnUnboundedPathSearch() {
        WorkCadence.ReachBudget budget=new WorkCadence.ReachBudget();
        AtomicInteger paths=new AtomicInteger();
        for(int candidate=0;candidate<343;candidate++) assertFalse(budget.check(() -> { paths.incrementAndGet(); return false; }));
        assertEquals(4,paths.get(),"A station full of unreachable candidates costs at most four path probes per update");
        assertTrue(budget.deferred(),"A throttled search must retry instead of marking the station idle");
        budget.reset();
        assertTrue(budget.check(() -> { paths.incrementAndGet(); return true; }));
        assertEquals(5,paths.get(),"The next update can resume and find reachable work");
        assertFalse(budget.deferred());
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void resourceScansAreSharedAndRefreshWhenOwnershipChanges(MinecraftServer server) {
        Station warehouse=new Station(new BlockPos(0,64,0),StructureRole.WAREHOUSE);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Cache check",BlockPos.ZERO,240,List.of(),List.of(warehouse),"balanced");
        StationResourceCache cache=new StationResourceCache();
        AtomicInteger scans=new AtomicInteger();
        BlockPos.MutableBlockPos chest=new BlockPos.MutableBlockPos(1,64,0);
        java.util.function.Supplier<List<BlockPos>> detect=() -> { scans.incrementAndGet(); return List.of(chest); };
        for(int citizen=0;citizen<30;citizen++) assertEquals(List.of(new BlockPos(1,64,0)),cache.positions(town,warehouse,100,detect));
        assertEquals(1,scans.get(),"Thirty workers share a single cube scan");
        chest.set(2,64,0);
        assertEquals(List.of(new BlockPos(1,64,0)),cache.positions(town,warehouse,119,detect),"Cached coordinates are immutable");
        assertEquals(List.of(new BlockPos(2,64,0)),cache.positions(town,warehouse,120,detect),"New furniture is discovered within twenty ticks");
        assertEquals(2,scans.get());

        town.stations.add(new Station(new BlockPos(2,64,1),StructureRole.WAREHOUSE));
        cache.positions(town,warehouse,120,detect);
        assertEquals(3,scans.get(),"Adding an overlapping station invalidates resource ownership immediately");
        town.radius++;
        cache.positions(town,warehouse,120,detect);
        assertEquals(4,scans.get(),"Changing claim bounds invalidates the scan immediately");
        cache.refresh(town.id);
        cache.positions(town,warehouse,120,detect);
        assertEquals(5,scans.get(),"Inspection and recruitment can explicitly demand current detection");
        cache.prune(140);
        cache.positions(town,warehouse,140,detect);
        assertEquals(6,scans.get(),"Expired scans can be discarded and rebuilt");
    }
}

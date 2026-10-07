package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.settlement.TradePlanner;
import io.github.swishhyy.wwmc.settlement.TradePlanner.Cell;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Trader routes over a coarse map of seen land: roads, open ground, forest, water, bridges, cliffs and unseen land. */
public final class TradeRouteChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    /** A map built cell by cell; anything not set is unseen. */
    private static final class Land implements TradePlanner.Terrain {
        final Map<Long,Cell> cells=new HashMap<>();
        final Set<Long> blocked=new HashSet<>();
        static long key(int x,int z) { return (long)x<<32 | z&0xffffffffL; }
        Land fill(int minX,int maxX,int minZ,int maxZ,Cell cell) {
            for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) cells.put(key(x,z),cell);
            return this;
        }
        public Cell cell(int x,int z) { return cells.getOrDefault(key(x,z),Cell.UNKNOWN); }
        public boolean blocked(int x,int z) { return blocked.contains(key(x,z)); }
    }
    private static Cell ground(int height,int flags) { return new Cell(height,Cell.SEEN|Cell.PASSABLE|flags,5); }
    private static final Cell OPEN=ground(64,0),FOREST=ground(64,Cell.FOREST),ROAD=ground(64,Cell.ROAD);
    private static final Cell WATER=new Cell(0,Cell.SEEN|Cell.WATER,-1),BRIDGE=new Cell(64,Cell.SEEN|Cell.PASSABLE|Cell.ROAD|Cell.WATER,1);
    private static final BlockPos HOME=new BlockPos(0,64,0);
    private static boolean steady(List<BlockPos> route,BlockPos from) {
        BlockPos last=from;
        for(BlockPos step:route) { if(Math.max(Math.abs(step.getX()-last.getX()),Math.abs(step.getZ()-last.getZ()))>16) return false; last=step; }
        return true;
    }

    @Test void openLand() {
        BlockPos market=new BlockPos(0,64,160);
        var route=TradePlanner.plan(new Land().fill(-40,40,-20,60,OPEN),HOME,market,64);
        check(!route.isEmpty() && route.getLast().equals(market),"An open route ends at the destination itself");
        check(route.stream().allMatch(step -> Math.abs(step.getX())<=4),"Over open land the route runs straight");
        check(steady(route,HOME) && route.size()>=10 && route.size()<=16,"Waypoints come about every twelve blocks: "+route.size());
        var unseen=TradePlanner.plan(new Land(),HOME,market,64);
        check(!unseen.isEmpty() && unseen.getLast().equals(market) && unseen.stream().allMatch(step -> Math.abs(step.getX())<=4),
                "Unseen land is assumed walkable, so the first plan heads straight for the destination");
        System.out.println("Passed "+checks+" open route checks.");
    }

    @Test void farBridge() {
        // A river crosses the whole map; its only bridge stands sixty blocks to the side of the straight line.
        Land land=new Land().fill(-40,40,-20,60,OPEN).fill(-40,40,18,20,WATER).fill(-15,-15,18,20,BRIDGE);
        BlockPos market=new BlockPos(0,64,160);
        var route=TradePlanner.plan(land,HOME,market,80);
        check(!route.isEmpty() && route.getLast().equals(market),"The far bridge leads to the destination");
        var crossing=route.stream().filter(step -> step.getZ()>=72 && step.getZ()<84).toList();
        check(!crossing.isEmpty() && crossing.stream().allMatch(step -> step.getX()==-59),"Every waypoint over the river is on the bridge deck: "+crossing);
        check(crossing.size()==3,"Each bridge cell gets its own waypoint, so a narrow deck is followed exactly");
        check(steady(route,HOME),"No leg is longer than one trader step");
        System.out.println("Passed "+checks+" bridge checks.");
    }

    @Test void clearedHighway() {
        // The straight line runs through a forest; cleared open land lies beside it.
        Land land=new Land().fill(-40,40,-20,80,OPEN).fill(-3,3,-20,80,FOREST);
        BlockPos market=new BlockPos(0,64,240);
        var route=TradePlanner.plan(land,HOME,market,96);
        var middle=route.stream().filter(step -> step.getZ()>=60 && step.getZ()<=180).toList();
        check(!middle.isEmpty() && middle.stream().allMatch(step -> Math.abs(TradePlanner.cellOf(step.getX()))>3),"A cleared way beside the woods beats the straight line through them: "+middle);
        Land paved=new Land().fill(-40,40,-20,80,OPEN).fill(12,12,-20,80,ROAD);
        var road=TradePlanner.plan(paved,HOME,market,96).stream().filter(step -> step.getZ()>=40 && step.getZ()<=200).toList();
        check(!road.isEmpty() && road.stream().allMatch(step -> step.getX()>=48 && step.getX()<52),"A road beside the straight line is taken: "+road);
        System.out.println("Passed "+checks+" highway checks.");
    }

    @Test void cliffsAndBlockedCells() {
        // A sixteen-block cliff with one ramp climbing four blocks per cell.
        Land land=new Land().fill(-40,40,-20,60,OPEN).fill(-40,40,20,60,ground(80,0));
        int[] ramp={64,68,72,76,80};
        for(int n=0;n<ramp.length;n++) land.fill(10,10,17+n,17+n,ground(ramp[n],0));
        BlockPos market=new BlockPos(0,80,160);
        var route=TradePlanner.plan(land,HOME,market,64);
        check(!route.isEmpty() && route.getLast().equals(market),"The ramp leads up the cliff");
        check(route.stream().anyMatch(step -> step.getX()>=40 && step.getX()<44 && step.getZ()>=68 && step.getZ()<88),"The route climbs by the ramp: "+route);
        Land walled=new Land().fill(-40,40,-20,60,OPEN);
        for(int x=-6;x<=6;x++) walled.blocked.add(Land.key(x,20));
        var around=TradePlanner.plan(walled,HOME,market,64);
        check(around.stream().filter(step -> step.getZ()>=76 && step.getZ()<88).allMatch(step -> Math.abs(step.getX())>=24),"Cells a trader failed to reach are routed around: "+around);
        System.out.println("Passed "+checks+" cliff checks.");
    }

    @Test void noCrossing() {
        // Every cell within the search is seen, and the river has no bridge.
        Land land=new Land().fill(-60,60,-60,100,OPEN).fill(-60,60,18,20,WATER);
        BlockPos market=new BlockPos(0,64,160);
        var route=TradePlanner.plan(land,HOME,market,64);
        check(!route.isEmpty() && !route.getLast().equals(market) && route.getLast().getZ()<72,"Without a crossing the route ends at the near bank: "+route);
        check(TradePlanner.plan(land,new BlockPos(0,64,68),market,64).isEmpty(),"Standing at the water's edge with no way over, there is no route at all");
        System.out.println("Passed "+checks+" river checks.");
    }
}

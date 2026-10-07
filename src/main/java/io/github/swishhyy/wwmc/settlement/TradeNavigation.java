package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.entity.RoadSurface;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * A trader's travel. Far from its destination it follows a route planned over the land seen so far
 * ({@link TradeAtlas}), so it takes a cleared highway or a distant bridge rather than heading straight into a forest
 * or a river. Each step toward the next waypoint is a short, reachable ground leg inside the trader's moving chunk
 * window.
 */
public final class TradeNavigation {
    private static final int MAX_PATHS=12,STALLED_TICKS=100,RETRY_TICKS=40;
    private static final double[] ANGLES={0,0.55,-0.55,1.1,-1.1,Math.PI/2,-Math.PI/2,2.2,-2.2};
    /** Farther than this from its destination, a trader follows a planned route. */
    private static final int ROUTE_RANGE=24;
    /** Routes are planned this far ahead, and planned again as the trader goes and sees more land. */
    private static final int ROUTE_REACH=512,REPLAN_TICKS=400;
    /** A route waypoint is passed once the trader is this close to it, ignoring height. */
    private static final int PASSED=6;
    /** Without a moving leg for this long the way is really blocked, not just being planned again. */
    private static final int STUCK_TICKS=300;
    private final Map<BlockPos,Long> avoided=new LinkedHashMap<>();
    private BlockPos destination,waypoint;
    private Vec3 anchor;
    private long progressAt,nextPlanAt;
    private BlockPos goal;
    private List<BlockPos> route=List.of();
    private int next,failedLegs;
    private long replanAt,movingAt;
    private boolean failedPlan;

    public void reset() {
        resetLeg();
        goal=null; route=List.of(); next=0; failedLegs=0; replanAt=0; movingAt=0;
    }
    private void resetLeg() {
        destination=null; waypoint=null; anchor=null; nextPlanAt=0; avoided.clear(); failedPlan=false;
    }
    /** True once no leg has moved for fifteen seconds. */
    public boolean stuck(long now) { return goal!=null && now-movingAt>=STUCK_TICKS; }
    private static double flat(BlockPos a,BlockPos b) {
        double dx=a.getX()-b.getX(),dz=a.getZ()-b.getZ();
        return dx*dx+dz*dz;
    }

    /** Returns false while no walkable leg is moving; cargo and itinerary remain with the citizen. */
    public boolean walk(ServerLevel level,Mob mob,BlockPos target,double speed) {
        long now=level.getGameTime();
        if(!target.equals(goal)) { reset(); goal=target.immutable(); movingAt=now; }
        BlockPos aim=target;
        if(flat(mob.blockPosition(),target)>ROUTE_RANGE*ROUTE_RANGE) {
            if(route.isEmpty() || now>=replanAt) {
                route=TradeAtlas.get(level).route(level,mob.blockPosition(),target,ROUTE_REACH);
                next=0; replanAt=now+REPLAN_TICKS;
            }
            while(next<route.size()-1 && flat(mob.blockPosition(),route.get(next))<=PASSED*PASSED) next++;
            if(next<route.size()) aim=route.get(next);
        }
        boolean moving=leg(level,mob,aim,speed);
        if(moving) { movingAt=now; failedLegs=0; }
        else if(failedPlan) {
            failedPlan=false;
            // A waypoint the trader cannot reach twice running is avoided, and the route planned around it.
            if(!aim.equals(target) && ++failedLegs>=2) { TradeAtlas.get(level).block(level,aim); failedLegs=0; replanAt=now; }
        }
        return moving;
    }

    /** One short leg toward the aim, a route waypoint or the destination itself. */
    private boolean leg(ServerLevel level,Mob mob,BlockPos target,double speed) {
        long now=level.getGameTime();
        if(!target.equals(destination)) { resetLeg(); destination=target.immutable(); }
        avoided.entrySet().removeIf(e -> e.getValue()<=now);
        if(waypoint!=null) {
            boolean arrived=mob.distanceToSqr(Vec3.atBottomCenterOf(waypoint))<=6.25;
            if(anchor==null || mob.position().distanceToSqr(anchor)>1) { anchor=mob.position(); progressAt=now; }
            if(arrived || mob.getNavigation().isDone() || now-progressAt>=STALLED_TICKS) {
                // Avoid repeatedly selecting a reached side step or the same stuck leg.
                avoid(waypoint,now);
                waypoint=null; anchor=null; nextPlanAt=now;
                mob.getNavigation().stop();
            }
        }
        if(waypoint==null) {
            if(now<nextPlanAt) return false;
            nextPlanAt=now+RETRY_TICKS;
            Path path=plan(level,mob,target);
            if(path==null || !mob.getNavigation().moveTo(path,speed)) { failedPlan=true; return false; }
            waypoint=path.getTarget().immutable(); anchor=mob.position(); progressAt=now;
        }
        mob.getLookControl().setLookAt(waypoint.getX()+0.5,waypoint.getY()+0.5,waypoint.getZ()+0.5);
        return true;
    }

    private void avoid(BlockPos pos,long now) {
        avoided.put(pos,now+600);
        if(avoided.size()>24) avoided.remove(avoided.keySet().iterator().next());
    }
    private boolean avoided(BlockPos pos) { return avoided.keySet().stream().anyMatch(p -> p.distSqr(pos)<=9); }

    private Path plan(ServerLevel level,Mob mob,BlockPos target) {
        int tried=0;
        if(mob.blockPosition().distSqr(target)<=18*18 && !avoided(target)) {
            tried++;
            Path direct=mob.getNavigation().createPath(target,1);
            if(direct!=null && direct.canReach()) return direct;
        }
        var world=new CitizenReach.StandingView() {
            public boolean available(BlockPos p) {
                return p.getY()>=level.getMinY() && p.getY()<level.getMaxY()
                        && level.hasChunkAt(p) && level.getWorldBorder().isWithinBounds(p);
            }
            public boolean clear(BlockPos p) {
                return level.getBlockState(p).getCollisionShape(level,p).isEmpty() && level.getFluidState(p).isEmpty();
            }
            public boolean footing(BlockPos p) {
                // Dirt paths, slabs and stairs support a walker without a full-height sturdy top face.
                return !level.getBlockState(p).is(BlockTags.LEAVES)
                        && !level.getBlockState(p).getCollisionShape(level,p).isEmpty();
            }
        };
        double angle=Math.atan2(target.getZ()-mob.getZ(),target.getX()-mob.getX());
        Set<BlockPos> candidates=new LinkedHashSet<>();
        // Prefer the ground near the citizen's feet. A heightmap can point to a roof or tree crown.
        for(int length:new int[]{16,10,6}) for(double offset:ANGLES) {
            int x=(int)Math.floor(mob.getX()+Math.cos(angle+offset)*length);
            int z=(int)Math.floor(mob.getZ()+Math.sin(angle+offset)*length);
            for(int dy: new int[]{0,1,-1,2,-2,3,-3,4,-4,5,-5,6,-6,7,-7,8,-8}) {
                BlockPos pos=new BlockPos(x,mob.blockPosition().getY()+dy,z);
                if(!avoided(pos) && CitizenReach.standing(world,pos)) candidates.add(pos);
            }
        }
        // Angular samples can miss a one-block bridge. Sample three bounded rings to find its deck.
        List<BlockPos> roads=new ArrayList<>();
        for(int radius:new int[]{6,10,16}) for(int side=-radius;side<=radius;side++) {
            for(int[] offset:new int[][]{{radius,side},{-radius,side},{side,radius},{side,-radius}}) {
                for(int dy:new int[]{0,1,-1,2,-2,3,-3,4,-4}) {
                    BlockPos pos=mob.blockPosition().offset(offset[0],dy,offset[1]);
                    if(!world.available(pos) || avoided(pos) || !RoadSurface.preferred(level,pos)
                            || !CitizenReach.standing(world,pos)) continue;
                    roads.add(pos); break;
                }
            }
        }
        roads.stream().distinct().sorted(Comparator.comparingDouble(p -> score(level,mob,p,target)))
                .limit(12).forEach(candidates::add);
        // Check shorter legs in front before distant side steps, without exhausting path probes on one heading.
        List<BlockPos> ordered=new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(p -> score(level,mob,p,target)));
        for(BlockPos candidate:ordered) {
            if(tried++>=MAX_PATHS) break;
            Path path=mob.getNavigation().createPath(candidate,1);
            if(path!=null && path.canReach()) return path;
        }
        return null;
    }
    private static double score(ServerLevel level,Mob mob,BlockPos pos,BlockPos target) {
        return Math.sqrt(pos.distSqr(target))+Math.abs(pos.getY()-mob.getY())*2
                -(RoadSurface.preferred(level,pos) ? 8 : 0);
    }
}

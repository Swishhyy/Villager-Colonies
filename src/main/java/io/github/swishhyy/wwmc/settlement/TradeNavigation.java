package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Short, reachable ground waypoints inside the trader's moving chunk window. */
public final class TradeNavigation {
    private static final int MAX_PATHS=12,STALLED_TICKS=100,RETRY_TICKS=40;
    private static final double[] ANGLES={0,0.55,-0.55,1.1,-1.1,Math.PI/2,-Math.PI/2,2.2,-2.2};
    private final Map<BlockPos,Long> avoided=new LinkedHashMap<>();
    private BlockPos destination,waypoint;
    private Vec3 anchor;
    private long progressAt,nextPlanAt;

    public void reset() {
        destination=null; waypoint=null; anchor=null; nextPlanAt=0; avoided.clear();
    }

    /** Returns false when no walkable leg is available; cargo and itinerary remain with the citizen. */
    public boolean walk(ServerLevel level,Mob mob,BlockPos target,double speed) {
        long now=level.getGameTime();
        if(!target.equals(destination)) { reset(); destination=target.immutable(); }
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
            if(path==null || !mob.getNavigation().moveTo(path,speed)) return false;
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
                return !level.getBlockState(p).is(BlockTags.LEAVES) && level.getFluidState(p).isEmpty()
                        && level.getBlockState(p).isFaceSturdy(level,p,Direction.UP);
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
        // Check shorter legs in front before distant side steps, without exhausting path probes on one heading.
        List<BlockPos> ordered=new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(p -> Math.sqrt(p.distSqr(target))+Math.abs(p.getY()-mob.getY())*2));
        for(BlockPos candidate:ordered) {
            if(tried++>=MAX_PATHS) break;
            Path path=mob.getNavigation().createPath(candidate,1);
            if(path!=null && path.canReach()) return path;
        }
        return null;
    }
}

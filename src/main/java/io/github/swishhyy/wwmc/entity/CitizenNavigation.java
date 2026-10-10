package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.settlement.CitizenReach;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.*;

/** Shared by every citizen job: prefer roads, cross water on solid decks, and retain water escape. */
public final class CitizenNavigation extends GroundPathNavigation {
    /**
     * Extra cost of each step off paved ground. Minecraft's planner weighs the remaining distance at 1.5 per block, so
     * at 0.5 or less it heads straight for the goal and never looks at a road beside it. Much more and it searches so
     * widely that ordinary 30 to 40 block routes through a town run out of search before reaching the goal.
     */
    public static final float OFF_ROAD=0.6F;
    /** Citizens plan routes up to this many blocks; longer trips are walked leg by leg. */
    public static final float ROUTE_LENGTH=64.0F;
    public CitizenNavigation(Mob mob,Level level) { super(mob,level); setCanOpenDoors(true); }
    @Override protected PathFinder createPathFinder(int maximumNodes) {
        RoadEvaluator roads=new RoadEvaluator();
        roads.setCanPassDoors(true);
        nodeEvaluator=roads;
        return new RoadPathFinder(roads,maximumNodes);
    }
    // Walk smoothly toward the next node, except beside water, where a cut corner could step off a narrow bridge or bank.
    @Override public boolean canCutCorner(PathType type) { return type!=PathType.WATER_BORDER && super.canCutCorner(type); }
    @Override public void tick() {
        if(path!=null && !path.isDone() && !escapingWater(mob)
                && RoadSurface.openWater(level,path.getNextNodePos())) stop();
        super.tick();
    }
    private static boolean escapingWater(Mob mob) {
        return mob.isInWater() || RoadSurface.openWater(mob.level(),mob.blockPosition());
    }

    /**
     * Minecraft's planner overwrites a node's walked distance whenever a neighbour looks at it, even from a longer way
     * round, so beyond an obstacle routes look far longer than they are and are cut off at the route length. The limit
     * is raised by this factor; the planning region around the citizen and the search budget still bound the search.
     */
    private static final float WALK_SLACK=4.0F;
    /**
     * Plans the road-preferring route first. Its wider search can run out around long obstacles, so when it does not
     * reach the goal, the plain shortest route is planned instead; a route that arrives matters more than the road.
     */
    private static final class RoadPathFinder extends PathFinder {
        private final RoadEvaluator roads;
        RoadPathFinder(RoadEvaluator roads,int maximumNodes) { super(roads,maximumNodes); this.roads=roads; }
        @Override public Path findPath(PathNavigationRegion region,Mob mob,Set<BlockPos> targets,float maxRange,int accuracy,float searchDepthMultiplier) {
            float walk=maxRange*WALK_SLACK;
            Path preferred=super.findPath(region,mob,targets,walk,accuracy,searchDepthMultiplier);
            if(preferred!=null && preferred.canReach()) return preferred;
            roads.plain=true;
            try {
                Path plain=super.findPath(region,mob,targets,walk,accuracy,searchDepthMultiplier);
                if(plain==null) return preferred;
                return preferred==null || plain.canReach() || plain.getDistToTarget()<preferred.getDistToTarget() ? plain : preferred;
            } finally { roads.plain=false; }
        }
    }

    private static final class RoadEvaluator extends WalkNodeEvaluator {
        /** Set while planning the fallback route, which ignores road preference. */
        private boolean plain;
        private boolean escape;
        private float previousWaterCost;
        @Override public PathType getPathType(PathfindingContext context,int x,int y,int z) {
            PathType type=super.getPathType(context,x,y,z);
            BlockPos pos=new BlockPos(x,y,z);
            if(type==PathType.BLOCKED && CitizenReach.softCover(context.level(),pos,context.getBlockState(pos))) {
                return CitizenReach.ground(context.level(),p -> true).footing(pos.below()) ? PathType.WALKABLE : PathType.OPEN;
            }
            return type;
        }
        @Override public void prepare(PathNavigationRegion region,Mob mob) {
            previousWaterCost=mob.getPathfindingMalus(PathType.WATER);
            escape=escapingWater(mob);
            super.prepare(region,mob);
            mob.setPathfindingMalus(PathType.WATER,escape ? 8 : -1);
        }
        @Override public void done() {
            Mob actor=mob;
            super.done();
            actor.setPathfindingMalus(PathType.WATER,previousWaterCost);
        }
        private boolean safe(Node node) {
            return node!=null && (escape || !RoadSurface.openWater(currentContext.level(),node.asBlockPos()));
        }
        @Override protected boolean isNeighborValid(Node node,Node from) {
            return safe(node) && super.isNeighborValid(node,from);
        }
        @Override protected boolean isDiagonalValid(Node from,Node first,Node second) {
            return safe(first) && safe(second) && super.isDiagonalValid(from,first,second);
        }
        @Override protected boolean isDiagonalValid(Node node) {
            return safe(node) && super.isDiagonalValid(node);
        }
        @Override public int getNeighbors(Node[] output,Node from) {
            int count=super.getNeighbors(output,from);
            for(int n=0;n<count;n++) {
                Node node=output[n];
                boolean paved=RoadSurface.preferred(currentContext.level(),node.asBlockPos());
                // WATER_BORDER is dry ground beside water; a sound narrow deck is safe to use.
                if(paved && node.type==PathType.WATER_BORDER) node.costMalus=0;
                else if(!paved && !plain) node.costMalus=Math.max(node.costMalus,OFF_ROAD);
            }
            return count;
        }
    }
}

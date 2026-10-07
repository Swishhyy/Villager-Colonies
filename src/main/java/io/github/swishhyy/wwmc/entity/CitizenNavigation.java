package io.github.swishhyy.wwmc.entity;

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
    public CitizenNavigation(Mob mob,Level level) { super(mob,level); }
    @Override protected PathFinder createPathFinder(int maximumNodes) {
        nodeEvaluator=new RoadEvaluator();
        return new PathFinder(nodeEvaluator,maximumNodes);
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

    private static final class RoadEvaluator extends WalkNodeEvaluator {
        private boolean escape;
        private float previousWaterCost;
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
                else if(!paved) node.costMalus=Math.max(node.costMalus,OFF_ROAD);
            }
            return count;
        }
    }
}

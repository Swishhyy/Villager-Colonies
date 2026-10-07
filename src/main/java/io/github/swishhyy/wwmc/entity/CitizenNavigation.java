package io.github.swishhyy.wwmc.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.*;

/** Shared by every citizen job: prefer roads, cross water on solid decks, and retain water escape. */
public final class CitizenNavigation extends GroundPathNavigation {
    public CitizenNavigation(Mob mob,Level level) { super(mob,level); }
    @Override protected PathFinder createPathFinder(int maximumNodes) {
        nodeEvaluator=new RoadEvaluator();
        return new PathFinder(nodeEvaluator,maximumNodes);
    }
    // Follow the chosen nodes instead of cutting across road bends or one-block bridge corners.
    @Override public boolean canCutCorner(PathType type) { return false; }
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
                else if(!paved) node.costMalus=Math.max(node.costMalus,2);
            }
            return count;
        }
    }
}

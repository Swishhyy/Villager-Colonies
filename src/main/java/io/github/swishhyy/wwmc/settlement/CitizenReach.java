package io.github.swishhyy.wwmc.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Hand reach is measured from the eyes to the target's surface, separately from walking arrival distance. */
public final class CitizenReach {
    public static final double BLOCKS=4.0;
    private CitizenReach() {}
    public static Vec3 closest(Vec3 eye,AABB target) {
        return new Vec3(Math.clamp(eye.x,target.minX,target.maxX),Math.clamp(eye.y,target.minY,target.maxY),
                Math.clamp(eye.z,target.minZ,target.maxZ));
    }
    public static boolean within(Vec3 eye,AABB target) { return eye.distanceToSqr(closest(eye,target))<=BLOCKS*BLOCKS; }
    public static boolean within(Vec3 eye,BlockPos target) { return within(eye,new AABB(target)); }
    /** Aim just inside the closest face, so a boundary endpoint still intersects a full block. */
    public static BlockHitResult hit(BlockGetter world,Vec3 eye,BlockPos target) {
        var shape=world.getBlockState(target).getShape(world,target);
        AABB bounds=shape.isEmpty() ? new AABB(target) : shape.bounds().move(target.getX(),target.getY(),target.getZ());
        Vec3 face=closest(eye,bounds);
        Vec3 end=face.add(bounds.getCenter().subtract(face).scale(0.0001));
        BlockHitResult nearest=world.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,CollisionContext.empty()));
        if(nearest.getType()==HitResult.Type.BLOCK && nearest.getBlockPos().equals(target)
                || shape.isEmpty()) return nearest;
        // Thin blocks and a trunk's shared top edge may need an aim point farther inside the target.
        return world.clip(new ClipContext(eye,bounds.getCenter(),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,CollisionContext.empty()));
    }
    public static boolean visible(BlockGetter world,Vec3 eye,BlockPos target) {
        BlockHitResult hit=hit(world,eye,target);
        if(!hit.getBlockPos().equals(target)) return false;
        // Empty planting/support cells have no shape; solid targets must really be hit, not just near the ray's endpoint.
        return hit.getType()==HitResult.Type.BLOCK || world.getBlockState(target).getShape(world,target).isEmpty();
    }
    public static boolean canUse(BlockGetter world,Vec3 eye,BlockPos target) {
        if(!within(eye,target)) return false;
        BlockHitResult hit=hit(world,eye,target);
        return hit.getBlockPos().equals(target) && (hit.getType()==HitResult.Type.BLOCK
                ? eye.distanceToSqr(hit.getLocation())<=BLOCKS*BLOCKS+1.0E-7 : world.getBlockState(target).getShape(world,target).isEmpty());
    }
    public interface StandingView {
        boolean available(BlockPos pos);
        boolean clear(BlockPos pos);
        boolean footing(BlockPos pos);
        default boolean room(BlockPos pos) { return clear(pos) && clear(pos.above()) && footing(pos.below()); }
        /** Navigation nodes name the air above a floor, whose surface may be below a whole block. */
        default Vec3 feet(BlockPos pos) { return Vec3.atBottomCenterOf(pos); }
    }
    /** Use collision surfaces, so walkable paths, slabs and carpets provide footing just like full blocks. */
    public static StandingView ground(BlockGetter world,Predicate<BlockPos> available) {
        return new StandingView() {
            public boolean available(BlockPos pos) { return available.test(pos); }
            public boolean clear(BlockPos pos) { return world.getBlockState(pos).getCollisionShape(world,pos).isEmpty() && world.getFluidState(pos).isEmpty(); }
            private boolean cover(BlockPos pos) {
                var shape=world.getBlockState(pos).getCollisionShape(world,pos);
                return !shape.isEmpty() && shape.bounds().maxY<=0.125 && world.getFluidState(pos).isEmpty();
            }
            public boolean room(BlockPos pos) {
                // Carpets occupy the walking node itself; slabs and ordinary floors sit below it.
                if(cover(pos)) return footing(pos.below()) && clear(pos.above()) && available(pos.above(2)) && clear(pos.above(2));
                return !cover(pos.below()) && clear(pos) && clear(pos.above()) && footing(pos.below());
            }
            public boolean footing(BlockPos pos) {
                var state=world.getBlockState(pos);
                var shape=state.getCollisionShape(world,pos);
                if(state.is(BlockTags.LEAVES) || !world.getFluidState(pos).isEmpty() || shape.isEmpty()) return false;
                AABB bounds=shape.bounds();
                // Narrow poles and walls are not ordinary standing floors; the pathfinder checks access separately.
                return bounds.maxY>0 && bounds.maxY<=1 && bounds.minX<=0.2 && bounds.maxX>=0.8 && bounds.minZ<=0.2 && bounds.maxZ>=0.8;
            }
            public Vec3 feet(BlockPos pos) {
                if(cover(pos)) return Vec3.atBottomCenterOf(pos).add(0,world.getBlockState(pos).getCollisionShape(world,pos).bounds().maxY,0);
                BlockPos below=pos.below();
                var shape=world.getBlockState(below).getCollisionShape(world,below);
                double surface=shape.isEmpty() ? 0 : shape.bounds().maxY;
                return new Vec3(pos.getX()+0.5,below.getY()+surface,pos.getZ()+0.5);
            }
        };
    }
    public static boolean standing(StandingView world,BlockPos pos) {
        return world.available(pos) && world.available(pos.above()) && world.available(pos.below())
                && world.room(pos);
    }
    /** Bounded alternatives with room for the body and firm footing; never stand on the block being removed. */
    public static List<BlockPos> stands(StandingView world,BlockPos target,Vec3 from,double eyeHeight) {
        record Approach(BlockPos pos,double distance) {}
        List<Approach> spots=new ArrayList<>();
        for(int dx=-4;dx<=4;dx++) for(int dz=-4;dz<=4;dz++) for(int dy=-2;dy<=2;dy++) {
            BlockPos pos=target.offset(dx,dy,dz);
            if(pos.below().equals(target) || !standing(world,pos)) continue;
            Vec3 feet=world.feet(pos);
            if(!within(feet.add(0,eyeHeight,0),target)) continue;
            spots.add(new Approach(pos.immutable(),from.distanceToSqr(feet)));
        }
        spots.sort(Comparator.comparingDouble(Approach::distance));
        return spots.stream().map(Approach::pos).toList();
    }
}

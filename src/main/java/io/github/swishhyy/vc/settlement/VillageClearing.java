package io.github.swishhyy.vc.settlement;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Clears trees and huge mushrooms from a native village's land. A tree rooted on the land is felled whole, with the
 * foliage its wood held up; wood and foliage from trees rooted outside are trimmed back to the edge of the land. The
 * village's own blocks, protected blocks and containers are never touched. The same pass serves world generation, a
 * village that has just been found, and a village founded later before it is built.
 */
public final class VillageClearing {
    /** Highest a canopy reaches above the land, and the depth below the surveyed ground a trunk may stand. */
    private static final int CANOPY=40,ROOTS=3;
    /** How far foliage reaches from its wood, how far one tree spreads, and the most blocks one tree may hold. */
    private static final int LEAF_REACH=7,TREE_SPREAD=16,MAX_WOOD=2048,MAX_FOLIAGE=16384;
    private VillageClearing() {}

    public static boolean wood(BlockState state) { return state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM); }
    public static boolean foliage(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(BlockStateProperties.PERSISTENT)
                || state.is(Blocks.VINE) || state.is(Blocks.COCOA) || state.is(BlockTags.BEEHIVES)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    /**
     * Clears the columns of a village's land between the given corners. {@code keep} marks blocks that stay whatever
     * they are; {@code writable} bounds where the search may look and write.
     */
    public static int clear(WorldGenLevel level,BlockPos center,NpcVillageLayout layout,int minX,int minZ,int maxX,int maxZ,
            Predicate<BlockPos> keep,Predicate<BlockPos> writable,int flags) {
        int extent=layout.extent();
        var pass=new Pass(level,center,extent,keep,writable,flags);
        for(int x=Math.max(minX,center.getX()-extent);x<=Math.min(maxX,center.getX()+extent);x++)
            for(int z=Math.max(minZ,center.getZ()-extent);z<=Math.min(maxZ,center.getZ()+extent);z++) {
                int ground=center.getY()+layout.ground(x-center.getX(),z-center.getZ());
                int top=Math.min(level.getMaxY()-1,Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z),ground+CANOPY));
                for(int y=ground-ROOTS;y<=top;y++) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!writable.test(pos) || keep.test(pos)) continue;
                    BlockState state=level.getBlockState(pos);
                    if(wood(state)) pass.tree(pos);
                    else if(foliage(state)) pass.remove(pos);
                }
            }
        return pass.finish();
    }
    /** The whole land of a village in a loaded world, keeping its plan, protected blocks and containers. */
    public static int clear(ServerLevel level,Settlement town,List<NpcSettlements.Placement> plan) {
        var layout=town.trading.layout;
        if(layout==null) return 0;
        Map<BlockPos,Block> planned=new HashMap<>();
        for(var p:plan) if(!p.state().isAir()) planned.put(p.pos(),p.state().getBlock());
        var protection=WorldWorkData.get(level).protectedBlocks;
        Predicate<BlockPos> keep=pos -> {
            Block block=planned.get(pos); BlockState state=level.getBlockState(pos);
            return block!=null && state.is(block) || protection.contains(pos)
                    || level.getBlockEntity(pos)!=null && !state.is(BlockTags.BEEHIVES);
        };
        int extent=layout.extent();
        return clear(level,town.center,layout,town.center.getX()-extent,town.center.getZ()-extent,town.center.getX()+extent,town.center.getZ()+extent,
                keep,pos -> !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos),18);
    }

    private static final class Pass {
        final WorldGenLevel level; final BlockPos center; final int extent; final Predicate<BlockPos> keep,writable; final int flags;
        final Set<BlockPos> removed=new HashSet<>();
        Pass(WorldGenLevel level,BlockPos center,int extent,Predicate<BlockPos> keep,Predicate<BlockPos> writable,int flags) {
            this.level=level; this.center=center; this.extent=extent; this.keep=keep; this.writable=writable; this.flags=flags;
        }
        boolean land(BlockPos pos) { return Math.abs(pos.getX()-center.getX())<=extent && Math.abs(pos.getZ()-center.getZ())<=extent; }
        boolean open(BlockPos pos) { return !removed.contains(pos) && writable.test(pos) && !keep.test(pos); }
        void remove(BlockPos pos) { if(removed.add(pos)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),flags); }
        /** Fells a tree rooted on the land; of one rooted outside, only what reaches over the land goes. */
        void tree(BlockPos start) {
            Set<BlockPos> logs=new HashSet<>(); var open=new ArrayDeque<BlockPos>();
            logs.add(start); open.add(start);
            boolean rooted=false;
            while(!open.isEmpty() && logs.size()<MAX_WOOD) {
                BlockPos at=open.poll();
                BlockState below=level.getBlockState(at.below());
                if(land(at) && !wood(below) && !foliage(below) && !below.isAir() && below.getFluidState().isEmpty()) rooted=true;
                for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                    BlockPos next=at.offset(dx,dy,dz);
                    if(logs.contains(next) || Math.abs(next.getX()-start.getX())>TREE_SPREAD || Math.abs(next.getZ()-start.getZ())>TREE_SPREAD
                            || !open(next) || !wood(level.getBlockState(next))) continue;
                    logs.add(next); open.add(next);
                }
            }
            Set<BlockPos> felled=new HashSet<>();
            for(BlockPos log:logs) if(rooted || land(log)) felled.add(log);
            // Foliage the felled wood held up, searched through touching foliage as a leaf's distance is.
            Map<BlockPos,Integer> reach=new HashMap<>(); var search=new ArrayDeque<BlockPos>();
            for(BlockPos log:felled) { reach.put(log,0); search.add(log); }
            while(!search.isEmpty() && reach.size()<MAX_FOLIAGE) {
                BlockPos at=search.poll(); int depth=reach.get(at);
                if(depth>=LEAF_REACH) continue;
                for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                    BlockPos next=at.offset(dx,dy,dz);
                    if(reach.containsKey(next) || !open(next) || !foliage(level.getBlockState(next))) continue;
                    reach.put(next,depth+1); search.add(next);
                }
            }
            for(BlockPos log:felled) {
                remove(log);
                // Mushrooms and moss growing on a fallen log go with it.
                BlockPos above=log.above();
                if(!felled.contains(above) && open(above) && !level.getBlockState(above).isAir() && !level.getBlockState(above).canSurvive(level,above)) remove(above);
            }
            for(BlockPos leaf:reach.keySet()) {
                if(felled.contains(leaf)) continue;
                if(land(leaf) || rooted && !besideStandingWood(leaf,logs)) remove(leaf);
            }
        }
        /** Foliage off the land that touches wood still standing belongs to another tree. */
        boolean besideStandingWood(BlockPos leaf,Set<BlockPos> tree) {
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                BlockPos near=leaf.offset(dx,dy,dz);
                if(tree.contains(near) || removed.contains(near) || !writable.test(near) || keep.test(near)) continue;
                if(wood(level.getBlockState(near))) return true;
            }
            return false;
        }
        /** Leaves left beside a cleared block learn they lost their wood, so a cut canopy decays as it would. */
        int finish() {
            Set<BlockPos> told=new HashSet<>();
            for(BlockPos pos:removed) for(Direction direction:Direction.values()) {
                BlockPos near=pos.relative(direction);
                if(removed.contains(near) || !told.add(near) || !writable.test(near)) continue;
                BlockState state=level.getBlockState(near);
                if(state.getBlock() instanceof LeavesBlock && !state.getValue(BlockStateProperties.PERSISTENT)) level.scheduleTick(near,state.getBlock(),1);
            }
            return removed.size();
        }
    }
}

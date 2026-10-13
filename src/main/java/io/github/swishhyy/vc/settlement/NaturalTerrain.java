package io.github.swishhyy.vc.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/** Heightmaps include trees: remove only natural vegetation from a read-only soil survey. */
public final class NaturalTerrain {
    public static final int MAX_TREE_HEIGHT=64;
    public record Surface(int ground,int top) {}
    private NaturalTerrain() {}
    public static Surface surface(ServerLevel level,int x,int z) {
        if(!level.hasChunkAt(new BlockPos(x,0,z))) return null;
        int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z),y=top;
        while(y>level.getMinY()+2 && top-y<=MAX_TREE_HEIGHT && vegetation(level.getBlockState(new BlockPos(x,y-1,z)))) y--;
        return new Surface(y,top);
    }
    public static boolean soil(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRASS_BLOCK)
                || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.MUD);
    }
    public static boolean vegetation(BlockState s) {
        if(!s.getFluidState().isEmpty()) return false;
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM)
                || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || s.getBlock() instanceof LeavesBlock && !s.getValue(BlockStateProperties.PERSISTENT);
    }
}

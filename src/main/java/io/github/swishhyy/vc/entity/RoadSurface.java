package io.github.swishhyy.vc.entity;

import io.github.swishhyy.vc.VC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/** Route preference is a block tag; waterlogged solid decks still count as safe support. */
public final class RoadSurface {
    public static final TagKey<Block> PAVING=TagKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath(VC.MODID,"paved_paths"));
    private RoadSurface() {}

    public static boolean openWater(BlockGetter level,BlockPos feet) {
        return openWaterCell(level,feet) || openWaterCell(level,feet.below());
    }
    private static boolean openWaterCell(BlockGetter level,BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty();
    }
    public static boolean preferred(BlockGetter level,BlockPos feet) {
        // On the lower half of a stair or slab step, a walker stands inside the step's own block.
        var step=level.getBlockState(feet);
        if(step.is(PAVING) && (step.is(BlockTags.STAIRS) || step.is(BlockTags.SLABS)) && !step.getCollisionShape(level,feet).isEmpty()) return true;
        BlockPos floor=feet.below();
        var support=level.getBlockState(floor);
        if(support.getCollisionShape(level,floor).isEmpty()) return false;
        if(support.is(PAVING)) return true;
        // A solid deck over water is a bridge even when it uses an unusual building material.
        if(level.getFluidState(floor).is(FluidTags.WATER)) return true;
        for(int depth=2;depth<=5;depth++) {
            BlockPos below=feet.below(depth);
            if(level.getFluidState(below).is(FluidTags.WATER)) return true;
            if(!level.getBlockState(below).getCollisionShape(level,below).isEmpty()) return false;
        }
        return false;
    }
}

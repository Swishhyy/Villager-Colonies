package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.Tags;

/** Block matching is shared by live scanning and station inspection. */
public final class StationDetection {
    private StationDetection() {}
    public static boolean storageBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
    }
    public static boolean completeBed(BlockState head, BlockState foot) {
        return head.getBlock() instanceof BedBlock && foot.is(head.getBlock())
                && head.getValue(BedBlock.PART)==BedPart.HEAD && foot.getValue(BedBlock.PART)==BedPart.FOOT
                && head.getValue(BedBlock.FACING)==foot.getValue(BedBlock.FACING);
    }
    public static boolean workBlock(StructureRole role, BlockState state) {
        return switch(role) {
            case FARM -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
                    && (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS));
            case LUMBER -> state.is(BlockTags.LOGS);
            case MINE -> state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES);
            default -> false;
        };
    }
}

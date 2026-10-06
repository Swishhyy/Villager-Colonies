package io.github.swishhyy.wwmc.settlement;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public enum TreeSpecies {
    OAK(Blocks.OAK_LOG,Blocks.OAK_LEAVES,Blocks.OAK_SAPLING,Items.OAK_SAPLING,1),
    BIRCH(Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,Blocks.BIRCH_SAPLING,Items.BIRCH_SAPLING,1),
    SPRUCE(Blocks.SPRUCE_LOG,Blocks.SPRUCE_LEAVES,Blocks.SPRUCE_SAPLING,Items.SPRUCE_SAPLING,1),
    JUNGLE(Blocks.JUNGLE_LOG,Blocks.JUNGLE_LEAVES,Blocks.JUNGLE_SAPLING,Items.JUNGLE_SAPLING,1),
    ACACIA(Blocks.ACACIA_LOG,Blocks.ACACIA_LEAVES,Blocks.ACACIA_SAPLING,Items.ACACIA_SAPLING,1),
    DARK_OAK(Blocks.DARK_OAK_LOG,Blocks.DARK_OAK_LEAVES,Blocks.DARK_OAK_SAPLING,Items.DARK_OAK_SAPLING,2),
    CHERRY(Blocks.CHERRY_LOG,Blocks.CHERRY_LEAVES,Blocks.CHERRY_SAPLING,Items.CHERRY_SAPLING,1),
    MANGROVE(Blocks.MANGROVE_LOG,Blocks.MANGROVE_LEAVES,Blocks.MANGROVE_PROPAGULE,Items.MANGROVE_PROPAGULE,1),
    PALE_OAK(Blocks.PALE_OAK_LOG,Blocks.PALE_OAK_LEAVES,Blocks.PALE_OAK_SAPLING,Items.PALE_OAK_SAPLING,2);
    public final Block log,leaves,sapling;
    public final Item seed;
    public final int width;
    TreeSpecies(Block log,Block leaves,Block sapling,Item seed,int width) {
        this.log=log; this.leaves=leaves; this.sapling=sapling; this.seed=seed; this.width=width;
    }
    public static TreeSpecies ofLog(BlockState state) {
        for(TreeSpecies species:values()) if(state.is(species.log)) return species;
        return null;
    }
}

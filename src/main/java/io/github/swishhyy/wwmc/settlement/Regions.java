package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The land around an expedition site decides its regional resource: the ore its outpost's mine works as an endless
 * vein. Research needs goods from several regions, so a growing realm reaches out to more than one kind of land.
 */
public final class Regions {
    /** A region: its name, the ore an outpost there mines, and the item that ore yields, which the outpost exports home. */
    public record Region(String id,String title,Block ore,String product) {}
    public static final Region HIGHLANDS=new Region("highlands","Highlands",Blocks.EMERALD_ORE,"minecraft:emerald");
    public static final Region DRYLANDS=new Region("drylands","Badlands and desert",Blocks.GOLD_ORE,"minecraft:raw_gold");
    public static final Region JUNGLE=new Region("jungle","Jungle",Blocks.LAPIS_ORE,"minecraft:lapis_lazuli");
    public static final Region COAST=new Region("coast","Coast and rivers",Blocks.COPPER_ORE,"minecraft:raw_copper");
    public static final Region SAVANNA=new Region("savanna","Savanna",Blocks.REDSTONE_ORE,"minecraft:redstone");
    public static final Region NORTH=new Region("north","Northern taiga",Blocks.COAL_ORE,"minecraft:coal");
    public static final Region LOWLANDS=new Region("lowlands","Lowlands",Blocks.IRON_ORE,"minecraft:raw_iron");
    public static final List<Region> ALL=List.of(HIGHLANDS,DRYLANDS,JUNGLE,COAST,SAVANNA,NORTH,LOWLANDS);
    private Regions() {}
    public static Region of(ServerLevel level,BlockPos pos) {
        var biome=level.getBiome(pos);
        if(biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return HIGHLANDS;
        if(biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.HAS_DESERT_PYRAMID)) return DRYLANDS;
        if(biome.is(BiomeTags.IS_JUNGLE)) return JUNGLE;
        if(biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)) return COAST;
        if(biome.is(BiomeTags.IS_SAVANNA)) return SAVANNA;
        if(biome.is(BiomeTags.IS_TAIGA)) return NORTH;
        return LOWLANDS;
    }
    public static Region byId(String id) { return ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null); }
}

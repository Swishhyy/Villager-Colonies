package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.VC;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/** Follow dry natural ground, clear vegetation, and reject construction only where the plan touches it. */
public final class NpcVillageTerrain {
    public static final int MAX_RELIEF=12,MAX_FOUNDATION=6,MAX_TREE_HEIGHT=32;
    public record Site(BlockPos center,NpcVillageLayout layout) {}
    private NpcVillageTerrain() {}

    public static Site inspect(ServerLevel level,BlockPos probe) {
        int extent=NpcVillageLayout.EXTENT;
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.CELLS),surface=new ArrayList<>(NpcVillageLayout.CELLS);
        for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,probe.getX()+x,probe.getZ()+z),y=top;
            // Tree trunks are part of the heightmap. Descend through natural vegetation to actual soil.
            while(y>level.getMinY()+2 && top-y<=MAX_TREE_HEIGHT && vegetation(level.getBlockState(probe.offset(x,y-1-probe.getY(),z)))) y--;
            ground.add(y); surface.add(top);
        }
        int banner=Integer.MIN_VALUE;
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) banner=Math.max(banner,ground.get(NpcVillageLayout.index(x,z)));
        BlockPos center=new BlockPos(probe.getX(),banner,probe.getZ());
        List<Integer> relativeGround=ground.stream().map(y -> y-center.getY()).toList();
        List<Integer> relativeSurface=surface.stream().map(y -> y-center.getY()).toList();
        var layout=new NpcVillageLayout(palette(level,center),relativeGround,relativeSurface);
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(var column:NpcVillagePlan.footprint()) {
            int y=ground.get(NpcVillageLayout.index(column.x(),column.z()));
            BlockPos p=new BlockPos(probe.getX()+column.x(),y-1,probe.getZ()+column.z());
            if(y<level.getMinY()+4 || y+MAX_RELIEF+10>=level.getMaxY()) return reject(probe,p,"surface height outside safe range");
            if(!soil(level.getBlockState(p))) return reject(probe,p,"unsupported ground "+level.getBlockState(p));
            low=Math.min(low,y); high=Math.max(high,y);
            if(high-low>MAX_RELIEF) return reject(probe,p,"terrain relief exceeds "+MAX_RELIEF+" blocks");
        }
        for(var lot:NpcVillagePlan.LOTS) {
            int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(int x=-lot.radiusX();x<=lot.radiusX();x++) for(int z=-lot.radiusZ();z<=lot.radiusZ();z++) {
                int y=layout.ground(lot.x()+x,lot.z()+z); min=Math.min(min,y); max=Math.max(max,y);
            }
            if(max-min>MAX_FOUNDATION) return reject(probe,center.offset(lot.x(),max,lot.z()),"building foundation exceeds "+MAX_FOUNDATION+" blocks");
        }
        var preview=new Settlement(new UUID(0,0),new UUID(0,0),"Placement",center,240,List.of(),List.of(),"balanced");
        preview.trading.layout=layout;
        var protectedBlocks=WorldWorkData.get(level).protectedBlocks;
        for(var placement:NpcVillagePlan.blueprint(preview)) {
            BlockPos p=placement.pos(); BlockState state=level.getBlockState(p);
            if(!level.getWorldBorder().isWithinBounds(p)) return reject(probe,p,"outside world border");
            if(protectedBlocks.contains(p)) return reject(probe,p,"protected block");
            if(level.getBlockEntity(p)!=null) return reject(probe,p,"block entity");
            if(!level.getFluidState(p).isEmpty()) return reject(probe,p,"fluid");
            if(!state.isAir() && !soil(state) && !vegetation(state)) return reject(probe,p,"construction or obstruction "+state);
        }
        return new Site(center,layout);
    }
    private static String palette(ServerLevel level,BlockPos pos) {
        var biome=level.getBiome(pos);
        if(biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS) || biome.is(Biomes.WOODED_BADLANDS)) return "sandstone";
        if(biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) || biome.is(Biomes.WINDSWEPT_SAVANNA)) return "acacia";
        if(biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.GROVE)) return "spruce";
        return "oak";
    }
    private static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.TERRACOTTA) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.MUD);
    }
    private static boolean vegetation(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS)
                || state.getBlock() instanceof LeavesBlock && !state.getValue(BlockStateProperties.PERSISTENT);
    }
    private static Site reject(BlockPos probe,BlockPos p,String reason) {
        VC.LOGGER.debug("[VC][npc-search] Rejected plot={} position={} reason={}",probe,p,reason); return null;
    }
}

package io.github.swishhyy.vc.settlement;

import io.github.swishhyy.vc.VC;
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
    public static final int MAX_RELIEF=64,MAX_FOUNDATION=20,MAX_TREE_HEIGHT=NaturalTerrain.MAX_TREE_HEIGHT;
    public record Site(BlockPos center,NpcVillageLayout layout) {}
    private static final ThreadLocal<String> REJECTION=ThreadLocal.withInitial(() -> "no suitable plot");
    public static String lastRejection() { return REJECTION.get(); }
    private NpcVillageTerrain() {}

    public static Site inspect(ServerLevel level,BlockPos probe) {
        int extent=NpcVillageLayout.EXTENT;
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.CELLS),surface=new ArrayList<>(NpcVillageLayout.CELLS);
        for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
            var column=NaturalTerrain.surface(level,probe.getX()+x,probe.getZ()+z);
            if(column==null) return reject(probe,probe.offset(x,0,z),"unloaded terrain");
            ground.add(column.ground()); surface.add(column.top());
        }
        List<Integer> plaza=new ArrayList<>();
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) plaza.add(ground.get(NpcVillageLayout.index(x,z)));
        plaza.sort(Integer::compareTo); int banner=plaza.get(plaza.size()/2);
        BlockPos center=new BlockPos(probe.getX(),banner,probe.getZ());
        List<Integer> relativeGround=ground.stream().map(y -> y-center.getY()).toList();
        List<Integer> relativeSurface=surface.stream().map(y -> y-center.getY()).toList();
        var layout=new NpcVillageLayout(palette(level,center),relativeGround,relativeSurface);
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(var column:NpcVillagePlan.footprint()) {
            int y=ground.get(NpcVillageLayout.index(column.x(),column.z()));
            BlockPos p=new BlockPos(probe.getX()+column.x(),y-1,probe.getZ()+column.z());
            if(y<level.getMinY()+4 || y+12>=level.getMaxY()) return reject(probe,p,"surface height outside safe range");
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
        String earthwork=NpcVillagePlan.earthworkIssue(preview);
        if(earthwork!=null) return reject(probe,center,earthwork);
        var protectedBlocks=WorldWorkData.get(level).protectedBlocks;
        for(var placement:NpcVillagePlan.blueprint(preview)) {
            BlockPos p=placement.pos(); BlockState state=level.getBlockState(p);
            if(p.getY()<level.getMinY() || p.getY()>=level.getMaxY()) return reject(probe,p,"building outside height range");
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
        return NaturalTerrain.soil(state);
    }
    private static boolean vegetation(BlockState state) {
        return NaturalTerrain.vegetation(state);
    }
    private static Site reject(BlockPos probe,BlockPos p,String reason) {
        REJECTION.set(reason);
        VC.LOGGER.debug("[VC][npc-search] Rejected plot={} position={} reason={}",probe,p,reason); return null;
    }
}

package io.github.swishhyy.vc.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A real structure start: terrain is sampled without loading chunks or accessing saved AI data. */
public final class NpcTownStructure extends Structure {
    public static final MapCodec<NpcTownStructure> CODEC=simpleCodec(NpcTownStructure::new);
    /** Heights are sampled every four blocks over the whole walled village and one sample beyond it. */
    private static final int STEP=4,HALF=32,SAMPLES=2*HALF/STEP+1;
    public NpcTownStructure(StructureSettings settings) { super(settings); }
    @Override public StructureType<?> type() { return SettlementStructures.TOWN_TYPE.get(); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if(!Config.RANDOM_TOWNS.get() || !Config.INITIAL_NPC_TOWNS.get() || Config.MAX_NPC_TOWNS.get()==0) return Optional.empty();
        var site=terrain(context);
        if(site==null) return Optional.empty();
        var candidate=NpcTownPlan.worldgenCandidate(context.seed(),site.center());
        var town=NpcSettlements.createTown(candidate,site,Config.SETTLEMENT_RADIUS.get());
        if(NpcVillagePlan.earthworkIssue(town)!=null) return Optional.empty();
        return Optional.of(new GenerationStub(site.center(),builder -> builder.addPiece(new NpcTownPiece(candidate,site,town.radius))));
    }

    /** A small noise-height grid bounds generation work; interpolation follows natural slopes. */
    public static NpcVillageTerrain.Site terrain(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        int[][] samples=new int[SAMPLES][SAMPLES];
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int a=0;a<SAMPLES;a++) for(int b=0;b<SAMPLES;b++) {
            int height=context.chunkGenerator().getBaseHeight(x-HALF+a*STEP,z-HALF+b*STEP,
                    Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            // Sea-level terrain includes river/ocean water, not a suitable town foundation.
            if(height<=context.chunkGenerator().getSeaLevel() || height<context.heightAccessor().getMinY()+4
                    || height+12>=context.heightAccessor().getMaxY()) return null;
            samples[a][b]=height; low=Math.min(low,height); high=Math.max(high,height);
        }
        if(high-low>NpcVillageTerrain.MAX_RELIEF) return null;
        int centerY=samples[HALF/STEP][HALF/STEP];
        if(!context.chunkGenerator().getBaseColumn(x,z,context.heightAccessor(),context.randomState())
                .getBlock(centerY-1).getFluidState().isEmpty()) return null;
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(centerY),QuartPos.fromBlock(z),context.randomState().sampler());
        String palette=biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.WOODED_BADLANDS) ? "sandstone"
                : biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) ? "acacia"
                : biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.GROVE) ? "spruce" : "oak";
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.CELLS);
        for(int dx=-NpcVillageLayout.EXTENT;dx<=NpcVillageLayout.EXTENT;dx++) for(int dz=-NpcVillageLayout.EXTENT;dz<=NpcVillageLayout.EXTENT;dz++) {
            int a=(dx+HALF)/STEP,b=(dz+HALF)/STEP,tx=(dx+HALF)%STEP,tz=(dz+HALF)%STEP;
            int height=Math.round((samples[a][b]*(STEP-tx)*(STEP-tz)+samples[a+1][b]*tx*(STEP-tz)
                    +samples[a][b+1]*(STEP-tx)*tz+samples[a+1][b+1]*tx*tz)/(float)(STEP*STEP));
            ground.add(height-centerY);
        }
        return new NpcVillageTerrain.Site(new BlockPos(x,centerY,z),new NpcVillageLayout(palette,ground,ground));
    }
}

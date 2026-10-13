package io.github.swishhyy.vc.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A real structure start: terrain is sampled without loading chunks or accessing saved AI data. Towns and cities are two
 * structures sharing one placement, so /locate can tell them apart; each region holds at most one of them.
 */
public final class NpcTownStructure extends Structure {
    public static final MapCodec<NpcTownStructure> CODEC=simpleCodec(settings -> new NpcTownStructure(settings,false));
    public static final MapCodec<NpcTownStructure> CITY_CODEC=simpleCodec(settings -> new NpcTownStructure(settings,true));
    /** Heights are sampled every four blocks over the whole walled site and one sample beyond it. */
    private static final int STEP=4;
    private final boolean city;
    public NpcTownStructure(StructureSettings settings,boolean city) { super(settings); this.city=city; }
    public boolean city() { return city; }
    @Override public StructureType<?> type() { return city ? SettlementStructures.CITY_TYPE.get() : SettlementStructures.TOWN_TYPE.get(); }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if(!Config.RANDOM_TOWNS.get() || !Config.INITIAL_NPC_TOWNS.get() || Config.MAX_NPC_TOWNS.get()==0) return Optional.empty();
        var chunk=context.chunkPos();
        var spread=RoadNetwork.placement(context.registryAccess());
        boolean region=spread!=null && NpcTownPlan.cityRegion(context.seed(),Math.floorDiv(chunk.x(),spread.spacing()),Math.floorDiv(chunk.z(),spread.spacing()));
        if(this.city && !region) return Optional.empty();
        // The start stands on the surface at the chunk's middle, so a biome without settlements is known before the survey;
        // /locate asks this of every region it passes.
        int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ();
        int y=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
        if(!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),
                QuartPos.fromBlock(z),context.randomState().sampler()))) return Optional.empty();
        // A region chosen for a city holds one where the land allows, and a town otherwise; the other structure stands aside.
        var city=region ? citySite(context.chunkGenerator(),context.heightAccessor(),context.randomState(),context.biomeSource(),chunk) : null;
        if(this.city!=(city!=null)) return Optional.empty();
        var site=city!=null ? city : terrain(context.chunkGenerator(),context.heightAccessor(),context.randomState(),context.biomeSource(),chunk,NpcVillageLayout.LEVELLED);
        if(site==null) return Optional.empty();
        var candidate=NpcTownPlan.worldgenCandidate(context.seed(),site.center(),city!=null);
        var town=NpcSettlements.createTown(candidate,site,Config.SETTLEMENT_RADIUS.get());
        if(city==null && NpcVillagePlan.earthworkIssue(town)!=null) return Optional.empty();
        return Optional.of(new GenerationStub(site.center(),builder -> builder.addPiece(new NpcTownPiece(candidate,site,town.radius))));
    }
    /** A city site in this chunk, or null where the land cannot hold one; shared with the road network so roads meet its gates. */
    public static NpcVillageTerrain.Site citySite(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,BiomeSource biomes,ChunkPos chunk) {
        var site=terrain(generator,heights,random,biomes,chunk,NpcVillageLayout.CITY);
        if(site==null) return null;
        var preview=new Settlement(new UUID(0,0),new UUID(0,0),"Placement",site.center(),240,List.of(),List.of(),"balanced");
        preview.trading.layout=site.layout();
        return NpcVillagePlan.earthworkIssue(preview)==null ? site : null;
    }

    /** A small noise-height grid bounds generation work; interpolation follows natural slopes. */
    public static NpcVillageTerrain.Site terrain(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,BiomeSource biomes,ChunkPos chunk,int design) {
        int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ(),extent=NpcVillageLayout.extent(design);
        int half=STEP*((extent+STEP)/STEP),samples=2*half/STEP+1;
        int[][] grid=new int[samples][samples];
        boolean[][] sampled=new boolean[samples][samples];
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        // Coarse samples first, so water or too steep a slope rejects the site after a few of them.
        for(int stride=Integer.highestOneBit(samples-1);stride>=1;stride>>=1)
            for(int a=0;a<samples;a+=stride) for(int b=0;b<samples;b+=stride) {
                if(sampled[a][b]) continue;
                sampled[a][b]=true;
                int height=generator.getBaseHeight(x-half+a*STEP,z-half+b*STEP,Heightmap.Types.WORLD_SURFACE_WG,heights,random);
                // Sea-level terrain includes river/ocean water, not a suitable town foundation.
                if(height<=generator.getSeaLevel() || height<heights.getMinY()+4 || height+12>=heights.getMaxY()) return null;
                grid[a][b]=height; low=Math.min(low,height); high=Math.max(high,height);
                if(high-low>NpcVillageTerrain.MAX_RELIEF) return null;
            }
        if(high-low>NpcVillageTerrain.MAX_RELIEF) return null;
        int centerY=grid[half/STEP][half/STEP];
        if(!generator.getBaseColumn(x,z,heights,random).getBlock(centerY-1).getFluidState().isEmpty()) return null;
        var biome=biomes.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(centerY),QuartPos.fromBlock(z),random.sampler());
        String palette=biome.is(Biomes.DESERT) || biome.is(Biomes.BADLANDS) || biome.is(Biomes.WOODED_BADLANDS) ? "sandstone"
                : biome.is(Biomes.SAVANNA) || biome.is(Biomes.SAVANNA_PLATEAU) ? "acacia"
                : biome.is(Biomes.TAIGA) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA)
                || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.GROVE) ? "spruce" : "oak";
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.cells(design));
        for(int dx=-extent;dx<=extent;dx++) for(int dz=-extent;dz<=extent;dz++) {
            int a=(dx+half)/STEP,b=(dz+half)/STEP,tx=(dx+half)%STEP,tz=(dz+half)%STEP;
            int height=Math.round((grid[a][b]*(STEP-tx)*(STEP-tz)+grid[a+1][b]*tx*(STEP-tz)
                    +grid[a][b+1]*(STEP-tx)*tz+grid[a+1][b+1]*tx*tz)/(float)(STEP*STEP));
            ground.add(height-centerY);
        }
        return new NpcVillageTerrain.Site(new BlockPos(x,centerY,z),new NpcVillageLayout(palette,ground,ground,design));
    }
}

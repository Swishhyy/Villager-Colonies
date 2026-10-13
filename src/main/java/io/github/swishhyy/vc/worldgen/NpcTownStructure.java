package io.github.swishhyy.vc.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
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
    /**
     * How many chunks from its region's site a city may stand, so that rivers and lakes, which turn most single sites
     * away, leave room for one nearby. Its land then reaches seven chunks from the start's chunk, inside the eight within
     * which world generation finds a structure's pieces.
     */
    public static final int CITY_REACH=5;
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
        NpcVillageTerrain.Site site;
        if(this.city) site=citySite(context.chunkGenerator(),context.heightAccessor(),context.randomState(),context.chunkGenerator().getBiomeSource(),
                cityBiomes(context.registryAccess()),chunk);
        else {
            // A town stands on the surface at the chunk's middle, so a biome without settlements is known before the
            // survey; /locate asks this of every region it passes.
            int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ();
            int y=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            if(!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z),context.randomState().sampler()))) return Optional.empty();
            site=terrain(context.chunkGenerator(),context.heightAccessor(),context.randomState(),context.biomeSource(),chunk,NpcVillageLayout.LEVELLED);
        }
        if(site==null) return Optional.empty();
        var candidate=NpcTownPlan.worldgenCandidate(context.seed(),site.center(),this.city);
        var town=NpcSettlements.createTown(candidate,site,Config.SETTLEMENT_RADIUS.get());
        if(!this.city) {
            if(NpcVillagePlan.earthworkIssue(town)!=null) return Optional.empty();
            // A region chosen for a city holds one where the land allows, and a town otherwise. Few sites take a town,
            // so the town asks about the city, a wider survey, only once its own land would do.
            if(region && citySite(context.chunkGenerator(),context.heightAccessor(),context.randomState(),context.chunkGenerator().getBiomeSource(),
                    cityBiomes(context.registryAccess()),chunk)!=null) return Optional.empty();
        }
        return Optional.of(new GenerationStub(site.center(),builder -> builder.addPiece(new NpcTownPiece(candidate,site,town.radius))));
    }
    /** The biomes a city may stand in, or none where a data pack removed cities. */
    public static Predicate<Holder<Biome>> cityBiomes(RegistryAccess access) {
        var structure=access.lookupOrThrow(Registries.STRUCTURE).get(SettlementStructures.CITY);
        if(structure.isEmpty()) return biome -> false;
        var biomes=structure.get().value().biomes();
        return biomes::contains;
    }
    /**
     * The city a region offering its site at this chunk holds, or null where the land cannot hold one: the nearest chunk
     * within {@link #CITY_REACH} whose middle lies in a city biome and whose land takes the city. Shared with the road
     * network so roads meet its gates.
     */
    public static NpcVillageTerrain.Site citySite(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,BiomeSource biomes,
            Predicate<Holder<Biome>> valid,ChunkPos chunk) {
        // Neighbouring sites share most of their samples, all on the same four-block lattice.
        Map<Long,Integer> surveyed=new HashMap<>();
        IntBinaryOperator height=(x,z) -> surveyed.computeIfAbsent((long)x<<32 | z&0xffffffffL,
                key -> generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,heights,random));
        for(int ring=0;ring<=CITY_REACH;ring++) for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring) continue;
            var near=new ChunkPos(chunk.x()+dx,chunk.z()+dz);
            int x=near.getMiddleBlockX(),z=near.getMiddleBlockZ(),y=height.applyAsInt(x,z);
            if(!valid.test(biomes.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z),random.sampler()))) continue;
            var site=terrain(generator,heights,random,biomes,near,NpcVillageLayout.CITY,height);
            if(site==null) continue;
            var preview=new Settlement(new UUID(0,0),new UUID(0,0),"Placement",site.center(),240,List.of(),List.of(),"balanced");
            preview.trading.layout=site.layout();
            if(NpcVillagePlan.earthworkIssue(preview)==null) return site;
        }
        return null;
    }

    /** A small noise-height grid bounds generation work; interpolation follows natural slopes. */
    public static NpcVillageTerrain.Site terrain(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,BiomeSource biomes,ChunkPos chunk,int design) {
        return terrain(generator,heights,random,biomes,chunk,design,(x,z) -> generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,heights,random));
    }
    private static NpcVillageTerrain.Site terrain(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,BiomeSource biomes,ChunkPos chunk,
            int design,IntBinaryOperator surface) {
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
                int height=surface.applyAsInt(x-half+a*STEP,z-half+b*STEP);
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

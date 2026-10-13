package io.github.swishhyy.vc;

import io.github.swishhyy.vc.settlement.*;
import io.github.swishhyy.vc.worldgen.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** How often the normal Overworld's terrain lets a city stand, surveyed the way world generation surveys it. */
@ExtendWith(EphemeralTestServerProvider.class)
public final class CityDensityChecks {
    private static final int STEP=4,REGIONS=12;
    private static final long[] SEEDS={246813579L,12345L,-8812736455L};

    private record Survey(String reason,int relief) {}

    /** The same survey as the structure's, with the relief limit as a parameter, naming why a site fails. */
    private static Survey survey(ChunkGenerator generator,LevelHeightAccessor heights,RandomState random,ChunkPos chunk,int design,int maxRelief) {
        int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ(),extent=NpcVillageLayout.extent(design);
        int half=STEP*((extent+STEP)/STEP),samples=2*half/STEP+1;
        int[][] grid=new int[samples][samples];
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int a=0;a<samples;a++) for(int b=0;b<samples;b++) {
            int height=generator.getBaseHeight(x-half+a*STEP,z-half+b*STEP,Heightmap.Types.WORLD_SURFACE_WG,heights,random);
            if(height<=generator.getSeaLevel()) return new Survey("water",0);
            if(height<heights.getMinY()+4 || height+12>=heights.getMaxY()) return new Survey("height",0);
            grid[a][b]=height; low=Math.min(low,height); high=Math.max(high,height);
        }
        if(high-low>maxRelief) return new Survey("relief",high-low);
        int centerY=grid[half/STEP][half/STEP];
        if(!generator.getBaseColumn(x,z,heights,random).getBlock(centerY-1).getFluidState().isEmpty()) return new Survey("pond",high-low);
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.cells(design));
        for(int dx=-extent;dx<=extent;dx++) for(int dz=-extent;dz<=extent;dz++) {
            int a=(dx+half)/STEP,b=(dz+half)/STEP,tx=(dx+half)%STEP,tz=(dz+half)%STEP;
            int height=Math.round((grid[a][b]*(STEP-tx)*(STEP-tz)+grid[a+1][b]*tx*(STEP-tz)
                    +grid[a][b+1]*(STEP-tx)*tz+grid[a+1][b+1]*tx*tz)/(float)(STEP*STEP));
            ground.add(height-centerY);
        }
        var preview=new Settlement(new UUID(0,0),new UUID(0,0),"Placement",new BlockPos(x,centerY,z),240,List.of(),List.of(),"balanced");
        preview.trading.layout=new NpcVillageLayout("oak",ground,ground,design);
        return new Survey(NpcVillagePlan.earthworkIssue(preview)==null ? "ok" : "earthwork",high-low);
    }

    @Test void citiesStandOftenEnoughToFind(MinecraftServer server) {
        var registries=server.registryAccess();
        var stem=registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow();
        var generator=stem.generator();
        var type=stem.type().value();
        var heights=LevelHeightAccessor.create(type.minY(),type.height());
        var spread=(RandomSpreadStructurePlacement)registries.lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"))).value().placement();
        var biomes=registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(SettlementStructures.CITY).value().biomes();
        StringBuilder report=new StringBuilder();
        int total=0;
        for(long seed:SEEDS) {
            var random=RandomState.create(((NoiseBasedChunkGenerator)generator).generatorSettings().value(),registries.lookupOrThrow(Registries.NOISE),seed);
            int regions=0,biome=0,water=0,relief=0,pond=0,earthwork=0,ok=0,rescued=0,relief16=0,relief20=0,towns=0,townRegions=0;
            List<Integer> reliefs=new ArrayList<>();
            for(int i=-REGIONS;i<REGIONS;i++) for(int j=-REGIONS;j<REGIONS;j++) {
                ChunkPos chunk=spread.getPotentialStructureChunk(seed,i*spread.spacing(),j*spread.spacing());
                int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ();
                int y=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,heights,random);
                boolean land=biomes.contains(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z),random.sampler()));
                if(!NpcTownPlan.cityRegion(seed,i,j)) {
                    townRegions++;
                    if(land && NpcTownStructure.terrain(generator,heights,random,generator.getBiomeSource(),chunk,NpcVillageLayout.LEVELLED)!=null) towns++;
                    continue;
                }
                regions++;
                if(!land) { biome++; continue; }
                var survey=survey(generator,heights,random,chunk,NpcVillageLayout.CITY,NpcVillageTerrain.MAX_RELIEF);
                boolean structure=NpcTownStructure.citySite(generator,heights,random,generator.getBiomeSource(),chunk)!=null;
                assertEquals(survey.reason().equals("ok"),structure,"The survey disagrees with the structure at "+chunk);
                switch(survey.reason()) {
                    case "water" -> water++;
                    case "relief" -> { relief++; reliefs.add(survey.relief()); }
                    case "pond","height" -> pond++;
                    case "earthwork" -> earthwork++;
                    default -> ok++;
                }
                if(structure) continue;
                if(survey(generator,heights,random,chunk,NpcVillageLayout.CITY,16).reason().equals("ok")) relief16++;
                if(survey(generator,heights,random,chunk,NpcVillageLayout.CITY,20).reason().equals("ok")) relief20++;
                // Would a site a few chunks away hold the city?
                search:
                for(int dx=-4;dx<=4;dx+=2) for(int dz=-4;dz<=4;dz+=2) {
                    if(dx==0 && dz==0) continue;
                    var near=new ChunkPos(chunk.x()+dx,chunk.z()+dz);
                    if(NpcTownStructure.citySite(generator,heights,random,generator.getBiomeSource(),near)!=null) { rescued++; break search; }
                }
            }
            total+=ok;
            report.append(String.format("seed %d: %d city regions: biome %d, water %d, relief %d %s, pond %d, earthwork %d, ok %d;"
                    +" a nearby site would hold %d more; relief 16 would add %d, relief 20 %d; town terrain fits %d of %d regions%n",
                    seed,regions,biome,water,relief,reliefs,pond,earthwork,ok,rescued,relief16,relief20,towns,townRegions));
        }
        assertTrue(total>=SEEDS.length*4,"Too few cities within "+REGIONS*spread.spacing()*16+" blocks of the origin:\n"+report);
    }
}

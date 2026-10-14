package io.github.swishhyy.vc;

import io.github.swishhyy.vc.settlement.*;
import io.github.swishhyy.vc.worldgen.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/**
 * How often the normal Overworld's terrain lets a town or city stand. Rivers and lakes turn most single sites away: three
 * seeds held only three cities in 192 city regions, and about one region in fifty had land for a town, before either
 * could stand a few chunks from its region's site.
 */
@ExtendWith(EphemeralTestServerProvider.class)
public final class CityDensityChecks {
    private static final int REGIONS=8,TOWN_REGIONS=6;
    private static final long[] SEEDS={246813579L,12345L};

    @Test void citiesStandOftenEnoughToFind(MinecraftServer server) {
        var registries=server.registryAccess();
        var stem=registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow();
        var generator=stem.generator();
        var type=stem.type().value();
        var heights=LevelHeightAccessor.create(type.minY(),type.height());
        var spread=(RandomSpreadStructurePlacement)registries.lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"))).value().placement();
        var valid=NpcTownStructure.cityBiomes(registries);
        StringBuilder report=new StringBuilder();
        int total=0;
        for(long seed:SEEDS) {
            var random=RandomState.create(((NoiseBasedChunkGenerator)generator).generatorSettings().value(),registries.lookupOrThrow(Registries.NOISE),seed);
            int regions=0,cities=0,moved=0;
            for(int i=-REGIONS;i<REGIONS;i++) for(int j=-REGIONS;j<REGIONS;j++) {
                if(!NpcTownPlan.cityRegion(seed,i,j)) continue;
                regions++;
                ChunkPos chunk=spread.getPotentialStructureChunk(seed,i*spread.spacing(),j*spread.spacing());
                var site=NpcTownStructure.citySite(generator,heights,random,generator.getBiomeSource(),valid,chunk);
                if(site==null) continue;
                cities++;
                var at=ChunkPos.containing(site.center());
                int reach=Math.max(Math.abs(at.x()-chunk.x()),Math.abs(at.z()-chunk.z()));
                if(reach>0) moved++;
                assertTrue(reach<=NpcTownStructure.CITY_REACH,"A city stands "+reach+" chunks from its region's site");
                // World generation finds a structure's pieces within eight chunks of its start.
                int e=NpcVillageLayout.CITY_EXTENT,x=site.center().getX(),z=site.center().getZ();
                int land=Math.max(Math.max(Math.abs(Math.floorDiv(x-e,16)-chunk.x()),Math.abs(Math.floorDiv(x+e,16)-chunk.x())),
                        Math.max(Math.abs(Math.floorDiv(z-e,16)-chunk.z()),Math.abs(Math.floorDiv(z+e,16)-chunk.z())));
                assertTrue(land<=8,"A city's land reaches "+land+" chunks from its start");
            }
            total+=cities;
            report.append(String.format("seed %d: %d cities (%d moved from the region's site) in %d city regions%n",seed,cities,moved,regions));
        }
        assertTrue(total>=SEEDS.length*2,"Too few cities within "+REGIONS*spread.spacing()*16+" blocks of the origin:\n"+report);
    }

    /** Towns may also stand a few chunks from their region's site, and still outnumber cities. */
    @Test void townsStandMoreOftenThanCities(MinecraftServer server) {
        var registries=server.registryAccess();
        var stem=registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().overworld().orElseThrow();
        var generator=stem.generator();
        var type=stem.type().value();
        var heights=LevelHeightAccessor.create(type.minY(),type.height());
        var spread=(RandomSpreadStructurePlacement)registries.lookupOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"))).value().placement();
        var townBiomes=NpcTownStructure.townBiomes(registries);
        var cityBiomes=NpcTownStructure.cityBiomes(registries);
        long seed=SEEDS[0];
        var random=RandomState.create(((NoiseBasedChunkGenerator)generator).generatorSettings().value(),registries.lookupOrThrow(Registries.NOISE),seed);
        int regions=0,towns=0,cities=0,moved=0;
        for(int i=-TOWN_REGIONS;i<TOWN_REGIONS;i++) for(int j=-TOWN_REGIONS;j<TOWN_REGIONS;j++) {
            regions++;
            ChunkPos chunk=spread.getPotentialStructureChunk(seed,i*spread.spacing(),j*spread.spacing());
            if(NpcTownPlan.cityRegion(seed,i,j) && NpcTownStructure.citySite(generator,heights,random,generator.getBiomeSource(),cityBiomes,chunk)!=null) { cities++; continue; }
            var site=NpcTownStructure.townSite(generator,heights,random,generator.getBiomeSource(),townBiomes,chunk);
            if(site==null) continue;
            towns++;
            var at=ChunkPos.containing(site.center());
            int reach=Math.max(Math.abs(at.x()-chunk.x()),Math.abs(at.z()-chunk.z()));
            if(reach>0) moved++;
            assertTrue(reach<=NpcTownStructure.TOWN_REACH,"A town stands "+reach+" chunks from its region's site");
        }
        String report=String.format("seed %d: %d towns (%d moved from the region's site) and %d cities in %d regions",seed,towns,moved,cities,regions);
        assertTrue(towns>cities && towns>=regions/20,"Too few towns: "+report);
    }
}

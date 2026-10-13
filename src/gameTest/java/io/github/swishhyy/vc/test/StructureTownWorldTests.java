package io.github.swishhyy.vc.test;

import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import io.github.swishhyy.vc.worldgen.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class StructureTownWorldTests {
    @GameTest(timeoutTicks=100,batch="structureTown") @EmptyTemplate
    @TestHolder(description="Native town structure metadata survives serialization; a furnished, walled worldgen town activates its saved claim and twelve real workers exactly once, without rebuilding its blocks.")
    static void savedStructureActivatesOnce(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            var candidate=NpcTownPlan.candidate(level.getSeed(),811,811,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()+12,candidate.center().getZ());
            int extent=NpcSettlements.EXTENT;
            var chunks=CitizenNavigationTests.pinArea(level,base,-extent,extent,-extent,extent);
            CitizenNavigationTests.meadow(level,base,-extent,extent,-extent,extent);
            var site=NpcVillageTerrain.inspect(level,base);
            helper.assertTrue(site!=null,"Native activation meadow was rejected");
            var piece=new NpcTownPiece(NpcTownPlan.worldgenCandidate(level.getSeed(),site.center()),site,120);
            var context=StructurePieceSerializationContext.fromLevel(level);
            var structure=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(SettlementStructures.TOWN).value();
            var start=new StructureStart(structure,ChunkPos.containing(base),0,new PiecesContainer(List.of(piece)));
            var restored=StructureStart.loadStaticStart(context,start.createTag(context,ChunkPos.containing(base)),level.getSeed());
            helper.assertTrue(restored!=null && restored.isValid(),"The native start did not survive its save");
            var saved=(NpcTownPiece)restored.getPieces().getFirst();
            helper.assertTrue(saved.candidate().equals(piece.candidate()) && saved.placements().equals(piece.placements()),"Saving changes the founding layout or identity");
            for(var placement:saved.placements()) level.setBlock(placement.pos(),placement.state(),2);
            var town=SettlementStructures.activate(level,saved);
            helper.assertTrue(town!=null && town.trading.buildIndex<0 && town.citizens.size()==NpcSettlements.startingPopulation(town)
                    && town.citizens.size()==12,"Native town did not staff every workstation and gate sentry");
            helper.assertTrue(SettlementService.housingBeds(level,town).size()==12 && level.getBlockState(town.center).is(VC.BANNER.get()),"Native buildings or beds are missing");
            level.setBlock(town.center.above(10),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            helper.assertTrue(SettlementStructures.activate(level,saved)==town && town.citizens.size()==12,"Reloading duplicates the claim or crew");
            helper.assertTrue(level.getBlockState(town.center.above(10)).is(Blocks.DIAMOND_BLOCK),"Activation rebuilt the player's changed blocks");
            test.whenDisabled(() -> {
                for(var id:town.citizens) { var entity=level.getEntity(id); if(entity!=null) entity.discard(); }
                SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
                NpcWorldData.get(level).generated.remove(saved.candidate().region()); NpcWorldData.get(level).setDirty();
                CitizenNavigationTests.release(level,base,chunks);
            });
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks=200,batch="structureCity") @EmptyTemplate
    @TestHolder(description="A worldgen walled city is its own locatable structure in the settlement set, survives serialization, and activates once with forty citizens, every work post staffed and forty-six beds, without rebuilding its blocks.")
    static void savedCityActivatesOnce(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            var structures=level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var structure=structures.getOrThrow(SettlementStructures.CITY).value();
            helper.assertTrue(structure instanceof NpcTownStructure asCity && asCity.city() && structure.type()==SettlementStructures.CITY_TYPE.get()
                    && structures.getOrThrow(SettlementStructures.TOWN).value() instanceof NpcTownStructure asTown && !asTown.city(),"Cities and towns are not separate structures");
            var set=level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                    .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"))).value();
            helper.assertTrue(set.structures().stream().anyMatch(e -> e.structure().is(SettlementStructures.CITY))
                    && set.structures().stream().anyMatch(e -> e.structure().is(SettlementStructures.TOWN)),"The settlement placement does not offer both towns and cities");
            var candidate=NpcTownPlan.candidate(level.getSeed(),830,830,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()+12,candidate.center().getZ());
            int extent=NpcVillageLayout.CITY_EXTENT;
            var chunks=CitizenNavigationTests.pinArea(level,base,-extent,extent,-extent,extent);
            CitizenNavigationTests.meadow(level,base,-extent,extent,-extent,extent);
            var site=NpcVillageTerrain.inspect(level,base,NpcVillageLayout.CITY);
            helper.assertTrue(site!=null,"City meadow was rejected: "+NpcVillageTerrain.lastRejection());
            var built=new NpcTownPiece(NpcTownPlan.worldgenCandidate(level.getSeed(),site.center(),true),site,120);
            helper.assertTrue(built.extent()==extent,"The city piece does not cover the city's land");
            var context=StructurePieceSerializationContext.fromLevel(level);
            var start=new StructureStart(structure,ChunkPos.containing(base),0,new PiecesContainer(List.of(built)));
            var restored=StructureStart.loadStaticStart(context,start.createTag(context,ChunkPos.containing(base)),level.getSeed());
            helper.assertTrue(restored!=null && restored.isValid() && restored.getStructure()==structure,"The city start did not survive its save");
            var piece=(NpcTownPiece)restored.getPieces().getFirst();
            helper.assertTrue(piece.candidate().equals(built.candidate()) && piece.placements().equals(built.placements()),"Saving changes the city's layout or identity");
            for(var placement:piece.placements()) level.setBlock(placement.pos(),placement.state(),2);
            var town=SettlementStructures.activate(level,piece);
            test.whenDisabled(() -> {
                if(town!=null) {
                    for(var id:town.citizens) { var entity=level.getEntity(id); if(entity!=null) entity.discard(); }
                    SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
                }
                NpcWorldData.get(level).generated.remove(piece.candidate().region()); NpcWorldData.get(level).setDirty();
                CitizenNavigationTests.release(level,base,chunks);
            });
            helper.assertTrue(town!=null && NpcVillagePlan.city(town),"The city did not activate");
            helper.assertTrue(town.citizens.size()==NpcSettlements.CITY_POPULATION,"The city was not founded with forty citizens: "+town.citizens.size());
            int beds=SettlementService.housingBeds(level,town).size();
            helper.assertTrue(beds==46,"The city's homes hold "+beds+" beds, not forty-six");
            helper.assertTrue(town.stations.stream().filter(s -> s.role().providesWork()).allMatch(s -> town.jobs.assigned(s.position())==1),"A city work post is unstaffed");
            helper.assertTrue(SettlementStructures.activate(level,piece)==town && town.citizens.size()==NpcSettlements.CITY_POPULATION,"Reloading duplicates the city's claim or crew");
            helper.succeed();
        });
    }
}

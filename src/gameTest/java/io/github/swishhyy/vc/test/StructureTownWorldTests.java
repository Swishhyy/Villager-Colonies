package io.github.swishhyy.vc.test;

import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import io.github.swishhyy.vc.worldgen.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
    @TestHolder(description="Native town structure metadata survives serialization; a furnished worldgen town activates its saved claim and eight real workers exactly once, without rebuilding its blocks.")
    static void savedStructureActivatesOnce(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            var candidate=NpcTownPlan.candidate(level.getSeed(),811,811,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()+12,candidate.center().getZ());
            var chunks=CitizenNavigationTests.pinArea(level,base,-23,23,-23,23);
            CitizenNavigationTests.meadow(level,base,-23,23,-23,23);
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
            helper.assertTrue(town!=null && town.trading.buildIndex<0 && town.citizens.size()==8,"Native town did not initialize its real workers");
            helper.assertTrue(SettlementService.housingBeds(level,town).size()==12 && level.getBlockState(town.center).is(VC.BANNER.get()),"Native buildings or beds are missing");
            level.setBlock(town.center.above(10),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            helper.assertTrue(SettlementStructures.activate(level,saved)==town && town.citizens.size()==8,"Reloading duplicates the claim or crew");
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
}

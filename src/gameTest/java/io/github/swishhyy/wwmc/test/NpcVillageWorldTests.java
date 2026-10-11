package io.github.swishhyy.wwmc.test;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class NpcVillageWorldTests {
    private static void terrain(ServerLevel level,BlockPos base,boolean slope) {
        int extent=NpcSettlements.EXTENT;
        for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
            int h=slope ? Math.floorDiv(x+extent,6) : 0;
            for(int y=-3;y<h-1;y++) level.setBlock(base.offset(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(base.offset(x,h-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            for(int y=h;y<=h+20;y++) level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
    }
    @GameTest(timeoutTicks=1200,batch="npcVillage")
    @EmptyTemplate
    @TestHolder(description="A seven-block natural slope with trees, below sea level, builds a furnished village with separate cottages, real housing, warehouse stock and reachable doors. Its terrain plan survives serialization during construction.")
    static void buildsOnSlopesAndNaturalTrees(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var candidate=NpcTownPlan.candidate(level.getSeed(),710,710,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()-6,candidate.center().getZ());
            int extent=NpcSettlements.EXTENT;
            var chunks=CitizenNavigationTests.pinArea(level,base,-extent,extent,-extent,extent);
            terrain(level,base,true);
            BlockPos tree=base.offset(-13,1,-13);
            for(int y=0;y<8;y++) level.setBlock(tree.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.setBlock(tree.offset(x,8,z),Blocks.OAK_LEAVES.defaultBlockState(),2);
            var town=NpcSettlements.discover(level,candidate,List.of(candidate.center()));
            helper.assertTrue(town!=null,"A gentle slope and natural tree still prevented NPC discovery");
            var plan=NpcSettlements.blueprint(town);
            test.whenDisabled(() -> {
                for(UUID id:town.citizens) { var citizen=level.getEntity(id); if(citizen!=null) citizen.discard(); }
                SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
                NpcWorldData.get(level).generated.remove(candidate.region()); NpcWorldData.get(level).setDirty();
                CitizenNavigationTests.release(level,base,chunks);
            });
            helper.runAtTickTime(5,() -> {
                helper.assertTrue(town.trading.buildIndex>0 && town.trading.buildIndex<plan.size(),"Village did not use bounded construction batches");
                var restored=TradeSettings.CODEC.parse(JsonOps.INSTANCE,TradeSettings.CODEC.encodeStart(JsonOps.INSTANCE,town.trading).getOrThrow()).getOrThrow();
                helper.assertTrue(restored.layout.equals(town.trading.layout) && restored.buildIndex==town.trading.buildIndex,"Terrain or checkpoint was lost on reload");
                var copy=new Settlement(town.id,town.owner,town.name,town.center,town.radius,List.of(),List.of(),"balanced");
                copy.trading.layout=restored.layout;
                helper.assertTrue(plan.equals(NpcSettlements.blueprint(copy)),"Changed terrain changed the saved construction plan");
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(town.trading.buildIndex<0,"Village construction is incomplete: "+town.trading.buildIndex+" "+town.trading.status);
                helper.assertTrue(level.getBlockState(town.center).is(VC.BANNER.get()) && town.citizens.size()==6,"Village lacks its banner or native crew");
                helper.assertTrue(town.stations.stream().filter(s -> s.role()==StructureRole.HOUSING).count()==3,"Village did not build three cottages");
                helper.assertTrue(SettlementService.populationLimit(town)>=10,"Separate cottages do not provide population capacity");
                helper.assertTrue(InventoryOps.count(SettlementService.storage(level,town),s -> s.is(net.minecraft.world.item.Items.BREAD))>0,"Warehouse has no real starter supplies");
                for(var p:plan) if(p.protect()) helper.assertTrue(level.getBlockState(p.pos()).getBlock()==p.state().getBlock(),"Furnished building lost "+p.state()+" at "+p.pos());
                var walker=new io.github.swishhyy.wwmc.entity.CitizenEntity(VC.CITIZEN.get(),level);
                walker.setPos(town.center.getX()-0.5,town.center.getY(),town.center.getZ()-0.5);
                try {
                    for(var station:town.stations) if(station.role()==StructureRole.HOUSING || station.role()==StructureRole.WAREHOUSE
                            || station.role()==StructureRole.COOK || station.role()==StructureRole.SMELTERY) {
                        BlockPos inside=station.position().relative(station.facing(),2);
                        var path=walker.getNavigation().createPath(inside,0);
                        helper.assertTrue(path!=null && path.canReach(),"Village road or doorway is unreachable: "+station.role()+" at "+inside);
                    }
                } finally { walker.discard(); }
            });
        });
    }
    @GameTest(timeoutTicks=20,batch="npcTerrain")
    @EmptyTemplate
    @TestHolder(description="Expanded NPC terrain rules clear natural trees, ignore unused corners, and still reject protected player blocks, storage, water and cliffs without modifying terrain.")
    static void terrainStillProtectsConstructionAndWater(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var candidate=NpcTownPlan.candidate(level.getSeed(),720,720,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()+4,candidate.center().getZ());
            int extent=NpcSettlements.EXTENT;
            var chunks=CitizenNavigationTests.pinArea(level,base,-extent,extent,-extent,extent);
            terrain(level,base,false); var protection=WorldWorkData.get(level);
            BlockPos used=base.offset(-13,0,-13),corner=base.offset(23,0,23);
            test.whenDisabled(() -> {
                protection.protectedBlocks.remove(used); protection.protectedBlocks.remove(corner); protection.setDirty();
                CitizenNavigationTests.release(level,base,chunks);
            });
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())!=null,"Dry flat natural terrain was rejected");
            level.setBlock(used,Blocks.OAK_LOG.defaultBlockState(),2); protection.protect(used);
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())==null,"A player-placed log was cleared");
            helper.assertTrue(level.getBlockState(used).is(Blocks.OAK_LOG),"Terrain validation modified a player block");
            protection.protectedBlocks.remove(used); level.setBlock(used,Blocks.CHEST.defaultBlockState(),2);
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())==null,"Player storage was overwritten");
            level.setBlock(used,Blocks.WATER.defaultBlockState(),2);
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())==null,"An underwater building site was accepted");
            level.setBlock(used,Blocks.AIR.defaultBlockState(),2);
            level.setBlock(corner,Blocks.OAK_PLANKS.defaultBlockState(),2); protection.protect(corner);
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())!=null,"An unused corner unnecessarily rejected the entire village");
            for(int y=0;y<=13;y++) level.setBlock(used.above(y),Blocks.STONE.defaultBlockState(),2);
            helper.assertTrue(NpcVillageTerrain.inspect(level,candidate.center())==null,"A cliff passed the terrain safety limit");
            helper.succeed();
        });
    }
}

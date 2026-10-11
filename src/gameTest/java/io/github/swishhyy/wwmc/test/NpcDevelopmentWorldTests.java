package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class NpcDevelopmentWorldTests {
    private static Settlement town(ServerLevel level,BlockPos center,boolean npc,StructureRole role) {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Development fixture",center,npc ? 96 : 16,List.of(),List.of(),"balanced");
        town.trading.npc=npc;
        var station=new Station(center.north(2),role); town.stations.add(station);
        SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(center,VC.BANNER.get().defaultBlockState());
        level.setBlockAndUpdate(station.position(),VC.STATIONS.get(role).get().defaultBlockState()); return town;
    }
    @GameTest(timeoutTicks=100,batch="npcDevelopment") @EmptyTemplate
    @TestHolder(description="An NPC spends real building stock once, pauses beside players and changed protected blocks, saves its paid construction cursor, and resumes a furnished cottage with actual housing.")
    static void paidGrowthResumesAndPreservesPlayerEdits(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(-192000,2,-16000));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,48,-12,12); CitizenNavigationTests.meadow(level,start,-8,48,-12,12);
            var town=town(level,start,true,StructureRole.WAREHOUSE); BlockPos barrel=town.stations.getFirst().position().west(2);
            level.setBlockAndUpdate(barrel,Blocks.BARREL.defaultBlockState()); Container stock=(Container)level.getBlockEntity(barrel);
            stock.setItem(0,new ItemStack(Items.OAK_LOG,32)); stock.setItem(1,new ItemStack(Items.COBBLESTONE,16)); stock.setItem(2,new ItemStack(Items.BREAD,64));
            var observer=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DevelopmentObserver"));
            observer.setGameMode(GameType.CREATIVE); observer.setPos(start.getX()+1000,start.getY(),start.getZ()); level.addNewPlayer(observer);
            test.whenDisabled(() -> {
                level.removePlayerImmediately(observer,Entity.RemovalReason.DISCARDED);
                SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty(); CitizenNavigationTests.release(level,start,chunks);
            });
            helper.runAtTickTime(10,() -> {
                var d=town.trading.development; d.nextProject=level.getGameTime()-1; d.nextImmigrant=level.getGameTime()+5000;
                NpcDirector.evaluate(level,town);
                helper.assertTrue(d.construction!=null,"Funded town failed to plan a cottage");
                helper.assertTrue(stock.getItem(0).isEmpty() && stock.getItem(1).isEmpty(),"Project did not reserve its actual materials");
                var project=d.construction; observer.setPos(project.station.position().getX()+1,start.getY(),project.station.position().getZ());
                NpcDirector.advance(level,town); helper.assertTrue(project.cursor==0,"Construction changed blocks beside a player");
                observer.setPos(start.getX()+1000,start.getY(),start.getZ());
                var obstacle=project.steps.stream().filter(s -> s.original().isAir() && !s.desired().isAir()).skip(10).findFirst().orElseThrow();
                level.setBlockAndUpdate(obstacle.pos(),Blocks.DIAMOND_BLOCK.defaultBlockState()); WorldWorkData.get(level).protect(obstacle.pos());
                for(int n=0;n<100 && project.cursor<project.steps.size();n++) NpcDirector.advance(level,town);
                helper.assertTrue(!project.complete() && level.getBlockState(obstacle.pos()).is(Blocks.DIAMOND_BLOCK),"Construction replaced a player's protected edit");
                int savedCursor=project.cursor;
                var restored=TradeSettings.CODEC.parse(JsonOps.INSTANCE,TradeSettings.CODEC.encodeStart(JsonOps.INSTANCE,town.trading).getOrThrow()).getOrThrow();
                helper.assertTrue(restored.development.construction.cursor==savedCursor,"Saved project lost its construction checkpoint");
                town.trading.development=restored.development; project=restored.development.construction;
                level.setBlockAndUpdate(obstacle.pos(),obstacle.original()); WorldWorkData.get(level).protectedBlocks.remove(obstacle.pos());
                for(int n=0;n<100 && town.trading.development.construction!=null;n++) NpcDirector.advance(level,town);
                helper.assertTrue(town.trading.development.construction==null && town.trading.development.completed==1,"Paid project failed to resume");
                helper.assertTrue(town.station(project.station.position())!=null && SettlementService.housingBeds(level,town).size()==4,"Addition has no actual furnished housing");
                var walker=new io.github.swishhyy.wwmc.entity.CitizenEntity(VC.CITIZEN.get(),level);
                walker.setPos(start.getX()+22.5,start.getY(),start.getZ()+6.5); walker.setOnGround(true);
                var doorPath=walker.getNavigation().createPath(project.station.position().relative(project.station.facing(),2),0);
                helper.assertTrue(doorPath!=null && doorPath.canReach(),"New cottage path or entrance is unreachable"); walker.discard();
                helper.assertTrue(stock.getItem(0).isEmpty() && stock.getItem(1).isEmpty(),"Resuming paid for the project twice");
                helper.succeed();
            });
        });
    }
    @GameTest(timeoutTicks=100,batch="npcHighway") @EmptyTemplate
    @TestHolder(description="A saved highway requires agreed trade and surveyed loaded terrain, respects player claims and edits, resumes a budgeted road cursor and supplies the shared trader graph without loading unvisited terrain.")
    static void highwaysRespectConsentClaimsAndUnloadedTerrain(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(-320000,2,-18000));
            var chunks=CitizenNavigationTests.pinArea(level,start,-16,176,-16,16); CitizenNavigationTests.meadow(level,start,-16,176,-16,16);
            var a=town(level,start,false,StructureRole.TRADER); var b=town(level,start.east(160),true,StructureRole.TRADER);
            var data=SharedRoads.get(level); int oldRoads=data.roads.size();
            test.whenDisabled(() -> {
                data.roads.removeIf(r -> r.a.equals(a.id) || r.b.equals(a.id)); data.setDirty();
                SettlementData.get(level).settlements.remove(a); SettlementData.get(level).settlements.remove(b); SettlementData.get(level).setDirty();
                CitizenNavigationTests.release(level,start,chunks);
            });
            helper.runAtTickTime(10,() -> {
                helper.assertTrue(data.plan(level,a,b)==null,"Highway ignored route consent");
                a.trading.partner=b.id; b.trading.partner=a.id;
                var atlas=TradeAtlas.get(level);
                for(var chunk:chunks) atlas.sample(level,chunk.x(),chunk.z());
                var road=data.plan(level,a,b); helper.assertTrue(road!=null,"Loaded surveyed grass corridor was rejected");
                var inside=road.line.stream().filter(a::contains).findFirst().orElseThrow();
                BlockPos obstruction=road.line.get(road.line.size()/2);
                level.setBlockAndUpdate(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState()); WorldWorkData.get(level).protect(obstruction);
                data.roads.add(road); data.build(level,road,4); helper.assertTrue(road.cursor<=4,"Road exceeded its per-tick column budget");
                for(int n=0;n<100;n++) data.build(level,road,4);
                helper.assertTrue(!road.complete() && level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"Highway replaced a player obstruction");
                var restored=SharedRoads.CODEC.parse(JsonOps.INSTANCE,SharedRoads.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
                var saved=restored.between(a.id,b.id); helper.assertTrue(saved.cursor==road.cursor,"Highway checkpoint did not survive saving");
                level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState()); WorldWorkData.get(level).protectedBlocks.remove(obstruction); road.blockedUntil=0;
                for(int n=0;n<100 && !road.complete();n++) data.build(level,road,4);
                helper.assertTrue(road.complete(),"Highway failed to resume");
                helper.assertTrue(level.getBlockState(inside.below()).is(Blocks.GRASS_BLOCK),"Highway changed a player's claim");
                helper.assertTrue(level.getBlockState(obstruction.below()).is(Blocks.DIRT_PATH),"Highway did not pave public ground");
                var route=SharedRoads.route(level,inside,b.stations.getFirst().position()); helper.assertTrue(!route.isEmpty(),"Player trader cannot use the shared highway");
                var path=data.mapPath(a,b); helper.assertTrue(path.size()>=3 && path.size()<=64,"Map route lost the actual highway or exceeded its packet budget");
                // Do not touch the distant chunk: construct only saved metadata for an unvisited destination.
                var far=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Unvisited",start.east(10000),96,List.of(),
                        List.of(new Station(start.east(10000),StructureRole.TRADER)),"balanced"); far.trading.npc=true;
                a.trading.partner=far.id; far.trading.partner=a.id;
                helper.assertTrue(!level.hasChunkAt(far.center) && data.plan(level,a,far)==null && !level.hasChunkAt(far.center),"Highway planning loaded unvisited terrain");
                helper.assertTrue(data.roads.size()==oldRoads+1,"Highway planning duplicated the route");
                helper.succeed();
            });
        });
    }
}

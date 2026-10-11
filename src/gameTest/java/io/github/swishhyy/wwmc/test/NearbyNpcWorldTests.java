package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.settlement.NearbyNpcTown;
import io.github.swishhyy.wwmc.settlement.NpcTownPlan;
import io.github.swishhyy.wwmc.settlement.NpcWorldData;
import io.github.swishhyy.wwmc.settlement.SettlementData;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class NearbyNpcWorldTests {
    @GameTest(timeoutTicks=1000,batch="nearbyNpc")
    @EmptyTemplate
    @TestHolder(description="A nearby NPC search builds a real furnished town and recruits its crew, holds at most nine temporary chunks, releases them and does not duplicate its neighbour.")
    static void nearbyTownBuildsAndReleasesChunks(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            var candidate=NpcTownPlan.candidate(level.getSeed(),700,700,Config.NPC_SPACING.get());
            BlockPos center=new BlockPos(candidate.center().getX(),level.getSeaLevel()+5,candidate.center().getZ());
            int extent=io.github.swishhyy.wwmc.settlement.NpcSettlements.EXTENT;
            var chunks=CitizenNavigationTests.pinArea(level,center,-extent,extent,-extent,extent);
            CitizenNavigationTests.meadow(level,center,-extent,extent,-extent,extent);
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"NearbyVisitor"));
            player.setGameMode(GameType.CREATIVE); player.setPos(center.getX()-256,center.getY(),center.getZ()); level.addNewPlayer(player);
            var service=new NearbyNpcTown(); NeoForge.EVENT_BUS.register(service);
            test.whenDisabled(() -> {
                service.stopped(new ServerStoppedEvent(level.getServer())); NeoForge.EVENT_BUS.unregister(service);
                level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
                var town=SettlementData.get(level).byId(candidate.id());
                if(town!=null) {
                    for(UUID id:town.citizens) { var citizen=level.getEntity(id); if(citizen!=null) citizen.discard(); }
                    SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
                    level.setBlockAndUpdate(town.center,Blocks.AIR.defaultBlockState());
                }
                NpcWorldData.get(level).generated.remove(candidate.region()); NpcWorldData.get(level).setDirty();
                CitizenNavigationTests.release(level,center,chunks);
            });
            helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ())==center.getY(),
                    "Prepared NPC meadow has a different surface height: "+level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ()));
            helper.assertTrue(service.ensureNearby(player),"Nearby NPC search did not start");
            var maximum=new AtomicInteger();
            helper.succeedWhen(() -> {
                maximum.accumulateAndGet(service.heldChunks(level),Math::max);
                helper.assertTrue(service.heldChunks(level)<=9,"NPC generation exceeded its temporary chunk budget");
                var town=SettlementData.get(level).byId(candidate.id());
                helper.assertTrue(town!=null && town.trading.buildIndex<0,"NPC town has not finished construction: "+(town==null ? "not discovered; held="+service.heldChunks(level)+", maximum="+maximum.get() : town.trading.buildIndex+" "+town.trading.status));
                helper.assertTrue(level.getBlockState(town.center).is(VC.BANNER.get()) && town.citizens.size()==6,"NPC town lacks its banner or real starting crew");
                helper.assertTrue(service.heldChunks(level)==0 && maximum.get()>0,"NPC search did not release its temporary chunks");
                helper.assertTrue(!service.ensureNearby(player),"A completed neighbour started duplicate generation");
            });
        });
    }
}

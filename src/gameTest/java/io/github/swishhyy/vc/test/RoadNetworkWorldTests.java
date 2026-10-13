package io.github.swishhyy.vc.test;

import io.github.swishhyy.vc.core.StructureRole;
import io.github.swishhyy.vc.settlement.*;
import io.github.swishhyy.vc.worldgen.RoadNetwork;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class RoadNetworkWorldTests {
    private static Settlement town(ServerLevel level,RoadNetwork.Node node,String name) {
        BlockPos center=new BlockPos(node.x(),level.getSeaLevel()+3,node.z());
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),name,center,120,List.of(),
                List.of(new Station(center.north(13),StructureRole.TRADER)),"balanced");
        town.trading.npc=true; town.trading.buildIndex=-1;
        return town;
    }
    @GameTest(timeoutTicks=600,batch="oldRoads") @EmptyTemplate
    @TestHolder(description="Near the world centre a continuous dirt road joins neighbouring town sites gate to gate and is paved into the chunks it crosses; towns on those sites open trade along it and traders get it as a shared highway.")
    static void neighbouringTownsTradeAlongTheirRoad(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            var network=RoadNetwork.of(level);
            helper.assertTrue(network!=null,"The Overworld has no pregenerated road network");
            var home=network.node(0,0);
            helper.assertTrue(home.land(),"The central town site is not on land");
            var next=network.neighbours(home).stream().filter(n -> network.road(home,n)!=null).findFirst().orElse(null);
            helper.assertTrue(next!=null,"The central town site has no road to a neighbour");
            var road=network.road(home,next);
            for(int n=1;n<road.length();n++) helper.assertTrue(Math.abs(road.xs()[n]-road.xs()[n-1])<=1 && Math.abs(road.zs()[n]-road.zs()[n-1])<=1,
                    "The road has a gap at "+road.xs()[n]+","+road.zs()[n]);
            RoadNetwork.Node first=home.key()<next.key() ? home : next;
            RoadNetwork.Node last=first==home ? next : home;
            // A city's gates stand further out than a town's.
            java.util.function.ToIntFunction<RoadNetwork.Node> gateOf=n -> NpcVillageLayout.extent(network.city(n) ? NpcVillageLayout.CITY : NpcVillageLayout.LEVELLED)+1;
            int gate=gateOf.applyAsInt(first);
            helper.assertTrue(Math.max(Math.abs(road.xs()[0]-first.x()),Math.abs(road.zs()[0]-first.z()))==gate
                    && Math.max(Math.abs(road.xs()[road.length()-1]-last.x()),Math.abs(road.zs()[road.length()-1]-last.z()))==gateOf.applyAsInt(last),
                    "The road does not run from gate to gate");
            int homeGate=gateOf.applyAsInt(home);
            // Pave the chunk under the middle of the road, as world generation would.
            int middle=road.length()/2; BlockPos column=new BlockPos(road.xs()[middle],road.feet()[middle],road.zs()[middle]);
            var chunks=CitizenNavigationTests.pinArea(level,column,-8,8,-8,8);
            var feature=level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
                    .getOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE,Identifier.fromNamespaceAndPath("vc","roads"))).value();
            BlockPos origin=new BlockPos(ChunkPos.containing(column).x()*16,level.getMinY(),ChunkPos.containing(column).z()*16);
            helper.assertTrue(feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(1),origin),"The road feature paved nothing");
            helper.assertTrue(level.getBlockState(column.below()).is(Blocks.DIRT_PATH) && level.getBlockState(column).isAir(),
                    "The road is not paved where it was surveyed: "+level.getBlockState(column.below())+" at "+column.below());
            Settlement a=town(level,home,"Hearth");
            Settlement b=town(level,next,"Ford");
            var data=SettlementData.get(level); data.settlements.add(a); data.settlements.add(b); data.setDirty();
            test.whenDisabled(() -> {
                var roads=SharedRoads.get(level); roads.roads.removeIf(r -> r.a.equals(a.id) || r.b.equals(a.id)); roads.setDirty();
                data.settlements.remove(a); data.settlements.remove(b); data.setDirty();
                CitizenNavigationTests.release(level,column,chunks);
            });
            // The game test server runs its ticks as fast as it can: wait for the survey itself, not for a number of ticks.
            RoadNetwork.connect(level,a).orTimeout(60,TimeUnit.SECONDS).join();
            helper.succeedWhen(() -> {
                helper.assertTrue(TradeRoutes.agreed(a,b),"Neighbouring towns did not open trade along their road");
                var shared=SharedRoads.get(level).between(a.id,b.id);
                helper.assertTrue(shared!=null && shared.complete() && shared.line.size()>=2,"Traders were not given the road as a highway");
                helper.assertTrue(shared.line.getFirst().distSqr(new BlockPos(a.center.getX(),shared.line.getFirst().getY(),a.center.getZ()))<=2*homeGate*homeGate,
                        "The highway does not start at the first town's gate");
            });
        });
    }
}

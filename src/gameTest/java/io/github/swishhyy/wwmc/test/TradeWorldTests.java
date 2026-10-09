package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.clock.WorldClocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.conf.Feature;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

@Mod("wwmc_tests")
public final class TradeWorldTests {
    public TradeWorldTests(IEventBus bus,ModContainer container) {
        bus.addListener(CitizenNavigationTests::registerTickets);
        FrameworkConfiguration.builder(Identifier.fromNamespaceAndPath("wwmc_tests","travel"))
                .enable(Feature.GAMETEST).build().create().init(bus,container);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            var server=event.getServer();
            // These fixtures exercise daytime jobs. Long earlier batches must not turn later checks into sleep tests.
            // Pause only the daylight clock: normal game ticks, AI, food and research cadence still advance.
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
            server.clockManager().setPaused(server.registryAccess().getOrThrow(WorldClocks.OVERWORLD),true);
            if(SettlementService.night(server.overworld())) throw new IllegalStateException("Daytime work fixtures started at night");
            WWMC.LOGGER.info("[WWMC tests] Daylight clock fixed for daytime job fixtures");
        });
    }

    @GameTest(timeoutTicks=18000)
    @EmptyTemplate
    @TestHolder(description="A trader delivers real cargo over 640 blocks and returns without a nearby player, beneath a roof, around a wall, and across a wide river on a narrow bridge.")
    static void longTripUnderRoof(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,16));
            // The small template must not force-load the road; only the moving trader window may do that.
            for(int x=-8;x<=648;x++) for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            // Partial-height dirt paths must remain valid high-level travel waypoints.
            for(int x=-8;x<=648;x++)
                level.setBlockAndUpdate(start.offset(x,-1,0),Blocks.DIRT_PATH.defaultBlockState());
            // Heightmap waypoints land on this roof. There is two-block headroom all the way beneath it.
            for(int x=296;x<=352;x++) for(int z=-20;z<=20;z++)
                level.setBlockAndUpdate(start.offset(x,3,z),Blocks.OAK_LOG.defaultBlockState());
            for(int z=-6;z<=6;z++) for(int y=0;y<=3;y++)
                level.setBlockAndUpdate(start.offset(400,y,z),Blocks.STONE.defaultBlockState());
            for(int x=448;x<=500;x++) for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(start.offset(x,-4,z),Blocks.STONE.defaultBlockState());
                for(int y=-3;y<=-1;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.WATER.defaultBlockState());
            }
            for(int x=440;x<=508;x++)
                level.setBlockAndUpdate(start.offset(x,-1,6),Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP)
                        .setValue(BlockStateProperties.WATERLOGGED,true));
            UUID owner=UUID.randomUUID();
            Settlement a=town(owner,"Start",start),b=town(owner,"End",start.east(640));
            var data=SettlementData.get(level); data.settlements.add(a); data.settlements.add(b);
            for(Settlement town:List.of(a,b)) {
                level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
                for(Station station:town.stations)
                    level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
                level.setBlockAndUpdate(town.center.south(3),Blocks.BARREL.defaultBlockState());
            }
            Container from=(Container)level.getBlockEntity(a.center.south(3));
            from.setItem(0,new ItemStack(Items.IRON_INGOT,16));
            from.setItem(1,new ItemStack(Items.BREAD,64));
            a.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",0,16));
            TradeRoutes.link(a,b,data.settlements,8192);
            CitizenEntity trader=new CitizenEntity(WWMC.CITIZEN.get(),level);
            trader.join(a.id); trader.setPos(start.getX()+2.5,start.getY(),start.getZ()+0.5);
            a.citizens.add(trader.getUUID()); level.addFreshEntity(trader); data.setDirty();
            var wet=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(trader.isInWater()) wet.set(true);
                helper.assertTrue(level.players().isEmpty(),"Travel test must run without player-loaded chunks");
                helper.assertTrue(trader.isAlive(),"Trader died: "+a.trading.status);
                helper.assertTrue(a.trading.delivered==16,"Not delivered: "+a.trading.status+" at "+trader.blockPosition());
                helper.assertTrue(trader.tradeCargoCount()==0,"Trader still carries exports");
                helper.assertTrue(trader.blockPosition().distSqr(a.center)<64,"Not returned: "+a.trading.status+" at "+trader.blockPosition());
                helper.assertTrue(!wet.get(),"Trader swam instead of using the wide river's bridge");
                // Chunk unload/reload replaces block entities: inspect the current warehouse, not a stale object.
                var received=SettlementService.storageAt(level,b,b.center.south(2));
                var remaining=SettlementService.storageAt(level,a,a.center.south(2));
                helper.assertTrue(InventoryOps.count(received,s -> s.is(Items.IRON_INGOT))==16,"Destination cargo differs after chunk reload");
                helper.assertTrue(InventoryOps.count(remaining,s -> s.is(Items.IRON_INGOT))==0,"Exports duplicated at home");
                TradeChunks.release(level,a.id); TradeChunks.release(level,b.id);
                trader.discard(); data.settlements.remove(a); data.settlements.remove(b); data.setDirty();
            });
        });
    }

    @GameTest(timeoutTicks=12000)
    @EmptyTemplate
    @TestHolder(description="A trader crosses a river by its only bridge, sixty blocks to the side of the straight line, instead of stopping at the bank.")
    static void crossesByTheFarBridge(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,1400));
            var chunks=CitizenNavigationTests.pinArea(level,start,-90,90,-12,152);
            for(int x=-90;x<=90;x++) for(int z=-12;z<=152;z++) {
                level.setBlockAndUpdate(start.offset(x,-2,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            // A river across all the land, crossed only by a one-block deck sixty blocks west of the straight line.
            for(int x=-90;x<=90;x++) for(int z=64;z<=76;z++) {
                level.setBlockAndUpdate(start.offset(x,-4,z),Blocks.STONE.defaultBlockState());
                for(int y=-3;y<=-1;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.WATER.defaultBlockState());
            }
            for(int z=63;z<=77;z++) level.setBlockAndUpdate(start.offset(-60,-1,z),Blocks.OAK_PLANKS.defaultBlockState());
            UUID owner=UUID.randomUUID();
            Settlement a=town(owner,"Ford",start),b=town(owner,"Far Bank",start.south(140));
            var data=SettlementData.get(level); data.settlements.add(a); data.settlements.add(b);
            for(Settlement town:List.of(a,b)) {
                level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
                for(Station station:town.stations)
                    level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
                level.setBlockAndUpdate(town.center.south(3),Blocks.BARREL.defaultBlockState());
            }
            Container from=(Container)level.getBlockEntity(a.center.south(3));
            from.setItem(0,new ItemStack(Items.IRON_INGOT,16));
            from.setItem(1,new ItemStack(Items.BREAD,64));
            a.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",0,16));
            TradeRoutes.link(a,b,data.settlements,8192);
            // A player flying over the land would let traders see it; here the test reads the loaded land directly.
            TradeAtlas.get(level).survey(level,start.offset(-90,0,-12),start.offset(90,0,152));
            CitizenEntity trader=new CitizenEntity(WWMC.CITIZEN.get(),level);
            trader.join(a.id); trader.setPos(start.getX()+2.5,start.getY(),start.getZ()+0.5);
            a.citizens.add(trader.getUUID()); level.addFreshEntity(trader); data.setDirty();
            var wet=new AtomicBoolean();
            var offBridge=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(trader.isInWater()) wet.set(true);
                int z=trader.blockPosition().getZ()-start.getZ(),x=trader.blockPosition().getX()-start.getX();
                if(z>=64 && z<=76 && Math.abs(x+60)>1) offBridge.set(true);
                helper.assertTrue(trader.isAlive(),"Trader died: "+a.trading.status);
                helper.assertTrue(!wet.get(),"Trader swam instead of walking to the far bridge");
                helper.assertTrue(!offBridge.get(),"Trader crossed the river somewhere other than the bridge");
                helper.assertTrue(a.trading.delivered==16,"Not delivered: "+a.trading.status+" at "+trader.blockPosition());
                helper.assertTrue(trader.blockPosition().distSqr(a.center)<64,"Not returned: "+a.trading.status+" at "+trader.blockPosition());
                TradeChunks.release(level,a.id); TradeChunks.release(level,b.id);
                trader.discard(); data.settlements.remove(a); data.settlements.remove(b); data.setDirty();
                CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }

    private static Settlement town(UUID owner,String name,BlockPos center) {
        return new Settlement(UUID.randomUUID(),owner,name,center,240,List.of(),
                List.of(new Station(center.east(2),StructureRole.TRADER),new Station(center.south(2),StructureRole.WAREHOUSE)),"balanced");
    }
}

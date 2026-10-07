package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.conf.Feature;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

@Mod("wwmc_tests")
public final class TradeWorldTests {
    public TradeWorldTests(IEventBus bus,ModContainer container) {
        FrameworkConfiguration.builder(Identifier.fromNamespaceAndPath("wwmc_tests","travel"))
                .enable(Feature.GAMETEST).build().create().init(bus,container);
    }

    @GameTest(timeoutTicks=18000)
    @EmptyTemplate
    @TestHolder(description="A trader delivers real cargo over 640 blocks and returns without a nearby player, beneath a roof and around a wall.")
    static void longTripUnderRoof(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,16));
            // The small template must not force-load the road; only the moving trader window may do that.
            for(int x=-8;x<=648;x++) for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            // Heightmap waypoints land on this roof. There is two-block headroom all the way beneath it.
            for(int x=296;x<=352;x++) for(int z=-20;z<=20;z++)
                level.setBlockAndUpdate(start.offset(x,3,z),Blocks.OAK_LOG.defaultBlockState());
            for(int z=-6;z<=6;z++) for(int y=0;y<=3;y++)
                level.setBlockAndUpdate(start.offset(400,y,z),Blocks.STONE.defaultBlockState());
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
            Container to=(Container)level.getBlockEntity(b.center.south(3));
            from.setItem(0,new ItemStack(Items.IRON_INGOT,16));
            from.setItem(1,new ItemStack(Items.BREAD,64));
            a.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",0,16));
            TradeRoutes.link(a,b,data.settlements,8192);
            CitizenEntity trader=new CitizenEntity(WWMC.CITIZEN.get(),level);
            trader.join(a.id); trader.setPos(start.getX()+2.5,start.getY(),start.getZ()+0.5);
            a.citizens.add(trader.getUUID()); level.addFreshEntity(trader); data.setDirty();
            helper.succeedWhen(() -> {
                helper.assertTrue(level.players().isEmpty(),"Travel test must run without player-loaded chunks");
                helper.assertTrue(trader.isAlive(),"Trader died: "+a.trading.status);
                helper.assertTrue(a.trading.delivered==16,"Not delivered: "+a.trading.status+" at "+trader.blockPosition());
                helper.assertTrue(trader.tradeCargoCount()==0,"Trader still carries exports");
                helper.assertTrue(trader.blockPosition().distSqr(a.center)<64,"Not returned: "+a.trading.status+" at "+trader.blockPosition());
                helper.assertTrue(InventoryOps.count(List.of(to),s -> s.is(Items.IRON_INGOT))==16,"Destination cargo differs");
                helper.assertTrue(InventoryOps.count(List.of(from),s -> s.is(Items.IRON_INGOT))==0,"Exports duplicated at home");
                TradeChunks.release(level,a.id); TradeChunks.release(level,b.id);
                trader.discard(); data.settlements.remove(a); data.settlements.remove(b); data.setDirty();
            });
        });
    }

    private static Settlement town(UUID owner,String name,BlockPos center) {
        return new Settlement(UUID.randomUUID(),owner,name,center,240,List.of(),
                List.of(new Station(center.east(2),StructureRole.TRADER),new Station(center.south(2),StructureRole.WAREHOUSE)),"balanced");
    }
}

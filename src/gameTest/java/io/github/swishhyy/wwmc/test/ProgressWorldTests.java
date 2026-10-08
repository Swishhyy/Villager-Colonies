package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** The town's Needs list and research, against real stations and warehouse inventories. */
public final class ProgressWorldTests {
    @GameTest(timeoutTicks=400)
    @EmptyTemplate
    @TestHolder(description="The Needs tab names what a new kitchen lacks and where it stands, and research spends real warehouse goods exactly once.")
    static void needsAndResearch(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,3200));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,24,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,24,-8,8);
            Station warehouse=new Station(start.east(2),StructureRole.WAREHOUSE),kitchen=new Station(start.east(14),StructureRole.COOK);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Needs",start,96,List.of(),List.of(warehouse,kitchen),"balanced");
            var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
            level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
            for(Station station:town.stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
            BlockPos chestPos=warehouse.position().south(2);
            level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState());
            Container chest=(Container)level.getBlockEntity(chestPos);
            chest.setItem(0,new ItemStack(Items.IRON_INGOT,40)); chest.setItem(1,new ItemStack(Items.COAL,16)); chest.setItem(2,new ItemStack(Items.GOLD_INGOT,8));
            helper.succeedWhen(() -> {
                var needs=TownNeeds.assess(level,town);
                var titles=needs.stream().map(TownNeeds.Need::title).toList();
                helper.assertTrue(needs.stream().anyMatch(n -> n.title().equals("Cook Station has no worker") && kitchen.position().equals(n.at())),
                        "The unstaffed kitchen is named and located: "+titles);
                helper.assertTrue(titles.contains("Cook Station needs a job barrel") && titles.contains("Cook Station needs a smoker, furnace or campfire"),
                        "The kitchen's missing barrel and oven are named: "+titles);
                helper.assertTrue(needs.getFirst().severity()>=needs.getLast().severity(),"Needs are listed most urgent first");
                String result=Research.study(level,town,"steel_tools");
                helper.assertTrue(Research.has(town,"steel_tools"),"Steel Tools could not be researched: "+result);
                var stock=SettlementService.storage(level,town);
                helper.assertTrue(InventoryOps.count(stock,s -> s.is(Items.IRON_INGOT))==8 && InventoryOps.count(stock,s -> s.is(Items.COAL))==0
                        && InventoryOps.count(stock,s -> s.is(Items.GOLD_INGOT))==0,"Steel Tools spends exactly 32 iron, 16 coal and 8 gold");
                helper.assertTrue(Research.study(level,town,"steel_tools").startsWith("Already"),"Research is paid for once");
                helper.assertTrue(Research.study(level,town,"reinforced_armor").contains("schematic"),"Advanced research needs a captain's schematic");
                data.settlements.remove(town); data.setDirty();
                CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }
}

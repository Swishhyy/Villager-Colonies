package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.ArrayList;
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
    @TestHolder(description="Thirty citizens and twenty-eight stations can correctly mean twenty-four filled jobs and six unemployed citizens; disabled, open, quarry and medic places are counted separately.")
    static void jobCountsExplainSupportStations(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,5200));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,48,-8,28);
            CitizenNavigationTests.meadow(level,start,-8,48,-8,28);
            List<Station> stations=new ArrayList<>();
            for(int n=0;n<24;n++) stations.add(new Station(start.offset(7+(n%6)*7,0,(n/6)*7),n==0 ? StructureRole.MINE : StructureRole.FARM));
            stations.add(new Station(start.west(4),StructureRole.WAREHOUSE));
            stations.add(new Station(start.offset(-4,0,6),StructureRole.HOUSING));
            stations.add(new Station(start.offset(-4,0,12),StructureRole.HOUSING));
            stations.add(new Station(start.offset(-4,0,18),StructureRole.BARRACKS));
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Job counts",start,96,List.of(),stations,"balanced");
            var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
            level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
            for(Station station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
            List<CitizenEntity> citizens=new ArrayList<>();
            for(int n=0;n<30;n++) {
                var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level); citizen.join(town.id); citizen.setNoAi(true);
                citizen.setPos(start.getX()+2.5,start.getY(),start.getZ()+2.5);
                town.citizens.add(citizen.getUUID()); level.addFreshEntity(citizen); citizens.add(citizen);
                if(n<24) town.jobs.assign(citizen.getUUID(),stations.get(n).position());
            }
            helper.runAtTickTime(5,() -> {
                TownJobs jobs=TownJobs.assess(level,town);
                helper.assertTrue(town.stations.size()==28 && jobs.assigned()==24 && jobs.places()==24 && jobs.unassigned()==6 && jobs.open()==0,
                        "Support stations must not inflate employment: "+jobs);
                var view=Panels.town(level,town);
                helper.assertTrue(view.subtitle().getString().startsWith("30 citizens · 24/24 jobs · "),"Header hides job capacity: "+view.subtitle());
                var unemployment=TownNeeds.assess(level,town).stream().filter(need -> need.title().equals("6 citizens have no job")).findFirst().orElseThrow();
                helper.assertTrue(unemployment.detail().startsWith("24/24 enabled job places filled.") && !unemployment.detail().contains("priority"),
                        "Full crews must not recommend raising priority: "+unemployment.detail());
                SettlementService.setJobLevel(level,town,StructureRole.MINE,JobBoard.OFF);
                jobs=TownJobs.assess(level,town);
                helper.assertTrue(jobs.assigned()==23 && jobs.places()==23 && jobs.off()==1 && jobs.unassigned()==7 && jobs.noJobAdvice().contains("enable jobs"),
                        "Switched-off work must be explained separately: "+jobs);
                SettlementService.setJobLevel(level,town,StructureRole.MINE,JobBoard.NORMAL);
                jobs=TownJobs.assess(level,town);
                helper.assertTrue(jobs.places()==24 && jobs.open()==1 && jobs.off()==0 && jobs.noJobAdvice().contains("open"),"Re-enabled places must be available: "+jobs);
                var quarry=new Station(start.offset(42,0,27),StructureRole.QUARRY);
                town.stations.add(quarry); level.setBlockAndUpdate(quarry.position(),WWMC.STATIONS.get(StructureRole.QUARRY).get().defaultBlockState());
                var hospital=new Station(start.offset(-4,0,24),StructureRole.HOSPITAL);
                town.stations.add(hospital); level.setBlockAndUpdate(hospital.position(),WWMC.STATIONS.get(StructureRole.HOSPITAL).get().defaultBlockState());
                jobs=TownJobs.assess(level,town);
                int capacity=24+SettlementService.workerLimit(town,quarry);
                helper.assertTrue(jobs.places()==capacity,"A quarry adds its whole crew; an unfunded medic adds none: "+jobs);
                town.campaign.projects.add("hospital");
                helper.assertTrue(TownJobs.assess(level,town).places()==capacity+1,"Funding the medic must open exactly one place");
                citizens.forEach(CitizenEntity::discard); data.settlements.remove(town); data.setDirty();
                CitizenNavigationTests.release(level,start,chunks); helper.succeed();
            });
        });
    }

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

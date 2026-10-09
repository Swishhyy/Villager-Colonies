package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Physical appliances, late supplies, pickaxe replenishment and station upgrade drops in a loaded server world. */
public final class ProductionWorldTests {
    private record Fixture(ServerLevel level,BlockPos start,List<ChunkPos> chunks,Settlement town,List<CitizenEntity> workers) {
        CitizenEntity worker(Station station,BlockPos feet) {
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level); citizen.join(town.id);
            citizen.setPos(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5);
            town.citizens.add(citizen.getUUID()); town.jobs.assign(citizen.getUUID(),station.position());
            level.addFreshEntity(citizen); workers.add(citizen); return citizen;
        }
        void close() {
            workers.forEach(CitizenEntity::discard); SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
            CitizenNavigationTests.release(level,start,chunks);
        }
    }
    private static Fixture fixture(ServerLevel level,BlockPos start,Station... stations) {
        var chunks=CitizenNavigationTests.pinArea(level,start,-8,48,-14,14);
        CitizenNavigationTests.meadow(level,start,-8,48,-14,14);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Production regression",start.west(4),64,List.of(),List.of(stations),"balanced");
        SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
        level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
        for(var s:stations) level.setBlockAndUpdate(s.position(),WWMC.STATIONS.get(s.role()).get().defaultBlockState()
                .setValue(StationBlock.RANGE,s.range()).setValue(StationBlock.CREW,s.crew()).setValue(StationBlock.YIELD,s.yieldLevel()));
        return new Fixture(level,start,chunks,town,new ArrayList<>());
    }
    private static Container barrel(ServerLevel level,BlockPos pos,ItemStack... contents) {
        level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState()); Container barrel=(Container)level.getBlockEntity(pos);
        for(int i=0;i<contents.length;i++) barrel.setItem(i,contents[i]); return barrel;
    }
    private static int count(Container box,net.minecraft.world.item.Item item) { return InventoryOps.count(List.of(box),s -> s.is(item)); }
    private static String describe(CitizenEntity c) { return c.activity()+" at "+c.blockPosition()+", bag "+c.bag().contents(); }

    private static void takesRodAcrossFloor(DynamicTest test,int offset,Block floor,int approachY) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,offset));
            Station station=new Station(start.east(24),StructureRole.FISHERMAN);
            var f=fixture(level,start,station);
            for(int x=12;x<=32;x++) for(int z=-8;z<=8;z++) level.setBlockAndUpdate(start.offset(x,-1,z),floor.defaultBlockState());
            BlockPos depot=station.position().west(2);
            Container stock=barrel(level,depot,new ItemStack(Items.FISHING_ROD));
            var fisher=f.worker(station,start);
            helper.runAtTickTime(5,() -> {
                var path=fisher.getNavigation().createPath(depot.west(2).above(approachY),0);
                helper.assertTrue(path!=null && path.canReach(),"Fixture must have a walkable route to the barrel");
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(fisher.getMainHandItem().is(Items.FISHING_ROD),"Walkable barrel was rejected: "+describe(fisher));
                helper.assertTrue(count(stock,Items.FISHING_ROD)==0,"Taking a tool must not duplicate it");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="A fisherman walks over dirt paths to collect a real rod from its job barrel.")
    static void reachesBarrelAcrossDirtPaths(DynamicTest test) { takesRodAcrossFloor(test,-4600,Blocks.DIRT_PATH,0); }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="A fisherman walks over bottom slabs to collect a real rod from its job barrel.")
    static void reachesBarrelAcrossBottomSlabs(DynamicTest test) { takesRodAcrossFloor(test,-5000,Blocks.STONE_SLAB,0); }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="A fisherman walks over carpet to collect a real rod from its job barrel.")
    static void reachesBarrelAcrossCarpet(DynamicTest test) { takesRodAcrossFloor(test,-5400,BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_carpet")),-1); }

    @GameTest(timeoutTicks=4000)
    @EmptyTemplate
    @TestHolder(description="A miner, cook and smelter fetch real supplies and complete work over bottom slabs, including job barrels and appliance approaches.")
    static void productionJobsUseSlabFloors(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-5800));
            Station mine=new Station(start.offset(24,0,-8),StructureRole.MINE),cook=new Station(start.east(24),StructureRole.COOK),smelter=new Station(start.offset(24,0,8),StructureRole.SMELTERY);
            var f=fixture(level,start,mine,cook,smelter);
            for(int x=12;x<=32;x++) for(int z=-14;z<=14;z++) level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.STONE_SLAB.defaultBlockState());
            Container tools=barrel(level,mine.position().west(2),new ItemStack(Items.IRON_PICKAXE));
            Container kitchen=barrel(level,cook.position().west(2),new ItemStack(Items.BEEF,2),new ItemStack(Items.COAL));
            Container foundry=barrel(level,smelter.position().west(2),new ItemStack(Items.RAW_IRON,2),new ItemStack(Items.COAL));
            level.setBlockAndUpdate(mine.position().east(2),Blocks.COAL_ORE.defaultBlockState());
            level.setBlockAndUpdate(cook.position().east(2),Blocks.SMOKER.defaultBlockState());
            level.setBlockAndUpdate(smelter.position().east(2),Blocks.BLAST_FURNACE.defaultBlockState());
            var miner=f.worker(mine,start.north(8)); var chef=f.worker(cook,start); var smith=f.worker(smelter,start.south(8));
            for(var citizen:f.workers()) citizen.bag().offer(new ItemStack(Items.BREAD,2));
            helper.succeedWhen(() -> {
                helper.assertTrue(miner.getMainHandItem().is(Items.IRON_PICKAXE) && miner.getMainHandItem().getDamageValue()>0,
                        "Miner did not fetch its pickaxe and work: "+describe(miner));
                helper.assertTrue(count(kitchen,Items.COOKED_BEEF)==2 && count(foundry,Items.IRON_INGOT)==2,
                        "Slab production stalled: cook "+describe(chef)+"; smelter "+describe(smith));
                helper.assertTrue(count(tools,Items.IRON_PICKAXE)==0,"The miner's real pickaxe was duplicated");
                helper.assertTrue(count(kitchen,Items.BEEF)==0 && count(foundry,Items.RAW_IRON)==0,"Production inputs were duplicated");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=4000)
    @EmptyTemplate
    @TestHolder(description="One cook uses a raised ordinary furnace and one smelter uses a raised blast furnace, supplying real food, ore and fuel from raised job barrels without walking into either appliance.")
    static void cooksAndSmeltersUseRaisedAppliances(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-3200));
            Station cook=new Station(start.east(24),StructureRole.COOK),smelter=new Station(start.offset(24,0,8),StructureRole.SMELTERY);
            var f=fixture(level,start,cook,smelter);
            Container kitchen=barrel(level,cook.position().offset(-2,1,0),new ItemStack(Items.BEEF,4),new ItemStack(Items.COAL,2));
            Container foundry=barrel(level,smelter.position().offset(-2,1,0),new ItemStack(Items.RAW_IRON,4),new ItemStack(Items.COAL,2));
            BlockPos oven=cook.position().offset(2,2,0),blast=smelter.position().offset(2,2,0);
            for(BlockPos device:List.of(oven,blast)) {
                level.setBlockAndUpdate(device.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(device.below(2),Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(oven,Blocks.FURNACE.defaultBlockState()); level.setBlockAndUpdate(blast,Blocks.BLAST_FURNACE.defaultBlockState());
            var chef=f.worker(cook,start); var smith=f.worker(smelter,start.south(8));
            helper.runAtTickTime(5,() -> {
                for(var entry:Map.of(chef,oven,smith,blast).entrySet()) {
                    var path=entry.getKey().getNavigation().createPath(entry.getValue(),1);
                    helper.assertTrue(path==null || !path.canReach(),"Fixture must block walking into the raised appliance");
                }
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(count(kitchen,Items.COOKED_BEEF)==4 && count(foundry,Items.IRON_INGOT)==4,
                        "Appliances stalled: chef "+describe(chef)+"; smelter "+describe(smith));
                helper.assertTrue(count(kitchen,Items.BEEF)+chef.bag().count(Items.BEEF)==0,"Raw food duplicated");
                helper.assertTrue(count(foundry,Items.RAW_IRON)+smith.bag().count(Items.RAW_IRON)==0,"Raw ore duplicated");
                helper.assertTrue(((Container)level.getBlockEntity(oven)).getItem(0).isEmpty() && ((Container)level.getBlockEntity(blast)).getItem(0).isEmpty(),"Loaded inputs duplicated");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=3000)
    @EmptyTemplate
    @TestHolder(description="An inaccessible smoker does not block the reachable smoker, and its single cook resumes after ingredients and fuel arrive later in the job barrel.")
    static void blockedApplianceDoesNotStarveLateSupplies(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-3320));
            Station cook=new Station(start.east(24),StructureRole.COOK).withRange(3); var f=fixture(level,start,cook);
            Container stock=barrel(level,cook.position().north(4));
            BlockPos blocked=cook.position().west(3),usable=cook.position().east(3);
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                if(Math.abs(x)==1 || Math.abs(z)==1) for(int y=0;y<3;y++) level.setBlockAndUpdate(blocked.offset(x,y,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(blocked.offset(x,3,z),Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(blocked,Blocks.SMOKER.defaultBlockState()); level.setBlockAndUpdate(usable,Blocks.SMOKER.defaultBlockState());
            var chef=f.worker(cook,start);
            helper.runAtTickTime(300,() -> { stock.setItem(0,new ItemStack(Items.BEEF,4)); stock.setItem(1,new ItemStack(Items.COAL,2)); });
            helper.succeedWhen(() -> {
                helper.assertTrue(count(stock,Items.COOKED_BEEF)==4,"Cook never resumed at the accessible appliance: "+describe(chef));
                helper.assertTrue(((Container)level.getBlockEntity(blocked)).isEmpty(),"Cook reached through a sealed wall");
                helper.assertTrue(count(stock,Items.BEEF)+chef.bag().count(Items.BEEF)==0,"Food was duplicated");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=3000)
    @EmptyTemplate
    @TestHolder(description="A cook alternates bread batches with serving other appliances, collecting the smoker's four actual steaks before the wheat-driven bread target monopolizes the kitchen.")
    static void breadDoesNotMonopolizeOtherAppliances(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-3800));
            Station cook=new Station(start.east(24),StructureRole.COOK); var f=fixture(level,start,cook);
            Container stock=barrel(level,cook.position().south(2),new ItemStack(Items.WHEAT,64),new ItemStack(Items.WHEAT,32),new ItemStack(Items.COAL,4));
            level.setBlockAndUpdate(cook.position().west(2),Blocks.FURNACE.defaultBlockState());
            level.setBlockAndUpdate(cook.position().east(2),Blocks.SMOKER.defaultBlockState());
            var smoker=(AbstractFurnaceBlockEntity)level.getBlockEntity(cook.position().east(2));
            smoker.setItem(0,new ItemStack(Items.BEEF,4)); smoker.setItem(1,new ItemStack(Items.COAL));
            var chef=f.worker(cook,start);
            helper.succeedWhen(() -> {
                helper.assertTrue(count(stock,Items.COOKED_BEEF)==4,"Cook did not collect all actual smoker meals: "+describe(chef));
                helper.assertTrue(count(stock,Items.BREAD)>0 && count(stock,Items.BREAD)<32,"Bread monopolized the worker until its full target: "+count(stock,Items.BREAD));
                helper.assertTrue(count(stock,Items.WHEAT)+chef.bag().count(Items.WHEAT)+3*(count(stock,Items.BREAD)+chef.bag().count(Items.BREAD))==96,"Bread duplicated or destroyed its wheat");
                f.close();
            });
        });
    }

    @GameTest(timeoutTicks=2500)
    @EmptyTemplate
    @TestHolder(description="Actual miners retain ore veins, spend pickaxe durability, replenish at their equipped pickaxe's rate, and farm/mine yield upgrades survive real block loot and appear in the station screen.")
    static void minersUseEquippedPickaxeAndYieldSurvivesMoving(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-3440));
            Station mine=new Station(start.east(12),StructureRole.MINE).withYield(2),farm=new Station(start.east(32),StructureRole.FARM).withRange(1).withYield(3);
            var f=fixture(level,start,mine,farm); BlockPos ore=mine.position().east(2);
            level.setBlockAndUpdate(ore,Blocks.COAL_ORE.defaultBlockState()); barrel(level,mine.position().south(2));
            var miner=f.worker(mine,start); miner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE));
            for(Station station:List.of(mine,farm)) {
                var drops=Block.getDrops(level.getBlockState(station.position()),level,station.position(),null);
                helper.assertTrue(drops.size()==1,"Upgraded station dropped incorrectly"); var state=drops.getFirst().get(DataComponents.BLOCK_STATE);
                helper.assertTrue(state!=null && state.properties().get("yield").equals(Integer.toString(station.yieldLevel())),"Moving the station lost its yield level");
                helper.assertTrue(Panels.station(level,f.town(),station).actions().stream().anyMatch(a -> a.id()==Panels.YIELD_UP),"Missing yield control");
                helper.assertTrue(SettlementService.workerLimit(station)==1,"Yield bought extra workers");
            }
            helper.succeedWhen(() -> {
                helper.assertTrue(miner.getMainHandItem().getDamageValue()>0,"Miner has not worked: "+describe(miner));
                long remaining=OreVeins.readyAt(level,ore)-level.getGameTime();
                helper.assertTrue(remaining>0 && remaining<=OreVeins.interval(level.getBlockState(ore),Config.ORE_VEIN_SECONDS.get(),miner.getMainHandItem()),"Miner ignored equipped pickaxe replenishment");
                helper.assertTrue(remaining<OreVeins.interval(level.getBlockState(ore),Config.ORE_VEIN_SECONDS.get()),"Diamond pickaxe did not improve stone baseline");
                helper.assertTrue(level.getBlockState(ore).is(Blocks.COAL_ORE),"Endless vein was removed");
                helper.assertTrue(f.town().progress.milestones.contains("mining"),"Completed mining was not recorded");
                helper.assertTrue(miner.appearanceJob()==StructureRole.MINE,"Waiting miner lost its job appearance");
                f.close();
            });
        });
    }
}

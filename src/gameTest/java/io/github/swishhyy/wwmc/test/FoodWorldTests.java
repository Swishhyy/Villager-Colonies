package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Real job scheduling, animals, storage, couriers, butchery and vanilla cooking in a player-free world. */
public final class FoodWorldTests {
    private record Fixture(ServerLevel level,BlockPos start,List<ChunkPos> chunks,Settlement town,List<Entity> entities) {
        void close() {
            entities.forEach(Entity::discard);
            var data=SettlementData.get(level); data.settlements.remove(town); data.setDirty();
            CitizenNavigationTests.release(level,start,chunks);
        }
        CitizenEntity worker(Station station,BlockPos feet) {
            var worker=new CitizenEntity(WWMC.CITIZEN.get(),level);
            worker.join(town.id); worker.setPos(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5);
            town.citizens.add(worker.getUUID()); town.jobs.assign(worker.getUUID(),station.position());
            level.addFreshEntity(worker); entities.add(worker); return worker;
        }
        Animal cow(BlockPos feet,boolean baby,boolean noAi) {
            var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("cow"));
            var cow=(Animal)type.create(level,EntitySpawnReason.COMMAND);
            cow.setPos(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5); cow.setAge(baby ? -24000 : 0); cow.setNoAi(noAi);
            level.addFreshEntity(cow); entities.add(cow); return cow;
        }
    }
    private static Fixture fixture(ServerLevel level,BlockPos start,Station... stations) {
        var chunks=CitizenNavigationTests.pinArea(level,start,-8,88,-16,16);
        CitizenNavigationTests.meadow(level,start,-8,88,-16,16);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Food regression",start.west(4),96,List.of(),List.of(stations),"food");
        var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
        level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
        for(Station station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
        return new Fixture(level,start,chunks,town,new ArrayList<>());
    }
    private static Container barrel(ServerLevel level,BlockPos pos,ItemStack... contents) {
        level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());
        Container barrel=(Container)level.getBlockEntity(pos);
        for(int slot=0;slot<contents.length;slot++) barrel.setItem(slot,contents[slot]);
        return barrel;
    }
    private static int count(Container box,net.minecraft.world.item.Item item) { return InventoryOps.count(List.of(box),s -> s.is(item)); }

    @GameTest(timeoutTicks=8000)
    @EmptyTemplate
    @TestHolder(description="Actual hunting produces one carcass, couriers move every food stage and its tools, the butcher prepares it, and the cook makes four steaks without duplicate meat.")
    static void hunterCourierButcherCookChain(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-960));
            Station hunter=new Station(start.east(4),StructureRole.HUNTER),butcher=new Station(start.east(24),StructureRole.BUTCHER),
                    cook=new Station(start.east(40),StructureRole.COOK),courier=new Station(start.east(14),StructureRole.COURIER),
                    warehouse=new Station(start.east(60),StructureRole.WAREHOUSE);
            var fixture=fixture(level,start,hunter,butcher,cook,courier,warehouse);
            // Starter rations keep this production-chain test independent of the separate hunger-sharing test.
            Container pantry=barrel(level,warehouse.position().north(2),new ItemStack(Items.IRON_SWORD,1),new ItemStack(Items.IRON_AXE,1),new ItemStack(Items.COAL,8),new ItemStack(Items.BREAD,16));
            Container huntBarrel=barrel(level,hunter.position().south(2)),butcherBarrel=barrel(level,butcher.position().south(2)),cookBarrel=barrel(level,cook.position().south(2));
            level.setBlockAndUpdate(cook.position().east(),Blocks.SMOKER.defaultBlockState());
            var hunt=fixture.worker(hunter,hunter.position().west());
            var prepare=fixture.worker(butcher,butcher.position().west());
            var chef=fixture.worker(cook,cook.position().west());
            var haulA=fixture.worker(courier,courier.position().west());
            var haulB=fixture.worker(courier,courier.position().south());
            Animal game=fixture.cow(start.east(8),false,true),named=fixture.cow(start.offset(10,0,2),false,true);
            named.setCustomName(Component.literal("Protected cow"));
            helper.succeedWhen(() -> {
                String state="hunter="+describe(hunt,level)+", butcher="+describe(prepare,level)+", cook="+describe(chef,level)
                        +", couriers="+describe(haulA,level)+" / "+describe(haulB,level)
                        +", tools in warehouse="+count(pantry,Items.IRON_SWORD)+" sword / "+count(pantry,Items.IRON_AXE)+" axe";
                helper.assertTrue(count(pantry,Items.COOKED_BEEF)==4,"Food chain unfinished: "+state);
                helper.assertTrue(!game.isAlive() && named.isAlive(),"The hunt failed or killed a named animal");
                helper.assertTrue(count(pantry,Items.BEEF)+count(huntBarrel,Items.BEEF)+count(butcherBarrel,Items.BEEF)+count(cookBarrel,Items.BEEF)==0,"Duplicate raw meat survived the four cooked portions");
                helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(start).inflate(80),e -> Carcasses.meatDrop(e.getItem())).isEmpty(),"Vanilla meat dropped alongside the carcass");
                fixture.close();
            });
        });
    }

    @GameTest(timeoutTicks=2400)
    @EmptyTemplate
    @TestHolder(description="A fisherman uses a real rod on a dry bank beside deep water and puts whole fish in its job barrel without swimming.")
    static void fishesFromDryBank(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1060));
            Station station=new Station(start.east(4),StructureRole.FISHERMAN);
            var fixture=fixture(level,start,station); Container storage=barrel(level,station.position().south(2),new ItemStack(Items.FISHING_ROD));
            for(int x=6;x<=12;x++) for(int z=-4;z<=4;z++) {
                level.setBlockAndUpdate(start.offset(x,-3,z),Blocks.STONE.defaultBlockState());
                for(int y=-2;y<=-1;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.WATER.defaultBlockState());
            }
            var fisher=fixture.worker(station,station.position().west()); var wet=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(fisher.isInWater()) wet.set(true);
                int fish=count(storage,Carcasses.Kind.COD.stack().getItem())+count(storage,Carcasses.Kind.SALMON.stack().getItem());
                helper.assertTrue(fish>0,"Not fishing: "+describe(fisher,level)+", station="+level.getBlockState(station.position())
                        +", assigned="+fixture.town.jobs.home(fisher.getUUID())+", rod="+count(storage,Items.FISHING_ROD));
                helper.assertTrue(!wet.get(),"Fisherman entered the pond instead of using its bank");
                helper.assertTrue(count(storage,Items.COD)+count(storage,Items.SALMON)==0,"The catch bypassed butchery");
                fixture.close();
            });
        });
    }

    @GameTest(timeoutTicks=3600)
    @EmptyTemplate
    @TestHolder(description="A keeper harvests one of five surplus adults, keeps four breeders and the original baby alive, then feeds a real pair that produces another baby.")
    static void keepsBreedersAndBreedsRealAnimals(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1160));
            Station station=new Station(start.east(5),StructureRole.ANIMAL_KEEPER);
            var fixture=fixture(level,start,station); Container storage=barrel(level,station.position().south(2),new ItemStack(Items.IRON_SWORD),new ItemStack(Items.WHEAT,16));
            for(int x=1;x<=9;x++) for(int z=-4;z<=4;z++) if(x==1 || x==9 || z==-4 || z==4)
                level.setBlockAndUpdate(start.offset(x,0,z),Blocks.OAK_FENCE.defaultBlockState());
            var keeper=fixture.worker(station,station.position().west());
            for(int n=0;n<5;n++) fixture.cow(start.offset(3+n%3,0,-2+n/3),false,false);
            Animal baby=fixture.cow(start.offset(6,0,1),true,false);
            helper.succeedWhen(() -> {
                var herd=level.getEntitiesOfClass(Animal.class,new AABB(station.position()).inflate(4),a -> a.isAlive() && Carcasses.kind(a)==Carcasses.Kind.COW);
                helper.assertTrue(count(storage,Carcasses.Kind.COW.stack().getItem())==1,"Surplus not harvested: "+keeper.activity());
                helper.assertTrue(herd.stream().filter(a -> !a.isBaby()).count()==4,"Keeper took a breeder");
                helper.assertTrue(baby.isAlive() && baby.isBaby(),"Keeper harvested the original baby");
                helper.assertTrue(herd.stream().filter(Animal::isBaby).count()>=2,"Real breeding has not produced a new baby: "+keeper.activity());
                fixture.close();
            });
        });
    }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="Without a courier a butcher does not fetch a warehouse carcass, so supplies must arrive in its own job barrel.")
    static void noCourierMeansNoWarehouseHauling(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1260));
            Station butcher=new Station(start.east(4),StructureRole.BUTCHER),warehouse=new Station(start.east(32),StructureRole.WAREHOUSE);
            var fixture=fixture(level,start,butcher,warehouse);
            Container local=barrel(level,butcher.position().south(2),new ItemStack(Items.IRON_AXE));
            Container stock=barrel(level,warehouse.position().north(),Carcasses.Kind.COW.stack());
            var worker=fixture.worker(butcher,butcher.position().west());
            helper.runAtTickTime(600,() -> {
                helper.assertTrue(count(stock,Carcasses.Kind.COW.stack().getItem())==1,"Butcher took the warehouse carcass without a courier");
                helper.assertTrue(count(local,Items.BEEF)==0 && worker.bag().count(Items.BEEF)==0,"Butcher processed goods not delivered to its station");
                helper.assertTrue(worker.blockPosition().distSqr(warehouse.position())>100,"Butcher walked to the warehouse to fetch production supplies");
                fixture.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=1600)
    @EmptyTemplate
    @TestHolder(description="Ten hungry injured citizens share ten loaves: each eats one, none stockpiles spares or eats somebody else's share for healing.")
    static void tenCitizensShareTenLoaves(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1360));
            List<Station> stations=new ArrayList<>();
            for(int n=0;n<10;n++) stations.add(new Station(start.offset(n*8,0,8),StructureRole.FARM));
            Station warehouse=new Station(start.offset(40,0,-8),StructureRole.WAREHOUSE); stations.add(warehouse);
            var fixture=fixture(level,start,stations.toArray(Station[]::new));
            Container pantry=barrel(level,warehouse.position().north(),new ItemStack(Items.BREAD,10));
            List<CitizenEntity> citizens=new ArrayList<>();
            for(int n=0;n<10;n++) {
                barrel(level,stations.get(n).position().south(2));
                // Ten distinct clear spots; do not spawn citizens inside the warehouse or its barrel.
                var citizen=fixture.worker(stations.get(n),warehouse.position().offset(-2-n%2,0,-3+n/2));
                citizen.setHealth(1);
                try { var hunger=CitizenEntity.class.getDeclaredField("mealTicks"); hunger.setAccessible(true); hunger.setInt(citizen,0); }
                catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
                citizens.add(citizen);
            }
            helper.runAtTickTime(900,() -> {
                helper.assertTrue(count(pantry,Items.BREAD)==0,"Some hungry citizens never reached the communal pantry; loaves="+count(pantry,Items.BREAD)
                        +", citizens="+citizens.stream().map(c -> c.getHealth()+" hp, "+c.activity()+" at "+c.blockPosition()).toList());
                for(CitizenEntity citizen:citizens) {
                    helper.assertTrue(citizen.getHealth()==6,"A citizen did not get exactly one loaf: health="+citizen.getHealth()+", "+citizen.activity());
                    helper.assertTrue(citizen.bag().count(Items.BREAD)==0,"A citizen stockpiled scarce bread");
                }
                fixture.close(); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="An idle hungry worker eats from a raised warehouse by walking to clear ground within hand reach, without needing to climb onto its solid station.")
    static void eatsAtRaisedWarehouse(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1460));
            Station farm=new Station(start.east(4),StructureRole.FARM),warehouse=new Station(start.offset(40,2,-8),StructureRole.WAREHOUSE);
            var fixture=fixture(level,start,farm,warehouse);
            level.setBlockAndUpdate(warehouse.position().below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(warehouse.position().below(2),Blocks.STONE.defaultBlockState());
            Container pantry=barrel(level,warehouse.position().south(2),new ItemStack(Items.BREAD));
            var citizen=fixture.worker(farm,warehouse.position().offset(-7,-2,0));
            citizen.setHealth(1);
            try { var hunger=CitizenEntity.class.getDeclaredField("mealTicks"); hunger.setAccessible(true); hunger.setInt(citizen,0); }
            catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
            helper.succeedWhen(() -> {
                helper.assertTrue(count(pantry,Items.BREAD)==0,"Worker has not reached the raised pantry: "+describe(citizen,level));
                helper.assertTrue(citizen.getHealth()==6,"The real loaf did not heal the worker exactly once");
                helper.assertTrue(citizen.bag().count(Items.BREAD)==0,"The worker stockpiled its meal");
                helper.assertTrue(farm.position().equals(fixture.town.jobs.home(citizen.getUUID())),"The meal trip changed the worker's job");
                fixture.close();
            });
        });
    }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="Couriers equip a hunter and butcher from shared stock without giving the hunter the butcher's axe or hoarding another tool after both workers are equipped.")
    static void couriersShareToolsAcrossJobs(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1560));
            Station hunter=new Station(start.east(4),StructureRole.HUNTER),butcher=new Station(start.east(24),StructureRole.BUTCHER),
                    courier=new Station(start.east(14),StructureRole.COURIER),warehouse=new Station(start.east(60),StructureRole.WAREHOUSE);
            var fixture=fixture(level,start,hunter,butcher,courier,warehouse);
            Container pantry=barrel(level,warehouse.position().north(2),new ItemStack(Items.IRON_SWORD),new ItemStack(Items.IRON_AXE),new ItemStack(Items.IRON_AXE));
            Container hunting=barrel(level,hunter.position().south(2));
            barrel(level,butcher.position().south(2));
            var hunt=fixture.worker(hunter,hunter.position().west());
            var prepare=fixture.worker(butcher,butcher.position().west());
            var haul=fixture.worker(courier,courier.position().west());
            helper.runAtTickTime(900,() -> {
                helper.assertTrue(hunt.getMainHandItem().is(Items.IRON_SWORD),"The hunter did not receive its sword: "+describe(hunt,level));
                helper.assertTrue(prepare.getMainHandItem().is(Items.IRON_AXE),"The butcher did not receive its axe: "+describe(prepare,level)+", courier="+describe(haul,level));
                helper.assertTrue(count(pantry,Items.IRON_AXE)==1,"An equipped worker drew another job's spare axe out of shared stock");
                helper.assertTrue(count(hunting,Items.IRON_AXE)==0 && hunt.bag().count(Items.IRON_AXE)==0,"The hunter hoarded axes beside its equipped sword");
                fixture.close(); helper.succeed();
            });
        });
    }

    private static String describe(CitizenEntity citizen,ServerLevel level) {
        return citizen.activity()+" ("+citizen.jobRole()+", at "+citizen.blockPosition()+", ticks="+citizen.tickCount
                +", alive="+citizen.isAlive()+", ticking="+level.isPositionEntityTicking(citizen.blockPosition())
                +", town="+(citizen.town(level)!=null)+", hand="+citizen.getMainHandItem()+")";
    }
}

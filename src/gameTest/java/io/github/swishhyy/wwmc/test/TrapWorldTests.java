package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.TrapBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.TrapKind;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class TrapWorldTests {
    private static Settlement town(net.minecraft.server.level.ServerLevel level,BlockPos start,UUID owner) {
        var town=new Settlement(UUID.randomUUID(),owner,"Trap fixture",start,96,List.of(),List.of(),"balanced");
        SettlementData.get(level).settlements.add(town);
        level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
        return town;
    }
    private static void place(net.minecraft.server.level.ServerLevel level,BlockPos pos,TrapKind kind) {
        level.setBlockAndUpdate(pos,WWMC.TRAPS.get(kind).get().defaultBlockState());
        if(!TrapService.register(level,pos)) throw new AssertionError("Trap was not indexed");
    }
    private static Mob mob(net.minecraft.server.level.ServerLevel level,BlockPos pos,String id) {
        var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
        if(!(type.create(level,EntitySpawnReason.EVENT) instanceof Mob mob)) throw new AssertionError("Not a mob: "+id);
        mob.setNoAi(true); mob.setNoGravity(true);
        mob.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); level.addFreshEntity(mob); return mob;
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="All five traps activate against real monsters, spend one use, apply their effect, and respect cooldowns.")
    static void realHostileActivations(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-11800));
            var chunks=CitizenNavigationTests.pinTicking(level,start,3); CitizenNavigationTests.meadow(level,start,-8,36,-8,8);
            helper.runAtTickTime(10,() -> {
            var town=town(level,start,UUID.randomUUID()); town.progress.research.addAll(List.of("bronze_age","iron_age"));
            int x=4;
            for(TrapKind kind:TrapKind.values()) {
                BlockPos pos=start.east(x); x+=6; place(level,pos,kind);
                var zombie=mob(level,pos,"zombie"); float health=zombie.getHealth();
                TrapService.tickTown(level,town);
                var state=level.getBlockState(pos);
                helper.assertTrue(state.getValue(TrapBlock.WEAR)==1 && !state.getValue(TrapBlock.ARMED),kind+" did not spend one activation");
                helper.assertTrue(kind.damage==0 ? zombie.getHealth()==health : zombie.getHealth()<health,kind+" damage is wrong");
                helper.assertTrue(zombie.hasEffect(MobEffects.SLOWNESS),kind+" did not slow the attacker");
                TrapService.tickTown(level,town);
                helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==1,kind+" triggered again during its cooldown");
                if(!kind.manual && kind!=TrapKind.IRON_SPRING_TRAP) {
                    int i=town.progress.traps.size()-1;
                    town.progress.traps.set(i,new TrapService.Entry(pos,level.getGameTime()-1)); zombie.discard();
                    TrapService.tickTown(level,town);
                    helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.ARMED),kind+" did not reset after its saved cooldown");
                } else zombie.discard();
            }
            helper.assertTrue(town.progress.traps.getLast().readyAt()>level.getGameTime(),"Active spring cooldown was not retained");
            var saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
            helper.assertTrue(saved.progress.traps.equals(town.progress.traps),"Trap positions or cooldowns changed in a save roundtrip");
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Citizens, players, animals and a calm neutral monster cross an armed trap without damage or durability use.")
    static void friendlyTrafficIsSafe(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-12000));
            var chunks=CitizenNavigationTests.pinTicking(level,start,3); CitizenNavigationTests.meadow(level,start,-8,16,-8,8);
            helper.runAtTickTime(10,() -> {
            var town=town(level,start,UUID.randomUUID()); BlockPos pos=start.east(5); place(level,pos,TrapKind.WOODEN_SPIKES);
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level); citizen.join(town.id); citizen.setNoAi(true);
            citizen.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); level.addFreshEntity(citizen);
            var cow=mob(level,pos,"cow"); var wolf=mob(level,pos,"wolf"); var neutral=mob(level,pos,"enderman");
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"TrapVisitor")); player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            for(var entity:List.of(citizen,cow,wolf,neutral,player)) helper.assertTrue(!TrapService.target(entity),"Friendly or neutral entity classified as a trap target");
            TrapService.tickTown(level,town);
            helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==0 && level.getBlockState(pos).getValue(TrapBlock.ARMED),"Friendly traffic spent the trap");
            for(var entity:List.of(citizen,cow,wolf,neutral)) { helper.assertTrue(entity.getHealth()==entity.getMaxHealth(),"Friendly traffic was hurt"); entity.discard(); }
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Real block drops preserve worn defenses when placed again, repair costs are exact, and rearming never restores snare durability.")
    static void savedWearAndPaidMaintenance(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-12200));
            var chunks=CitizenNavigationTests.pinTicking(level,start,3); CitizenNavigationTests.meadow(level,start,-8,20,-8,8);
            helper.runAtTickTime(10,() -> {
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"TrapBuilder"));
            var town=town(level,start,player.getUUID()); BlockPos pos=start.east(5);
            place(level,pos,TrapKind.WOODEN_SPIKES);
            level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(TrapBlock.WEAR,12).setValue(TrapBlock.ARMED,false));
            var drops=Block.getDrops(level.getBlockState(pos),level,pos,null);
            helper.assertTrue(drops.size()==1 && drops.getFirst().get(DataComponents.BLOCK_STATE).properties().get("wear").equals("12"),"Block drop lost its wear");
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()); TrapService.tickTown(level,town);
            ItemStack held=drops.getFirst(); player.setItemInHand(InteractionHand.MAIN_HAND,held);
            ((BlockItem)held.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,held,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false)));
            helper.assertTrue(level.getBlockState(pos).getBlock() instanceof TrapBlock && level.getBlockState(pos).getValue(TrapBlock.WEAR)==12,
                    "Breaking and replacing repaired a trap");
            var stock=new SimpleContainer(3); stock.setItem(0,new ItemStack(Items.SPRUCE_PLANKS,2));
            helper.assertTrue(TrapService.maintain(level,town,pos,List.of(stock)),"Any plank species should repair spikes");
            helper.assertTrue(stock.getItem(0).getCount()==1 && level.getBlockState(pos).getValue(TrapBlock.WEAR)==0,"Spike repair spent the wrong amount");
            helper.assertTrue(!TrapService.maintain(level,town,pos,List.of(stock)) && stock.getItem(0).getCount()==1,"Ready trap spent materials twice");
            town.progress.research.add("bronze_age"); BlockPos snare=start.east(10); place(level,snare,TrapKind.BRONZE_SNARE);
            level.setBlockAndUpdate(snare,level.getBlockState(snare).setValue(TrapBlock.WEAR,2).setValue(TrapBlock.ARMED,false));
            stock.setItem(1,new ItemStack(Items.STRING,2));
            helper.assertTrue(TrapService.maintain(level,town,snare,List.of(stock)) && stock.getItem(1).getCount()==1
                    && level.getBlockState(snare).getValue(TrapBlock.WEAR)==2,"Rearming must retain existing snare wear");
            level.setBlockAndUpdate(snare,level.getBlockState(snare).setValue(TrapBlock.WEAR,6).setValue(TrapBlock.ARMED,false));
            helper.assertTrue(!TrapService.maintain(level,town,snare,List.of(stock)),"String repaired a broken bronze mechanism");
            stock.setItem(2,new ItemStack(WWMC.BRONZE_INGOT.get()));
            helper.assertTrue(TrapService.maintain(level,town,snare,List.of(stock)) && stock.getItem(2).isEmpty(),"Bronze repair did not spend an ingot");
            var legacy=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow().getAsJsonObject();
            legacy.getAsJsonObject("progress").remove("traps");
            helper.assertTrue(Settlement.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().progress.traps.isEmpty(),"Old settlements must load without trap data");
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=100) @EmptyTemplate
    @TestHolder(description="Trap research tags gate real shift-click crafting and prevent a placed bronze defense from firing before its town unlocks it.")
    static void ageLocksApplyToRecipesAndBlocks(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-12400));
            var chunks=CitizenNavigationTests.pinTicking(level,start,3); CitizenNavigationTests.meadow(level,start,-8,16,-8,8);
            helper.runAtTickTime(10,() -> {
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"TrapResearcher")); var town=town(level,start,player.getUUID());
            for(var kind:TrapKind.values()) helper.assertTrue(AgeProgression.required(new ItemStack(WWMC.TRAP_ITEMS.get(kind).get()))==kind.age,"Wrong age for "+kind);
            var menu=new CraftingMenu(2,player.getInventory(),ContainerLevelAccess.create(level,start));
            for(int slot:new int[]{2,4,5,6}) menu.getSlot(slot).setByPlayer(new ItemStack(WWMC.BRONZE_INGOT.get()));
            menu.slotsChanged(menu.getSlot(2).container);
            helper.assertTrue(menu.getSlot(0).getItem().is(WWMC.TRAP_ITEMS.get(TrapKind.BRONZE_CALTROPS).get()),"Caltrop recipe did not load");
            menu.clicked(0,0,ContainerInput.QUICK_MOVE,player);
            helper.assertTrue(!menu.getSlot(2).getItem().isEmpty() && player.getInventory().countItem(WWMC.TRAP_ITEMS.get(TrapKind.BRONZE_CALTROPS).get())==0,
                    "Locked trap recipe spent inputs or produced an item");
            BlockPos pos=start.east(6); place(level,pos,TrapKind.BRONZE_SNARE); var zombie=mob(level,pos,"zombie");
            TrapService.tickTown(level,town); helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==0,"Locked trap activated");
            town.progress.research.add("bronze_age"); TrapService.tickTown(level,town);
            helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==1,"Research did not enable the trap");
            menu.clicked(0,0,ContainerInput.QUICK_MOVE,player);
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && player.getInventory().countItem(WWMC.TRAP_ITEMS.get(TrapKind.BRONZE_CALTROPS).get())==4,
                    "Unlocked recipe did not craft the promised four caltrops");
            zombie.discard(); SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=650) @EmptyTemplate
    @TestHolder(description="A real craftsman waits through an alarm, collects repair supplies, walks to a worn trap and restores it without remote repairs.")
    static void craftsmanMaintainsAfterCombat(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-12600));
            var chunks=CitizenNavigationTests.pinTicking(level,start,2); CitizenNavigationTests.meadow(level,start,-16,36,-12,12);
            var town=town(level,start,UUID.randomUUID()); Station craft=new Station(start.east(6),StructureRole.CRAFTSMAN);
            Station warehouse=new Station(start.west(7),StructureRole.WAREHOUSE); town.stations.addAll(List.of(craft,warehouse));
            for(var station:town.stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
            BlockPos barrel=craft.position().south(2); level.setBlockAndUpdate(barrel,Blocks.BARREL.defaultBlockState());
            Container supplies=(Container)level.getBlockEntity(barrel); supplies.setItem(0,new ItemStack(Items.OAK_PLANKS,2));
            level.setBlockAndUpdate(warehouse.position().north(2),Blocks.CHEST.defaultBlockState());
            BlockPos pos=start.east(24); place(level,pos,TrapKind.WOODEN_SPIKES);
            level.setBlockAndUpdate(pos,level.getBlockState(pos).setValue(TrapBlock.WEAR,12).setValue(TrapBlock.ARMED,false));
            var worker=new CitizenEntity(WWMC.CITIZEN.get(),level); worker.join(town.id); worker.setNoAi(true);
            worker.setPos(craft.position().getX()+.5,craft.position().getY(),craft.position().getZ()+1.5);
            worker.bag().offer(new ItemStack(Items.BREAD)); town.citizens.add(worker.getUUID()); town.jobs.assign(worker.getUUID(),craft.position()); level.addFreshEntity(worker);
            DefenseService.waveAlarm(level,town);
            helper.runAtTickTime(80,() -> {
                helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==12 && supplies.getItem(0).getCount()==2,"Trap repaired without a working citizen");
                DefenseService.toggle(level,town); worker.setNoAi(false);
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(level.getBlockState(pos).getValue(TrapBlock.WEAR)==0 && level.getBlockState(pos).getValue(TrapBlock.ARMED),"Craftsman did not repair: "+worker.activity());
                helper.assertTrue(worker.blockPosition().distSqr(pos)<25,"Craftsman repaired remotely");
                helper.assertTrue(InventoryOps.count(List.of(supplies,worker.bag()),s -> s.is(Items.OAK_PLANKS))==1,"Repair did not consume exactly one plank");
                worker.discard(); SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=150) @EmptyTemplate
    @TestHolder(description="Wave arrivals stay outside the station and trap perimeter on natural ticking ground, reject built roofs, and postpone when every approach is blocked.")
    static void wavesUseTheDefensePerimeter(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-13000));
            var chunks=CitizenNavigationTests.pinTicking(level,start,6); CitizenNavigationTests.meadow(level,start,-80,80,-80,80);
            helper.runAtTickTime(10,() -> {
            var town=town(level,start,UUID.randomUUID()); town.radius=80;
            Station guard=new Station(start.west(62),StructureRole.GUARD); town.stations.add(guard);
            level.setBlockAndUpdate(guard.position(),WWMC.STATIONS.get(guard.role()).get().defaultBlockState());
            place(level,start.east(60),TrapKind.WOODEN_SPIKES);
            var bounds=WaveService.perimeter(level,town);
            helper.assertTrue(bounds.contains(start.getX()+60,start.getZ()) && bounds.contains(start.getX()-62,start.getZ()),"Defense footprint omitted a post or trap");
            int found=0;
            for(int i=0;i<32;i++) {
                BlockPos site=WaveService.arrivalSite(level,town,null); if(site==null) continue; found++;
                helper.assertTrue(!bounds.contains(site.getX(),site.getZ()) && town.contains(site) && level.isPositionEntityTicking(site),"Wave bypassed the perimeter or forced unloaded terrain");
                helper.assertTrue(level.getBlockState(site.below()).is(Blocks.GRASS_BLOCK),"Wave chose constructed or unsafe ground");
            }
            helper.assertTrue(found>0,"No valid perimeter approach found");
            // All heights now end on a built roof: do not fall back into the village to make a wave happen.
            for(int x=-80;x<=80;x++) for(int z=-80;z<=80;z++) if(!bounds.contains(start.getX()+x,start.getZ()+z))
                level.setBlockAndUpdate(start.offset(x,1,z),Blocks.OAK_PLANKS.defaultBlockState());
            helper.assertTrue(WaveService.arrivalSite(level,town,null)==null,"Unsafe approaches caused a spawn on a roof or inside defenses");
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.releaseTicking(level,start,chunks); helper.succeed();
            });
        });
    }
}

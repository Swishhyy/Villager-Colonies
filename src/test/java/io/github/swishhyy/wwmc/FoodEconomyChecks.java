package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.entity.FuelValues;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

public final class FoodEconomyChecks {
    @Test void tenMealsCannotBeClaimedByTheFirstOfTenHungryCitizens() {
        Map<UUID,Long> hungry=new LinkedHashMap<>();
        for(int n=0;n<10;n++) hungry.put(new UUID(0,n),0L);
        assertEquals(0,FoodSharing.spareLimit(10,10));
        int bread=10; long time=100;
        for(UUID citizen:hungry.keySet()) {
            assertTrue(FoodSharing.mayTake(citizen,hungry.get(citizen),hungry,bread,10));
            hungry.put(citizen,time++); bread--;
            if(bread>0) assertFalse(FoodSharing.mayTake(citizen,hungry.get(citizen),hungry,bread,10),"A second meal waits for hungry people not yet fed");
        }
        assertEquals(0,bread);
        assertFalse(FoodSharing.mayTake(hungry.keySet().iterator().next(),100,hungry,0,10));
        assertEquals(1,FoodSharing.spareLimit(20,10));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void carcassesAreNotMealsAndPreparingThemConservesFinitePortions(MinecraftServer server) {
        for(var kind:Carcasses.Kind.values()) {
            CitizenInventory bag=new CitizenInventory(p -> false);
            ItemStack stack=kind.stack(); stack.setCount(3); bag.offer(stack);
            assertFalse(FoodHealing.food(stack));
            assertFalse(ProcessingService.ingredient(StructureRole.COOK,stack));
            assertEquals(kind.portions,Carcasses.prepare(bag));
            assertEquals(2,bag.count(stack.getItem())); assertEquals(kind.portions,bag.count(kind.meat));
        }
        CitizenInventory full=new CitizenInventory(p -> false);
        for(int slot=0;slot<CitizenInventory.SIZE;slot++) full.setItem(slot,new ItemStack(Items.STONE,64));
        ItemStack carcass=Carcasses.Kind.COW.stack(); carcass.setCount(2); full.setItem(0,carcass);
        assertEquals(4,Carcasses.prepare(full));
        assertEquals(1,full.count(carcass.getItem())); assertEquals(4,full.count(Items.BEEF));
        assertTrue(full.hasPending(),"Portions from a full bag stay in its saved overflow");
        assertEquals(4,Carcasses.prepare(full)); assertEquals(8,full.count(Items.BEEF));
        assertEquals(0,Carcasses.prepare(full));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void couriersKeepInputsButCollectTheNextFoodStage(MinecraftServer server) {
        var supplies=new JobStorage.Supplies(FuelValues.vanillaBurnTimes(server.registryAccess(),FeatureFlags.DEFAULT_FLAGS,200),server.getRecipeManager(),null);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Food",BlockPos.ZERO,240,List.of(),List.of(),"food");
        SimpleContainer hunter=new SimpleContainer(9);
        hunter.setItem(0,new ItemStack(Items.IRON_SWORD)); hunter.setItem(1,Carcasses.Kind.COW.stack());
        assertEquals(1,JobStorage.goods(JobStorage.collectable(supplies,town,StructureRole.HUNTER,List.of(hunter))));
        SimpleContainer butcher=new SimpleContainer(9);
        butcher.setItem(0,Carcasses.Kind.COW.stack()); butcher.setItem(1,new ItemStack(Items.IRON_AXE)); butcher.setItem(2,new ItemStack(Items.BEEF,4));
        assertEquals(4,JobStorage.goods(JobStorage.collectable(supplies,town,StructureRole.BUTCHER,List.of(butcher))));
        assertTrue(JobStorage.food(JobStorage.collectable(supplies,town,StructureRole.BUTCHER,List.of(butcher))));
        SimpleContainer warehouse=new SimpleContainer(9); warehouse.setItem(0,Carcasses.Kind.PIG.stack()); warehouse.setItem(1,new ItemStack(Items.IRON_AXE));
        SimpleContainer empty=new SimpleContainer(9); CitizenInventory courier=new CitizenInventory(p -> false);
        assertTrue(JobStorage.needsSupplies(supplies,town,StructureRole.BUTCHER,List.of(empty),List.of(warehouse)));
        assertEquals(2,JobStorage.load(supplies,town,StructureRole.BUTCHER,List.of(empty),List.of(warehouse),courier));
        assertEquals(1,courier.count(Carcasses.Kind.PIG.stack().getItem())); assertEquals(1,courier.count(Items.IRON_AXE));
        assertTrue(JobStorage.input(supplies,town,StructureRole.FISHERMAN,new ItemStack(Items.FISHING_ROD)));
        assertFalse(JobStorage.input(supplies,town,StructureRole.COOK,Carcasses.Kind.COD.stack()));
        assertTrue(JobStorage.input(supplies,town,StructureRole.COOK,new ItemStack(Items.COD)));
        ItemStack worn=new ItemStack(Items.IRON_PICKAXE); worn.setDamageValue(worn.getMaxDamage()-1);
        assertFalse(JobStorage.input(supplies,town,StructureRole.MINE,worn));
    }
    @Test void minersStaySoloAndKeepersPreserveTheirBreedingGroup() {
        Station mine=new Station(BlockPos.ZERO,StructureRole.MINE,Direction.NORTH,3,3);
        assertEquals(0,mine.crew()); assertFalse(Upgrades.hires(StructureRole.MINE)); assertTrue(Upgrades.soloCrew(StructureRole.MINE));
        assertFalse(AnimalWork.cullable(4,4,false,false,false));
        assertTrue(AnimalWork.cullable(5,4,false,false,false));
        assertFalse(AnimalWork.cullable(5,4,true,false,false));
        assertFalse(AnimalWork.cullable(5,4,false,true,false));
        assertFalse(AnimalWork.cullable(5,4,false,false,true));
        for(StructureRole role:List.of(StructureRole.HUNTER,StructureRole.FISHERMAN,StructureRole.BUTCHER,StructureRole.ANIMAL_KEEPER)) {
            assertEquals(JobBoard.HIGH,JobBoard.preset("food").level(role));
            assertEquals(JobBoard.LOW,JobBoard.preset("materials").level(role));
        }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void excessPersonalFoodIsReturnedInsteadOfAnEightMealReserve(MinecraftServer server) {
        CitizenInventory bag=new CitizenInventory(p -> false); bag.offer(new ItemStack(Items.BREAD));
        assertFalse(bag.hasDeliverable(s -> false,FoodHealing::food));
        bag.offer(new ItemStack(Items.BREAD)); assertTrue(bag.hasDeliverable(s -> false,FoodHealing::food));
        SimpleContainer pantry=new SimpleContainer(9);
        bag.deposit(List.of(pantry),s -> FoodSharing.PERSONAL_LIMIT);
        assertEquals(1,bag.count(Items.BREAD)); assertEquals(1,InventoryOps.count(List.of(pantry),s -> s.is(Items.BREAD)));
        assertEquals(600,FoodHealing.COOLDOWN);
    }
}

package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FuelValues;

/**
 * What stays in a job's barrels and what couriers carry. A job keeps its tools and supplies (with a small reserve of
 * floor blocks and saplings); everything it produced is collected for the warehouse. Smelters and cooks also get
 * their barrels filled with a load of ingredients and fuel.
 */
public final class JobStorage {
    public static final int SUPPORT_RESERVE=16,SAPLING_RESERVE=32,FUEL_RESERVE=8,WHEAT_RESERVE=9;
    /** A courier sets out once this many goods wait, or sooner when a barrel is nearly full or the pantry is low. */
    public static final int COLLECT_LOAD=32,PANTRY_LOW=16;
    public record Pickup(Container container,int slot,int amount) {}
    /** Fuel burn times and cooking recipes that decide a job's inputs. The level is only passed on to recipe checks. */
    public record Supplies(FuelValues fuels,RecipeManager recipes,Level level) {
        public static Supplies of(ServerLevel level) { return new Supplies(level.fuelValues(),level.getServer().getRecipeManager(),level); }
        public boolean fuel(ItemStack stack) { return ProcessingService.fuel(fuels,stack); }
        /** Something the job's appliance can smelt or cook. */
        public boolean ingredient(StructureRole role,ItemStack stack) {
            if(!ProcessingService.ingredient(role,stack)) return false;
            SingleRecipeInput input=new SingleRecipeInput(stack);
            return role==StructureRole.SMELTERY ? recipes.getRecipeFor(RecipeType.SMELTING,input,level).isPresent()
                    : recipes.getRecipeFor(RecipeType.SMOKING,input,level).isPresent();
        }
    }
    private JobStorage() {}
    private static boolean tool(StructureRole role,ItemStack stack) {
        return role==StructureRole.LUMBER && stack.is(ItemTags.AXES) || role.excavates() && stack.is(ItemTags.PICKAXES);
    }
    /** Inputs the job takes from its own barrels; a craftsman's barrels hold materials, so only finished orders leave. */
    private static boolean supply(Supplies supplies,Settlement town,StructureRole role,ItemStack stack) {
        if(role.processes()) return ProcessingService.supply(supplies.fuels(),role,stack);
        return role==StructureRole.CRAFTSMAN && !Workshop.product(town,stack);
    }
    /** Goods a courier may take from these barrels, leaving the job's tools, supplies and reserves. */
    public static List<Pickup> collectable(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels) {
        int support=SUPPORT_RESERVE,saplings=SAPLING_RESERVE;
        List<Pickup> result=new ArrayList<>();
        for(Container barrel:barrels) for(int slot=0;slot<barrel.getContainerSize();slot++) {
            ItemStack stack=barrel.getItem(slot);
            // Worn tools still leave, so blacksmiths in the warehouse can repair them.
            if(stack.isEmpty() || tool(role,stack) && !GuardEquipment.worn(stack) || supply(supplies,town,role,stack)) continue;
            int keep=0;
            if(role.excavates() && ExcavationService.supportMaterial(stack)) { keep=Math.min(support,stack.getCount()); support-=keep; }
            else if(role==StructureRole.LUMBER && stack.is(ItemTags.SAPLINGS)) { keep=Math.min(saplings,stack.getCount()); saplings-=keep; }
            if(stack.getCount()>keep) result.add(new Pickup(barrel,slot,stack.getCount()-keep));
        }
        return result;
    }
    public static int goods(List<Pickup> pickups) { return pickups.stream().mapToInt(Pickup::amount).sum(); }
    /** Meals waiting among the goods; couriers fetch these early when the warehouse pantry runs low. */
    public static boolean food(List<Pickup> pickups) {
        return pickups.stream().anyMatch(pickup -> FoodHealing.food(pickup.container().getItem(pickup.slot())));
    }
    /** A courier sets out for a worthwhile load, a nearly full barrel, or any food while the pantry is low. */
    public static boolean worthCollecting(List<Pickup> pickups,int freeSlots,int pantry) {
        int goods=goods(pickups);
        return goods>=COLLECT_LOAD || goods>0 && (freeSlots<=2 || pantry<PANTRY_LOW && food(pickups));
    }
    /** Move collectable goods into the courier's bag until it would need to deliver; returns the items moved. */
    public static int collect(Supplies supplies,Settlement town,StructureRole role,List<Container> barrels,CitizenInventory bag) {
        int moved=0;
        for(Pickup pickup:collectable(supplies,town,role,barrels)) {
            if(bag.needsDelivery()) break;
            ItemStack taken=pickup.container().removeItem(pickup.slot(),pickup.amount());
            moved+=taken.getCount(); bag.offer(taken); pickup.container().setChanged();
        }
        return moved;
    }
    public static int freeSlots(List<Container> containers) {
        int free=0;
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) if(container.getItem(slot).isEmpty()) free++;
        return free;
    }
    /** Something a smeltery or kitchen barrel is stocked with: smeltable or cookable ingredients, fuel, and wheat for bread. */
    public static boolean input(Supplies supplies,StructureRole role,ItemStack stack) {
        if(!role.processes() || stack.isEmpty()) return false;
        return supplies.fuel(stack) || role==StructureRole.COOK && stack.is(Items.WHEAT) || supplies.ingredient(role,stack);
    }
    private static Predicate<ItemStack> ingredient(Supplies supplies,StructureRole role) {
        return s -> !supplies.fuel(s) && !s.is(Items.WHEAT) && supplies.ingredient(role,s);
    }
    /** Inputs a processing job's barrels are short of that the warehouse can supply. */
    public static boolean needsSupplies(Supplies supplies,StructureRole role,List<Container> barrels,List<Container> warehouse) {
        if(!role.processes()) return false;
        Predicate<ItemStack> ingredients=ingredient(supplies,role),fuel=supplies::fuel,wheat=s -> s.is(Items.WHEAT);
        return InventoryOps.count(barrels,ingredients)<ProcessingService.INPUT_LOAD && InventoryOps.count(warehouse,ingredients)>0
                || InventoryOps.count(barrels,fuel)<FUEL_RESERVE && InventoryOps.count(warehouse,fuel)>0
                || role==StructureRole.COOK && InventoryOps.count(barrels,wheat)<WHEAT_RESERVE && InventoryOps.count(warehouse,wheat)>=3;
    }
    /** Load the courier's bag with what the barrels are short of; returns the items taken from the warehouse. */
    public static int load(Supplies supplies,StructureRole role,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) {
        Predicate<ItemStack> ingredients=ingredient(supplies,role),fuel=supplies::fuel,wheat=s -> s.is(Items.WHEAT);
        int moved=carry(warehouse,bag,ingredients,ProcessingService.INPUT_LOAD*2-InventoryOps.count(barrels,ingredients));
        moved+=carry(warehouse,bag,fuel,FUEL_RESERVE*2-InventoryOps.count(barrels,fuel));
        if(role==StructureRole.COOK) moved+=carry(warehouse,bag,wheat,WHEAT_RESERVE*2-InventoryOps.count(barrels,wheat));
        return moved;
    }
    private static int carry(List<Container> sources,CitizenInventory bag,Predicate<ItemStack> eligible,int maximum) {
        int moved=0;
        while(moved<maximum && !bag.needsDelivery()) {
            ItemStack next=InventoryOps.takeOne(sources,eligible);
            if(next.isEmpty()) break;
            bag.offer(next); moved++;
        }
        return moved;
    }
}

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
import net.minecraft.world.item.crafting.RecipeType;

/**
 * What stays in a job's barrels and what couriers carry. A job keeps its tools and supplies (with a small reserve of
 * floor blocks and saplings); everything it produced is collected for the warehouse. Smelters and cooks also get
 * their barrels filled with a load of ingredients and fuel.
 */
public final class JobStorage {
    public static final int SUPPORT_RESERVE=16,SAPLING_RESERVE=32,FUEL_RESERVE=8,WHEAT_RESERVE=9;
    /** A courier sets out once this many goods wait, or sooner when a barrel is nearly full. */
    public static final int COLLECT_LOAD=32;
    public record Pickup(Container container,int slot,int amount) {}
    private JobStorage() {}
    private static boolean tool(StructureRole role,ItemStack stack) {
        return role==StructureRole.LUMBER && stack.is(ItemTags.AXES) || role.excavates() && stack.is(ItemTags.PICKAXES);
    }
    /** Inputs the job takes from its own barrels; a craftsman's barrels hold materials, so only finished orders leave. */
    private static boolean supply(ServerLevel level,Settlement town,StructureRole role,ItemStack stack) {
        if(role.processes()) return ProcessingService.supply(level,role,stack);
        return role==StructureRole.CRAFTSMAN && !Workshop.product(town,stack);
    }
    /** Goods a courier may take from these barrels, leaving the job's tools, supplies and reserves. */
    public static List<Pickup> collectable(ServerLevel level,Settlement town,StructureRole role,List<Container> barrels) {
        int support=SUPPORT_RESERVE,saplings=SAPLING_RESERVE;
        List<Pickup> result=new ArrayList<>();
        for(Container barrel:barrels) for(int slot=0;slot<barrel.getContainerSize();slot++) {
            ItemStack stack=barrel.getItem(slot);
            // Worn tools still leave, so blacksmiths in the warehouse can repair them.
            if(stack.isEmpty() || tool(role,stack) && !GuardEquipment.worn(stack) || supply(level,town,role,stack)) continue;
            int keep=0;
            if(role.excavates() && ExcavationService.supportMaterial(stack)) { keep=Math.min(support,stack.getCount()); support-=keep; }
            else if(role==StructureRole.LUMBER && stack.is(ItemTags.SAPLINGS)) { keep=Math.min(saplings,stack.getCount()); saplings-=keep; }
            if(stack.getCount()>keep) result.add(new Pickup(barrel,slot,stack.getCount()-keep));
        }
        return result;
    }
    public static int goods(List<Pickup> pickups) { return pickups.stream().mapToInt(Pickup::amount).sum(); }
    /** Move collectable goods into the courier's bag until it would need to deliver; returns the items moved. */
    public static int collect(ServerLevel level,Settlement town,StructureRole role,List<Container> barrels,CitizenInventory bag) {
        int moved=0;
        for(Pickup pickup:collectable(level,town,role,barrels)) {
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
    public static boolean input(ServerLevel level,StructureRole role,ItemStack stack) {
        if(!role.processes() || stack.isEmpty()) return false;
        if(ProcessingService.fuel(level,stack) || role==StructureRole.COOK && stack.is(Items.WHEAT)) return true;
        return ProcessingService.input(level,role,role==StructureRole.SMELTERY ? RecipeType.SMELTING : RecipeType.SMOKING,stack);
    }
    private static Predicate<ItemStack> ingredient(ServerLevel level,StructureRole role) {
        return s -> input(level,role,s) && !ProcessingService.fuel(level,s) && !s.is(Items.WHEAT);
    }
    /** Inputs a processing job's barrels are short of that the warehouse can supply. */
    public static boolean needsSupplies(ServerLevel level,StructureRole role,List<Container> barrels,List<Container> warehouse) {
        if(!role.processes()) return false;
        Predicate<ItemStack> ingredients=ingredient(level,role),fuel=s -> ProcessingService.fuel(level,s),wheat=s -> s.is(Items.WHEAT);
        return InventoryOps.count(barrels,ingredients)<ProcessingService.INPUT_LOAD && InventoryOps.count(warehouse,ingredients)>0
                || InventoryOps.count(barrels,fuel)<FUEL_RESERVE && InventoryOps.count(warehouse,fuel)>0
                || role==StructureRole.COOK && InventoryOps.count(barrels,wheat)<WHEAT_RESERVE && InventoryOps.count(warehouse,wheat)>=3;
    }
    /** Load the courier's bag with what the barrels are short of; returns the items taken from the warehouse. */
    public static int load(ServerLevel level,StructureRole role,List<Container> barrels,List<Container> warehouse,CitizenInventory bag) {
        int moved=0;
        moved+=carry(warehouse,bag,ingredient(level,role),ProcessingService.INPUT_LOAD*2-InventoryOps.count(barrels,ingredient(level,role)));
        moved+=carry(warehouse,bag,s -> ProcessingService.fuel(level,s),FUEL_RESERVE*2-InventoryOps.count(barrels,s -> ProcessingService.fuel(level,s)));
        if(role==StructureRole.COOK) moved+=carry(warehouse,bag,s -> s.is(Items.WHEAT),WHEAT_RESERVE*2-InventoryOps.count(barrels,s -> s.is(Items.WHEAT)));
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

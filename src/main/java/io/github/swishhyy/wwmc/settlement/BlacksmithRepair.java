package io.github.swishhyy.wwmc.settlement;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Vanilla repair-material components cover ingots, diamonds, leather, wood and stone. */
public final class BlacksmithRepair {
    public static final int WORK_TICKS=40;
    private BlacksmithRepair() {}
    public static boolean equipment(ItemStack stack) {
        return GuardWeapons.weapon(stack) || GuardEquipment.protective(stack)
                || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }
    public static boolean damaged(ItemStack stack) {
        return !stack.isEmpty() && equipment(stack) && stack.isDamageableItem() && stack.getDamageValue()>0
                && stack.get(DataComponents.REPAIRABLE)!=null;
    }
    public static boolean material(ItemStack gear,ItemStack offered) {
        var repair=gear.get(DataComponents.REPAIRABLE);
        return damaged(gear) && !offered.isEmpty() && repair!=null && repair.isValidRepairItem(offered);
    }
    public static int materialsNeeded(ItemStack gear) {
        if(!damaged(gear)) return 0;
        int amount=Math.max(1,gear.getMaxDamage()/4);
        return (gear.getDamageValue()+amount-1)/amount;
    }
    public static boolean supplied(ItemStack gear,List<Container> storage) {
        return damaged(gear) && InventoryOps.count(storage,s -> material(gear,s))>0;
    }
    /** Consume one correct material before changing the original item's damage; all other components stay intact. */
    public static boolean repair(ItemStack gear,Container materials) {
        ItemStack material=InventoryOps.takeOne(List.of(materials),s -> material(gear,s));
        if(material.isEmpty()) return false;
        gear.setDamageValue(Math.max(0,gear.getDamageValue()-Math.max(1,gear.getMaxDamage()/4)));
        return true;
    }
}

package io.github.swishhyy.wwmc.settlement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Transfer the actual stand item; neither side receives a copied award. */
public final class GuardEquipment {
    public static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    public interface Equipment {
        ItemStack get(EquipmentSlot slot);
        void set(EquipmentSlot slot,ItemStack stack);
    }
    private GuardEquipment() {}
    public static boolean armor(ItemStack stack,EquipmentSlot slot) {
        var equippable=stack.get(DataComponents.EQUIPPABLE);
        return !stack.isEmpty() && equippable!=null && equippable.slot()==slot
                && java.util.Arrays.asList(ARMOR).contains(slot);
    }
    public static boolean transfer(Equipment source,Equipment target,EquipmentSlot slot) {
        ItemStack item=source.get(slot);
        if(!target.get(slot).isEmpty() || !armor(item,slot)) return false;
        ItemStack moved=item.split(1);
        source.set(slot,item.isEmpty() ? ItemStack.EMPTY : item);
        target.set(slot,moved); return true;
    }
}

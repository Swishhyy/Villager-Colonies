package io.github.swishhyy.wwmc.settlement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.Arrays;

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
    /** Real armor that protects its wearer, as opposed to elytra, skulls or carved pumpkins that merely fit the slot. */
    public static boolean protective(ItemStack stack) {
        if(Arrays.stream(ARMOR).noneMatch(slot -> armor(stack,slot))) return false;
        for(var entry:stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY).modifiers())
            if(entry.attribute().equals(Attributes.ARMOR) && entry.modifier().amount()>0) return true;
        return false;
    }
    /** Exactly 25% remaining is usable; below that the item goes back for repair. */
    public static boolean worn(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem()
                && (long)(stack.getMaxDamage()-stack.getDamageValue())*4<stack.getMaxDamage();
    }
    public static boolean usable(ItemStack stack) { return !stack.isEmpty() && !worn(stack); }
    /** Armor points, a little for toughness, and a quarter point per enchantment level, so an enchanted piece beats a plain one of the same kind. */
    public static double protection(ItemStack stack) {
        double result=0;
        for(var entry:stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY).modifiers()) {
            if(entry.attribute().equals(Attributes.ARMOR)) result+=entry.modifier().amount();
            if(entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)) result+=entry.modifier().amount()*0.1;
        }
        return result+0.25*enchantmentLevels(stack);
    }
    /** Total enchantment levels on an item, such as five for Protection III with Unbreaking II. */
    public static int enchantmentLevels(ItemStack stack) {
        int total=0;
        for(var entry:stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).entrySet()) total+=entry.getIntValue();
        return total;
    }
    public static boolean upgrade(ItemStack next,ItemStack current,EquipmentSlot slot) {
        return armor(next,slot) && protective(next) && usable(next)
                && (current.isEmpty() || worn(current) || protection(next)>protection(current)+0.001);
    }
    /** Exchange actual pieces, preserving both sets of components and all durability. */
    public static boolean upgrade(Equipment source,Equipment target,EquipmentSlot slot) {
        ItemStack next=source.get(slot),previous=target.get(slot);
        if(!upgrade(next,previous,slot)) return false;
        if(!previous.isEmpty() && next.getCount()!=1) return false;
        ItemStack moved=next.split(1);
        source.set(slot,previous.isEmpty() ? (next.isEmpty() ? ItemStack.EMPTY : next) : previous);
        target.set(slot,moved); return true;
    }
    /** Returning armor only ever fills an empty matching slot. */
    public static boolean deposit(Equipment source,Equipment stand,EquipmentSlot slot) {
        return transfer(source,stand,slot);
    }
    public static boolean transfer(Equipment source,Equipment target,EquipmentSlot slot) {
        ItemStack item=source.get(slot);
        if(!target.get(slot).isEmpty() || !armor(item,slot)) return false;
        ItemStack moved=item.split(1);
        source.set(slot,item.isEmpty() ? ItemStack.EMPTY : item);
        target.set(slot,moved); return true;
    }
}

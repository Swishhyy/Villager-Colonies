package io.github.swishhyy.wwmc.settlement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Guards defend with swords, spears and bows; bows need real arrows. */
public final class GuardWeapons {
    public enum Kind { NONE, SWORD, SPEAR, BOW }
    private static final TagKey<Item> SPEARS=TagKey.create(Registries.ITEM,Identifier.withDefaultNamespace("spears"));
    /** Center-to-center distance at which a guard strikes; spears keep enemies farther away. */
    public static final double SWORD_REACH=2.0,SPEAR_REACH=3.25;
    /** Beyond this distance an archer draws the bow instead of closing in. */
    public static final double BOW_MIN_RANGE=5.0,BOW_MAX_RANGE=24.0;
    private GuardWeapons() {}
    public static Kind kind(ItemStack stack) {
        if(stack.isEmpty()) return Kind.NONE;
        if(stack.is(ItemTags.SWORDS)) return Kind.SWORD;
        if(stack.is(SPEARS) || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_spear")) return Kind.SPEAR;
        if(stack.getItem() instanceof BowItem) return Kind.BOW;
        return Kind.NONE;
    }
    public static boolean weapon(ItemStack stack) { return kind(stack)!=Kind.NONE; }
    public static boolean melee(ItemStack stack) { Kind kind=kind(stack); return kind==Kind.SWORD || kind==Kind.SPEAR; }
    public static boolean bow(ItemStack stack) { return kind(stack)==Kind.BOW; }
    public static boolean arrow(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof ArrowItem; }
    public static double reach(ItemStack held) { return kind(held)==Kind.SPEAR ? SPEAR_REACH : SWORD_REACH; }
    /** Main-hand attack damage the item adds; durability breaks ties so worn weapons are used last. */
    public static double score(ItemStack stack) {
        if(!melee(stack)) return 0;
        double damage=0;
        for(var entry:stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY).modifiers()) {
            if(entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation()==AttributeModifier.Operation.ADD_VALUE) damage+=entry.modifier().amount();
        }
        double wear=stack.isDamageableItem() ? (stack.getMaxDamage()-stack.getDamageValue())/(double)Math.max(1,stack.getMaxDamage()) : 1.0;
        return 1.0+damage+wear*0.01;
    }
}

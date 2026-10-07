package io.github.swishhyy.wwmc.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * Villager enchanting. An enchanter works at an enchanting table, whose bookshelves set the level exactly as they do
 * for players, but never above the configured cap below 30. Each item costs lapis like the table's rows (one, two or
 * three) and takes minutes of work, longer for rarer items.
 */
public final class Enchanting {
    public static final int TICKS_PER_MINUTE=1200;
    /** Lapis an enchanter carries at most. */
    public static final int LAPIS_CARRY=9;
    private Enchanting() {}
    /** Unenchanted gear and books the table accepts; nearly broken gear is left for the blacksmith. */
    public static boolean candidate(ItemStack stack) {
        return !stack.isEmpty() && stack.isEnchantable() && !GuardEquipment.worn(stack);
    }
    public static boolean lapis(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }
    /** Lapis spent on one item: one below level 10, two below 20 and three from 20, like the table's three rows. */
    public static int lapisCost(int level) { return Math.clamp(level/10+1,1,3); }
    /** How many times longer than a book an item takes: by the material it is repaired with, then by its rarity. */
    public static double rarity(ItemStack stack) {
        double factor=1.0;
        var repair=stack.get(DataComponents.REPAIRABLE);
        if(repair!=null) {
            if(repair.isValidRepairItem(new ItemStack(Items.NETHERITE_INGOT))) factor=2.0;
            else if(repair.isValidRepairItem(new ItemStack(Items.DIAMOND))) factor=1.6;
            else if(repair.isValidRepairItem(new ItemStack(Items.IRON_INGOT)) || repair.isValidRepairItem(new ItemStack(Items.GOLD_INGOT))) factor=1.3;
        }
        return factor+switch(stack.getRarity()) {
            case UNCOMMON -> 0.25;
            case RARE -> 0.5;
            case EPIC -> 1.0;
            default -> 0.0;
        };
    }
    /** Work ticks for one item. */
    public static int ticks(ItemStack stack,int minutes) { return (int)Math.round(Math.max(1,minutes)*TICKS_PER_MINUTE*rarity(stack)); }
    /** Bookshelf power around a table, counted the way the vanilla table counts it. */
    public static int power(Level level,BlockPos table) {
        float power=0;
        for(BlockPos offset:EnchantingTableBlock.BOOKSHELF_OFFSETS)
            if(EnchantingTableBlock.isValidBookShelf(level,table,offset)) power+=level.getBlockState(table.offset(offset)).getEnchantPowerBonus(level,table.offset(offset));
        return (int)power;
    }
    /** About the best level a table with this power reaches: twice its bookshelves, and up to 8 with none. */
    public static int typicalLevel(int power,int cap) { return Math.min(cap,Math.max(8,Math.min(15,power)*2)); }
    /** The level for one item: the table's best row for these bookshelves, never above the cap. Zero if the item cannot be enchanted. */
    public static int level(RandomSource random,int power,ItemStack stack,int cap) {
        return Math.clamp(EnchantmentHelper.getEnchantmentCost(random,2,power,stack),0,cap);
    }
    /** Roll enchantments as the table's best row does and apply them to a copy; empty when nothing applies. */
    public static ItemStack enchant(RegistryAccess access,RandomSource random,ItemStack stack,int level) {
        if(level<=0 || stack.isEmpty()) return ItemStack.EMPTY;
        var table=access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.IN_ENCHANTING_TABLE);
        if(table.isEmpty()) return ItemStack.EMPTY;
        List<EnchantmentInstance> chosen=new ArrayList<>(EnchantmentHelper.selectEnchantment(random,stack,level,table.get().stream()));
        if(chosen.isEmpty()) return ItemStack.EMPTY;
        // As at the table, a book keeps one enchantment fewer.
        if(stack.is(Items.BOOK) && chosen.size()>1) chosen.remove(random.nextInt(chosen.size()));
        return stack.getItem().applyEnchantments(stack.copyWithCount(1),chosen);
    }
    /** Lower comes first: armor and weapons, then tools, then other gear, then books. */
    public static int priority(ItemStack stack) {
        if(GuardWeapons.weapon(stack) || GuardEquipment.protective(stack)) return 0;
        if(stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) return 1;
        return stack.is(Items.BOOK) ? 3 : 2;
    }
    private static boolean before(ItemStack a,ItemStack b) {
        return priority(a)<priority(b) || priority(a)==priority(b) && rarity(a)>rarity(b);
    }
    public static boolean waiting(List<Container> sources,Predicate<ItemStack> skipped) { return count(sources,skipped)>0; }
    /** Items waiting to be enchanted, a stack of books counting each book. */
    public static int count(List<Container> sources,Predicate<ItemStack> skipped) {
        return InventoryOps.count(sources,stack -> candidate(stack) && !skipped.test(stack));
    }
    /** Take the most important waiting item, one at a time from a stack. */
    public static ItemStack takeNext(List<Container> sources,Predicate<ItemStack> skipped) {
        Container best=null; int bestSlot=-1;
        for(Container container:sources) for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack stack=container.getItem(slot);
            if(!candidate(stack) || skipped.test(stack)) continue;
            if(best==null || before(stack,best.getItem(bestSlot))) { best=container; bestSlot=slot; }
        }
        if(best==null) return ItemStack.EMPTY;
        ItemStack taken=best.removeItem(bestSlot,1);
        best.setChanged();
        return taken;
    }
}

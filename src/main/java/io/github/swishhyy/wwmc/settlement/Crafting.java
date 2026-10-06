package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Craftsman work orders. Each recipe keeps a stock of its product in the warehouse and is made only from real
 * materials in storage, so a craftsman never creates items and stops once the town is supplied.
 */
public final class Crafting {
    public record Input(Predicate<ItemStack> item,int count) {}
    public record Recipe(String id,String label,List<Input> inputs,int yield,Item fixed,Predicate<ItemStack> product,int target) {
        public boolean uses(ItemStack stack) { return inputs.stream().anyMatch(input -> input.item().test(stack)); }
        /** Planks follow the log that went in; every other recipe has a fixed product. */
        public Item result(ItemStack firstInput) { return fixed!=null ? fixed : planks(firstInput); }
    }
    private static final Predicate<ItemStack> STICK=s -> s.is(Items.STICK);
    private static final Predicate<ItemStack> PLANKS=s -> s.is(ItemTags.PLANKS);
    private static final Predicate<ItemStack> STONE=s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.BLACKSTONE);
    private static final Predicate<ItemStack> FUEL=s -> s.is(Items.COAL) || s.is(Items.CHARCOAL);
    /** Listed in priority order: food and worker tools before building materials. */
    public static final List<Recipe> RECIPES=List.of(
        new Recipe("bread","bread",List.of(new Input(s -> s.is(Items.WHEAT),3)),1,Items.BREAD,s -> s.is(Items.BREAD),32),
        new Recipe("stone_pickaxe","stone pickaxes",List.of(new Input(STONE,3),new Input(STICK,2)),1,Items.STONE_PICKAXE,s -> s.is(Items.STONE_PICKAXE),2),
        new Recipe("stone_axe","stone axes",List.of(new Input(STONE,3),new Input(STICK,2)),1,Items.STONE_AXE,s -> s.is(Items.STONE_AXE),2),
        new Recipe("stone_sword","stone swords",List.of(new Input(STONE,2),new Input(STICK,1)),1,Items.STONE_SWORD,s -> s.is(Items.STONE_SWORD),2),
        new Recipe("bow","bows",List.of(new Input(s -> s.is(Items.STRING),3),new Input(STICK,3)),1,Items.BOW,s -> s.is(Items.BOW),1),
        new Recipe("arrows","arrows",List.of(new Input(s -> s.is(Items.FLINT),1),new Input(STICK,1),new Input(s -> s.is(Items.FEATHER),1)),4,Items.ARROW,s -> s.is(Items.ARROW),64),
        new Recipe("torches","torches",List.of(new Input(FUEL,1),new Input(STICK,1)),4,Items.TORCH,s -> s.is(Items.TORCH),32),
        new Recipe("ladders","ladders",List.of(new Input(STICK,7)),3,Items.LADDER,s -> s.is(Items.LADDER),32),
        new Recipe("sticks","sticks",List.of(new Input(PLANKS,2)),4,Items.STICK,STICK,32),
        new Recipe("planks","planks",List.of(new Input(s -> planks(s)!=null,1)),4,null,PLANKS,64));
    /** The most batches carried to the workbench in one trip. */
    public static final int TRIP_BATCHES=8;
    private Crafting() {}
    public static Recipe byId(String id) { return RECIPES.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null); }
    /** The planks a log, stem or wood block saws into, by Minecraft's naming; null for anything else. */
    public static Item planks(ItemStack stack) {
        if(stack.isEmpty() || !stack.is(ItemTags.LOGS)) return null;
        Identifier id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        String base=id.getPath().replace("stripped_","");
        for(String suffix:new String[]{"_log","_wood","_stem","_hyphae"}) if(base.endsWith(suffix)) {
            Item planks=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(),base.substring(0,base.length()-suffix.length())+"_planks"));
            return planks==Items.AIR ? null : planks;
        }
        return null;
    }
    /** Complete batches the containers hold materials for. */
    public static int batches(List<Container> sources,Recipe recipe) {
        int batches=Integer.MAX_VALUE;
        for(Input input:recipe.inputs()) batches=Math.min(batches,InventoryOps.count(sources,input.item())/input.count());
        return batches;
    }
    public static int stock(List<Container> storage,Recipe recipe) { return InventoryOps.count(storage,recipe.product()); }
    /** First enabled recipe the warehouse is short of and holds materials for. */
    public static Recipe choose(List<Container> storage,Collection<String> disabled) {
        for(Recipe recipe:RECIPES) if(!disabled.contains(recipe.id()) && stock(storage,recipe)<recipe.target() && batches(storage,recipe)>0) return recipe;
        return null;
    }
    /** Move materials for up to {@link #TRIP_BATCHES} batches, no more than the shortage needs, into the bag. */
    public static int fetch(List<Container> storage,Container bag,Recipe recipe) {
        int needed=(recipe.target()-stock(storage,recipe)+recipe.yield()-1)/recipe.yield();
        int batches=Math.min(TRIP_BATCHES,Math.min(needed,batches(storage,recipe)));
        for(Input input:recipe.inputs()) for(int i=0;i<input.count()*batches;i++) {
            ItemStack item=InventoryOps.takeOne(storage,input.item());
            ItemStack rest=InventoryOps.insert(bag,item);
            // A full bag returns the material rather than losing it.
            if(!rest.isEmpty()) for(Container container:storage) rest=InventoryOps.insert(container,rest);
        }
        return Math.max(0,batches);
    }
    public static boolean ready(Container bag,Recipe recipe) { return batches(List.of(bag),recipe)>0; }
    /** Consume one batch of materials from the bag and return the product. */
    public static ItemStack craft(Container bag,Recipe recipe) {
        if(!ready(bag,recipe)) return ItemStack.EMPTY;
        ItemStack first=ItemStack.EMPTY;
        for(Input input:recipe.inputs()) for(int i=0;i<input.count();i++) {
            ItemStack used=InventoryOps.takeOne(List.of(bag),input.item());
            if(first.isEmpty()) first=used;
        }
        Item result=recipe.result(first);
        return result==null ? ItemStack.EMPTY : new ItemStack(result,recipe.yield());
    }
    public static String status(List<Container> storage,Collection<String> disabled) {
        StringBuilder text=new StringBuilder();
        for(Recipe recipe:RECIPES) {
            if(!text.isEmpty()) text.append(", ");
            text.append(recipe.id()).append(disabled.contains(recipe.id()) ? " (off)" : " "+stock(storage,recipe)+"/"+recipe.target());
        }
        return text.toString();
    }
}

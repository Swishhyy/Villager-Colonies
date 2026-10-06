package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import java.util.function.Predicate;
import io.github.swishhyy.wwmc.core.StructureRole;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fixed work orders: cooks bake bread from wheat up to a warehouse stock target. Craftsmen follow learned crafting
 * recipes instead (see {@link Workshop}). Orders use real materials from storage, so nothing is ever created.
 */
public final class Crafting {
    public record Input(Predicate<ItemStack> item,int count) {}
    public record Recipe(String id,String label,List<Input> inputs,int yield,Item product,int target) {
        public boolean uses(ItemStack stack) { return inputs.stream().anyMatch(input -> input.item().test(stack)); }
    }
    public static final List<Recipe> RECIPES=List.of(
        new Recipe("bread","bread",List.of(new Input(s -> s.is(Items.WHEAT),3)),1,Items.BREAD,32));
    /** The most batches carried to the workbench in one trip. */
    public static final int TRIP_BATCHES=8;
    private Crafting() {}
    public static Recipe byId(String id) { return RECIPES.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null); }
    /** Complete batches the containers hold materials for. */
    public static int batches(List<Container> sources,Recipe recipe) {
        int batches=Integer.MAX_VALUE;
        for(Input input:recipe.inputs()) batches=Math.min(batches,InventoryOps.count(sources,input.item())/input.count());
        return batches;
    }
    public static int stock(List<Container> storage,Recipe recipe) { return InventoryOps.count(storage,s -> s.is(recipe.product())); }
    /** The first enabled order the storage is short of and holds materials for; only cooks have fixed orders. */
    public static Recipe choose(List<Container> storage,Collection<String> disabled,StructureRole role) {
        if(role!=StructureRole.COOK) return null;
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
        for(Input input:recipe.inputs()) for(int i=0;i<input.count();i++) InventoryOps.takeOne(List.of(bag),input.item());
        return new ItemStack(recipe.product(),recipe.yield());
    }
}

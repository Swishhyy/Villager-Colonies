package io.github.swishhyy.wwmc.settlement;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** All transfers run on the server thread and move real stacks. */
public final class InventoryOps {
    private InventoryOps() {}
    public static ItemStack insert(Container destination, ItemStack offered) {
        ItemStack remaining=offered.copy();
        for(int pass=0;pass<2;pass++) {
            for(int slot=0;slot<destination.getContainerSize() && !remaining.isEmpty();slot++) {
                ItemStack present=destination.getItem(slot);
                if(!destination.canPlaceItem(slot,remaining)) continue;
                if(pass==0 && !present.isEmpty() && ItemStack.isSameItemSameComponents(present,remaining)) {
                    int amount=Math.min(remaining.getCount(),Math.min(present.getMaxStackSize(),destination.getMaxStackSize())-present.getCount());
                    if(amount>0) { present.grow(amount); remaining.shrink(amount); destination.setItem(slot,present); }
                } else if(pass==1 && present.isEmpty()) {
                    int amount=Math.min(remaining.getCount(),Math.min(remaining.getMaxStackSize(),destination.getMaxStackSize()));
                    destination.setItem(slot,remaining.split(amount));
                }
            }
        }
        destination.setChanged();
        return remaining;
    }
    public static ItemStack takeOne(List<Container> sources, Predicate<ItemStack> eligible) {
        for(Container source:sources) for(int slot=0;slot<source.getContainerSize();slot++) {
            ItemStack stack=source.getItem(slot);
            if(!stack.isEmpty() && eligible.test(stack)) {
                ItemStack result=source.removeItem(slot,1); source.setChanged(); return result;
            }
        }
        return ItemStack.EMPTY;
    }
    /** Fill only the requested slot, honoring its rules, capacity and stack components. */
    public static int moveToSlot(List<Container> sources,Container destination,int slot,Predicate<ItemStack> eligible,int maximum) {
        int moved=0;
        for(Container source:sources) {
            if(source==destination) continue;
            for(int index=0;index<source.getContainerSize() && moved<maximum;index++) {
                ItemStack offered=source.getItem(index),present=destination.getItem(slot);
                if(offered.isEmpty() || !eligible.test(offered) || !destination.canPlaceItem(slot,offered)
                        || !present.isEmpty() && !ItemStack.isSameItemSameComponents(present,offered)) continue;
                int capacity=Math.min(offered.getMaxStackSize(),destination.getMaxStackSize())-present.getCount();
                int amount=Math.min(Math.min(maximum-moved,capacity),offered.getCount());
                if(amount<=0) continue;
                ItemStack taken=source.removeItem(index,amount);
                if(present.isEmpty()) destination.setItem(slot,taken);
                else { present.grow(taken.getCount()); destination.setItem(slot,present); }
                source.setChanged(); destination.setChanged(); moved+=taken.getCount();
            }
        }
        return moved;
    }
    /** Remove one item with the highest score among eligible stacks; earlier slots win ties. */
    public static ItemStack takeBest(List<Container> sources, Predicate<ItemStack> eligible, ToDoubleFunction<ItemStack> score) {
        Container best=null; int bestSlot=-1; double bestScore=Double.NEGATIVE_INFINITY;
        for(Container source:sources) for(int slot=0;slot<source.getContainerSize();slot++) {
            ItemStack stack=source.getItem(slot);
            if(stack.isEmpty() || !eligible.test(stack)) continue;
            double value=score.applyAsDouble(stack);
            if(value>bestScore) { best=source; bestSlot=slot; bestScore=value; }
        }
        if(best==null) return ItemStack.EMPTY;
        ItemStack result=best.removeItem(bestSlot,1); best.setChanged(); return result;
    }
    public static int count(List<Container> sources, Predicate<ItemStack> eligible) {
        int total=0;
        for(Container source:sources) for(int slot=0;slot<source.getContainerSize();slot++) {
            ItemStack stack=source.getItem(slot);
            if(!stack.isEmpty() && eligible.test(stack)) total+=stack.getCount();
        }
        return total;
    }
}

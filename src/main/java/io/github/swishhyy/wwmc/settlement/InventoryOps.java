package io.github.swishhyy.wwmc.settlement;
import java.util.List;
import java.util.function.Predicate;
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
}

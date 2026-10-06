package io.github.swishhyy.wwmc.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Personal stock plus a saved remainder for unusually large tree harvests. No overflow is discarded. */
public final class CitizenInventory extends SimpleContainer {
    public static final int SIZE=36;
    private final List<ItemStack> pending=new ArrayList<>();
    private final Predicate<Player> allowed;
    private final Map<Player,ChestMenu> viewers=new HashMap<>();
    public CitizenInventory(Predicate<Player> allowed) { super(SIZE); this.allowed=allowed; }
    @Override public boolean stillValid(Player player) { return allowed.test(player); }
    public ChestMenu createMenu(int id,Inventory inventory,Player viewer) {
        ChestMenu menu=new ChestMenu(MenuType.GENERIC_9x4,id,inventory,this,4);
        viewers.put(viewer,menu);
        return menu;
    }
    public boolean isOpen() {
        viewers.entrySet().removeIf(e -> e.getKey().isRemoved() || !e.getKey().isAlive()
                || !allowed.test(e.getKey()) || e.getKey().containerMenu!=e.getValue());
        return !viewers.isEmpty();
    }
    public void offer(ItemStack stack) {
        ItemStack rest=InventoryOps.insert(this,stack);
        if(!rest.isEmpty()) pending.add(rest);
    }
    public void flush() {
        for(int index=0;index<pending.size();) {
            ItemStack rest=InventoryOps.insert(this,pending.get(index));
            if(rest.isEmpty()) pending.remove(index); else { pending.set(index,rest); index++; }
        }
    }
    public boolean needsDelivery() {
        if(!pending.isEmpty()) return true;
        int used=0; for(int slot=0;slot<SIZE;slot++) if(!getItem(slot).isEmpty()) used++;
        return used>=32;
    }
    public boolean hasPending() { return !pending.isEmpty(); }
    public List<ItemStack> pendingItems() { return pending.stream().map(ItemStack::copy).toList(); }
    public int count(Item item) {
        int total=0;
        for(int slot=0;slot<SIZE;slot++) if(getItem(slot).is(item)) total+=getItem(slot).getCount();
        for(ItemStack stack:pending) if(stack.is(item)) total+=stack.getCount();
        return total;
    }
    public List<ItemStack> contents() {
        var stacks=new ArrayList<ItemStack>();
        for(int slot=0;slot<SIZE;slot++) stacks.add(getItem(slot).copy());
        return stacks;
    }
    public void restore(List<ItemStack> stacks,List<ItemStack> remainder) {
        clearContent(); pending.clear();
        for(int slot=0;slot<Math.min(SIZE,stacks.size());slot++) setItem(slot,stacks.get(slot).copy());
        for(int slot=SIZE;slot<stacks.size();slot++) if(!stacks.get(slot).isEmpty()) pending.add(stacks.get(slot).copy());
        for(ItemStack stack:remainder) if(!stack.isEmpty()) pending.add(stack.copy());
        flush();
    }
    public void deposit(List<Container> storage,ToIntFunction<ItemStack> retained) {
        for(int slot=0;slot<SIZE;slot++) {
            ItemStack original=getItem(slot);
            int keep=Math.min(original.getCount(),Math.max(0,retained.applyAsInt(original)));
            ItemStack rest=original.copyWithCount(original.getCount()-keep);
            for(Container container:storage) rest=InventoryOps.insert(container,rest);
            setItem(slot,original.copyWithCount(keep+rest.getCount()));
        }
        for(int index=0;index<pending.size();) {
            ItemStack original=pending.get(index);
            int keep=Math.min(original.getCount(),Math.max(0,retained.applyAsInt(original)));
            ItemStack rest=original.copyWithCount(original.getCount()-keep);
            for(Container container:storage) rest=InventoryOps.insert(container,rest);
            int left=keep+rest.getCount();
            if(left==0) pending.remove(index); else { pending.set(index,original.copyWithCount(left)); index++; }
        }
        flush();
    }
    public boolean hasDeliverable(Predicate<ItemStack> retained,Predicate<ItemStack> food) {
        int rations=0;
        var all=new ArrayList<>(pending);
        for(int slot=0;slot<SIZE;slot++) all.add(getItem(slot));
        for(ItemStack stack:all) {
            if(stack.isEmpty() || retained.test(stack)) continue;
            if(!food.test(stack)) return true;
            rations+=stack.getCount();
        }
        return rations>8;
    }
}

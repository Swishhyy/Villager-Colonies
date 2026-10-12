package io.github.swishhyy.vc.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Reserved legacy menu ID for existing registry snapshots. New clients open an optional map mod instead. */
public final class MapMenu extends AbstractContainerMenu {
    public MapData map;
    public MapMenu(int id,MapData map) { super(VcMenus.MAP.get(),id); this.map=map; }
    public static MapMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) { return new MapMenu(id,MapData.STREAM_CODEC.decode(buf)); }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player.isAlive(); }
}

package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.CitizenInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A citizen's status and equipment above their 36-slot bag, which the owner can fill or empty. */
public final class CitizenMenu extends SettlementMenu {
    public static final int WIDTH=176,HEIGHT=262,BAG_X=8,BAG_Y=95,INVENTORY_X=8,INVENTORY_Y=180,GEAR_Y=65,SKILL_Y=53;
    private final Container bag;
    private final CitizenEntity citizen;
    public CitizenMenu(int id,Inventory inventory,Container bag,CitizenEntity citizen,ServerPlayer viewer,PanelView view) {
        super(WwmcMenus.CITIZEN.get(),id,viewer,view);
        this.bag=bag; this.citizen=citizen;
        for(int row=0;row<4;row++) for(int column=0;column<9;column++) addSlot(new Slot(bag,column+row*9,BAG_X+column*18,BAG_Y+row*18));
        for(int row=0;row<3;row++) for(int column=0;column<9;column++)
            addSlot(new Slot(inventory,column+row*9+9,INVENTORY_X+column*18,INVENTORY_Y+row*18));
        for(int column=0;column<9;column++) addSlot(new Slot(inventory,column,INVENTORY_X+column*18,INVENTORY_Y+58));
    }
    public static CitizenMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        return new CitizenMenu(id,inventory,new SimpleContainer(CitizenInventory.SIZE),null,null,PanelView.STREAM_CODEC.decode(buf));
    }
    @Override protected PanelView build() { return citizen==null || !citizen.isAlive() ? null : Panels.citizen(citizen); }
    @Override public boolean stillValid(Player player) {
        return bag.stillValid(player) && (viewer==null || citizen!=null && citizen.level() instanceof net.minecraft.server.level.ServerLevel level
                && io.github.swishhyy.wwmc.settlement.TownAccess.manages(citizen.town(level),player.getUUID()));
    }
    @Override public void act(ServerPlayer player,int action,int index,int value,String key) {}
    /** Shift-click moves stacks between the bag and the player's inventory, like a chest. */
    @Override public ItemStack quickMoveStack(Player player,int index) {
        Slot slot=slots.get(index);
        if(!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),original=stack.copy();
        int bagEnd=CitizenInventory.SIZE;
        if(index<bagEnd ? !moveItemStackTo(stack,bagEnd,slots.size(),true) : !moveItemStackTo(stack,0,bagEnd,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
}

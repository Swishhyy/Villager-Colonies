package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.settlement.TraderPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Select a partner and show example items to set exports; the example itself is never consumed. */
public final class TraderMenu extends SettlementMenu {
    public static final int WIDTH=310,HEIGHT=250,EXAMPLE_X=282,EXAMPLE_Y=32,INVENTORY_X=74,INVENTORY_Y=166;
    public final BlockPos pos;
    private String feedback="";
    private int feedbackTicks;
    public TraderMenu(int id,Inventory inventory,BlockPos pos,ServerPlayer viewer,PanelView view) {
        super(WwmcMenus.TRADER.get(),id,viewer,view); this.pos=pos.immutable();
        addSlot(new Slot(new SimpleContainer(1),0,EXAMPLE_X,EXAMPLE_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(inventory,9+col+row*9,INVENTORY_X+col*18,INVENTORY_Y+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(inventory,col,INVENTORY_X+col*18,INVENTORY_Y+58));
    }
    public static TraderMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) { return new TraderMenu(id,inventory,buf.readBlockPos(),null,PanelView.STREAM_CODEC.decode(buf)); }
    public static void write(RegistryFriendlyByteBuf buf,BlockPos pos,PanelView view) { buf.writeBlockPos(pos); PanelView.STREAM_CODEC.encode(buf,view); }
    @Override public void clicked(int slot,int button,ContainerInput input,Player player) {
        if(slot==0) { if(viewer!=null && stillValid(viewer)) example(getCarried()); return; }
        super.clicked(slot,button,input,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(viewer!=null && stillValid(viewer) && index>0 && index<slots.size()) example(slots.get(index).getItem());
        return ItemStack.EMPTY;
    }
    private void example(ItemStack stack) { if(!stack.isEmpty()) { feedback=TraderPanel.example(viewer,pos,stack); feedbackTicks=100; refresh(); } }
    @Override protected PanelView build() { return TraderPanel.view(viewer,pos,feedback); }
    @Override public boolean stillValid(Player player) { return viewer==null || TraderPanel.owned(viewer,pos)!=null; }
    @Override public void broadcastChanges() { if(feedbackTicks>0 && --feedbackTicks==0) feedback=""; super.broadcastChanges(); }
    @Override public void act(ServerPlayer player,int action,int index,int value,String key) {
        feedback=TraderPanel.act(player,pos,action,index,value,key); feedbackTicks=100; refresh();
    }
}

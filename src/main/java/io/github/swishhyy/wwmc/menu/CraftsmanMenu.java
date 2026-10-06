package io.github.swishhyy.wwmc.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Craftsman orders. Clicking the teach slot with an item, or shift-clicking an item in the inventory, teaches its
 * recipe; the item itself stays with the player.
 */
public final class CraftsmanMenu extends SettlementMenu {
    public static final int WIDTH=230,HEIGHT=240,TEACH_X=8,TEACH_Y=34,INVENTORY_X=35,INVENTORY_Y=158;
    public static final int TEACH_SLOT=0;
    public final BlockPos pos;
    private String feedback="";
    private int feedbackTicks;
    public CraftsmanMenu(int id,Inventory inventory,BlockPos pos,ServerPlayer viewer,PanelView view) {
        super(WwmcMenus.CRAFTSMAN.get(),id,viewer,view);
        this.pos=pos.immutable();
        addSlot(new Slot(new SimpleContainer(1),0,TEACH_X,TEACH_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for(int row=0;row<3;row++) for(int column=0;column<9;column++)
            addSlot(new Slot(inventory,column+row*9+9,INVENTORY_X+column*18,INVENTORY_Y+row*18));
        for(int column=0;column<9;column++) addSlot(new Slot(inventory,column,INVENTORY_X+column*18,INVENTORY_Y+58));
    }
    public static CraftsmanMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        BlockPos pos=buf.readBlockPos();
        return new CraftsmanMenu(id,inventory,pos,null,PanelView.STREAM_CODEC.decode(buf));
    }
    public static void write(RegistryFriendlyByteBuf buf,BlockPos pos,PanelView view) { buf.writeBlockPos(pos); PanelView.STREAM_CODEC.encode(buf,view); }
    @Override public void clicked(int slot,int button,ContainerInput input,Player player) {
        // The teach slot never holds anything: the carried item is only shown to the craftsmen.
        if(slot==TEACH_SLOT) { if(viewer!=null) teach(getCarried()); return; }
        super.clicked(slot,button,input,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(viewer!=null && index>TEACH_SLOT && index<slots.size()) teach(slots.get(index).getItem());
        return ItemStack.EMPTY;
    }
    private void teach(ItemStack example) {
        if(example.isEmpty()) return;
        feedback=Panels.teach(viewer,pos,example); feedbackTicks=100; refresh();
    }
    @Override public void broadcastChanges() {
        if(feedbackTicks>0 && --feedbackTicks==0) feedback="";
        super.broadcastChanges();
    }
    @Override protected PanelView build() { return Panels.craftsman(viewer,pos,feedback); }
    @Override public boolean stillValid(Player player) { return viewer==null || Panels.valid(viewer,pos,false); }
    @Override public void act(ServerPlayer player,int action,int index,int value) {
        Panels.craftAction(player,pos,action,index,value); refresh();
    }
}

package io.github.swishhyy.wwmc.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The town overview (opened at the banner) or a station's panel. */
public final class PanelMenu extends SettlementMenu {
    public enum Kind { TOWN, STATION }
    public final Kind kind;
    public final BlockPos pos;
    public PanelMenu(int id,Kind kind,BlockPos pos,ServerPlayer viewer,PanelView view) {
        super(WwmcMenus.PANEL.get(),id,viewer,view); this.kind=kind; this.pos=pos.immutable();
    }
    public static PanelMenu read(int id,Inventory inventory,RegistryFriendlyByteBuf buf) {
        Kind kind=buf.readEnum(Kind.class);
        BlockPos pos=buf.readBlockPos();
        return new PanelMenu(id,kind,pos,null,PanelView.STREAM_CODEC.decode(buf));
    }
    public static void write(RegistryFriendlyByteBuf buf,Kind kind,BlockPos pos,PanelView view) {
        buf.writeEnum(kind); buf.writeBlockPos(pos); PanelView.STREAM_CODEC.encode(buf,view);
    }
    @Override protected PanelView build() { return kind==Kind.TOWN ? Panels.town(viewer,pos) : Panels.station(viewer,pos); }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return viewer==null || Panels.valid(viewer,pos,kind==Kind.TOWN); }
    @Override public void act(ServerPlayer player,int action,int index,int value) {
        if(kind==Kind.TOWN) Panels.townAction(player,pos,action);
        else Panels.stationAction(player,pos,action);
        refresh();
    }
}

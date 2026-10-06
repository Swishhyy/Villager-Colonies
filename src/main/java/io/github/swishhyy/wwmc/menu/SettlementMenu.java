package io.github.swishhyy.wwmc.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

/** A settlement screen's menu. On the server it rebuilds its view every second and after each action. */
public abstract class SettlementMenu extends AbstractContainerMenu implements ViewMenu {
    /** The owner looking at this screen; null on the client. */
    protected final ServerPlayer viewer;
    protected PanelView view;
    private int age;
    protected SettlementMenu(MenuType<?> type,int id,ServerPlayer viewer,PanelView view) {
        super(type,id); this.viewer=viewer; this.view=view;
    }
    @Override public PanelView view() { return view; }
    @Override public void view(PanelView view) { this.view=view; }
    /** The current view, or null once its subject is gone (the screen then closes through stillValid). */
    protected abstract PanelView build();
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if(viewer!=null && ++age%20==0) refresh();
    }
    public void refresh() {
        if(viewer==null) return;
        PanelView next=build();
        if(next!=null) { view=next; PacketDistributor.sendToPlayer(viewer,new WwmcNetwork.ViewPayload(containerId,next)); }
    }
}

package io.github.swishhyy.wwmc.menu;

import net.minecraft.server.level.ServerPlayer;

/** A settlement menu whose screen shows a server-built {@link PanelView}. */
public interface ViewMenu {
    PanelView view();
    /** Client side: the server sent a refresh. */
    void view(PanelView view);
    /** Server side: the owner pressed a control on the screen. */
    void act(ServerPlayer player,int action,int index,int value,String key);
}

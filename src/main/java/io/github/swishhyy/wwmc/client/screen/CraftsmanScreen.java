package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.CraftsmanMenu;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Learned craft orders, five to a page. Each order has a slider for how many to keep in town (zero pauses it), a
 * button to raise its priority and one to forget it. Drop an item on the teach slot to add an order.
 */
public final class CraftsmanScreen extends AbstractContainerScreen<CraftsmanMenu> {
    private static final int PAGE=5,ROW=19,LIST_TOP=54,MAX=256;
    private int page;
    private PanelView shown;
    private String layout="";
    /** Slider moves wait a quarter second before they are sent, so a drag sends one update. */
    private final Map<Integer,Integer> pending=new HashMap<>();
    private long changedAt;
    public CraftsmanScreen(CraftsmanMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,CraftsmanMenu.WIDTH,CraftsmanMenu.HEIGHT);
    }
    private List<PanelView.Row> orders() { return menu.view().tabs().isEmpty() ? List.of() : menu.view().tabs().getFirst().rows(); }
    private int pages() { return Math.max(1,(orders().size()+PAGE-1)/PAGE); }
    /** Slider position from 0 to 1 for a target: squared, so small amounts such as one or two tools are easy to set. */
    static double position(int target) { return Math.sqrt(Math.clamp(target,0,MAX)/(double)MAX); }
    static int amount(double position) { return (int)Math.round(MAX*position*position); }
    private final class TargetSlider extends AbstractSliderButton {
        private final int index;
        TargetSlider(int x,int y,int width,int index,int target) {
            super(x,y,width,15,Component.empty(),position(target));
            this.index=index; updateMessage();
        }
        @Override protected void updateMessage() { int target=amount(value); setMessage(Component.literal(target==0 ? "Paused" : "Keep "+target)); }
        @Override protected void applyValue() { pending.put(index,amount(value)); changedAt=Util.getMillis(); }
    }
    @Override protected void init() {
        super.init();
        List<PanelView.Row> orders=orders();
        page=Math.min(page,pages()-1);
        int listX=leftPos+8;
        for(int slot=0;slot<PAGE;slot++) {
            int index=page*PAGE+slot;
            if(index>=orders.size()) break;
            int rowY=topPos+LIST_TOP+slot*ROW;
            int target=pending.getOrDefault(index,Math.max(0,orders.get(index).value()));
            addRenderableWidget(new TargetSlider(listX+104,rowY+2,74,index,target));
            Button up=Button.builder(Component.literal("▲"),b -> send(Panels.RAISE,index,0)).bounds(listX+180,rowY+2,14,15).build();
            up.active=index>0;
            addRenderableWidget(up);
            addRenderableWidget(Button.builder(Component.literal("✕"),b -> send(Panels.FORGET,index,0)).bounds(listX+196,rowY+2,14,15).build());
        }
        Button previous=Button.builder(Component.literal("<"),b -> { flush(); page--; rebuildWidgets(); }).bounds(leftPos+imageWidth-62,topPos+34,16,16).build();
        previous.active=page>0;
        addRenderableWidget(previous);
        Button next=Button.builder(Component.literal(">"),b -> { flush(); page++; rebuildWidgets(); }).bounds(leftPos+imageWidth-24,topPos+34,16,16).build();
        next.active=page<pages()-1;
        addRenderableWidget(next);
        shown=menu.view(); layout=layout(menu.view());
    }
    private void send(int action,int index,int value) {
        flush();
        ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,action,index,value));
    }
    private void flush() {
        pending.forEach((index,target) -> ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,Panels.TARGET,index,target)));
        pending.clear();
    }
    /** The rows' items and targets; the widgets are rebuilt when the server reports a different list. */
    private static String layout(PanelView view) {
        StringBuilder key=new StringBuilder();
        if(!view.tabs().isEmpty()) for(PanelView.Row row:view.tabs().getFirst().rows()) key.append(row.text().getString()).append('=').append(row.value()).append('|');
        return key.toString();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        if(!pending.isEmpty() && Util.getMillis()-changedAt>250) flush();
        // Never rebuild under a slider that is still being moved.
        if(menu.view()!=shown && pending.isEmpty() && Util.getMillis()-changedAt>1000) {
            if(!layout(menu.view()).equals(layout)) rebuildWidgets();
            shown=menu.view();
        }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        List<PanelView.Row> orders=orders();
        for(int slot=0;slot<PAGE;slot++) {
            int index=page*PAGE+slot,rowY=topPos+LIST_TOP+slot*ROW;
            if(index>=orders.size()) break;
            if(mouseY>=rowY && mouseY<rowY+ROW-1 && mouseX>=leftPos+8 && mouseX<leftPos+28) g.setTooltipForNextFrame(font,orders.get(index).icon(),mouseX,mouseY);
            else if(mouseY>=rowY && mouseY<rowY+ROW-1 && mouseX>=leftPos+28 && mouseX<leftPos+110) g.setComponentTooltipForNextFrame(font,Ui.tooltip(font,orders.get(index)),mouseX,mouseY);
        }
        int teachX=leftPos+CraftsmanMenu.TEACH_X,teachY=topPos+CraftsmanMenu.TEACH_Y;
        if(mouseX>=teachX-1 && mouseX<teachX+17 && mouseY>=teachY-1 && mouseY<teachY+17 && menu.getCarried().isEmpty())
            g.setComponentTooltipForNextFrame(font,List.of(Component.literal("Teach a recipe"),Component.literal("Click here holding an item, or shift-click"),
                    Component.literal("an item in your inventory. You keep the item.")),mouseX,mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos;
        Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,Ui.fit(font,menu.view().title().getString(),imageWidth-16),x+8,y+7,Ui.TEXT,false);
        g.text(font,Ui.fit(font,menu.view().subtitle().getString(),imageWidth-16),x+8,y+18,Ui.MUTED,false);
        Ui.slot(g,x+CraftsmanMenu.TEACH_X,y+CraftsmanMenu.TEACH_Y);
        g.text(font,"Teach",x+CraftsmanMenu.TEACH_X+22,y+CraftsmanMenu.TEACH_Y+1,Ui.TEXT,false);
        g.text(font,"drop an item",x+CraftsmanMenu.TEACH_X+22,y+CraftsmanMenu.TEACH_Y+10,Ui.MUTED,false);
        g.text(font,(page+1)+"/"+pages(),x+imageWidth-43,y+38,Ui.TEXT,false);
        int top=y+LIST_TOP-2;
        Ui.inset(g,x+7,top,imageWidth-14,PAGE*ROW+3);
        List<PanelView.Row> orders=orders();
        for(int slot=0;slot<PAGE;slot++) {
            int index=page*PAGE+slot;
            if(index>=orders.size()) break;
            PanelView.Row row=orders.get(index);
            int rowY=y+LIST_TOP+slot*ROW,left=x+8;
            g.fill(left,rowY,left+imageWidth-16,rowY+ROW-1,Ui.ROW);
            g.item(row.icon(),left+2,rowY+1);
            g.text(font,Ui.fit(font,row.text().getString(),80),left+20,rowY+1,Ui.TEXT,false);
            g.text(font,Ui.fit(font,row.detail().getString(),80),left+20,rowY+10,row.color()!=0 ? row.color() : Ui.MUTED,false);
            if(row.bar()>=0) Ui.bar(g,left+20,rowY+ROW-3,80,row.bar(),row.color());
        }
        if(orders.isEmpty()) g.text(font,"No recipes yet: teach one with the slot above",x+12,top+8,Ui.MUTED,false);
        g.text(font,"Inventory",x+CraftsmanMenu.INVENTORY_X,y+CraftsmanMenu.INVENTORY_Y-11,Ui.TEXT,false);
        Ui.inventory(g,x+CraftsmanMenu.INVENTORY_X,y+CraftsmanMenu.INVENTORY_Y);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(scrollY!=0 && mouseY<topPos+LIST_TOP+PAGE*ROW) {
            int next=Math.clamp(page-(int)Math.signum(scrollY),0,pages()-1);
            if(next!=page) { flush(); page=next; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
    @Override public void onClose() { flush(); super.onClose(); }
}

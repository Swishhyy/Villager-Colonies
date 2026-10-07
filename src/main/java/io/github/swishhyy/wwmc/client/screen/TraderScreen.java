package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.TraderMenu;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import io.github.swishhyy.wwmc.settlement.TraderPanel;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Route selection and export reserves have their own compact pages beside the player's inventory. */
public final class TraderScreen extends AbstractContainerScreen<TraderMenu> {
    private static final int PAGE=5,ROW=18,TOP=54;
    private int tab,page;
    private String layout="";
    private long changedAt;
    private record Change(int action,int index,int amount,String key) {}
    private final Map<String,Change> pending=new LinkedHashMap<>();
    public TraderScreen(TraderMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,TraderMenu.WIDTH,TraderMenu.HEIGHT); }
    private List<PanelView.Row> rows() { return menu.view().tabs().get(tab).rows(); }
    private int pages() { return Math.max(1,(rows().size()+PAGE-1)/PAGE); }
    private void send(int action,int index,int value,String key) {
        ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,action,index,value,key));
    }
    private void flush() { for(Change c:pending.values()) send(c.action(),c.index(),c.amount(),c.key()); pending.clear(); }
    private final class Amount extends AbstractSliderButton {
        private final int action,index,max;
        private final String key,label;
        Amount(int x,int y,int width,int action,int index,String key,int count,int max,String label) {
            super(x,y,width,15,Component.empty(),Math.sqrt(Math.clamp(count,0,max)/(double)max));
            this.action=action; this.index=index; this.key=key; this.max=max; this.label=label; updateMessage();
            setTooltip(Tooltip.create(Component.literal(action==TraderPanel.RESERVE ? "Items to leave in your warehouse" : "Maximum items of this type sent per trip; zero disables it")));
        }
        private int amount() { return (int)Math.round(max*value*value); }
        @Override protected void updateMessage() { setMessage(Component.literal(label+" "+amount())); }
        @Override protected void applyValue() {
            pending.put(action+":"+index,new Change(action,index,amount(),key)); changedAt=Util.getMillis();
        }
    }
    @Override protected void init() {
        super.init(); page=Math.min(page,pages()-1);
        for(int n=0;n<3;n++) {
            int target=n; Button b=Button.builder(menu.view().tabs().get(n).name(),button -> { flush(); tab=target; page=0; rebuildWidgets(); })
                    .bounds(leftPos+8+n*76,topPos+31,74,18).build();
            b.active=tab!=n; addRenderableWidget(b);
        }
        for(int n=0;n<PAGE;n++) {
            int index=page*PAGE+n,y=topPos+TOP+n*ROW;
            if(index>=rows().size()) break;
            PanelView.Row row=rows().get(index);
            if(tab==0) {
                Button b=Button.builder(Component.literal(row.value()==1 ? "Linked" : "Connect"),button -> { flush(); button.active=false; send(TraderPanel.LINK,index,0,row.key()); })
                        .bounds(leftPos+234,y,66,15).build(); b.active=row.value()!=1; addRenderableWidget(b);
            } else if(tab==1) {
                addRenderableWidget(new Amount(leftPos+164,y,66,TraderPanel.RESERVE,index,row.key(),row.value(),4096,"Keep"));
                addRenderableWidget(new Amount(leftPos+232,y,50,TraderPanel.LOAD,index,row.key(),(int)row.bar(),64,"Send"));
                addRenderableWidget(Button.builder(Component.literal("×"),b -> { flush(); b.active=false; send(TraderPanel.FORGET,index,0,row.key()); }).bounds(leftPos+284,y,16,15).build());
            }
        }
        if(tab==2) for(int n=0;n<menu.view().actions().size();n++) {
            PanelView.Action a=menu.view().actions().get(n);
            Button b=Button.builder(a.label(),button -> { flush(); send(a.id(),0,0,""); }).bounds(leftPos+8+n*148,topPos+TOP+4*ROW,144,16).build();
            b.active=a.enabled(); addRenderableWidget(b);
        }
        else {
            Button previous=Button.builder(Component.literal("<"),b -> { flush(); page--; rebuildWidgets(); }).bounds(leftPos+8,topPos+145,16,12).build();
            previous.active=page>0; addRenderableWidget(previous);
            Button next=Button.builder(Component.literal(">"),b -> { flush(); page++; rebuildWidgets(); }).bounds(leftPos+imageWidth-25,topPos+145,16,12).build();
            next.active=page<pages()-1; addRenderableWidget(next);
        }
        layout=layout();
    }
    private String layout() {
        StringBuilder result=new StringBuilder();
        for(PanelView.Tab t:menu.view().tabs()) for(PanelView.Row row:t.rows()) result.append(row.key()).append('=').append(row.value()).append('/').append(row.bar()).append('|');
        for(PanelView.Action a:menu.view().actions()) result.append(a.label().getString()).append(a.enabled());
        return result.toString();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos;
        Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,Ui.fit(font,menu.view().title().getString(),imageWidth-16),x+8,y+7,Ui.TEXT,false);
        g.text(font,Ui.fit(font,menu.view().subtitle().getString(),imageWidth-16),x+8,y+18,Ui.MUTED,false);
        Ui.slot(g,x+TraderMenu.EXAMPLE_X,y+TraderMenu.EXAMPLE_Y);
        Ui.inset(g,x+7,y+TOP-2,imageWidth-14,PAGE*ROW+3);
        for(int n=0;n<PAGE;n++) {
            int index=page*PAGE+n;
            if(index>=rows().size()) break;
            var row=rows().get(index); int ry=y+TOP+n*ROW;
            g.fill(x+8,ry,x+imageWidth-8,ry+ROW-1,Ui.ROW);
            g.item(row.icon(),x+10,ry);
            int width=tab==1 ? 131 : tab==0 ? 198 : imageWidth-44;
            g.text(font,Ui.fit(font,row.text().getString(),width),x+30,ry,Ui.TEXT,false);
            g.text(font,Ui.fit(font,row.detail().getString(),width),x+30,ry+9,Ui.MUTED,false);
        }
        if(rows().isEmpty()) g.text(font,tab==0 ? "No towns with trader blocks in range yet" : "Show an item to the slot above to add an export",x+12,y+TOP+8,Ui.MUTED,false);
        if(tab!=2) g.text(font,(page+1)+"/"+pages(),x+imageWidth-58,y+147,Ui.MUTED,false);
        g.text(font,"Inventory",x+TraderMenu.INVENTORY_X,y+TraderMenu.INVENTORY_Y-11,Ui.TEXT,false);
        Ui.inventory(g,x+TraderMenu.INVENTORY_X,y+TraderMenu.INVENTORY_Y);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        if(!pending.isEmpty() && Util.getMillis()-changedAt>250) flush();
        if(pending.isEmpty() && Util.getMillis()-changedAt>1000 && !layout.equals(layout())) rebuildWidgets();
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if(mouseX>=leftPos+TraderMenu.EXAMPLE_X-1 && mouseX<=leftPos+TraderMenu.EXAMPLE_X+17 && mouseY>=topPos+TraderMenu.EXAMPLE_Y-1 && mouseY<=topPos+TraderMenu.EXAMPLE_Y+17)
            g.setComponentTooltipForNextFrame(font,List.of(Component.literal("Add an export"),Component.literal("Click with an item, or shift-click your inventory."),Component.literal("You keep the example item.")),mouseX,mouseY);
        for(int n=0;n<PAGE;n++) {
            int index=page*PAGE+n,ry=topPos+TOP+n*ROW;
            if(index>=rows().size()) break;
            if(mouseX>=leftPos+9 && mouseX<leftPos+(tab==1 ? 160 : 230) && mouseY>=ry && mouseY<ry+ROW)
                g.setComponentTooltipForNextFrame(font,Ui.tooltip(font,rows().get(index)),mouseX,mouseY);
        }
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(scrollY!=0 && mouseY<topPos+TOP+PAGE*ROW) {
            int next=Math.clamp(page-(int)Math.signum(scrollY),0,pages()-1);
            if(next!=page) { flush(); page=next; rebuildWidgets(); } return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
    @Override public void onClose() { flush(); super.onClose(); }
}

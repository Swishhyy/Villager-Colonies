package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.PanelMenu;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The town overview and station panels: tabs of rows that refresh every second, and the owner's buttons, two to a row. */
public final class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private static final int WIDTH=256,HEIGHT=214,ROW=22,LIST_TOP=48;
    private int tab,scroll;
    private PanelView shown;
    private String layout="";
    public PanelScreen(PanelMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title,WIDTH,HEIGHT);
    }
    private PanelView view() { return menu.view(); }
    @Override protected void init() {
        super.init();
        PanelView view=view();
        List<PanelView.Tab> tabs=view.tabs();
        tab=Math.min(tab,Math.max(0,tabs.size()-1));
        int tabWidth=Math.min(80,(imageWidth-14)/Math.max(1,tabs.size()));
        for(int i=0;i<tabs.size();i++) {
            int index=i;
            Button button=Button.builder(tabs.get(i).name(),b -> { tab=index; scroll=0; rebuildWidgets(); })
                    .bounds(leftPos+7+i*tabWidth,topPos+27,tabWidth-2,18).build();
            button.active=i!=tab;
            addRenderableWidget(button);
        }
        List<PanelView.Action> actions=view.actions();
        int rows=actionRows(),half=(imageWidth-14)/2;
        for(int i=0;i<actions.size();i++) {
            PanelView.Action action=actions.get(i);
            int row=i/2,column=i%2;
            // A last button alone on its row spans the whole width.
            boolean alone=column==0 && i==actions.size()-1;
            Button button=Button.builder(action.label(),b -> ClientPacketDistributor.sendToServer(new WwmcNetwork.ActionPayload(menu.containerId,action.id(),0,0,"")))
                    .bounds(leftPos+7+column*half,topPos+imageHeight-6-(rows-row)*22,alone ? half*2-2 : half-2,20).build();
            button.active=action.enabled();
            if(!action.tooltip().getString().isEmpty()) button.setTooltip(Tooltip.create(action.tooltip()));
            addRenderableWidget(button);
        }
        shown=view; layout=layout(view);
    }
    /** Buttons are rebuilt only when their labels change, not for every refresh. */
    private static String layout(PanelView view) {
        StringBuilder key=new StringBuilder();
        for(PanelView.Tab tab:view.tabs()) key.append(tab.name().getString()).append('|');
        for(PanelView.Action action:view.actions()) key.append(action.label().getString()).append(action.enabled()).append(action.tooltip().getString()).append('|');
        return key.toString();
    }
    private List<PanelView.Row> rows() {
        List<PanelView.Tab> tabs=view().tabs();
        return tabs.isEmpty() ? List.of() : tabs.get(Math.min(tab,tabs.size()-1)).rows();
    }
    private int actionRows() { return (view().actions().size()+1)/2; }
    private int listBottom() { return topPos+imageHeight-8-actionRows()*22; }
    private int visibleRows() { return (listBottom()-(topPos+LIST_TOP)-2)/ROW; }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos;
        Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,Ui.fit(font,view().title().getString(),imageWidth-16),x+8,y+7,Ui.TEXT,false);
        g.text(font,Ui.fit(font,view().subtitle().getString(),imageWidth-16),x+8,y+17,Ui.MUTED,false);
        int top=y+LIST_TOP,bottom=listBottom();
        Ui.inset(g,x+7,top,imageWidth-14,bottom-top);
        List<PanelView.Row> rows=rows();
        int visible=visibleRows();
        scroll=Math.clamp(scroll,0,Math.max(0,rows.size()-visible));
        for(int i=0;i<visible && scroll+i<rows.size();i++) {
            PanelView.Row row=rows.get(scroll+i);
            int rowY=top+2+i*ROW,left=x+9,width=imageWidth-22;
            boolean hover=mouseX>=left && mouseX<left+width && mouseY>=rowY && mouseY<rowY+ROW-1;
            g.fill(left,rowY,left+width,rowY+ROW-1,hover ? Ui.ROW_HOVER : Ui.ROW);
            if(!row.icon().isEmpty()) g.item(row.icon(),left+2,rowY+2);
            int textX=left+22,textWidth=width-26;
            g.text(font,Ui.fit(font,row.text().getString(),textWidth),textX,rowY+2,row.color()!=0 ? row.color() : Ui.TEXT,false);
            g.text(font,Ui.fit(font,row.detail().getString(),textWidth),textX,rowY+11,Ui.MUTED,false);
            if(row.bar()>=0) Ui.bar(g,textX,rowY+ROW-4,textWidth,row.bar(),row.color());
        }
        if(rows.size()>visible) {
            int track=bottom-top-4,thumb=Math.max(12,track*visible/rows.size());
            int thumbY=top+2+(track-thumb)*scroll/Math.max(1,rows.size()-visible);
            g.fill(x+imageWidth-11,top+2,x+imageWidth-9,bottom-2,Ui.SLOT_SHADE);
            g.fill(x+imageWidth-11,thumbY,x+imageWidth-9,thumbY+thumb,Ui.LIGHT);
        }
        if(rows.isEmpty()) g.text(font,"Nothing to show yet",x+12,top+6,Ui.MUTED,false);
    }
    /** Labels are part of the background; the default title and inventory labels do not apply. */
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        // A refresh that changes button labels (such as the alarm toggle) rebuilds them before anything is drawn.
        if(view()!=shown) { if(!layout(view()).equals(layout)) rebuildWidgets(); shown=view(); }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        List<PanelView.Row> rows=rows();
        int top=topPos+LIST_TOP,left=leftPos+9;
        for(int i=0;i<visibleRows() && scroll+i<rows.size();i++) {
            int rowY=top+2+i*ROW;
            if(mouseY<rowY || mouseY>=rowY+ROW-1 || mouseX<left || mouseX>=left+imageWidth-22) continue;
            PanelView.Row row=rows.get(scroll+i);
            if(mouseX<left+20 && !row.icon().isEmpty()) g.setTooltipForNextFrame(font,row.icon(),mouseX,mouseY);
            else if(Ui.truncated(font,row.text(),imageWidth-48) || Ui.truncated(font,row.detail(),imageWidth-48))
                g.setComponentTooltipForNextFrame(font,Ui.tooltip(font,row),mouseX,mouseY);
        }
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(scrollY!=0) { scroll-=(int)Math.signum(scrollY); return true; }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
}

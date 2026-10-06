package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.CitizenMenu;
import io.github.swishhyy.wwmc.menu.PanelView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A citizen's job, activity, health, meals and equipment above their bag. */
public final class CitizenScreen extends AbstractContainerScreen<CitizenMenu> {
    public CitizenScreen(CitizenMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        imageWidth=CitizenMenu.WIDTH; imageHeight=CitizenMenu.HEIGHT;
    }
    private List<PanelView.Row> tab(int index) { return menu.view().tabs().size()>index ? menu.view().tabs().get(index).rows() : List.of(); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        int x=leftPos,y=topPos,width=imageWidth-16;
        Ui.window(g,x,y,imageWidth,imageHeight);
        g.text(font,Ui.fit(font,menu.view().title().getString(),width-60),x+8,y+7,Ui.TEXT,false);
        String town=Ui.fit(font,menu.view().subtitle().getString(),58);
        g.text(font,town,x+imageWidth-8-font.width(town),y+7,Ui.MUTED,false);
        List<PanelView.Row> status=tab(0);
        if(!status.isEmpty()) {
            PanelView.Row job=status.getFirst();
            g.item(job.icon(),x+8,y+18);
            g.text(font,Ui.fit(font,job.text().getString(),width-20),x+28,y+18,Ui.TEXT,false);
            g.text(font,Ui.fit(font,job.detail().getString(),width-20),x+28,y+28,Ui.MUTED,false);
        }
        // Health and meals as two bars, each with its label.
        for(int i=1;i<Math.min(3,status.size());i++) {
            PanelView.Row row=status.get(i);
            int barX=x+8+(i-1)*82;
            g.text(font,Ui.fit(font,row.text().getString()+": "+row.detail().getString(),80),barX,y+39,Ui.TEXT,false);
            if(row.bar()>=0) Ui.bar(g,barX,y+49,76,row.bar(),row.color());
        }
        // Worn armor and held items.
        List<PanelView.Row> gear=tab(1);
        for(int i=0;i<gear.size() && i<6;i++) {
            int slotX=x+8+i*18,slotY=y+CitizenMenu.GEAR_Y;
            Ui.slot(g,slotX,slotY);
            g.item(gear.get(i).icon(),slotX,slotY);
        }
        if(status.size()>3) g.text(font,"Overflowing",x+120,y+CitizenMenu.GEAR_Y+4,0xFFC0392B,false);
        g.text(font,"Bag",x+CitizenMenu.BAG_X,y+CitizenMenu.BAG_Y-10,Ui.TEXT,false);
        for(int row=0;row<4;row++) for(int column=0;column<9;column++) Ui.slot(g,x+CitizenMenu.BAG_X+column*18,y+CitizenMenu.BAG_Y+row*18);
        g.text(font,"Inventory",x+CitizenMenu.INVENTORY_X,y+CitizenMenu.INVENTORY_Y-11,Ui.TEXT,false);
        Ui.inventory(g,x+CitizenMenu.INVENTORY_X,y+CitizenMenu.INVENTORY_Y);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        List<PanelView.Row> gear=tab(1);
        for(int i=0;i<gear.size() && i<6;i++) {
            int slotX=leftPos+8+i*18,slotY=topPos+CitizenMenu.GEAR_Y;
            if(mouseX>=slotX && mouseX<slotX+16 && mouseY>=slotY && mouseY<slotY+16) g.setTooltipForNextFrame(font,gear.get(i).icon(),mouseX,mouseY);
        }
        List<PanelView.Row> status=tab(0);
        if(!status.isEmpty() && mouseX>=leftPos+28 && mouseX<leftPos+imageWidth-8 && mouseY>=topPos+18 && mouseY<topPos+37)
            g.setComponentTooltipForNextFrame(font,Ui.tooltip(font,status.getFirst()),mouseX,mouseY);
    }
}

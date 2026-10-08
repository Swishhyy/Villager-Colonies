package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.WwmcNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The shared settlement map, drawn from plain rectangles: claims coloured by relation, trade routes as dotted lines,
 * expedition sites, pings and the player. Click to place the chosen ping; right-click one of yours to remove it.
 */
public final class MapScreen extends Screen {
    private static final int[] SPANS={256,512,1024,2048,4096,8192,16384};
    private static final List<String> KINDS=List.of("meet","bridge","build","danger","resource");
    private MapData map;
    private int span=2,kind,viewX,viewZ;
    public MapScreen(MapData map) {
        super(Component.literal("Settlement map"));
        this.map=map; viewX=map.centerX(); viewZ=map.centerZ();
    }
    /** Opens the map, or refreshes it in place after a ping changes. */
    public static void show(MapData map) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof MapScreen open) open.map=map; else mc.setScreen(new MapScreen(map));
    }
    private int size() { return Math.max(64,Math.min(width-20,height-64)); }
    private int left() { return (width-size())/2; }
    private int top() { return 22; }
    private double scale() { return size()/(double)SPANS[span]; }
    private int sx(double x) { return (int)Math.round(left()+size()/2.0+(x-viewX)*scale()); }
    private int sz(double z) { return (int)Math.round(top()+size()/2.0+(z-viewZ)*scale()); }
    private boolean inside(int x,int y) { return x>=left() && x<left()+size() && y>=top() && y<top()+size(); }
    private static String kindTitle(String kind) {
        return switch(kind) { case "bridge" -> "Build a bridge"; case "build" -> "Build here"; case "danger" -> "Danger"; case "resource" -> "Resources"; default -> "Meet here"; };
    }
    private static int kindColor(String kind) {
        return switch(kind) { case "bridge" -> 0xFF4FC3F7; case "build" -> 0xFFFFD54F; case "danger" -> 0xFFE53935; case "resource" -> 0xFFBA68C8; default -> 0xFFFFFFFF; };
    }
    private static int relationColor(int relation) {
        return switch(relation) { case MapData.OWN -> 0xFF3FA34D; case MapData.ALLY -> 0xFF4A90D9; case MapData.NEUTRAL -> 0xFFC9A227; case MapData.HOSTILE -> 0xFFC0392B; default -> 0xFF9A9A9A; };
    }
    private static int siteColor(int state) {
        return switch(state) { case MapData.CLEARED -> 0xFF8A8A8A; case MapData.CLAIMED -> 0xFFE0B03A; case MapData.RAID -> 0xFFFF7A00; default -> 0xFF8E1B1B; };
    }
    @Override protected void init() {
        int y=height-38,x=left(),w=size();
        int small=Math.max(16,(w-150)/8);
        Button ping=Button.builder(Component.literal("Ping: "+kindTitle(KINDS.get(kind))),b -> { kind=(kind+1)%KINDS.size(); rebuildWidgets(); })
                .bounds(x,y,110,20).build();
        ping.setTooltip(Tooltip.create(Component.literal("Click the map to place this ping for your town and its allies; right-click one of yours to remove it")));
        addRenderableWidget(ping);
        int bx=x+114;
        addRenderableWidget(Button.builder(Component.literal("W"),b -> viewX-=SPANS[span]/4).bounds(bx,y,small,20).build()); bx+=small+2;
        addRenderableWidget(Button.builder(Component.literal("N"),b -> viewZ-=SPANS[span]/4).bounds(bx,y,small,20).build()); bx+=small+2;
        addRenderableWidget(Button.builder(Component.literal("S"),b -> viewZ+=SPANS[span]/4).bounds(bx,y,small,20).build()); bx+=small+2;
        addRenderableWidget(Button.builder(Component.literal("E"),b -> viewX+=SPANS[span]/4).bounds(bx,y,small,20).build()); bx+=small+2;
        addRenderableWidget(Button.builder(Component.literal("-"),b -> span=Math.min(SPANS.length-1,span+1)).bounds(bx,y,small,20).build()); bx+=small+2;
        addRenderableWidget(Button.builder(Component.literal("+"),b -> span=Math.max(0,span-1)).bounds(bx,y,small,20).build()); bx+=small+2;
        Button me=Button.builder(Component.literal("Me"),b -> centerOnPlayer()).bounds(bx,y,Math.max(small,24),20).build();
        me.setTooltip(Tooltip.create(Component.literal("Center the map on yourself")));
        addRenderableWidget(me);
        addRenderableWidget(Button.builder(Component.literal("Done"),b -> onClose()).bounds(x+w-44,y+22,44,14).build());
    }
    private void centerOnPlayer() {
        var player=Minecraft.getInstance().player;
        if(player!=null) { viewX=player.getBlockX(); viewZ=player.getBlockZ(); }
    }
    /** A filled rectangle clipped to the map. */
    private void box(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int color) {
        int l=Math.max(left(),Math.min(x0,x1)),r=Math.min(left()+size(),Math.max(x0,x1)),t=Math.max(top(),Math.min(y0,y1)),b=Math.min(top()+size(),Math.max(y0,y1));
        if(l<r && t<b) g.fill(l,t,r,b,color);
    }
    private void outline(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int color) {
        box(g,x0,y0,x1,y0+1,color); box(g,x0,y1-1,x1,y1,color); box(g,x0,y0,x0+1,y1,color); box(g,x1-1,y0,x1,y1,color);
    }
    private void dotted(GuiGraphicsExtractor g,int x0,int y0,int x1,int y1,int color) {
        double length=Math.max(1,Math.hypot(x1-x0,y1-y0));
        for(double d=0;d<=length;d+=4) {
            int x=(int)Math.round(x0+(x1-x0)*d/length),y=(int)Math.round(y0+(y1-y0)*d/length);
            if(inside(x,y)) g.fill(x,y,x+2,y+2,color);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        int left=left(),top=top(),size=size();
        g.fill(left-2,top-2,left+size+2,top+size+2,0xFF1E1E1E);
        g.fill(left,top,left+size,top+size,0xFF33402E);
        // A faint grid every 256 blocks, so distances read at a glance.
        int step=256;
        for(int gx=Math.floorDiv(viewX-SPANS[span],step)*step;gx<=viewX+SPANS[span];gx+=step) { int x=sx(gx); if(x>left && x<left+size) g.fill(x,top,x+1,top+size,0x22FFFFFF); }
        for(int gz=Math.floorDiv(viewZ-SPANS[span],step)*step;gz<=viewZ+SPANS[span];gz+=step) { int y=sz(gz); if(y>top && y<top+size) g.fill(left,y,left+size,y+1,0x22FFFFFF); }
        List<Component> hover=new ArrayList<>();
        for(MapData.Town town:map.towns()) {
            int color=relationColor(town.relation());
            int x0=sx(town.x()-town.radius()),x1=sx(town.x()+town.radius()+1),y0=sz(town.z()-town.radius()),y1=sz(town.z()+town.radius()+1);
            box(g,x0,y0,x1,y1,(color&0x00FFFFFF)|0x30000000);
            outline(g,x0,y0,x1,y1,color);
            int cx=sx(town.x()),cy=sz(town.z());
            if(inside(cx,cy)) {
                g.fill(cx-2,cy-2,cx+3,cy+3,color);
                if(x1-x0>=24) g.text(font,Ui.fit(font,town.name(),Math.max(30,x1-x0)),cx-Math.min(font.width(town.name()),Math.max(30,x1-x0))/2,cy+4,0xFFFFFFFF,true);
            }
            if(hover.isEmpty() && mouseX>=Math.max(left,x0) && mouseX<Math.min(left+size,x1) && mouseY>=Math.max(top,y0) && mouseY<Math.min(top+size,y1)) {
                hover.add(Component.literal(town.name()));
                hover.add(Component.literal(switch(town.relation()) { case MapData.OWN -> "Your town"; case MapData.ALLY -> "Allied town"; case MapData.NEUTRAL -> "Neutral town";
                    case MapData.HOSTILE -> "Hostile town"; default -> "Another player's town"; }+" · "+town.x()+", "+town.z()+" · claim "+town.radius()));
            }
        }
        for(MapData.Route route:map.routes()) dotted(g,sx(route.x1()),sz(route.z1()),sx(route.x2()),sz(route.z2()),route.extra() ? 0xAAE0C070 : 0xFFF0D080);
        List<Component> top0=new ArrayList<>();
        for(MapData.Site site:map.sites()) {
            int x=sx(site.x()),y=sz(site.z());
            if(!inside(x,y)) continue;
            int color=siteColor(site.state());
            g.fill(x-3,y-1,x+4,y+2,color); g.fill(x-1,y-3,x+2,y+4,color);
            if(Math.abs(mouseX-x)<=4 && Math.abs(mouseY-y)<=4) { top0.clear(); top0.add(Component.literal(site.title()+" · "+site.x()+", "+site.z())); top0.add(Component.literal(site.detail())); }
        }
        for(MapData.Mark ping:map.pings()) {
            int x=sx(ping.x()),y=sz(ping.z());
            if(!inside(x,y)) continue;
            int color=kindColor(ping.kind());
            g.fill(x-1,y-4,x+2,y+5,color); g.fill(x-4,y-1,x+5,y+2,color); g.fill(x-1,y-1,x+2,y+2,0xFF000000);
            if(Math.abs(mouseX-x)<=4 && Math.abs(mouseY-y)<=4) {
                top0.clear(); top0.add(Component.literal(ping.label())); top0.add(Component.literal(ping.author()+" · "+ping.x()+", "+ping.z()+(ping.removable() ? " · right-click to remove" : "")));
            }
        }
        var player=Minecraft.getInstance().player;
        if(player!=null) {
            int x=sx(player.getX()),y=sz(player.getZ());
            if(inside(x,y)) { g.fill(x-3,y-3,x+4,y+4,0xFF000000); g.fill(x-2,y-2,x+3,y+3,0xFFFFFFFF); }
        }
        // The map's center, where the view is aimed.
        int cx=left+size/2,cy=top+size/2;
        g.fill(cx-4,cy,cx+5,cy+1,0x66FFFFFF); g.fill(cx,cy-4,cx+1,cy+5,0x66FFFFFF);
        g.text(font,"N",cx-2,top+3,0xFFFFFFFF,true);
        g.text(font,Ui.fit(font,"Settlement map · centered on "+viewX+", "+viewZ+" · "+SPANS[span]+" blocks across",width-20),left,top-12,0xFFFFFFFF,true);
        int ly=top+size+4;
        int lx=left;
        for(int relation=MapData.OWN;relation<=MapData.OTHER;relation++) {
            String label=switch(relation) { case MapData.OWN -> "Yours"; case MapData.ALLY -> "Allied"; case MapData.NEUTRAL -> "Neutral"; case MapData.HOSTILE -> "Hostile"; default -> "Other"; };
            g.fill(lx,ly+2,lx+6,ly+8,relationColor(relation)); g.text(font,label,lx+8,ly+1,0xFFDDDDDD,false); lx+=14+font.width(label);
        }
        g.fill(lx,ly+2,lx+6,ly+8,0xFF8E1B1B); g.text(font,"Sites",lx+8,ly+1,0xFFDDDDDD,false);
        if(!top0.isEmpty()) g.setComponentTooltipForNextFrame(font,top0,mouseX,mouseY);
        else if(!hover.isEmpty()) g.setComponentTooltipForNextFrame(font,hover,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        int x=(int)event.x(),y=(int)event.y();
        if(inside(x,y)) {
            if(event.button()==1) {
                for(MapData.Mark ping:map.pings()) if(ping.removable() && Math.abs(sx(ping.x())-x)<=4 && Math.abs(sz(ping.z())-y)<=4) {
                    ClientPacketDistributor.sendToServer(new WwmcNetwork.MapActionPayload(2,0,0,"",ping.id()));
                    return true;
                }
                return true;
            }
            if(event.button()==0) {
                int worldX=(int)Math.round(viewX+(x-(left()+size()/2.0))/scale()),worldZ=(int)Math.round(viewZ+(y-(top()+size()/2.0))/scale());
                ClientPacketDistributor.sendToServer(new WwmcNetwork.MapActionPayload(1,worldX,worldZ,KINDS.get(kind),""));
                return true;
            }
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(scrollY!=0) { span=Math.clamp(span-(int)Math.signum(scrollY),0,SPANS.length-1); return true; }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
}

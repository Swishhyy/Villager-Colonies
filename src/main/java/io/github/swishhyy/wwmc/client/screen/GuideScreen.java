package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.item.GuideBook;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** An illustrated, searchable handbook with actual item icons, recipe grids and short setup cards. */
public final class GuideScreen extends Screen {
    private static final int PAPER=0xFFF1E8D2,INK=0xFF292E36,MUTED=0xFF59616C,ACCENT=0xFF346E8A,CARD=0xFFFFFBED;
    private final Screen parent;
    private String topic,query="";
    private int x,y,w,h,nav,scroll,maxScroll,station;
    private EditBox search;
    private final List<Link> links=new ArrayList<>();
    private record Link(int x,int y,int width,int height,String topic) {}
    public GuideScreen(Screen parent,String topic) { super(Component.literal("Villager Colonies Settlement Guide")); this.parent=parent; this.topic=GuideBook.topic(topic).id(); }
    public static void openStation(Screen parent,io.github.swishhyy.wwmc.core.StructureRole role) {
        GuideScreen screen=new GuideScreen(parent,"stations");
        for(int i=0;i<GuideBook.STATIONS.size();i++) if(GuideBook.STATIONS.get(i).role()==role) screen.station=i;
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
    }
    public static void open(Screen parent,String topic) { net.minecraft.client.Minecraft.getInstance().gui.setScreen(new GuideScreen(parent,topic)); }
    private static boolean contains(String text,String query) { return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)); }
    private List<GuideBook.StationHelp> stations() {
        List<GuideBook.StationHelp> found=GuideBook.STATIONS.stream().filter(s -> query.isBlank() || contains(s.role().title()+" "+s.furniture()+" "+s.supplies()+" "+s.result(),query)).toList();
        return found.isEmpty() ? GuideBook.STATIONS : found;
    }
    private boolean matches(GuideBook.Topic t) {
        return query.isBlank() || contains(t.title()+" "+t.subtitle(),query)
                || t.cards().stream().anyMatch(c -> contains(c.title()+" "+c.text(),query))
                || t.id().equals("stations") && GuideBook.STATIONS.stream().anyMatch(s -> contains(s.role().title()+" "+s.furniture()+" "+s.supplies(),query));
    }
    private static String label(GuideBook.Topic topic) {
        return switch(topic.id()) { case "stations" -> "Recipes"; case "industry" -> "Workshops"; case "defense" -> "Defense"; case "frontier" -> "Frontier"; default -> topic.title(); };
    }
    private void choose(String id) { topic=id; scroll=0; station=0; query=""; rebuildWidgets(); }
    @Override protected void init() {
        w=Math.min(600,width-12); h=Math.min(350,height-12); x=(width-w)/2; y=(height-h)/2; nav=w<420 ? 94 : 124;
        List<GuideBook.Topic> topics=GuideBook.TOPICS.stream().filter(this::matches).toList();
        if(!topics.isEmpty() && topics.stream().noneMatch(t -> t.id().equals(topic))) topic=topics.getFirst().id();
        search=new EditBox(font,nav-12,18,Component.literal("Search guide"));
        search.setPosition(x+6,y+28); search.setMaxLength(48); search.setHint(Component.literal("Search...")); search.setValue(query);
        search.setResponder(value -> { query=value; scroll=0; station=0; rebuildWidgets(); setFocused(search); search.setFocused(true); });
        addRenderableWidget(search);
        int navStep=Math.min(18,(h-82)/GuideBook.TOPICS.size());
        for(int i=0;i<topics.size();i++) {
            var t=topics.get(i);
            Button button=Button.builder(Component.literal(Ui.fit(font,label(t),nav-16)),b -> choose(t.id())).bounds(x+6,y+52+i*navStep,nav-12,navStep-2).build();
            button.setTooltip(Tooltip.create(Component.literal(t.title())));
            button.active=!t.id().equals(topic); addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"),b -> onClose()).bounds(x+w-54,y+h-24,46,18).build());
        addRenderableWidget(Button.builder(Component.literal("Start"),b -> choose("start")).bounds(x+6,y+h-24,nav-12,18).build());
        if(topic.equals("stations")) {
            List<GuideBook.StationHelp> list=stations(); station=Math.clamp(station,0,list.size()-1);
            addRenderableWidget(Button.builder(Component.literal("<"),b -> { station=Math.floorMod(station-1,list.size()); scroll=0; }).bounds(left(),y+48,20,18).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b -> { station=(station+1)%list.size(); scroll=0; }).bounds(x+w-30,y+48,20,18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("^"),b -> scroll=Math.max(0,scroll-60)).bounds(x+w-24,y+76,14,16).build());
        addRenderableWidget(Button.builder(Component.literal("v"),b -> scroll=Math.min(maxScroll,scroll+60)).bounds(x+w-24,y+h-46,14,16).build());
    }
    private int left() { return x+nav+10; }
    private int contentWidth() { return w-nav-40; }
    private int contentTop() { return y+(topic.equals("stations") ? 74 : 48); }
    private int bottom() { return y+h-32; }
    private static ItemStack icon(String id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))); }
    private int text(GuiGraphicsExtractor g,String text,int left,int top,int width,int color) {
        int at=top;
        for(String line:Ui.wrap(font,text,width)) { g.text(font,line,left,at,color,false); at+=11; }
        return at;
    }
    private int card(GuiGraphicsExtractor g,GuideBook.Card card,int top,int mouseX,int mouseY) {
        int left=left(),cw=contentWidth(),lines=Ui.wrap(font,card.text(),cw-38).size();
        int headingLines=Ui.wrap(font,card.title(),cw-38).size();
        int ch=12+headingLines*11+lines*11+(!card.link().isEmpty() ? 13 : 0);
        boolean hover=!card.link().isEmpty() && mouseX>=left && mouseX<left+cw && mouseY>=top && mouseY<top+ch;
        g.fill(left,top,left+cw,top+ch,hover ? 0xFFE3F0ED : CARD);
        g.fill(left,top,left+3,top+ch,ACCENT);
        g.item(icon(card.icon()),left+8,top+8);
        int next=text(g,card.title(),left+30,top+7,cw-38,INK);
        next=text(g,card.text(),left+30,next+3,cw-38,MUTED);
        if(!card.link().isEmpty()) {
            g.text(font,"Open topic >",left+30,next+3,ACCENT,false);
            links.add(new Link(left,top,cw,ch,card.link()));
        }
        return top+ch+6;
    }
    private int flow(GuiGraphicsExtractor g,List<String> labels,int top) {
        if(labels.isEmpty()) return top;
        int cw=contentWidth(),columns=cw>=280 ? labels.size() : Math.min(3,labels.size());
        int box=cw/columns;
        for(int i=0;i<labels.size();i++) {
            int col=i%columns,row=i/columns,px=left()+col*box,py=top+row*26;
            g.fill(px,py,px+box-8,py+20,0xFFD9E6DE);
            g.text(font,Ui.fit(font,labels.get(i),box-14),px+3,py+6,INK,false);
            if(col<columns-1 && i<labels.size()-1) g.text(font,">",px+box-7,py+6,ACCENT,false);
        }
        return top+((labels.size()+columns-1)/columns)*26+4;
    }
    private int recipe(GuiGraphicsExtractor g,GuideBook.StationHelp s,int top) {
        int left=left(),cw=contentWidth();
        top=text(g,"8 planks + "+icon(s.ingredient()).getHoverName().getString(),left,top,cw,INK)+8;
        int gridX=left+Math.max(0,(cw-108)/2);
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
            int sx=gridX+col*19,sy=top+row*19;
            Ui.slot(g,sx,sy); g.item(icon(row==1 && col==1 ? s.ingredient() : "minecraft:oak_planks"),sx,sy);
        }
        g.text(font,">",gridX+64,top+23,ACCENT,false);
        Ui.slot(g,gridX+84,top+19); g.item(new ItemStack(WWMC.STATION_ITEMS.get(s.role()).get()),gridX+84,top+19);
        top+=65;
        top=card(g,new GuideBook.Card("wwmc:"+s.role().id()+"_station","Place beside",s.furniture()),top,-1,-1);
        top=card(g,new GuideBook.Card(s.ingredient(),"Supply",s.supplies()),top,-1,-1);
        return card(g,new GuideBook.Card("minecraft:chest","Produces / provides",s.result()),top,-1,-1);
    }
    private int bronzeRecipe(GuiGraphicsExtractor g,int top) {
        int left=left(),cw=contentWidth(),gridX=left+Math.max(0,(cw-108)/2);
        top=text(g,"Make bronze after Bronze Age research",left,top,cw,INK)+4;
        top=text(g,"1. Use three separate copper slots and one tin slot. Either crafting grid works.",left,top,cw,MUTED)+6;
        for(int row=0;row<2;row++) for(int col=0;col<2;col++) {
            int sx=gridX+col*19,sy=top+row*19;
            Ui.slot(g,sx,sy); g.item(icon(row==1 && col==1 ? "wwmc:tin_ingot" : "minecraft:copper_ingot"),sx,sy);
        }
        g.text(font,">",gridX+48,top+13,ACCENT,false);
        ItemStack blend=new ItemStack(WWMC.BRONZE_BLEND.get(),4);
        Ui.slot(g,gridX+66,top+9); g.item(blend,gridX+66,top+9);
        g.text(font,"4",gridX+80,top+23,INK,false);
        top+=45;
        top=text(g,"2. Smelt each bronze blend in a furnace with fuel to make one bronze ingot. A blast furnace also works.",left,top,cw,MUTED)+6;
        for(int i=0;i<3;i++) {
            int sx=gridX+i*33;
            Ui.slot(g,sx,top); g.item(icon(i==0 ? "wwmc:bronze_blend" : i==1 ? "minecraft:furnace" : "wwmc:bronze_ingot"),sx,top);
            if(i<2) g.text(font,">",sx+23,top+4,ACCENT,false);
        }
        return top+28;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(g,mouseX,mouseY,partialTick);
        g.fill(x,y,x+w,y+h,0xFF243540); g.fill(x+nav,y+3,x+w-3,y+h-3,PAPER);
        g.text(font,"VILLAGER",x+8,y+6,0xFFFFE0A0,false);
        g.text(font,"COLONIES",x+8,y+17,0xFFFFE0A0,false);
        var t=GuideBook.topic(topic);
        g.text(font,Ui.fit(font,t.title(),w-nav-22),left(),y+10,INK,false);
        g.text(font,Ui.fit(font,t.subtitle(),w-nav-22),left(),y+24,MUTED,false);
        if(topic.equals("stations")) {
            var s=stations().get(station);
            g.text(font,Ui.fit(font,s.role().title()+"  "+(station+1)+"/"+stations().size(),contentWidth()-50),left()+25,y+53,INK,false);
        }
        if(!query.isBlank() && GuideBook.TOPICS.stream().noneMatch(this::matches)) g.text(font,"No matches",x+8,y+54,0xFFFFFFFF,false);
        links.clear(); int top=contentTop(),at=top-scroll;
        g.enableScissor(left(),top,x+w-28,bottom());
        if(topic.equals("stations")) at=recipe(g,stations().get(station),at);
        else {
            at=flow(g,t.flow(),at);
            if(topic.equals("research")) at=bronzeRecipe(g,at);
            for(var c:t.cards()) at=card(g,c,at,mouseX,mouseY);
        }
        if(!t.tip().isEmpty()) {
            at+=4; g.fill(left(),at,left()+contentWidth(),at+1,ACCENT);
            at=text(g,"Tip: "+t.tip(),left()+4,at+7,contentWidth()-8,ACCENT)+4;
        }
        g.disableScissor();
        maxScroll=Math.max(0,at+scroll-bottom()); scroll=Math.clamp(scroll,0,maxScroll);
        if(maxScroll>0) {
            int track=bottom()-top,thumb=Math.max(14,track*track/(track+maxScroll)),sy=top+(track-thumb)*scroll/maxScroll;
            g.fill(x+w-7,top,x+w-5,bottom(),0xFFC1BAAA); g.fill(x+w-7,sy,x+w-5,sy+thumb,ACCENT);
        }
        if(top+20<bottom()) g.text(font,maxScroll>0 ? "Scroll for more" : "Click a topic",left(),y+h-18,MUTED,false);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(super.mouseClicked(event,doubleClick)) return true;
        if(event.button()==0 && event.y()>=contentTop() && event.y()<bottom()) {
            for(Link link:links) if(event.x()>=link.x && event.x()<link.x+link.width && event.y()>=link.y && event.y()<link.y+link.height) { choose(link.topic); return true; }
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        if(mouseX>=left() && mouseX<x+w && mouseY>=contentTop() && mouseY<bottom()) { scroll=Math.clamp(scroll-(int)(scrollY*32),0,maxScroll); return true; }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}

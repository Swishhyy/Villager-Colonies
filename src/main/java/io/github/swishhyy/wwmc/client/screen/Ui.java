package io.github.swishhyy.wwmc.client.screen;

import io.github.swishhyy.wwmc.menu.PanelView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Vanilla-styled panels drawn from plain rectangles, so the screens need no texture files. */
final class Ui {
    static final int FACE=0xFFC6C6C6,LIGHT=0xFFFFFFFF,SHADE=0xFF555555,EDGE=0xFF000000,SLOT=0xFF8B8B8B,SLOT_SHADE=0xFF373737;
    static final int TEXT=0xFF404040,MUTED=0xFF6A6A6A,ROW=0xFFB4B4B4,ROW_HOVER=0xFFA8A8B8,BAR_BACK=0xFF5A5A5A;
    private Ui() {}
    /** A raised window with a black outline, like a chest's. */
    static void window(GuiGraphicsExtractor g,int x,int y,int width,int height) {
        g.fill(x+1,y,x+width-1,y+height,EDGE);
        g.fill(x,y+1,x+width,y+height-1,EDGE);
        g.fill(x+1,y+1,x+width-1,y+height-1,FACE);
        g.fill(x+1,y+1,x+width-2,y+3,LIGHT);
        g.fill(x+1,y+1,x+3,y+height-2,LIGHT);
        g.fill(x+2,y+height-3,x+width-1,y+height-1,SHADE);
        g.fill(x+width-3,y+2,x+width-1,y+height-1,SHADE);
        g.fill(x+3,y+3,x+width-3,y+height-3,FACE);
    }
    /** A sunken area: a list well or an item slot. */
    static void inset(GuiGraphicsExtractor g,int x,int y,int width,int height) {
        g.fill(x,y,x+width,y+height,SLOT);
        g.fill(x,y,x+width-1,y+1,SLOT_SHADE);
        g.fill(x,y,x+1,y+height-1,SLOT_SHADE);
        g.fill(x+1,y+height-1,x+width,y+height,LIGHT);
        g.fill(x+width-1,y+1,x+width,y+height,LIGHT);
    }
    /** The background of an item slot whose item sits at (x, y). */
    static void slot(GuiGraphicsExtractor g,int x,int y) { inset(g,x-1,y-1,18,18); }
    /** Rows of slots for a player inventory whose first slot sits at (x, y), with the hotbar 58 pixels lower. */
    static void inventory(GuiGraphicsExtractor g,int x,int y) {
        for(int row=0;row<3;row++) for(int column=0;column<9;column++) slot(g,x+column*18,y+row*18);
        for(int column=0;column<9;column++) slot(g,x+column*18,y+58);
    }
    static void bar(GuiGraphicsExtractor g,int x,int y,int width,float fraction,int color) {
        g.fill(x,y,x+width,y+2,BAR_BACK);
        g.fill(x,y,x+Math.round(width*Math.clamp(fraction,0F,1F)),y+2,color==0 ? 0xFF3FA34D : color);
    }
    /** Text shortened with an ellipsis to fit the width. */
    static String fit(Font font,String text,int width) {
        if(font.width(text)<=width) return text;
        String cut=text;
        while(!cut.isEmpty() && font.width(cut+"…")>width) cut=cut.substring(0,cut.length()-1);
        return cut+"…";
    }
    static boolean truncated(Font font,Component text,int width) { return font.width(text.getString())>width; }
    /** Hover text for a row: its heading and detail, wrapped to a readable width. */
    static List<Component> tooltip(Font font,PanelView.Row row) {
        List<Component> lines=new ArrayList<>(List.of(row.text()));
        for(String line:wrap(font,row.detail().getString(),220)) lines.add(Component.literal(line));
        return lines;
    }
    static List<String> wrap(Font font,String text,int width) {
        List<String> lines=new ArrayList<>();
        StringBuilder line=new StringBuilder();
        for(String word:text.split(" ")) {
            if(!line.isEmpty() && font.width(line+" "+word)>width) { lines.add(line.toString()); line.setLength(0); }
            if(!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if(!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}

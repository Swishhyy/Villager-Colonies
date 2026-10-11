package io.github.swishhyy.wwmc.client.map;

import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.MapMarkers;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import xaero.lib.client.graphics.XaeroBufferProvider;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.render.*;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.gui.GuiMap;

/** World Map also works alone, without Xaero's Minimap or its waypoint manager. */
public final class XaeroWorldMapCompat implements ExternalMaps.Provider {
    private final Markers context=new Markers();
    private boolean registered;
    @Override public String id() { return "xaeroworldmap"; }
    @Override public void sync(MapData map) {
        context.dimension=map.dimension(); context.markers=MapMarkers.from(map);
        // With Minimap installed, its native third-party waypoints already appear on World Map.
        if(!registered && !ModList.get().isLoaded("xaerominimap") && WorldMap.mapElementRenderHandler!=null) {
            WorldMap.mapElementRenderHandler.add(new Renderer(context)); registered=true;
        }
    }
    @Override public boolean open() {
        var session=WorldMapSession.getCurrentSession(); var mc=Minecraft.getInstance();
        if(session==null || !session.isUsable() || mc.player==null) return false;
        mc.gui.setScreen(new GuiMap(null,null,session.getMapProcessor(),mc.getCameraEntity())); return true;
    }
    @Override public void clear() { context.markers=List.of(); context.dimension=""; }
    private static final class Markers {
        String dimension="",renderDimension="";
        List<MapMarkers.Marker> markers=List.of();
        Iterator<MapMarkers.Marker> iterator=Collections.emptyIterator();
    }
    private static final class Provider extends ElementRenderProvider<MapMarkers.Marker,Markers> {
        @Override public void begin(ElementRenderLocation location,Markers c) { c.iterator=c.markers.iterator(); }
        @Override public boolean hasNext(ElementRenderLocation location,Markers c) { return c.iterator.hasNext(); }
        @Override public MapMarkers.Marker getNext(ElementRenderLocation location,Markers c) { return c.iterator.next(); }
        @Override public void end(ElementRenderLocation location,Markers c) { c.iterator=Collections.emptyIterator(); }
    }
    private static final class Reader extends ElementReader<MapMarkers.Marker,Markers,Renderer> {
        @Override public boolean isHidden(MapMarkers.Marker m,Markers c) { return !c.dimension.equals(c.renderDimension); }
        @Override public double getRenderX(MapMarkers.Marker m,Markers c,float ticks) { return m.pos().getX()+0.5; }
        @Override public double getRenderZ(MapMarkers.Marker m,Markers c,float ticks) { return m.pos().getZ()+0.5; }
        @Override public int getInteractionBoxLeft(MapMarkers.Marker m,Markers c,float ticks) { return -6; }
        @Override public int getInteractionBoxRight(MapMarkers.Marker m,Markers c,float ticks) { return 6; }
        @Override public int getInteractionBoxTop(MapMarkers.Marker m,Markers c,float ticks) { return -6; }
        @Override public int getInteractionBoxBottom(MapMarkers.Marker m,Markers c,float ticks) { return 6; }
        @Override public int getRenderBoxLeft(MapMarkers.Marker m,Markers c,float ticks) { return -6; }
        @Override public int getRenderBoxRight(MapMarkers.Marker m,Markers c,float ticks) { return 6; }
        @Override public int getRenderBoxTop(MapMarkers.Marker m,Markers c,float ticks) { return -6; }
        @Override public int getRenderBoxBottom(MapMarkers.Marker m,Markers c,float ticks) { return 6; }
        @Override public int getLeftSideLength(MapMarkers.Marker m,Minecraft mc) { return mc.font.width(m.label()); }
        @Override public String getMenuName(MapMarkers.Marker m) { return m.label(); }
        @Override public String getFilterName(MapMarkers.Marker m) { return m.label(); }
        @Override public int getMenuTextFillLeftPadding(MapMarkers.Marker m) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(MapMarkers.Marker m) { return 0xFF000000|m.color(); }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return false; }
        @Override public boolean isInteractable(ElementRenderLocation location,MapMarkers.Marker m) { return location==ElementRenderLocation.WORLD_MAP; }
        @Override public boolean isRightClickValid(MapMarkers.Marker m) { return false; }
    }
    private static final class Renderer extends ElementRenderer<MapMarkers.Marker,Markers,Renderer> {
        Renderer(Markers context) { super(context,new Provider(),new Reader()); }
        @Override public boolean shouldBeDimScaled() { return false; }
        @Override public boolean shouldRender(ElementRenderLocation location,boolean shadow) { return location==ElementRenderLocation.WORLD_MAP; }
        @Override public void preRender(ElementRenderInfo info,XaeroBufferProvider buffer,MultiTextureRenderTypeRendererProvider textures,boolean shadow) {
            context.renderDimension=info.mapDimension.identifier().toString();
        }
        @Override public void postRender(ElementRenderInfo info,XaeroBufferProvider buffer,MultiTextureRenderTypeRendererProvider textures,boolean shadow) {}
        @Override public void renderElementShadow(MapMarkers.Marker marker,boolean hovered,float brightness,double scale,double optionalScale,ElementRenderInfo info,
                MapElementGraphics graphics,XaeroBufferProvider buffer,MultiTextureRenderTypeRendererProvider textures) {}
        @Override public boolean renderElement(MapMarkers.Marker marker,boolean hovered,double optionalScale,float brightness,double scale,double zoom,ElementRenderInfo info,
                MapElementGraphics graphics,XaeroBufferProvider buffer,MultiTextureRenderTypeRendererProvider textures) {
            graphics.fill(-6,-6,6,6,0xFF202020); graphics.fill(-5,-5,5,5,0xFF000000|marker.color());
            graphics.drawCenteredString(Minecraft.getInstance().font,marker.symbol(),0,-4,0xFF202020);
            if(hovered) graphics.drawCenteredString(Minecraft.getInstance().font,marker.label(),0,-18,0xFFFFFFFF);
            return false;
        }
    }
}

package io.github.swishhyy.vc.client.map;

import io.github.swishhyy.vc.menu.MapData;
import io.github.swishhyy.vc.menu.MapGeometry;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import xaero.lib.client.graphics.XaeroBufferProvider;
import xaero.map.WorldMap;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.render.*;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/** Native World Map renderer; also registered when Minimap supplies the location waypoints. */
final class XaeroMapOverlays {
    private final Context context=new Context();
    void sync(MapData map) { context.dimension=map.dimension(); context.shapes=MapGeometry.from(map); }
    void clear() { context.dimension=""; context.shapes=List.of(); }
    void register() { WorldMap.mapElementRenderHandler.add(new Renderer(context)); }
    private static final class Context {
        String dimension="",renderDimension="";
        double scale=1;
        List<MapGeometry.Shape> shapes=List.of();
        Iterator<MapGeometry.Shape> iterator=Collections.emptyIterator();
    }
    private static final class Provider extends ElementRenderProvider<MapGeometry.Shape,Context> {
        @Override public void begin(ElementRenderLocation location,Context c) { c.iterator=c.shapes.iterator(); }
        @Override public boolean hasNext(ElementRenderLocation location,Context c) { return c.iterator.hasNext(); }
        @Override public MapGeometry.Shape getNext(ElementRenderLocation location,Context c) { return c.iterator.next(); }
        @Override public void end(ElementRenderLocation location,Context c) { c.iterator=Collections.emptyIterator(); }
    }
    private static final class Reader extends ElementReader<MapGeometry.Shape,Context,Renderer> {
        @Override public boolean isHidden(MapGeometry.Shape s,Context c) { return !c.dimension.equals(c.renderDimension); }
        @Override public double getRenderX(MapGeometry.Shape s,Context c,float ticks) { return s.x(); }
        @Override public double getRenderZ(MapGeometry.Shape s,Context c,float ticks) { return s.z(); }
        @Override public int getInteractionBoxLeft(MapGeometry.Shape s,Context c,float t) { return -6; }
        @Override public int getInteractionBoxRight(MapGeometry.Shape s,Context c,float t) { return 6; }
        @Override public int getInteractionBoxTop(MapGeometry.Shape s,Context c,float t) { return -6; }
        @Override public int getInteractionBoxBottom(MapGeometry.Shape s,Context c,float t) { return 6; }
        @Override public int getRenderBoxLeft(MapGeometry.Shape s,Context c,float t) { return (int)Math.floor((s.points().stream().mapToInt(MapData.Point::x).min().orElse(0)-s.x())*c.scale)-64; }
        @Override public int getRenderBoxRight(MapGeometry.Shape s,Context c,float t) { return (int)Math.ceil((s.points().stream().mapToInt(MapData.Point::x).max().orElse(0)-s.x())*c.scale)+64; }
        @Override public int getRenderBoxTop(MapGeometry.Shape s,Context c,float t) { return (int)Math.floor((s.points().stream().mapToInt(MapData.Point::z).min().orElse(0)-s.z())*c.scale)-16; }
        @Override public int getRenderBoxBottom(MapGeometry.Shape s,Context c,float t) { return (int)Math.ceil((s.points().stream().mapToInt(MapData.Point::z).max().orElse(0)-s.z())*c.scale)+16; }
        @Override public int getLeftSideLength(MapGeometry.Shape s,Minecraft mc) { return mc.font.width(s.label()); }
        @Override public String getMenuName(MapGeometry.Shape s) { return s.label(); }
        @Override public String getFilterName(MapGeometry.Shape s) { return s.label(); }
        @Override public int getMenuTextFillLeftPadding(MapGeometry.Shape s) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(MapGeometry.Shape s) { return 0xFF000000|s.color(); }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return false; }
        @Override public boolean isInteractable(ElementRenderLocation l,MapGeometry.Shape s) { return false; }
        @Override public boolean isRightClickValid(MapGeometry.Shape s) { return false; }
    }
    private static final class Renderer extends ElementRenderer<MapGeometry.Shape,Context,Renderer> {
        Renderer(Context c) { super(c,new Provider(),new Reader()); }
        @Override public int getOrder() { return -10; }
        @Override public boolean shouldBeDimScaled() { return false; }
        @Override public boolean shouldRender(ElementRenderLocation l,boolean shadow) { return l==ElementRenderLocation.WORLD_MAP && !shadow; }
        @Override public void preRender(ElementRenderInfo info,XaeroBufferProvider b,MultiTextureRenderTypeRendererProvider textures,boolean shadow) {
            context.renderDimension=info.mapDimension.identifier().toString(); context.scale=Math.max(0.0001,info.scale);
        }
        @Override public void postRender(ElementRenderInfo i,XaeroBufferProvider b,MultiTextureRenderTypeRendererProvider textures,boolean shadow) {}
        @Override public void renderElementShadow(MapGeometry.Shape s,boolean h,float bright,double x,double z,ElementRenderInfo i,MapElementGraphics g,XaeroBufferProvider b,MultiTextureRenderTypeRendererProvider textures) {}
        @Override public boolean renderElement(MapGeometry.Shape s,boolean hover,double depth,float bright,double fractionX,double fractionZ,
                ElementRenderInfo i,MapElementGraphics g,XaeroBufferProvider b,MultiTextureRenderTypeRendererProvider textures) {
            var pose=g.pose(); pose.pushPose(); pose.translate(fractionX,fractionZ,depth);
            try {
                int count=s.points().size(),edges=s.closed() ? count : count-1;
                for(int n=0;n<edges;n++) {
                    var a=s.points().get(n); var end=s.points().get((n+1)%count);
                    double dx=(end.x()-a.x())*context.scale,dz=(end.z()-a.z())*context.scale;
                    pose.pushPose();
                    pose.translate((a.x()-s.x())*context.scale,(a.z()-s.z())*context.scale,0);
                    pose.mulPose(new Quaternionf().rotationZ((float)Math.atan2(dz,dx)));
                    g.fill(0,-1,(int)Math.ceil(Math.hypot(dx,dz)),1,(s.extra() ? 0x70000000 : 0xCC000000)|s.color());
                    pose.popPose();
                }
                if(s.closed()) g.drawCenteredString(Minecraft.getInstance().font,s.label(),0,-10,0xFF000000|s.color());
            } finally { pose.popPose(); }
            return false;
        }
    }
}

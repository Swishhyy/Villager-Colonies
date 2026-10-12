package io.github.swishhyy.wwmc.client.map;

import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.MapMarkers;
import io.github.swishhyy.wwmc.menu.MapGeometry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Discovered by JourneyMap itself; VC never references this class when JourneyMap is absent. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class JourneyMapCompat implements IClientPlugin,ExternalMaps.Provider {
    private IClientAPI api;
    private final Map<String,Waypoint> waypoints=new HashMap<>();
    private List<MapMarkers.Marker> previous=List.of();
    private List<MapGeometry.Shape> previousShapes=List.of();
    private final java.util.ArrayList<PolygonOverlay> overlays=new java.util.ArrayList<>();
    private String dimension="",world="";
    @Override public String getModId() { return VC.MODID; }
    @Override public String id() { return "journeymap"; }
    @Override public void initialize(IClientAPI api) { this.api=api; ExternalMaps.register(this); }
    /** A guarded fallback for loader builds whose annotation scan did not initialize the plugin. */
    public static ExternalMaps.Provider connect() throws ReflectiveOperationException {
        var bridge=new JourneyMapCompat();
        var api=(IClientAPI)Class.forName("journeymap.api.client.impl.ClientAPI").getField("INSTANCE").get(null);
        bridge.initialize(api); return bridge;
    }
    @Override public void sync(MapData map) {
        String current=api.getWorldId();
        if(current==null || current.isEmpty()) return;
        if(!current.equals(world) || !map.dimension().equals(dimension)) { clear(); world=current; dimension=map.dimension(); }
        var markers=MapMarkers.from(map);
        if(!markers.equals(previous)) {
        var wanted=new java.util.HashSet<String>();
        for(var marker:markers) {
            wanted.add(marker.id());
            Waypoint waypoint=waypoints.get(marker.id());
            boolean fresh=waypoint==null;
            if(fresh) {
                waypoint=WaypointFactory.createWaypoint(VC.MODID,marker.pos(),marker.label(),map.dimension(),false);
                waypoint.setCustomData("vc_marker",marker.id());
                waypoint.setShowBeacon(false); waypoint.setShowInWorld(false); waypoint.setShowOnLocatorBar(false);
                waypoint.setShowOnMap(true); waypoint.setShowLabel(true); waypoint.setShowIcon(true);
                waypoints.put(marker.id(),waypoint);
            }
            waypoint.setName(marker.label()); waypoint.setBlockPos(marker.pos()); waypoint.setColor(marker.color()); waypoint.setDescription(marker.detail());
            api.addWaypoint(VC.MODID,waypoint);
        }
        for(String id:List.copyOf(waypoints.keySet())) if(!wanted.contains(id)) api.removeWaypoint(VC.MODID,waypoints.remove(id));
        previous=markers;
        }
        var shapes=MapGeometry.from(map);
        if(shapes.equals(previousShapes)) return;
        for(var overlay:overlays) api.remove(overlay);
        overlays.clear();
        var dim=ResourceKey.create(Registries.DIMENSION,Identifier.parse(map.dimension()));
        for(var shape:shapes) {
            if(shape.closed()) {
                var polygon=new MapPolygon(shape.points().stream().map(p -> new BlockPos(p.x(),0,p.z())).toList());
                var overlay=new PolygonOverlay(VC.MODID,dim,new ShapeProperties().setStrokeColor(shape.color()).setStrokeOpacity(0.85F)
                        .setStrokeWidth(2).setFillColor(shape.color()).setFillOpacity(0.04F),polygon);
                overlay.setLabel(shape.label()); overlay.setTitle(shape.label()+" · settlement border");
                overlay.getTextProperties().setColor(shape.color()).setFontShadow(true).setBackgroundOpacity(0.4F);
                show(overlay);
            } else for(int n=1;n<shape.points().size();n++) {
                var a=shape.points().get(n-1); var b=shape.points().get(n);
                double length=Math.hypot(b.x()-a.x(),b.z()-a.z()); if(length==0) continue;
                int dx=(int)Math.round(-(b.z()-a.z())/length),dz=(int)Math.round((b.x()-a.x())/length);
                var polygon=new MapPolygon(new BlockPos(a.x()+dx,0,a.z()+dz),new BlockPos(b.x()+dx,0,b.z()+dz),
                        new BlockPos(b.x()-dx,0,b.z()-dz),new BlockPos(a.x()-dx,0,a.z()-dz));
                var overlay=new PolygonOverlay(VC.MODID,dim,new ShapeProperties().setStrokeColor(shape.color()).setStrokeWidth(1.5F)
                        .setStrokeOpacity(shape.extra() ? 0.4F : 0.8F).setFillColor(shape.color()).setFillOpacity(0.5F),polygon);
                overlay.setTitle(shape.label()); overlay.setDisplayOrder(900); show(overlay);
            }
        }
        previousShapes=shapes;
    }
    private void show(PolygonOverlay overlay) {
        try { api.show(overlay); overlays.add(overlay); }
        catch(Exception e) { throw new IllegalStateException("JourneyMap overlays are not ready",e); }
    }
    @Override public boolean open() {
        // API v2 has no fullscreen-opening method. This guarded bridge matches JourneyMap 26.2's UI manager.
        try {
            var manager=Class.forName("journeymap.client.ui.UIManager");
            return manager.getMethod("getOrOpenFullscreenMap").invoke(manager.getField("INSTANCE").get(null))!=null;
        } catch(ReflectiveOperationException | LinkageError e) { VC.LOGGER.warn("[VC] JourneyMap fullscreen bridge is unavailable",e); return false; }
    }
    @Override public void clear() {
        if(api!=null) for(var waypoint:waypoints.values()) api.removeWaypoint(VC.MODID,waypoint);
        if(api!=null) for(var overlay:overlays) api.remove(overlay);
        overlays.clear(); previousShapes=List.of();
        waypoints.clear(); previous=List.of(); dimension=""; world="";
    }
}

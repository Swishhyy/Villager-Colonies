package io.github.swishhyy.wwmc.client.map;

import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.MapMarkers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;

/** Discovered by JourneyMap itself; VC never references this class when JourneyMap is absent. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class JourneyMapCompat implements IClientPlugin,ExternalMaps.Provider {
    private IClientAPI api;
    private final Map<String,Waypoint> waypoints=new HashMap<>();
    private List<MapMarkers.Marker> previous=List.of();
    private String dimension="",world="";
    @Override public String getModId() { return VC.MODID; }
    @Override public String id() { return "journeymap"; }
    @Override public void initialize(IClientAPI api) { this.api=api; ExternalMaps.register(this); }
    @Override public void sync(MapData map) {
        String current=api.getWorldId();
        if(current==null || current.isEmpty()) return;
        if(!current.equals(world) || !map.dimension().equals(dimension)) { clear(); world=current; dimension=map.dimension(); }
        var markers=MapMarkers.from(map);
        if(markers.equals(previous)) return;
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
    @Override public boolean open() {
        // API v2 has no fullscreen-opening method. This guarded bridge matches JourneyMap 26.2's UI manager.
        try {
            var manager=Class.forName("journeymap.client.ui.UIManager");
            return manager.getMethod("getOrOpenFullscreenMap").invoke(manager.getField("INSTANCE").get(null))!=null;
        } catch(ReflectiveOperationException | LinkageError e) { VC.LOGGER.warn("[VC] JourneyMap fullscreen bridge is unavailable",e); return false; }
    }
    @Override public void clear() {
        if(api!=null) for(var waypoint:waypoints.values()) api.removeWaypoint(VC.MODID,waypoint);
        waypoints.clear(); previous=List.of(); dimension=""; world="";
    }
}

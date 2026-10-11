package io.github.swishhyy.wwmc.client.map;

import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.MapMarkers;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointVisibilityType;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

/** Xaero's third-party waypoint channel is shared with its World Map when both maps are installed. */
public final class XaeroMinimapCompat implements ExternalMaps.Provider {
    private static final Identifier ORIGIN=Identifier.fromNamespaceAndPath(VC.MODID,"settlements");
    private MinimapWorldContainer container;
    private List<MapMarkers.Marker> previous=List.of();
    @Override public String id() { return "xaerominimap"; }
    @Override public void sync(MapData map) {
        var session=XaeroMinimapSession.getCurrentSession();
        if(session==null) return;
        var world=session.getMinimapProcessor().getSession().getWorldManager().getAutoWorld();
        if(world==null) return;
        var next=world.getContainer();
        if(!map.dimension().equals(next.getEquivalentDimId().toString())) return;
        if(container!=next) { clear(); container=next; }
        var markers=MapMarkers.from(map);
        if(markers.equals(previous)) return;
        var channel=container.getThirdPartyWaypointManager().get(ORIGIN);
        var wanted=new java.util.HashSet<String>();
        for(var marker:markers) {
            wanted.add(marker.id());
            var waypoint=channel.get(marker.id());
            if(waypoint==null) {
                waypoint=new Waypoint(marker.pos().getX(),marker.pos().getY(),marker.pos().getZ(),marker.label(),marker.symbol(),palette(marker.color()));
                waypoint.setTemporary(true); waypoint.setVisibility(WaypointVisibilityType.GLOBAL); waypoint.setYIncluded(true);
                channel.add(marker.id(),waypoint);
            } else {
                waypoint.setName(marker.label()); waypoint.setX(marker.pos().getX()); waypoint.setY(marker.pos().getY()); waypoint.setZ(marker.pos().getZ()); waypoint.setColor(palette(marker.color()));
            }
        }
        for(String id:List.copyOf(channel.getWaypoints().keySet())) if(!wanted.contains(id)) channel.remove(id);
        previous=markers;
    }
    private static int palette(int rgb) {
        return switch(rgb) { case 0x55CC77 -> 2; case 0x5599FF -> 9; case 0xE8C65B,0xDDAA55 -> 14; case 0xEE5555 -> 12; case 0xAA77EE -> 13; default -> 7; };
    }
    @Override public boolean open() {
        var session=XaeroMinimapSession.getCurrentSession();
        if(session==null) return false;
        Minecraft.getInstance().gui.setScreen(null);
        session.getMinimapProcessor().setEnlargedMap(true);
        return true;
    }
    @Override public void clear() {
        if(container!=null) container.getThirdPartyWaypointManager().clearOrigin(ORIGIN);
        container=null; previous=List.of();
    }
}

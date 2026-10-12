package io.github.swishhyy.vc.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Stable, dimension-specific marker identities shared by both optional map adapters. */
public final class MapMarkers {
    private MapMarkers() {}
    public record Marker(String id,String label,BlockPos pos,int color,String symbol,String detail) {}
    public static int relationColor(int relation) {
        return switch(relation) {
            case MapData.OWN -> 0x55CC77;
            case MapData.ALLY -> 0x5599FF;
            case MapData.NEUTRAL -> 0xE8C65B;
            case MapData.HOSTILE -> 0xEE5555;
            default -> 0xBBBBBB;
        };
    }
    private static String relation(int relation) {
        return switch(relation) {
            case MapData.OWN -> "Your town";
            case MapData.ALLY -> "Allied town";
            case MapData.NEUTRAL -> "Neutral NPC town";
            case MapData.HOSTILE -> "Hostile NPC town";
            default -> "Another player's town";
        };
    }
    public static List<Marker> from(MapData map) {
        var markers=new ArrayList<Marker>();
        String prefix=map.dimension()+"/";
        for(var town:map.towns()) markers.add(new Marker(prefix+"town:"+town.id(),town.name(),new BlockPos(town.x(),town.y(),town.z()),
                relationColor(town.relation()),"T",relation(town.relation())+" · claim radius "+town.radius()+" blocks"));
        for(var site:map.sites()) markers.add(new Marker(prefix+"site:"+site.id(),site.title(),new BlockPos(site.x(),site.y(),site.z()),
                site.state()==MapData.RAID ? 0xEE5555 : site.state()==MapData.CLAIMED ? 0x55CC77 : site.state()==MapData.CLEARED ? 0xBBBBBB : 0xDDAA55,
                "!",site.detail()));
        for(var ping:map.pings()) markers.add(new Marker(prefix+"ping:"+ping.id(),ping.label(),new BlockPos(ping.x(),ping.y(),ping.z()),
                ping.kind().equals("danger") ? 0xEE5555 : 0xAA77EE,"P",ping.kind()+" · "+ping.author()
                +(ping.removable() ? " · remove: /vc ping remove "+ping.id() : "")));
        return List.copyOf(markers);
    }
}

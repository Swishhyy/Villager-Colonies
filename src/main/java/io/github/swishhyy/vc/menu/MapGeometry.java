package io.github.swishhyy.vc.menu;

import java.util.ArrayList;
import java.util.List;

/** Actual square claim edges and authorized logistics paths, shared by the optional maps. */
public final class MapGeometry {
    public record Shape(String id,String label,List<MapData.Point> points,int color,boolean closed,boolean extra) {
        public Shape { points=List.copyOf(points); }
        public double x() { return points.stream().mapToInt(MapData.Point::x).average().orElse(0); }
        public double z() { return points.stream().mapToInt(MapData.Point::z).average().orElse(0); }
    }
    private MapGeometry() {}
    public static List<Shape> from(MapData map) {
        List<Shape> shapes=new ArrayList<>(); String prefix=map.dimension()+"/";
        for(var town:map.towns()) {
            int x=town.x(),z=town.z(),r=Math.clamp(town.radius(),1,4096);
            shapes.add(new Shape(prefix+"claim:"+town.id(),town.name(),List.of(new MapData.Point(x-r,z-r),
                    new MapData.Point(x+r+1,z-r),new MapData.Point(x+r+1,z+r+1),new MapData.Point(x-r,z+r+1)),
                    MapMarkers.relationColor(town.relation()),true,false));
        }
        int n=0;
        for(var route:map.routes()) shapes.add(new Shape(prefix+"route:"+n++,"Trade route",route.path(),
                route.extra() ? 0xA7BFE8 : 0xFFB84D,false,route.extra()));
        return List.copyOf(shapes);
    }
}

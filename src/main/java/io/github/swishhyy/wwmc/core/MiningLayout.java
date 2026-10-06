package io.github.swishhyy.wwmc.core;

import java.util.ArrayList;
import java.util.List;

/** Immutable excavation geometry. No world access and no generated resources. */
public final class MiningLayout {
    public record Cell(int x,int y,int z) {
        public Cell offset(int dx,int dy,int dz) { return new Cell(x+dx,y+dy,z+dz); }
    }
    public record Cut(Cell foot,Cell stand,int offsetY) {
        public Cell block() { return foot.offset(0,offsetY,0); }
    }
    private MiningLayout() {}
    private static void direction(int dx,int dz) {
        if(Math.abs(dx)+Math.abs(dz)!=1) throw new IllegalArgumentException("Use a cardinal direction");
    }
    public static List<Cut> tunnel(int x,int y,int z,int dx,int dz,int targetY,int length,int branches) {
        direction(dx,dz);
        if(targetY>=y || y-targetY>512 || length<1 || length>64 || branches<1 || branches>16)
            throw new IllegalArgumentException("Invalid tunnel dimensions");
        List<Cut> result=new ArrayList<>();
        Cell stand=new Cell(x+dx,y,z+dz),foot=null;
        for(int depth=1;depth<=y-targetY;depth++) {
            foot=new Cell(x+dx*(depth+1),y-depth,z+dz*(depth+1));
            // Extra headroom allows walking down each one-block stair.
            for(int offset=2;offset>=0;offset--) result.add(new Cut(foot,stand,offset));
            stand=foot;
        }
        Cell bottom=foot;
        for(int step=1;step<=branches*3;step++) {
            foot=bottom.offset(dx*step,0,dz*step);
            result.add(new Cut(foot,stand,1)); result.add(new Cut(foot,stand,0)); stand=foot;
        }
        // Interleave the branch fronts so several crews can work independently.
        for(int depth=1;depth<=length;depth++) for(int branch=1;branch<=branches;branch++) for(int side:new int[]{-1,1}) {
            Cell junction=bottom.offset(dx*branch*3,0,dz*branch*3);
            foot=junction.offset(-dz*side*depth,0,dx*side*depth);
            stand=junction.offset(-dz*side*(depth-1),0,dx*side*(depth-1));
            result.add(new Cut(foot,stand,1)); result.add(new Cut(foot,stand,0));
        }
        return List.copyOf(result);
    }
    public static RoomBounds quarry(int x,int z,int dx,int dz,int topY,int targetY) {
        direction(dx,dz);
        if(targetY>topY || topY-targetY>512) throw new IllegalArgumentException("Invalid quarry depth");
        // The operator block remains on the neighboring chunk, outside the excavation.
        int minX=(Math.floorDiv(x,16)+dx)*16,minZ=(Math.floorDiv(z,16)+dz)*16;
        return new RoomBounds(minX,targetY,minZ,minX+15,topY,minZ+15);
    }
    public static Cell quarryCell(RoomBounds bounds,int index) {
        int total=(bounds.maxY()-bounds.minY()+1)*256;
        if(index<0 || index>=total) throw new IndexOutOfBoundsException(index);
        int column=index%256;
        return new Cell(bounds.minX()+column%16,bounds.maxY()-index/256,bounds.minZ()+column/16);
    }
    /** Cells around the edge of a 16x16 quarry, which carries its spiral staircase. */
    public static final int RING=60;
    /** The i-th edge cell, clockwise from the north-west corner. */
    public static Cell ringCell(RoomBounds bounds,int index,int y) {
        int i=Math.floorMod(index,RING);
        if(i<16) return new Cell(bounds.minX()+i,y,bounds.minZ());
        if(i<31) return new Cell(bounds.maxX(),y,bounds.minZ()+i-15);
        if(i<46) return new Cell(bounds.maxX()-(i-30),y,bounds.maxZ());
        return new Cell(bounds.minX(),y,bounds.maxZ()-(i-45));
    }
    /** Position of a column on the edge ring, or -1 for an interior or outside column. */
    public static int ringIndex(RoomBounds bounds,int x,int z) {
        if(x<bounds.minX() || x>bounds.maxX() || z<bounds.minZ() || z>bounds.maxZ()) return -1;
        if(z==bounds.minZ()) return x-bounds.minX();
        if(x==bounds.maxX()) return 15+z-bounds.minZ();
        if(z==bounds.maxZ()) return 30+bounds.maxX()-x;
        if(x==bounds.minX()) return 45+bounds.maxZ()-z;
        return -1;
    }
    /** The staircase starts on the edge facing the station, level with the rim the crew walks in from. */
    public static int ringStart(RoomBounds bounds,int stationX,int stationZ,int dx,int dz) {
        direction(dx,dz);
        int x=dx==0 ? Math.clamp(stationX,bounds.minX(),bounds.maxX()) : dx>0 ? bounds.minX() : bounds.maxX();
        int z=dz==0 ? Math.clamp(stationZ,bounds.minZ(),bounds.maxZ()) : dz>0 ? bounds.minZ() : bounds.maxZ();
        return ringIndex(bounds,x,z);
    }
    /**
     * Step i of the spiral staircase is the solid edge block left at {@code top-i}, one place further around the
     * ring than step i-1. Everything else is dug, so each step has headroom and the next one is a block lower.
     */
    public static boolean stair(RoomBounds bounds,int start,int top,int x,int y,int z) {
        if(y>top || y<bounds.minY()) return false;
        int ring=ringIndex(bounds,x,z);
        return ring>=0 && ring==Math.floorMod(start+top-y,RING);
    }
    /** Where a worker stands on step i: on top of the step block. */
    public static Cell stairStand(RoomBounds bounds,int start,int top,int step) {
        return ringCell(bounds,start+step,top-step+1);
    }
}

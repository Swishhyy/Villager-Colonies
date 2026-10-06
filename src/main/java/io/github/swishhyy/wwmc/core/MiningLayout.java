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
}

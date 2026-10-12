package io.github.swishhyy.vc.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** A five-block road swept along joined segments, rather than disconnected perpendicular slices. */
public final class RoadShape {
    private RoadShape() {}
    public static List<BlockPos> columns(List<BlockPos> line,int index) {
        BlockPos c=line.get(index),a=line.get(Math.max(0,index-1)),b=line.get(Math.min(line.size()-1,index+1));
        List<BlockPos> result=new ArrayList<>();
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) {
            double previous=distance(dx,dz,a.getX()-c.getX(),a.getZ()-c.getZ());
            double next=distance(dx,dz,b.getX()-c.getX(),b.getZ()-c.getZ());
            if(Math.min(previous,next)<=4.000001) result.add(c.offset(dx,0,dz));
        }
        return List.copyOf(result);
    }
    private static double distance(double x,double z,double dx,double dz) {
        double length=dx*dx+dz*dz,t=length==0 ? 0 : Math.clamp((x*dx+z*dz)/length,0,1);
        double ox=x-t*dx,oz=z-t*dz; return ox*ox+oz*oz;
    }
}

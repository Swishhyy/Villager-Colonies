package io.github.swishhyy.wwmc.settlement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Plans a trader's route over a coarse map of 4x4 block cells. Roads are cheapest and forest is dearer than open
 * ground, so a cleared highway beats a straight line through the woods; water is crossed only where a bridge deck makes
 * a cell walkable. Land not yet seen is assumed walkable but costs a little more than known open ground.
 */
public final class TradePlanner {
    /** Blocks along each side of a cell. */
    public static final int CELL=4;
    /** What is known about one cell. A cell nobody has seen yet is {@link #UNKNOWN}. */
    public record Cell(int height,int flags,int column) {
        public static final int SEEN=1,PASSABLE=2,ROAD=4,FOREST=8,WATER=16;
        public static final Cell UNKNOWN=new Cell(0,0,-1);
        public boolean seen() { return (flags&SEEN)!=0; }
        /** Unseen land counts as passable until someone sees otherwise. */
        public boolean passable() { return !seen() || (flags&PASSABLE)!=0; }
        public boolean is(int flag) { return (flags&flag)!=0; }
    }
    public interface Terrain {
        Cell cell(int cellX,int cellZ);
        /** A cell a trader recently failed to reach, avoided for a while. */
        default boolean blocked(int cellX,int cellZ) { return false; }
    }
    /** Cost per block on each kind of ground. */
    static final double ROAD=0.6,OPEN=1.0,FOREST=1.6,UNSEEN=1.25,SHORE=0.15,CLIMB=0.75;
    /** Largest height change between neighbouring cells: one block per block walked. */
    private static final int MAX_STEP=CELL;
    /** A waypoint every three cells, about twelve blocks apart. */
    private static final int SPACING=3;
    /** Cells examined per plan at most; a search that runs out heads for the known land nearest the target. */
    private static final int MAX_EXPANSIONS=30000;
    /**
     * Expected cost per block still to go. A little above the road cost, so a route may come out up to a third longer
     * than the very best one beside a road, for about half the search.
     */
    private static final double ESTIMATE=0.8;
    private TradePlanner() {}

    public static int cellOf(int block) { return Math.floorDiv(block,CELL); }
    private static double cost(Cell cell) {
        if(!cell.seen()) return UNSEEN;
        double base=cell.is(Cell.ROAD) ? ROAD : cell.is(Cell.FOREST) ? FOREST : OPEN;
        return cell.is(Cell.WATER) ? base+SHORE : base;
    }

    /**
     * Waypoints from just past the start to the target, ending at the target itself. When the known land offers no way
     * within {@code margin} blocks of the straight line, the route ends where it gets nearest the target instead, and
     * it is empty when no step gets any nearer.
     */
    public static List<BlockPos> plan(Terrain terrain,BlockPos from,BlockPos to,int margin) {
        int sx=cellOf(from.getX()),sz=cellOf(from.getZ()),gx=cellOf(to.getX()),gz=cellOf(to.getZ());
        int pad=Math.max(1,margin/CELL);
        int minX=Math.min(sx,gx)-pad,minZ=Math.min(sz,gz)-pad,width=Math.abs(sx-gx)+2*pad+1,depth=Math.abs(sz-gz)+2*pad+1;
        int size=width*depth,start=(sz-minZ)*width+(sx-minX),goal=(gz-minZ)*width+(gx-minX);
        Cell[] cells=new Cell[size];
        double[] g=new double[size];
        int[] parent=new int[size];
        boolean[] closed=new boolean[size];
        Arrays.fill(g,Double.POSITIVE_INFINITY);
        Arrays.fill(parent,-1);
        Frontier open=new Frontier();
        g[start]=0;
        open.push(start,heuristic(sx,sz,gx,gz));
        int best=start,expanded=0;
        double nearest=heuristic(sx,sz,gx,gz);
        while(!open.isEmpty() && expanded++<MAX_EXPANSIONS) {
            int current=open.pop();
            if(closed[current]) continue;
            closed[current]=true;
            if(current==goal) { best=goal; break; }
            int cx=minX+current%width,cz=minZ+current/width;
            double left=heuristic(cx,cz,gx,gz);
            if(left<nearest) { nearest=left; best=current; }
            Cell here=cell(terrain,cells,current,cx,cz);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dz==0) continue;
                int nx=cx+dx,nz=cz+dz;
                if(nx<minX || nz<minZ || nx>=minX+width || nz>=minZ+depth) continue;
                int index=(nz-minZ)*width+(nx-minX);
                if(closed[index]) continue;
                Cell there=cell(terrain,cells,index,nx,nz);
                if(index!=goal && (!there.passable() || terrain.blocked(nx,nz))) continue;
                boolean diagonal=dx!=0 && dz!=0;
                // Never slip diagonally between two impassable cells, such as across the corner of a river bend.
                if(diagonal && (!walkable(terrain,cells,minX,minZ,width,cx+dx,cz) || !walkable(terrain,cells,minX,minZ,width,cx,cz+dz))) continue;
                int climb=here.seen() && there.seen() && here.passable() && there.passable() ? Math.abs(here.height()-there.height()) : 0;
                if(climb>MAX_STEP+(diagonal ? 1 : 0)) continue;
                double step=(diagonal ? DIAGONAL : 1)*CELL*(cost(here)+cost(there))/2+CLIMB*climb;
                double next=g[current]+step;
                if(next<g[index]) {
                    g[index]=next; parent[index]=current;
                    open.push(index,next+heuristic(nx,nz,gx,gz));
                }
            }
        }
        if(best==start) return List.of();
        List<Integer> path=new ArrayList<>();
        for(int at=best;at!=-1 && at!=start;at=parent[at]) path.add(at);
        java.util.Collections.reverse(path);
        List<BlockPos> waypoints=new ArrayList<>();
        int y=from.getY();
        for(int n=0;n<path.size();n++) {
            int index=path.get(n);
            int cx=minX+index%width,cz=minZ+index/width;
            Cell cell=cell(terrain,cells,index,cx,cz);
            if(cell.seen() && cell.passable()) y=cell.height();
            if(n==path.size()-1) { waypoints.add(index==goal ? to.immutable() : position(cell,cx,cz,y)); break; }
            // Keep every bridge and road step on narrow decks; elsewhere one waypoint every few cells is enough.
            if((n+1)%SPACING!=0 && !(cell.is(Cell.WATER) && cell.is(Cell.ROAD))) continue;
            waypoints.add(position(cell,cx,cz,y));
        }
        return waypoints;
    }
    private static final double DIAGONAL=Math.sqrt(2);
    /** A binary min-heap of cells keyed by estimated route cost; a cell queued again keeps its old entry, skipped once closed. */
    private static final class Frontier {
        private int[] cells=new int[256];
        private double[] keys=new double[256];
        private int size;
        boolean isEmpty() { return size==0; }
        void push(int cell,double key) {
            if(size==cells.length) { cells=Arrays.copyOf(cells,size*2); keys=Arrays.copyOf(keys,size*2); }
            int at=size++;
            while(at>0) {
                int parent=(at-1)/2;
                if(keys[parent]<=key) break;
                cells[at]=cells[parent]; keys[at]=keys[parent]; at=parent;
            }
            cells[at]=cell; keys[at]=key;
        }
        int pop() {
            int top=cells[0],last=cells[--size];
            double key=keys[size];
            int at=0;
            while(true) {
                int child=2*at+1;
                if(child>=size) break;
                if(child+1<size && keys[child+1]<keys[child]) child++;
                if(keys[child]>=key) break;
                cells[at]=cells[child]; keys[at]=keys[child]; at=child;
            }
            cells[at]=last; keys[at]=key;
            return top;
        }
    }
    private static Cell cell(Terrain terrain,Cell[] cells,int index,int cx,int cz) {
        Cell cell=cells[index];
        if(cell==null) { cell=terrain.cell(cx,cz); if(cell==null) cell=Cell.UNKNOWN; cells[index]=cell; }
        return cell;
    }
    private static boolean walkable(Terrain terrain,Cell[] cells,int minX,int minZ,int width,int cx,int cz) {
        int index=(cz-minZ)*width+(cx-minX);
        return cell(terrain,cells,index,cx,cz).passable() && !terrain.blocked(cx,cz);
    }
    private static double heuristic(int x,int z,int gx,int gz) { return Math.sqrt((double)(x-gx)*(x-gx)+(double)(z-gz)*(z-gz))*CELL*ESTIMATE; }
    /** The cell's best standing column (a road or the most central), or its middle when unseen. */
    private static BlockPos position(Cell cell,int cx,int cz,int y) {
        if(cell.seen() && cell.column()>=0) return new BlockPos(cx*CELL+(cell.column()&3),cell.height(),cz*CELL+(cell.column()>>2));
        return new BlockPos(cx*CELL+CELL/2,y,cz*CELL+CELL/2);
    }
}

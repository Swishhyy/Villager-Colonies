package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The old roads: dirt roads laid down with new terrain between the native town sites near the world centre. The
 * {@code vc:settlements} structure set offers one site in each of its regions; a walled town stands on most of them, a
 * walled city on one in nine where the land allows, and the rest become crossroads. Roads join neighbouring sites at
 * their gates, follow gentle ground around hills, tunnel through steep ones, keep clear of other sites and bridge only
 * narrow water. Everything comes from the seed and the terrain noise, never from loaded chunks,
 * so each chunk paves exactly its own part of every road in whatever order chunks generate. Towns joined by a road trade
 * along it once both have been found.
 */
public final class RoadNetwork {
    static final ResourceKey<StructureSet> SETTLEMENTS=ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"));
    /** Blocks between survey samples; the most the ground may rise or fall between two samples on a road. */
    private static final int GRID=16,MAX_RISE=8;
    /** Blocks between height samples along a surveyed road; the heights between are interpolated. */
    private static final int PROFILE_STEP=4;
    /** Roads keep this far from other sites' centres, and give up rather than bridge more open water than this. */
    private static final int CLEARANCE=40,MAX_BRIDGE=64;
    /** Roads keep this far from a city's centre: clear of its corner towers, with room for the survey grid. */
    private static final int CITY_CLEARANCE=64;
    /** Most a road cuts into or builds up the ground; a deeper dip is spanned by a plank deck instead. */
    private static final int MAX_CUT=4,MAX_FILL=4;
    /**
     * Where a hill is too steep to climb gently, the road keeps a gentle grade and tunnels through. A road deeper under
     * the land than an open cutting may go runs in a tunnel, which needs two blocks of rock over its roof somewhere and
     * a sensible length; gaps too short to open to the sky are roofed over, and no tunnel starts beside a gate.
     */
    private static final int ENTER=MAX_CUT+1,COVER=7,MERGE=4,MIN_TUNNEL=8,MAX_TUNNEL=48,TUNNEL_MARGIN=12;
    /** Grades tried through a hill, gentlest first, as rise over run; the steepest is a stair cut through a cliff. */
    private static final int[][] GRADES={{1,4},{1,2},{1,1}};
    /** Clear height in a tunnel, the open road this close to a tunnel that may cut deeper to meet it, and blocks between lanterns. */
    private static final int HEADROOM=4,APPROACH=8,LAMP_SPACING=6;
    /** Blocks around a chunk whose roads it works out, so walls, timbers and lanterns agree with the chunks beside it. */
    private static final int REACH=8;
    /** Largest survey corridor, in samples. */
    private static final int MAX_CELLS=60000;
    /** Shortest stretch of saved road between traders' waypoints, so long roads stay small in the save. */
    private static final int SAVED_SPACING=4;
    /** Towns a site links to by road: its neighbours, or the towns beyond a crossroads. A town saves four extra routes. */
    private static final int MAX_LINKS=4;
    private static final Map<ServerLevel,RoadNetwork> NETWORKS=new WeakHashMap<>();
    private static final ExecutorService SURVEYOR=Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"VC road survey"); thread.setDaemon(true); return thread;
    });

    /** A site the structure set offers in region {@code i,j}, and whether a road may reach it. */
    public record Node(int i,int j,int x,int z,boolean land) {
        public long key() { return pack(i,j); }
    }
    /** A surveyed road: its centre line, with the height a walker stands at, whether it crosses water and where it runs underground. */
    public record Road(int[] xs,int[] zs,int[] feet,boolean[] water,boolean[] tunnel,int minX,int maxX,int minZ,int maxZ) {
        public int length() { return xs.length; }
        boolean near(int x0,int z0,int x1,int z1) { return maxX>=x0-1 && minX<=x1+1 && maxZ>=z0-1 && minZ<=z1+1; }
        /** Whether this column of open road leads into a tunnel, so it cuts down to the portal rather than stepping up. */
        boolean approach(int n) {
            for(int k=Math.max(0,n-APPROACH);k<=Math.min(length()-1,n+APPROACH);k++) if(tunnel[k]) return true;
            return false;
        }
    }
    /** Heights along a course and the columns that run in a tunnel. */
    public record Profile(int[] feet,boolean[] tunnel) {}

    private final long seed;
    private final ChunkGenerator generator;
    private final RandomState random;
    private final ServerLevel level;
    private final RandomSpreadStructurePlacement placement;
    private final int spacing,seaLevel;
    private final Map<Long,Node> nodes=new ConcurrentHashMap<>();
    private final Map<Long,Optional<Road>> roads=new ConcurrentHashMap<>();
    private final Map<Long,Road> spokes=new ConcurrentHashMap<>();
    private final Map<Long,Boolean> cities=new ConcurrentHashMap<>();

    private RoadNetwork(ServerLevel level,RandomSpreadStructurePlacement placement) {
        this.level=level; this.placement=placement; seed=level.getSeed();
        generator=level.getChunkSource().getGenerator(); random=level.getChunkSource().randomState();
        spacing=placement.spacing(); seaLevel=generator.getSeaLevel();
    }
    /** The network of an Overworld, or null when roads are disabled or a data pack changed how towns are placed. */
    public static RoadNetwork of(ServerLevel level) {
        if(!Config.OLD_ROADS.get() || Config.OLD_ROAD_RADIUS.get()<=0 || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return null;
        synchronized(NETWORKS) {
            var network=NETWORKS.get(level);
            if(network!=null) return network;
            var spread=placement(level.registryAccess());
            if(spread==null) return null;
            network=new RoadNetwork(level,spread);
            NETWORKS.put(level,network);
            return network;
        }
    }
    /** How the settlement structure set places its sites, or null when a data pack changed it. */
    static RandomSpreadStructurePlacement placement(net.minecraft.core.RegistryAccess access) {
        var set=access.lookupOrThrow(Registries.STRUCTURE_SET).get(SETTLEMENTS);
        return set.isPresent() && set.get().value().placement() instanceof RandomSpreadStructurePlacement spread ? spread : null;
    }
    /** Forgets the networks of a stopping server; each holds its level. */
    public static void forget(net.minecraft.server.MinecraftServer server) {
        synchronized(NETWORKS) { NETWORKS.keySet().removeIf(level -> level.getServer()==server); }
    }
    private static long pack(int a,int b) { return (long)a<<32 | b&0xffffffffL; }
    private static int radius() { return Config.OLD_ROAD_RADIUS.get(); }
    private static boolean inside(int x,int z) { int r=radius(); return Math.abs(x)<=r && Math.abs(z)<=r; }
    /** The first block of air above the ground or the water, from the terrain noise alone. */
    private int height(int x,int z) { return generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random); }

    // ---------- Sites and their links ----------
    public Node node(int i,int j) {
        return nodes.computeIfAbsent(pack(i,j),key -> {
            ChunkPos chunk=placement.getPotentialStructureChunk(seed,i*spacing,j*spacing);
            int x=chunk.x()*16+8,z=chunk.z()*16+8;
            return new Node(i,j,x,z,inside(x,z) && height(x,z)>seaLevel);
        });
    }
    /** The site whose centre is this town's centre, if the town stands on the network. */
    public Node nodeAt(BlockPos center) {
        var chunk=ChunkPos.containing(center);
        Node node=node(Math.floorDiv(chunk.x(),spacing),Math.floorDiv(chunk.z(),spacing));
        return node.land() && node.x()==center.getX() && node.z()==center.getZ() ? node : null;
    }
    /** Hashes a square of four sites, deciding its diagonal road. */
    private long mix(int i,int j) {
        long h=seed^(i*0x9E3779B97F4A7C15L)^(j*0xC2B2AE3D27D4EB4FL);
        h^=h>>>31; h*=0xBF58476D1CE4E5B9L; h^=h>>>29; return h;
    }
    /** Neighbouring sites joined by a road: the four beside it, and some across the corners of its squares. */
    public List<Node> neighbours(Node node) {
        if(!node.land()) return List.of();
        List<Node> result=new ArrayList<>();
        int i=node.i(),j=node.j();
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) result.add(node(i+d[0],j+d[1]));
        // One square in three gets a diagonal, alternating which way it runs, so the grid does not show.
        for(int a=i-1;a<=i;a++) for(int b=j-1;b<=j;b++) {
            long h=mix(a,b);
            if(Math.floorMod(h,3)!=0) continue;
            boolean falling=(h&8)==0;
            if(falling && a==i && b==j) result.add(node(i+1,j+1));
            if(falling && a==i-1 && b==j-1) result.add(node(i-1,j-1));
            if(!falling && a==i-1 && b==j) result.add(node(i-1,j+1));
            if(!falling && a==i && b==j-1) result.add(node(i+1,j-1));
        }
        result.removeIf(other -> !other.land());
        return result;
    }
    private static Direction side(Node from,Node to) { return NpcVillagePlan.side(to.x()-from.x(),to.z()-from.z()); }
    /** Whether a city stands, or would stand, on this site: the structure's own decision, from the seed and terrain noise. */
    public boolean city(Node node) {
        Boolean known=cities.get(node.key());
        if(known!=null) return known;
        // Worked out outside the map's lock: it reads a few hundred noise heights and grades the city.
        boolean city=node.land() && NpcTownPlan.cityRegion(seed,node.i(),node.j())
                && NpcTownStructure.citySite(generator,level,random,generator.getBiomeSource(),ChunkPos.containing(new BlockPos(node.x(),0,node.z())))!=null;
        Boolean raced=cities.putIfAbsent(node.key(),city);
        return raced==null ? city : raced;
    }
    private BlockPos gate(Node node,Direction side) {
        var offset=NpcVillagePlan.gateOffset(city(node) ? NpcVillageLayout.CITY : NpcVillageLayout.LEVELLED,side);
        return new BlockPos(node.x()+offset.x(),0,node.z()+offset.z());
    }

    // ---------- Surveying ----------
    /** The road between two sites, from the first in key order to the second; null where none can be built. */
    public Road road(Node a,Node b) {
        Node first=a.key()<b.key() ? a : b,second=first==a ? b : a;
        long key=first.key()*31+second.key();
        return roads.computeIfAbsent(key,k -> Optional.ofNullable(survey(first,second))).orElse(null);
    }
    /** The short street from a site's gate to its centre: a crossroads where no town stands. */
    public Road spoke(Node node,Direction side) {
        return spokes.computeIfAbsent(node.key()*8+side.ordinal(),k -> {
            BlockPos gate=gate(node,side);
            return profile(line(List.of(new double[]{gate.getX(),gate.getZ()},new double[]{node.x(),node.z()})),false);
        });
    }
    private record Cell(int x,int z) {}
    private Road survey(Node a,Node b) {
        BlockPos start=gate(a,side(a,b)),end=gate(b,side(b,a));
        double distance=Math.sqrt(start.distSqr(end));
        int margin=Math.max(96,(int)(distance/3));
        int minX=Math.min(start.getX(),end.getX())-margin,minZ=Math.min(start.getZ(),end.getZ())-margin;
        int width=(Math.max(start.getX(),end.getX())+margin-minX)/GRID+1,depth=(Math.max(start.getZ(),end.getZ())+margin-minZ)/GRID+1;
        if((long)width*depth>MAX_CELLS) return null;
        int cells=width*depth;
        int[] heights=new int[cells]; Arrays.fill(heights,Integer.MIN_VALUE);
        // Other sites may hold a walled town: roads go round them, not through their walls.
        List<int[]> avoid=new ArrayList<>();
        for(int i=Math.min(a.i(),b.i())-1;i<=Math.max(a.i(),b.i())+1;i++) for(int j=Math.min(a.j(),b.j())-1;j<=Math.max(a.j(),b.j())+1;j++) {
            Node other=node(i,j);
            if(other.key()!=a.key() && other.key()!=b.key()) avoid.add(new int[]{other.x(),other.z(),city(other) ? CITY_CLEARANCE : CLEARANCE});
        }
        int startCell=index(start,minX,minZ,width),goal=index(end,minX,minZ,width);
        double[] cost=new double[cells]; Arrays.fill(cost,Double.POSITIVE_INFINITY);
        int[] parent=new int[cells]; Arrays.fill(parent,-1);
        boolean[] closed=new boolean[cells];
        var open=new PriorityQueue<double[]>(Comparator.comparingDouble(e -> e[0]));
        cost[startCell]=0; open.add(new double[]{0,startCell});
        while(!open.isEmpty()) {
            int current=(int)open.poll()[1];
            if(closed[current]) continue;
            closed[current]=true;
            if(current==goal) break;
            int cx=current%width,cz=current/width;
            int here=sample(heights,current,minX,minZ,width);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dz==0) continue;
                int nx=cx+dx,nz=cz+dz;
                if(nx<0 || nz<0 || nx>=width || nz>=depth) continue;
                int next=nz*width+nx;
                if(closed[next]) continue;
                int bx=minX+nx*GRID,bz=minZ+nz*GRID;
                if(next!=goal && avoid.stream().anyMatch(p -> (p[0]-bx)*(p[0]-bx)+(p[1]-bz)*(p[1]-bz)<p[2]*p[2])) continue;
                int there=sample(heights,next,minX,minZ,width);
                boolean wet=there<=seaLevel,dry=here>seaLevel;
                int rise=Math.abs(Math.max(there,seaLevel+1)-Math.max(here,seaLevel+1));
                if(rise>MAX_RISE && !(wet || !dry)) continue;
                double length=(dx!=0 && dz!=0 ? Math.sqrt(2) : 1)*GRID;
                // Level ground is cheapest: a road prefers a longer way round to climbing over a hill, and keeps
                // bridges as short as it can.
                double step=length*(wet ? 8 : 1)+rise*rise*3;
                if(cost[current]+step<cost[next]) {
                    cost[next]=cost[current]+step; parent[next]=current;
                    int gx=goal%width-nx,gz=goal/width-nz;
                    open.add(new double[]{cost[next]+Math.sqrt(gx*gx+gz*gz)*GRID,next});
                }
            }
        }
        if(parent[goal]<0) return null;
        List<double[]> points=new ArrayList<>();
        int water=0;
        for(int at=goal;at!=-1;at=parent[at]) {
            if(heights[at]<=seaLevel) { if(++water*GRID>MAX_BRIDGE) return null; } else water=0;
            points.add(new double[]{minX+at%width*GRID,minZ+at/width*GRID});
        }
        Collections.reverse(points);
        points.set(0,new double[]{start.getX(),start.getZ()}); points.set(points.size()-1,new double[]{end.getX(),end.getZ()});
        return profile(line(smooth(smooth(points))),true);
    }
    private static int index(BlockPos pos,int minX,int minZ,int width) {
        return Math.floorDiv(pos.getZ()-minZ+GRID/2,GRID)*width+Math.floorDiv(pos.getX()-minX+GRID/2,GRID);
    }
    private int sample(int[] heights,int cell,int minX,int minZ,int width) {
        if(heights[cell]==Integer.MIN_VALUE) heights[cell]=height(minX+cell%width*GRID,minZ+cell/width*GRID);
        return heights[cell];
    }
    /** Rounds off the corners of the surveyed course, keeping both ends where they are. */
    private static List<double[]> smooth(List<double[]> points) {
        if(points.size()<3) return points;
        List<double[]> result=new ArrayList<>(); result.add(points.getFirst());
        for(int n=0;n<points.size()-1;n++) {
            double[] p=points.get(n),q=points.get(n+1);
            result.add(new double[]{p[0]*0.75+q[0]*0.25,p[1]*0.75+q[1]*0.25});
            result.add(new double[]{p[0]*0.25+q[0]*0.75,p[1]*0.25+q[1]*0.75});
        }
        result.add(points.getLast()); return result;
    }
    /** Every block column along the course, in order. */
    private static List<int[]> line(List<double[]> points) {
        List<int[]> columns=new ArrayList<>();
        columns.add(new int[]{(int)Math.round(points.getFirst()[0]),(int)Math.round(points.getFirst()[1])});
        for(int n=0;n<points.size()-1;n++) {
            double[] p=points.get(n),q=points.get(n+1);
            int steps=(int)Math.ceil(Math.max(Math.abs(q[0]-p[0]),Math.abs(q[1]-p[1]))*2)+1;
            for(int s=0;s<=steps;s++) {
                int x=(int)Math.round(p[0]+(q[0]-p[0])*s/steps),z=(int)Math.round(p[1]+(q[1]-p[1])*s/steps);
                if(columns.getLast()[0]!=x || columns.getLast()[1]!=z) columns.add(new int[]{x,z});
            }
        }
        return columns;
    }
    /** The road along a course, with tunnels through steep hills where it is not a town's own street. */
    private Road profile(List<int[]> columns,boolean tunnels) {
        int n=columns.size();
        int[] xs=new int[n],zs=new int[n],natural=new int[n];
        boolean[] water=new boolean[n];
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        int[] sampled=new int[n];
        for(int i=0;i<n;i++) {
            xs[i]=columns.get(i)[0]; zs[i]=columns.get(i)[1];
            if(i%PROFILE_STEP==0 || i==n-1) sampled[i]=height(xs[i],zs[i]);
            minX=Math.min(minX,xs[i]); maxX=Math.max(maxX,xs[i]); minZ=Math.min(minZ,zs[i]); maxZ=Math.max(maxZ,zs[i]);
        }
        // Terrain noise is costly to read: sample every few blocks and interpolate between.
        for(int i=0;i<n;i++) {
            int before=i-i%PROFILE_STEP,after=Math.min(before+PROFILE_STEP,n-1);
            int h=after==before ? sampled[before] : Math.round(sampled[before]+(sampled[after]-sampled[before])*(float)(i-before)/(after-before));
            water[i]=h<=seaLevel; natural[i]=water[i] ? seaLevel+1 : h;
        }
        Profile graded=grade(natural,water,tunnels);
        return new Road(xs,zs,graded.feet(),water,graded.tunnel(),minX,maxX,minZ,maxZ);
    }
    /**
     * Heights along a course from the land under it (water reads as one above sea level): eased to at most a block per
     * block walked within the cut and fill a road may make, except where a gentler grade through a hill is better.
     */
    public static Profile grade(int[] natural,boolean[] water,boolean tunnels) {
        int n=natural.length;
        int[] low=natural.clone(),high=natural.clone(),eased=new int[n],feet=new int[n];
        for(int i=1;i<n;i++) { low[i]=Math.min(low[i],low[i-1]+1); high[i]=Math.max(high[i],high[i-1]-1); }
        for(int i=n-2;i>=0;i--) { low[i]=Math.min(low[i],low[i+1]+1); high[i]=Math.max(high[i],high[i+1]-1); }
        for(int i=0;i<n;i++) {
            eased[i]=Math.floorDiv(low[i]+high[i],2);
            feet[i]=water[i] ? Math.max(eased[i],natural[i]) : Math.clamp(eased[i],natural[i]-MAX_CUT,natural[i]+MAX_FILL);
        }
        boolean[] tunnel=new boolean[n],done=new boolean[n];
        if(!tunnels) return new Profile(feet,tunnel);
        for(int[] grade:GRADES) {
            // The highest line at this grade that never rises above the land: it follows the valleys and runs under the hills.
            int[] gentle=new int[n];
            for(int i=0;i<n;i++) gentle[i]=natural[i]*grade[1];
            for(int i=1;i<n;i++) gentle[i]=Math.min(gentle[i],gentle[i-1]+grade[0]);
            for(int i=n-2;i>=0;i--) gentle[i]=Math.min(gentle[i],gentle[i+1]+grade[0]);
            for(int i=0;i<n;i++) gentle[i]=Math.floorDiv(gentle[i],grade[1]);
            for(int a=0,b;a<n;a=b+1) {
                b=a;
                if(gentle[a]>=eased[a]) continue;
                while(b+1<n && gentle[b+1]<eased[b+1]) b++;
                bore(natural,water,gentle,feet,tunnel,done,a,b,grade[0]>=grade[1]);
            }
        }
        return new Profile(feet,tunnel);
    }
    /**
     * Takes the gentle line along a stretch where it dips under the eased road, if every part too deep for a cutting can
     * be a tunnel. A tunnel as steep as the open road only climbs a cliff the open road cannot step up.
     */
    private static void bore(int[] natural,boolean[] water,int[] gentle,int[] feet,boolean[] tunnel,boolean[] done,int a,int b,boolean steepest) {
        int n=natural.length;
        for(int i=a;i<=b;i++) if(done[i]) return;
        if(steepest) {
            boolean cliff=false;
            for(int i=Math.max(1,a);i<=Math.min(n-1,b+1);i++) cliff|=Math.abs(feet[i]-feet[i-1])>1;
            if(!cliff) return;
        }
        List<int[]> runs=new ArrayList<>();
        for(int i=a;i<=b;i++) {
            if(water[i] || natural[i]-gentle[i]<ENTER) continue;
            int start=i;
            while(i<b && !water[i+1] && natural[i+1]-gentle[i+1]>=ENTER) i++;
            int[] last=runs.isEmpty() ? null : runs.getLast();
            boolean wet=false;
            if(last!=null) for(int k=last[1]+1;k<start;k++) wet|=water[k];
            // A gap too short to open to the sky is roofed over, so a tunnel has no skylights or stubs.
            if(last!=null && start-last[1]-1<=MERGE && !wet) last[1]=i;
            else runs.add(new int[]{start,i});
        }
        if(runs.isEmpty()) return;
        boolean[] under=new boolean[n];
        for(int[] run:runs) {
            int deepest=0,length=run[1]-run[0]+1;
            for(int i=run[0];i<=run[1];i++) { deepest=Math.max(deepest,natural[i]-gentle[i]); under[i]=true; }
            if(length<MIN_TUNNEL || length>MAX_TUNNEL || deepest<COVER || run[0]<TUNNEL_MARGIN || run[1]>n-1-TUNNEL_MARGIN) return;
        }
        // Out of the tunnels the road cuts no deeper than an open road may, and it meets the road either side without a step.
        for(int i=a;i<=b;i++) if(!under[i] && natural[i]-gentle[i]>MAX_CUT) return;
        if(a>0 && Math.abs(gentle[a]-feet[a-1])>1 || b<n-1 && Math.abs(gentle[b]-feet[b+1])>1) return;
        for(int i=a;i<=b;i++) { feet[i]=gentle[i]; tunnel[i]=under[i]; done[i]=true; }
    }

    // ---------- Paving a generating chunk ----------
    /** Paves this chunk's part of every road near it. Runs on world generation threads. */
    boolean pave(WorldGenLevel world,ChunkPos chunk) {
        int x0=chunk.x()*16,z0=chunk.z()*16,x1=x0+15,z1=z0+15,reach=radius()+spacing*16;
        if(Math.abs(x0)>reach || Math.abs(z0)>reach) return false;
        int ri=Math.floorDiv(chunk.x(),spacing),rj=Math.floorDiv(chunk.z(),spacing);
        // Roads, then gate streets, each in a fixed order, so every chunk agrees which reaches a shared column first.
        Map<Long,Road> near=new TreeMap<>(),streets=new TreeMap<>();
        for(int i=ri-1;i<=ri+1;i++) for(int j=rj-1;j<=rj+1;j++) {
            Node node=node(i,j);
            for(Node other:neighbours(node)) {
                BlockPos a=gate(node,side(node,other)),b=gate(other,side(other,node));
                int margin=Math.max(96,(int)(Math.sqrt(a.distSqr(b))/3))+2+REACH;
                if(Math.max(a.getX(),b.getX())+margin<x0 || Math.min(a.getX(),b.getX())-margin>x1
                        || Math.max(a.getZ(),b.getZ())+margin<z0 || Math.min(a.getZ(),b.getZ())-margin>z1) continue;
                Road road=road(node,other);
                if(road==null) continue;
                Node first=node.key()<other.key() ? node : other;
                near.put(first.key()*31+(first==node ? other : node).key(),road);
                Road spoke=spoke(node,side(node,other));
                if(spoke.near(x0-REACH,z0-REACH,x1+REACH,z1+REACH)) streets.put(node.key()*8+side(node,other).ordinal(),spoke);
            }
        }
        List<Road> ordered=new ArrayList<>(near.values()); ordered.addAll(streets.values());
        var plan=new Plan(ordered,x0-REACH,z0-REACH,16+2*REACH);
        // World generation reads the generation heightmaps; a finished world (the place command) keeps only live ones.
        boolean live=world instanceof ServerLevel,changed=false;
        for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) changed|=plan.pave(world,x,z,live);
        return changed;
    }
    private static void pave(WorldGenLevel world,int x,int z,int feet,boolean live,boolean approach) {
        int surface=world.getHeight(live ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG,x,z);
        int ground=world.getHeight(live ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.OCEAN_FLOOR_WG,x,z);
        int top=ground-1,road=feet-1;
        if(surface>ground || road>top+MAX_FILL) {
            // Over water, or a hole too deep to fill, a plank deck carries the road.
            int deck=Math.max(road,surface);
            set(world,x,deck,z,Blocks.OAK_PLANKS.defaultBlockState());
            clear(world,x,deck+1,deck+3,z);
            return;
        }
        // Where the land is higher than surveyed the road steps up onto it, except on the way into a tunnel, which it must meet.
        if(road<top-(approach ? 2*MAX_CUT : MAX_CUT)) road=top;
        for(int y=top+1;y<road;y++) set(world,x,y,z,Blocks.DIRT.defaultBlockState());
        set(world,x,road,z,Blocks.DIRT_PATH.defaultBlockState());
        clear(world,x,road+1,Math.max(top,road)+3,z);
    }
    /** A column of road: which road (in a chunk's fixed order) and which column of it reached here first, and its height. */
    private record Claim(int road,int n,int feet) {
        boolean before(Claim other) { return road<other.road || road==other.road && n<other.n; }
    }
    /**
     * The roads in and around a chunk: which reaches each column first, in open air or in a tunnel, the tunnels' walls,
     * timbers and lanterns. Each chunk works this out for the columns around it from the surveyed roads alone, so
     * neighbouring chunks agree on every column they share, and each paves only its own.
     */
    private static final class Plan {
        private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
        private final List<Road> roads;
        private final int ox,oz,size;
        private final Claim[] open,bore;
        /** The lowest block of each tunnel column's roof: headroom over the highest stretch of tunnel to reach it. */
        private final int[] roof;
        /** Tunnel columns with a timber beam in the roof or a lantern under it, and wall columns holding a post under a beam's end. */
        private final Set<Integer> beams=new HashSet<>(),lamps=new HashSet<>();
        private final Map<Integer,Integer> posts=new HashMap<>();

        Plan(List<Road> roads,int ox,int oz,int size) {
            this.roads=roads; this.ox=ox; this.oz=oz; this.size=size;
            open=new Claim[size*size]; bore=new Claim[size*size]; roof=new int[size*size];
            for(int r=0;r<roads.size();r++) {
                Road road=roads.get(r);
                if(!road.near(ox,oz,ox+size-1,oz+size-1)) continue;
                for(int n=0;n<road.length();n++) {
                    int x=road.xs()[n],z=road.zs()[n],feet=road.feet()[n];
                    if(x<ox-1 || x>ox+size || z<oz-1 || z>oz+size) continue;
                    // A three-block road: each column takes the height of the first stretch of road to reach it.
                    for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                        int i=index(x+dx,z+dz);
                        if(i<0) continue;
                        if(!road.tunnel()[n]) { if(open[i]==null) open[i]=new Claim(r,n,feet); }
                        else if(bore[i]==null) { bore[i]=new Claim(r,n,feet); roof[i]=feet+HEADROOM; }
                        else if(bore[i].road()==r) roof[i]=Math.max(roof[i],feet+HEADROOM);
                    }
                }
            }
            for(int i=0;i<open.length;i++) if(open[i]!=null && bore[i]!=null && open[i].feet()-1<=roof[i]) {
                // A tunnel's mouth is roofed, at the height of the first stretch to reach it so the floor has no step.
                if(open[i].road()==bore[i].road()) {
                    if(open[i].before(bore[i])) { bore[i]=open[i]; roof[i]=Math.max(roof[i],open[i].feet()+HEADROOM); }
                    open[i]=null;
                }
                // Where roads cross, one passing over a tunnel keeps both; otherwise the first to reach the column wins.
                else if(open[i].before(bore[i])) bore[i]=null;
                else open[i]=null;
            }
            for(int r=0;r<roads.size();r++) {
                Road road=roads.get(r);
                for(int s=0;s<road.length();s++) {
                    if(!road.tunnel()[s] || s>0 && road.tunnel()[s-1]) continue;
                    int e=s;
                    while(e+1<road.length() && road.tunnel()[e+1]) e++;
                    // A timber frame with a lantern under it at both mouths and every few blocks between, so nothing inside is dark.
                    int span=e-s+2,gaps=(span+LAMP_SPACING-1)/LAMP_SPACING;
                    for(int k=0;k<=gaps;k++) frame(r,road,s-1+Math.round((float)k*span/gaps));
                    s=e;
                }
            }
        }
        private int index(int x,int z) {
            x-=ox; z-=oz;
            return x<0 || z<0 || x>=size || z>=size ? -1 : z*size+x;
        }
        /** A beam across the tunnel at a column of its road, on posts set into the walls, with a lantern hung from its middle. */
        private void frame(int r,Road road,int n) {
            int x=road.xs()[n],z=road.zs()[n],at=index(x,z);
            if(at<0 || bore[at]==null || bore[at].road()!=r) return;
            lamps.add(at); beams.add(at);
            boolean alongX=Math.abs(road.xs()[n+1]-road.xs()[n-1])>=Math.abs(road.zs()[n+1]-road.zs()[n-1]);
            for(int side=-1;side<=1;side+=2) for(int k=1,last=at;k<=4;k++) {
                int cx=alongX ? x : x+side*k,cz=alongX ? z+side*k : z,i=index(cx,cz);
                if(i<0) break;
                if(bore[i]!=null && bore[i].road()==r) { beams.add(i); last=i; continue; }
                if(wall(cx,cz)!=null) posts.put(i,last);
                break;
            }
        }
        /** The blocks of a column beside a tunnel that wall it in, from its neighbours' floor to their roof; null if it is no wall. */
        private int[] wall(int x,int z) {
            int i=index(x,z);
            if(i<0 || bore[i]!=null) return null;
            int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
            for(int[] d:SIDES) {
                int j=index(x+d[0],z+d[1]);
                if(j>=0 && bore[j]!=null) { low=Math.min(low,bore[j].feet()-1); high=Math.max(high,roof[j]); }
            }
            // An open road at the tunnel's height meets it here; one passing well above leaves the wall below it.
            if(low>high || open[i]!=null && open[i].feet()-1<=high) return null;
            return new int[]{low,high};
        }
        boolean pave(WorldGenLevel world,int x,int z,boolean live) {
            int i=index(x,z);
            boolean changed=false;
            if(open[i]!=null) {
                RoadNetwork.pave(world,x,z,open[i].feet(),live,roads.get(open[i].road()).approach(open[i].n()));
                changed=true;
            }
            if(bore[i]!=null) { dig(world,x,z,i); changed=true; }
            else {
                int[] wall=wall(x,z);
                if(wall!=null) { for(int y=wall[0];y<=wall[1];y++) set(world,x,y,z,lining(x,y,z)); changed=true; }
            }
            Integer post=posts.get(i);
            if(post!=null) {
                for(int y=bore[post].feet();y<roof[post];y++) set(world,x,y,z,Blocks.SPRUCE_LOG.defaultBlockState());
                set(world,x,roof[post],z,Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            if(beams.contains(i)) set(world,x,roof[i],z,Blocks.SPRUCE_PLANKS.defaultBlockState());
            if(lamps.contains(i)) set(world,x,roof[i]-1,z,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
            return changed;
        }
        /**
         * A tunnel column: a path on firm ground, headroom cleared of rock and water, and a stone roof. The roof and the
         * ground under the path rise and fall to close off any step to the next column, so caves and water stay out.
         */
        private void dig(WorldGenLevel world,int x,int z,int i) {
            int feet=bore[i].feet(),low=feet-2,high=roof[i];
            for(int[] d:SIDES) {
                int j=index(x+d[0],z+d[1]);
                if(j>=0 && bore[j]!=null) { low=Math.min(low,bore[j].feet()); high=Math.max(high,roof[j]-1); }
            }
            for(int y=low;y<feet-1;y++) {
                BlockState state=world.getBlockState(new BlockPos(x,y,z));
                if(state.canBeReplaced() || state.getBlock() instanceof FallingBlock) set(world,x,y,z,Blocks.COBBLESTONE.defaultBlockState());
            }
            set(world,x,feet-1,z,Blocks.DIRT_PATH.defaultBlockState());
            clear(world,x,feet,roof[i]-1,z);
            for(int y=roof[i];y<=high;y++) set(world,x,y,z,lining(x,y,z));
        }
    }
    /** Stone bricks, here and there mossy or cracked: the same block whichever chunk asks. */
    private static BlockState lining(int x,int y,int z) {
        int h=x*0x1B873593^y*0x5BD1E995^z*0x27D4EB2D;
        h^=h>>>15; h*=0x2C1B3C6D; h^=h>>>12;
        int pick=Math.floorMod(h,12);
        return (pick<2 ? Blocks.MOSSY_STONE_BRICKS : pick<3 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
    }
    private static void set(WorldGenLevel world,int x,int y,int z,BlockState state) { world.setBlock(new BlockPos(x,y,z),state,2); }
    private static void clear(WorldGenLevel world,int x,int from,int to,int z) {
        for(int y=from;y<=to;y++) {
            BlockPos pos=new BlockPos(x,y,z);
            if(!world.getBlockState(pos).isAir()) world.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        }
    }

    // ---------- Trade along the roads ----------
    private record Link(Node other,List<BlockPos> line) {}
    /**
     * Towns reachable from a site by road: its neighbours, or through up to two crossroads. Runs off the server thread
     * and hands on each town as soon as its road is surveyed, nearest first, so a town hears of its neighbours long
     * before the wider network has been surveyed.
     */
    private void links(Node home,Set<Long> towns,Consumer<Link> found) {
        int count=0;
        record Trip(Node at,Node from,List<BlockPos> line,int crossroads) {}
        var queue=new ArrayDeque<Trip>(); queue.add(new Trip(home,null,List.of(),0));
        Set<Long> seen=new HashSet<>(); seen.add(home.key());
        while(!queue.isEmpty() && count<MAX_LINKS) {
            Trip trip=queue.poll();
            List<Node> ahead=new ArrayList<>(neighbours(trip.at()));
            ahead.sort(Comparator.comparing(node -> !towns.contains(node.key())));
            for(Node next:ahead) {
                if(!seen.add(next.key())) continue;
                Road road=road(trip.at(),next);
                if(road==null) continue;
                List<BlockPos> line=new ArrayList<>(trip.line());
                if(trip.from()!=null) {
                    // Through a crossroads: in from one gate to its centre, then out by the next gate.
                    append(line,spoke(trip.at(),side(trip.at(),trip.from())),false);
                    append(line,spoke(trip.at(),side(trip.at(),next)),true);
                }
                append(line,road,trip.at().key()>next.key());
                if(towns.contains(next.key())) { if(line.size()>=2 && line.size()<=8192) { found.accept(new Link(next,line)); count++; } }
                else if(trip.crossroads()<2) queue.add(new Trip(next,trip.at(),line,trip.crossroads()+1));
            }
        }
    }
    private static void append(List<BlockPos> line,Road road,boolean reversed) {
        if(road.length()==0) return;
        for(int k=0;k<road.length();k+=SAVED_SPACING) {
            int n=reversed ? road.length()-1-k : k;
            line.add(new BlockPos(road.xs()[n],road.feet()[n],road.zs()[n]));
        }
        int last=reversed ? 0 : road.length()-1;
        BlockPos end=new BlockPos(road.xs()[last],road.feet()[last],road.zs()[last]);
        if(!line.getLast().equals(end)) line.add(end);
    }
    /**
     * A town found on the network arranges trade with the towns already found along its roads, and the shared highway
     * graph learns the roads between them. Surveying runs in the background and each road is applied on the server
     * thread as it is found; the result completes once the survey has finished.
     */
    public static CompletableFuture<Void> connect(ServerLevel level,Settlement town) {
        var network=of(level);
        Node home=network==null ? null : network.nodeAt(town.center);
        if(home==null) return CompletableFuture.completedFuture(null);
        Map<Long,UUID> towns=new HashMap<>();
        for(Settlement other:SettlementData.get(level).settlements) if(other!=town && other.trading.npc && other.trading.buildIndex<0) {
            Node node=network.nodeAt(other.center);
            if(node!=null) towns.put(node.key(),other.id);
        }
        if(towns.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try { network.links(home,towns.keySet(),link -> level.getServer().execute(() -> network.apply(level,town,towns,link))); }
            catch(RuntimeException e) { VC.LOGGER.warn("[VC][roads] Survey from town={} failed",town.id,e); }
        },SURVEYOR);
    }
    private void apply(ServerLevel level,Settlement town,Map<Long,UUID> towns,Link link) {
        var data=SettlementData.get(level); var roads=SharedRoads.get(level);
        if(data.byId(town.id)!=town) return;
        Settlement other=data.byId(towns.get(link.other().key()));
        if(other==null || other.trading.relations.getOrDefault(town.owner,0)<0 || town.trading.relations.getOrDefault(other.owner,0)<0) return;
        if(!town.campaign.extraRoutes.contains(other.id) && town.campaign.extraRoutes.size()>=MAX_LINKS
                || !other.campaign.extraRoutes.contains(town.id) && other.campaign.extraRoutes.size()>=MAX_LINKS) return;
        boolean linked=town.campaign.extraRoutes.add(other.id) | other.campaign.extraRoutes.add(town.id);
        if(roads.between(town.id,other.id)==null && roads.roads.size()<SharedRoads.MAX_ROADS) {
            var line=link.line();
            roads.roads.add(new SharedRoads.Road(town.id,other.id,line,line.size(),0,line.size()));
            roads.setDirty();
        }
        if(linked) {
            NeighbourTrade.refreshRequests(town); NeighbourTrade.refreshRequests(other);
            CampaignService.journal(level,town,"Our road to "+other.name+" is open for trade.");
            CampaignService.journal(level,other,"Our road to "+town.name+" is open for trade.");
            VC.LOGGER.info("[VC][roads] Trade road opened: {} <-> {} ({} waypoints)",town.id,other.id,link.line().size());
        }
        data.setDirty();
    }
}

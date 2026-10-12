package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The old roads: dirt roads laid down with new terrain between the native town sites near the world centre. The
 * {@code vc:settlements} structure set offers one site in each of its regions; a walled town stands on most of them and
 * the rest become crossroads. Roads join neighbouring sites, follow gentle ground around hills, keep clear of other
 * sites and bridge only narrow water. Everything comes from the seed and the terrain noise, never from loaded chunks,
 * so each chunk paves exactly its own part of every road in whatever order chunks generate. Towns joined by a road trade
 * along it once both have been found.
 */
public final class RoadNetwork {
    static final ResourceKey<StructureSet> SETTLEMENTS=ResourceKey.create(Registries.STRUCTURE_SET,Identifier.fromNamespaceAndPath(VC.MODID,"settlements"));
    /** Blocks between survey samples; the most the ground may rise or fall between two samples on a road. */
    private static final int GRID=8,MAX_RISE=5;
    /** Roads keep this far from other sites' centres, and give up rather than bridge more open water than this. */
    private static final int CLEARANCE=40,MAX_BRIDGE=64;
    /** Most a road cuts into or builds up the ground; a deeper dip is spanned by a plank deck instead. */
    private static final int MAX_CUT=4,MAX_FILL=4;
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
    /** A surveyed road: its centre line, with the height a walker stands at and whether it crosses water. */
    public record Road(int[] xs,int[] zs,int[] feet,boolean[] water,int minX,int maxX,int minZ,int maxZ) {
        public int length() { return xs.length; }
        boolean near(int x0,int z0,int x1,int z1) { return maxX>=x0-1 && minX<=x1+1 && maxZ>=z0-1 && minZ<=z1+1; }
    }

    private final long seed;
    private final ChunkGenerator generator;
    private final RandomState random;
    private final ServerLevel level;
    private final RandomSpreadStructurePlacement placement;
    private final int spacing,seaLevel;
    private final Map<Long,Node> nodes=new ConcurrentHashMap<>();
    private final Map<Long,Optional<Road>> roads=new ConcurrentHashMap<>();
    private final Map<Long,Road> spokes=new ConcurrentHashMap<>();

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
            var set=level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(SETTLEMENTS);
            if(set.isEmpty() || !(set.get().value().placement() instanceof RandomSpreadStructurePlacement spread)) return null;
            network=new RoadNetwork(level,spread);
            NETWORKS.put(level,network);
            return network;
        }
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
    private static BlockPos gate(Node node,Direction side) {
        var offset=NpcVillagePlan.gateOffset(side); return new BlockPos(node.x()+offset.x(),0,node.z()+offset.z());
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
            return profile(line(List.of(new double[]{gate.getX(),gate.getZ()},new double[]{node.x(),node.z()})));
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
            if(other.key()!=a.key() && other.key()!=b.key()) avoid.add(new int[]{other.x(),other.z()});
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
                if(next!=goal && avoid.stream().anyMatch(p -> (p[0]-bx)*(p[0]-bx)+(p[1]-bz)*(p[1]-bz)<CLEARANCE*CLEARANCE)) continue;
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
        return profile(line(smooth(smooth(points))));
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
    /** Heights along a course, eased to at most a block per block walked within the cut and fill a road may make. */
    private Road profile(List<int[]> columns) {
        int n=columns.size();
        int[] xs=new int[n],zs=new int[n],natural=new int[n],feet=new int[n];
        boolean[] water=new boolean[n];
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        for(int i=0;i<n;i++) {
            xs[i]=columns.get(i)[0]; zs[i]=columns.get(i)[1];
            int h=height(xs[i],zs[i]); water[i]=h<=seaLevel; natural[i]=water[i] ? seaLevel+1 : h;
            minX=Math.min(minX,xs[i]); maxX=Math.max(maxX,xs[i]); minZ=Math.min(minZ,zs[i]); maxZ=Math.max(maxZ,zs[i]);
        }
        int[] low=natural.clone(),high=natural.clone();
        for(int i=1;i<n;i++) { low[i]=Math.min(low[i],low[i-1]+1); high[i]=Math.max(high[i],high[i-1]-1); }
        for(int i=n-2;i>=0;i--) { low[i]=Math.min(low[i],low[i+1]+1); high[i]=Math.max(high[i],high[i+1]-1); }
        for(int i=0;i<n;i++) {
            int eased=Math.floorDiv(low[i]+high[i],2);
            feet[i]=water[i] ? Math.max(eased,seaLevel+1) : Math.clamp(eased,natural[i]-MAX_CUT,natural[i]+MAX_FILL);
        }
        return new Road(xs,zs,feet,water,minX,maxX,minZ,maxZ);
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
                int margin=Math.max(96,(int)(Math.sqrt(a.distSqr(b))/3))+2;
                if(Math.max(a.getX(),b.getX())+margin<x0 || Math.min(a.getX(),b.getX())-margin>x1
                        || Math.max(a.getZ(),b.getZ())+margin<z0 || Math.min(a.getZ(),b.getZ())-margin>z1) continue;
                Road road=road(node,other);
                if(road==null) continue;
                Node first=node.key()<other.key() ? node : other;
                near.put(first.key()*31+(first==node ? other : node).key(),road);
                Road spoke=spoke(node,side(node,other));
                if(spoke.near(x0,z0,x1,z1)) streets.put(node.key()*8+side(node,other).ordinal(),spoke);
            }
        }
        Set<Long> paved=new HashSet<>();
        boolean changed=false;
        List<Road> ordered=new ArrayList<>(near.values()); ordered.addAll(streets.values());
        for(Road road:ordered) {
            if(!road.near(x0,z0,x1,z1)) continue;
            for(int n=0;n<road.length();n++) {
                int x=road.xs()[n],z=road.zs()[n];
                if(x<x0-1 || x>x1+1 || z<z0-1 || z>z1+1) continue;
                // A three-block road: each column takes the height of the first stretch of road to reach it.
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                    int cx=x+dx,cz=z+dz;
                    if(cx<x0 || cx>x1 || cz<z0 || cz>z1 || !paved.add(pack(cx,cz))) continue;
                    pave(world,cx,cz,road.feet()[n]);
                    changed=true;
                }
            }
        }
        return changed;
    }
    private static void pave(WorldGenLevel world,int x,int z,int feet) {
        int surface=world.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z),ground=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z);
        int top=ground-1,road=feet-1;
        if(surface>ground || road>top+MAX_FILL) {
            // Over water, or a hole too deep to fill, a plank deck carries the road.
            int deck=Math.max(road,surface);
            set(world,x,deck,z,Blocks.OAK_PLANKS.defaultBlockState());
            clear(world,x,deck+1,deck+3,z);
            return;
        }
        if(road<top-MAX_CUT) road=top;
        for(int y=top+1;y<road;y++) set(world,x,y,z,Blocks.DIRT.defaultBlockState());
        set(world,x,road,z,Blocks.DIRT_PATH.defaultBlockState());
        clear(world,x,road+1,Math.max(top,road)+3,z);
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
    /** Towns reachable from a site by road: its neighbours, or through up to two crossroads. Runs off the server thread. */
    private List<Link> links(Node home,Set<Long> towns) {
        List<Link> found=new ArrayList<>();
        record Trip(Node at,Node from,List<BlockPos> line,int crossroads) {}
        var queue=new ArrayDeque<Trip>(); queue.add(new Trip(home,null,List.of(),0));
        Set<Long> seen=new HashSet<>(); seen.add(home.key());
        while(!queue.isEmpty() && found.size()<MAX_LINKS) {
            Trip trip=queue.poll();
            for(Node next:neighbours(trip.at())) {
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
                if(towns.contains(next.key())) { if(line.size()>=2 && line.size()<=8192) found.add(new Link(next,line)); }
                else if(trip.crossroads()<2) queue.add(new Trip(next,trip.at(),line,trip.crossroads()+1));
            }
        }
        return found;
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
     * graph learns the roads between them. Surveying runs in the background; the result is applied on the server thread.
     */
    public static void connect(ServerLevel level,Settlement town) {
        var network=of(level);
        Node home=network==null ? null : network.nodeAt(town.center);
        if(home==null) return;
        Map<Long,UUID> towns=new HashMap<>();
        for(Settlement other:SettlementData.get(level).settlements) if(other!=town && other.trading.npc && other.trading.buildIndex<0) {
            Node node=network.nodeAt(other.center);
            if(node!=null) towns.put(node.key(),other.id);
        }
        if(towns.isEmpty()) return;
        SURVEYOR.execute(() -> {
            List<Link> links;
            try { links=network.links(home,towns.keySet()); }
            catch(RuntimeException e) { VC.LOGGER.warn("[VC][roads] Survey from town={} failed",town.id,e); return; }
            level.getServer().execute(() -> network.apply(level,town,towns,links));
        });
    }
    private void apply(ServerLevel level,Settlement town,Map<Long,UUID> towns,List<Link> links) {
        var data=SettlementData.get(level); var roads=SharedRoads.get(level);
        if(data.byId(town.id)!=town) return;
        for(Link link:links) {
            Settlement other=data.byId(towns.get(link.other().key()));
            if(other==null || other.trading.relations.getOrDefault(town.owner,0)<0 || town.trading.relations.getOrDefault(other.owner,0)<0) continue;
            if(!town.campaign.extraRoutes.contains(other.id) && town.campaign.extraRoutes.size()>=MAX_LINKS
                    || !other.campaign.extraRoutes.contains(town.id) && other.campaign.extraRoutes.size()>=MAX_LINKS) continue;
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
        }
        data.setDirty();
    }
}

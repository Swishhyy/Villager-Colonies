package io.github.swishhyy.vc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.entity.RoadSurface;
import io.github.swishhyy.vc.menu.MapData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Saved, consent-based highway graph. Planning and construction never request or force a chunk. */
public final class SharedRoads extends SavedData {
    public static final int MAX_ROADS=128,MAX_LENGTH=4096,COLUMNS_PER_TICK=4;
    public static final class Road {
        public static final Codec<Road> CODEC=RecordCodecBuilder.create(i -> i.group(
            Settlement.UUID_CODEC.fieldOf("a").forGetter(r -> r.a),Settlement.UUID_CODEC.fieldOf("b").forGetter(r -> r.b),
            BlockPos.CODEC.listOf().fieldOf("line").forGetter(r -> r.line),Codec.INT.optionalFieldOf("cursor",0).forGetter(r -> r.cursor),
            Codec.LONG.optionalFieldOf("blocked_until",0L).forGetter(r -> r.blockedUntil),
            Codec.INT.optionalFieldOf("width_cursor",0).forGetter(r -> r.widthCursor)
        ).apply(i,Road::new));
        public final UUID a,b;
        public final List<BlockPos> line;
        public int cursor;
        public int widthCursor;
        public long blockedUntil;
        public Road(UUID a,UUID b,List<BlockPos> line,int cursor,long blockedUntil) {
            this(a,b,line,cursor,blockedUntil,cursor);
        }
        public Road(UUID a,UUID b,List<BlockPos> line,int cursor,long blockedUntil,int widthCursor) {
            if(line.size()<2 || line.size()>8192) throw new IllegalArgumentException("Invalid highway length");
            this.a=a; this.b=b; this.line=new ArrayList<>(line.stream().map(BlockPos::immutable).toList());
            this.cursor=Math.clamp(cursor,0,line.size()); this.blockedUntil=blockedUntil;
            this.widthCursor=Math.clamp(widthCursor,0,this.cursor);
        }
        public boolean complete() { return cursor>=line.size(); }
        public List<BlockPos> from(UUID id) { if(id.equals(a)) return line; var reversed=new ArrayList<>(line); Collections.reverse(reversed); return reversed; }
        public UUID other(UUID id) { return id.equals(a) ? b : a; }
    }
    public static final Codec<SharedRoads> CODEC=Road.CODEC.listOf().xmap(SharedRoads::new,d -> List.copyOf(d.roads));
    private static final SavedDataType<SharedRoads> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(VC.MODID,"shared_roads"),SharedRoads::new,CODEC,null);
    public final List<Road> roads=new ArrayList<>();
    private final Map<String,Long> retries=new HashMap<>();
    private int planCursor,buildCursor;
    public SharedRoads() {}
    private SharedRoads(List<Road> roads) { this.roads.addAll(roads.stream().limit(MAX_ROADS).toList()); }
    public static SharedRoads get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    private static String key(UUID a,UUID b) { return a.compareTo(b)<0 ? a+"/"+b : b+"/"+a; }
    private static double flat(BlockPos a,BlockPos b) { double x=a.getX()-b.getX(),z=a.getZ()-b.getZ(); return x*x+z*z; }
    public Road between(UUID a,UUID b) { return roads.stream().filter(r -> key(r.a,r.b).equals(key(a,b))).findFirst().orElse(null); }
    private static BlockPos gate(Settlement town,Settlement other) {
        int dx=other.center.getX()-town.center.getX(),dz=other.center.getZ()-town.center.getZ();
        if(!town.trading.npc) return TradeRoutes.checkpoint(town).position().offset(Math.abs(dx)>Math.abs(dz) ? Integer.signum(dx)*2 : 0,0,
                Math.abs(dx)>Math.abs(dz) ? 0 : Integer.signum(dz)*2);
        return Math.abs(dx)>Math.abs(dz) ? town.center.offset(dx>0 ? 22 : -22,0,6) : town.center.offset(0,0,dz>0 ? 22 : -22);
    }
    public static void tick(ServerLevel level) {
        if(!Config.NPC_ROADS.get()) return;
        var data=get(level); var towns=SettlementData.get(level);
        var pending=data.roads.stream().filter(r -> r.widthCursor<r.line.size() && r.blockedUntil<=level.getGameTime()).toList();
        if(!pending.isEmpty()) data.build(level,pending.get(Math.floorMod(data.buildCursor++,pending.size())),COLUMNS_PER_TICK);
        if(level.getGameTime()%200!=0 || data.roads.size()>=MAX_ROADS) return;
        var sources=towns.settlements.stream().filter(t -> t.trading.buildIndex<0 && TradeRoutes.checkpoint(t)!=null).toList();
        if(sources.isEmpty()) return;
        var source=sources.get(Math.floorMod(data.planCursor++,sources.size()));
        for(var destination:TradeRoutes.partners(level,source)) {
            String key=key(source.id,destination.id);
            if(data.between(source.id,destination.id)!=null || data.retries.getOrDefault(key,0L)>level.getGameTime()) continue;
            data.retries.put(key,level.getGameTime()+1200);
            if(flat(source.center,destination.center)>MAX_LENGTH*MAX_LENGTH) continue;
            Road road=data.plan(level,source,destination);
            if(road!=null) { data.roads.add(road); data.setDirty(); }
            break;
        }
    }
    /** Plan from saved surveys; unvisited land is forbidden and each loaded segment is checked again before work. */
    public Road plan(ServerLevel level,Settlement a,Settlement b) {
        if(!TradeRoutes.agreed(a,b) || a.trading.buildIndex>=0 || b.trading.buildIndex>=0) return null;
        var atlas=TradeAtlas.get(level); BlockPos start=gate(a,b),end=gate(b,a);
        if(flat(start,end)>MAX_LENGTH*MAX_LENGTH) return null;
        TradePlanner.Terrain known=new TradePlanner.Terrain() {
            @Override public TradePlanner.Cell cell(int x,int z) { return atlas.cell(x,z); }
            @Override public boolean blocked(int x,int z) { var c=cell(x,z); return !c.seen() || !c.passable() || atlas.blocked(x,z); }
        };
        if(!known.cell(TradePlanner.cellOf(start.getX()),TradePlanner.cellOf(start.getZ())).seen()
                || !known.cell(TradePlanner.cellOf(end.getX()),TradePlanner.cellOf(end.getZ())).seen()) return null;
        List<BlockPos> coarse=new ArrayList<>(); coarse.add(start);
        coarse.addAll(TradePlanner.plan(known,start,end,64));
        if(coarse.size()<2 || !coarse.getLast().equals(end)) return null;
        List<BlockPos> line=new ArrayList<>();
        for(int n=1;n<coarse.size();n++) {
            BlockPos from=coarse.get(n-1),to=coarse.get(n);
            int count=Math.max(Math.abs(to.getX()-from.getX()),Math.abs(to.getZ()-from.getZ()));
            for(int step=0;step<=count;step++) {
                double part=count==0 ? 0 : (double)step/count;
                int x=(int)Math.round(from.getX()+(to.getX()-from.getX())*part),z=(int)Math.round(from.getZ()+(to.getZ()-from.getZ())*part);
                BlockPos column=new BlockPos(x,from.getY(),z);
                var cell=known.cell(TradePlanner.cellOf(x),TradePlanner.cellOf(z)); if(!cell.seen() || !cell.passable()) return null;
                int y=cell.height();
                if(level.hasChunkAt(column)) y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                BlockPos feet=new BlockPos(x,y,z);
                if(level.hasChunkAt(feet) && !passable(level,feet)) return null;
                if(!line.isEmpty() && Math.abs(line.getLast().getY()-y)>4) return null;
                if(line.isEmpty() || !line.getLast().equals(feet)) line.add(feet);
                if(line.size()>8192) return null;
            }
        }
        return line.size()<2 ? null : new Road(a.id,b.id,line,0,0);
    }
    private static boolean passable(ServerLevel level,BlockPos feet) {
        if(feet.getY()<level.getMinY()+1 || feet.getY()+1>=level.getMaxY() || !level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        for(var p:List.of(feet,feet.above(),feet.below())) if(!level.hasChunkAt(p) || level.getBlockEntity(p)!=null) return false;
        if(!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                || level.getFluidState(feet.below()).is(net.minecraft.tags.FluidTags.LAVA)) return false;
        var floor=level.getBlockState(feet.below());
        if(floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || level.getBlockState(feet).is(Blocks.WITHER_ROSE)
                || level.getBlockState(feet).is(Blocks.SWEET_BERRY_BUSH) || level.getBlockState(feet).is(Blocks.FIRE) || level.getBlockState(feet).is(Blocks.SOUL_FIRE)) return false;
        return !floor.getCollisionShape(level,feet.below()).isEmpty() && !floor.is(BlockTags.LOGS) && !floor.is(BlockTags.LEAVES)
                && !floor.is(BlockTags.FENCES) && !floor.is(BlockTags.WALLS)
                && (level.getBlockState(feet).isAir() || level.getBlockState(feet).canBeReplaced())
                && (level.getBlockState(feet.above()).isAir() || level.getBlockState(feet.above()).canBeReplaced());
    }
    public int build(ServerLevel level,Road road,int budget) {
        var towns=SettlementData.get(level); var a=towns.byId(road.a); var b=towns.byId(road.b);
        if(!TradeRoutes.agreed(a,b)) return 0;
        int done=0;
        while(road.widthCursor<road.line.size() && done<budget) {
            int index=road.widthCursor; boolean extending=index==road.cursor;
            BlockPos center=road.line.get(index);
            if(!level.hasChunkAt(center)) break;
            int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX(),center.getZ());
            BlockPos changedFloor=new BlockPos(center.getX(),height-1,center.getZ());
            if(height!=center.getY() && WorldWorkData.get(level).protectedBlocks.contains(changedFloor)) {
                road.blockedUntil=level.getGameTime()+1200; setDirty(); break;
            }
            if(Math.abs(height-center.getY())>4 || index>0 && Math.abs(height-road.line.get(index-1).getY())>1) {
                road.blockedUntil=level.getGameTime()+1200; setDirty(); break;
            }
            center=new BlockPos(center.getX(),height,center.getZ());
            Settlement centerClaim=towns.at(center);
            boolean readOnly=centerClaim!=null && !centerClaim.trading.npc || RoadSurface.preferred(level,center);
            if(!readOnly && !NpcConstruction.away(level,center)) break;
            if(!passable(level,center)) { road.blockedUntil=level.getGameTime()+1200; setDirty(); break; }
            road.line.set(index,center);
            for(BlockPos column:RoadShape.columns(road.line,index)) {
                if(!level.hasChunkAt(column)) continue;
                int laneHeight=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
                BlockPos feet=new BlockPos(column.getX(),laneHeight,column.getZ());
                if(Math.abs(laneHeight-height)>1 || !passable(level,feet) || !NpcConstruction.away(level,feet)) continue;
                Settlement claim=towns.at(feet);
                // An accepted route permits travel, not changes to the player's claim or supply chests.
                if(claim!=null && !claim.trading.npc) continue;
                var protection=WorldWorkData.get(level); var floor=feet.below();
                if(protection.protectedBlocks.contains(floor) || protection.protectedBlocks.contains(feet) || protection.protectedBlocks.contains(feet.above())) continue;
                var state=level.getBlockState(floor);
                if(!state.is(BlockTags.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.PODZOL)
                        && !state.is(Blocks.MYCELIUM) && !state.is(Blocks.DIRT_PATH)) continue;
                level.setBlock(floor,Blocks.DIRT_PATH.defaultBlockState(),2);
                for(var air:List.of(feet,feet.above())) if(!level.getBlockState(air).isAir()) level.setBlock(air,Blocks.AIR.defaultBlockState(),2);
            }
            road.widthCursor++; if(extending) road.cursor++; done++;
        }
        if(done>0) setDirty();
        return done;
    }
    /** Endpoints and road waypoints form a graph shared by NPC and player traders. */
    public List<BlockPos> route(BlockPos from,UUID destination,List<Settlement> towns,long now) {
        record Plan(UUID town,double cost,List<BlockPos> path) {}
        var open=new PriorityQueue<Plan>(Comparator.comparingDouble(Plan::cost)); Map<UUID,Double> best=new HashMap<>();
        for(var town:towns) if(flat(from,town.center)<=64*64) open.add(new Plan(town.id,Math.sqrt(flat(from,town.center)),List.of()));
        for(var road:roads) if(road.complete() && road.blockedUntil<=now) {
            int closest=0; double distance=Double.POSITIVE_INFINITY;
            for(int n=0;n<road.line.size();n+=4) { double d=flat(from,road.line.get(n)); if(d<distance) { distance=d; closest=n; } }
            if(distance>64*64) continue;
            var forward=new ArrayList<>(road.line.subList(closest,road.line.size()));
            var backward=new ArrayList<>(road.line.subList(0,closest+1)); Collections.reverse(backward);
            open.add(new Plan(road.b,Math.sqrt(distance)+forward.size(),forward));
            open.add(new Plan(road.a,Math.sqrt(distance)+backward.size(),backward));
        }
        while(!open.isEmpty()) {
            Plan p=open.remove(); if(p.cost()>=best.getOrDefault(p.town(),Double.POSITIVE_INFINITY)) continue;
            best.put(p.town(),p.cost());
            if(p.town().equals(destination)) return waypoints(p.path(),12);
            for(var road:roads) if(road.complete() && road.blockedUntil<=now && (road.a.equals(p.town()) || road.b.equals(p.town()))) {
                var path=new ArrayList<>(p.path()); path.addAll(road.from(p.town()));
                open.add(new Plan(road.other(p.town()),p.cost()+road.line.size(),path));
            }
        }
        return List.of();
    }
    public static List<BlockPos> waypoints(List<BlockPos> line,int spacing) {
        if(line.isEmpty()) return List.of(); List<BlockPos> result=new ArrayList<>(); result.add(line.getFirst());
        for(int n=1;n<line.size()-1;n++) if(flat(result.getLast(),line.get(n))>=(double)spacing*spacing) result.add(line.get(n));
        if(!result.getLast().equals(line.getLast())) result.add(line.getLast()); return List.copyOf(result);
    }
    public static List<BlockPos> route(ServerLevel level,BlockPos from,BlockPos target) {
        var towns=SettlementData.get(level).settlements; Settlement destination=SettlementData.get(level).at(target);
        if(destination==null) return List.of();
        var data=get(level);
        var valid=data.roads.stream().filter(r -> TradeRoutes.agreed(SettlementData.get(level).byId(r.a),SettlementData.get(level).byId(r.b))).toList();
        var graph=new SharedRoads(valid); return graph.route(from,destination.id,towns,level.getGameTime());
    }
    public static void blocked(ServerLevel level,BlockPos point) {
        var data=get(level);
        for(var road:data.roads) if(road.complete() && road.line.stream().anyMatch(p -> flat(p,point)<=16*16)) road.blockedUntil=level.getGameTime()+6000;
        data.setDirty();
    }
    public List<MapData.Point> mapPath(Settlement from,Settlement to) {
        Road road=between(from.id,to.id);
        if(road==null || !road.complete()) return List.of(new MapData.Point(from.center.getX(),from.center.getZ()),new MapData.Point(to.center.getX(),to.center.getZ()));
        var line=road.from(from.id); List<MapData.Point> result=new ArrayList<>();
        result.add(new MapData.Point(from.center.getX(),from.center.getZ()));
        int step=Math.max(1,(line.size()+60)/61);
        for(int n=0;n<line.size();n+=step) result.add(new MapData.Point(line.get(n).getX(),line.get(n).getZ()));
        result.add(new MapData.Point(to.center.getX(),to.center.getZ())); return List.copyOf(result);
    }
}

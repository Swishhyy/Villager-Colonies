package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.entity.RoadSurface;
import io.github.swishhyy.wwmc.settlement.TradePlanner.Cell;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What traders know of the land along their routes: one coarse cell per 4x4 blocks, read from chunks that players,
 * towns or traders already have loaded. Nothing is loaded for it, so knowledge grows as land is seen. It is saved with
 * the world, and loaded land is read again every few minutes so new roads and bridges are noticed.
 */
public final class TradeAtlas extends SavedData implements TradePlanner.Terrain {
    /** Cells along each side of a saved region. */
    private static final int REGION=32;
    /** Ticks before a loaded chunk is read again. */
    private static final int RESAMPLE=2400;
    /** A cell a trader failed to reach is avoided this long. */
    private static final int BLOCKED_TICKS=6000;
    private static final int SAMPLES_PER_TICK=2,REFRESH_TICKS=100,MAX_QUEUE=8192;
    /** Corridors up to this many chunks are read wherever loaded; larger ones only near players, towns and traders. */
    private static final int MAX_CORRIDOR_CHUNKS=4096;
    private record Stored(long key,ByteBuffer cells) {
        static final Codec<Stored> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("key").forGetter(Stored::key),
            Codec.BYTE_BUFFER.fieldOf("cells").forGetter(Stored::cells)).apply(i,Stored::new));
    }
    private static final Codec<TradeAtlas> CODEC=Stored.CODEC.listOf().xmap(TradeAtlas::new,TradeAtlas::stored);
    private static final SavedDataType<TradeAtlas> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath(WWMC.MODID,"trade_atlas"),TradeAtlas::new,CODEC);

    private static final class Region {
        final short[] height=new short[REGION*REGION];
        final byte[] flags=new byte[REGION*REGION];
        final byte[] column=new byte[REGION*REGION];
        Region() { Arrays.fill(column,(byte)-1); }
        ByteBuffer encode() {
            ByteBuffer out=ByteBuffer.allocate(REGION*REGION*4);
            for(int n=0;n<REGION*REGION;n++) { out.putShort(height[n]); out.put(flags[n]); out.put(column[n]); }
            out.flip();
            return out;
        }
        static Region decode(ByteBuffer saved) {
            Region region=new Region();
            ByteBuffer in=saved.duplicate();
            if(in.remaining()<REGION*REGION*4) return region;
            for(int n=0;n<REGION*REGION;n++) { region.height[n]=in.getShort(); region.flags[n]=in.get(); region.column[n]=in.get(); }
            return region;
        }
    }
    private final Map<Long,Region> regions=new HashMap<>();
    private final Map<Long,Long> blockedUntil=new HashMap<>(),sampledAt=new HashMap<>();
    private final Set<Long> queue=new LinkedHashSet<>();
    private long clock;

    public TradeAtlas() {}
    private TradeAtlas(List<Stored> stored) { for(Stored region:stored) regions.put(region.key(),Region.decode(region.cells())); }
    private List<Stored> stored() {
        List<Stored> result=new ArrayList<>();
        regions.forEach((key,region) -> result.add(new Stored(key,region.encode())));
        return result;
    }
    public static TradeAtlas get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    private static long key(int x,int z) { return (long)x<<32 | z&0xffffffffL; }

    // ---------- Cells ----------
    /** Plans read neighbouring cells, mostly in the same region: keep the last one at hand. */
    private long lastRegionKey=Long.MIN_VALUE;
    private Region lastRegion;
    @Override public Cell cell(int cellX,int cellZ) {
        long regionKey=key(Math.floorDiv(cellX,REGION),Math.floorDiv(cellZ,REGION));
        if(regionKey!=lastRegionKey) { lastRegionKey=regionKey; lastRegion=regions.get(regionKey); }
        Region region=lastRegion;
        if(region==null) return Cell.UNKNOWN;
        int index=Math.floorMod(cellZ,REGION)*REGION+Math.floorMod(cellX,REGION);
        int flags=region.flags[index]&0xff;
        return flags==0 ? Cell.UNKNOWN : new Cell(region.height[index],flags,region.column[index]);
    }
    private void put(int cellX,int cellZ,int height,int flags,int column) {
        lastRegionKey=Long.MIN_VALUE;
        Region region=regions.computeIfAbsent(key(Math.floorDiv(cellX,REGION),Math.floorDiv(cellZ,REGION)),k -> new Region());
        int index=Math.floorMod(cellZ,REGION)*REGION+Math.floorMod(cellX,REGION);
        region.height[index]=(short)height; region.flags[index]=(byte)flags; region.column[index]=(byte)column;
    }
    @Override public boolean blocked(int cellX,int cellZ) { return blockedUntil.getOrDefault(key(cellX,cellZ),0L)>clock; }
    /** A trader could not reach this spot: route around its cell for a few minutes. */
    public void block(ServerLevel level,BlockPos pos) {
        clock=level.getGameTime();
        blockedUntil.put(key(TradePlanner.cellOf(pos.getX()),TradePlanner.cellOf(pos.getZ())),clock+BLOCKED_TICKS);
    }

    // ---------- Routes ----------
    /** Routes look this far to the side of the straight line: half the trip, within limits. */
    static int margin(BlockPos from,BlockPos to) {
        return (int)Math.clamp(Math.sqrt(flat(from,to))/2,64,256);
    }
    private static double flat(BlockPos a,BlockPos b) {
        double dx=a.getX()-b.getX(),dz=a.getZ()-b.getZ();
        return dx*dx+dz*dz;
    }
    /**
     * A route from {@code from} toward {@code to} over the land seen so far. A target farther than {@code reach} is
     * approached through a point that far along the straight line, and planned again as the trader goes.
     */
    public List<BlockPos> route(ServerLevel level,BlockPos from,BlockPos to,int reach) {
        clock=level.getGameTime();
        // The land around the trader is always loaded and may have changed: read it before planning.
        int x=Math.floorDiv(from.getX(),16),z=Math.floorDiv(from.getZ(),16);
        for(int cx=x-1;cx<=x+1;cx++) for(int cz=z-1;cz<=z+1;cz++)
            if(loaded(level,cx,cz) && sampledAt.getOrDefault(key(cx,cz),Long.MIN_VALUE/2)+REFRESH_TICKS<=clock) sample(level,cx,cz);
        double distance=Math.sqrt(flat(from,to));
        BlockPos goal=to;
        if(distance>reach) {
            double share=reach/distance;
            goal=new BlockPos((int)Math.floor(from.getX()+(to.getX()-from.getX())*share),from.getY(),(int)Math.floor(from.getZ()+(to.getZ()-from.getZ())*share));
        }
        return TradePlanner.plan(this,from,goal,margin(from,goal));
    }

    // ---------- Reading loaded land ----------
    private static boolean loaded(ServerLevel level,int chunkX,int chunkZ) { return level.hasChunkAt(new BlockPos(chunkX*16,0,chunkZ*16)); }
    private static boolean clear(ServerLevel level,BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level,pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
    /** Solid ground to stand on: not a trunk, a canopy, a fence or a wall. */
    private static boolean floor(ServerLevel level,BlockPos pos) {
        BlockState ground=level.getBlockState(pos);
        return !ground.getCollisionShape(level,pos).isEmpty() && !ground.is(BlockTags.LOGS) && !ground.is(BlockTags.LEAVES)
                && !ground.is(BlockTags.FENCES) && !ground.is(BlockTags.WALLS) && !ground.is(BlockTags.FENCE_GATES);
    }
    private static boolean room(ServerLevel level,BlockPos feet) { return clear(level,feet) && clear(level,feet.above()) && floor(level,feet.below()); }
    /**
     * Where a walker stands in this column: on the top surface, or beneath a thin cover such as a roof, a branch or an
     * overhang, on the ground under it. Integer.MIN_VALUE when nobody can stand there.
     */
    private static int standing(ServerLevel level,int x,int z,int feet) {
        BlockPos top=new BlockPos(x,feet-1,z);
        for(int depth=1;depth<=2;depth++) {
            BlockPos gap=top.below(depth);
            if(!clear(level,gap)) continue;
            for(int y=gap.getY()-1;y>=gap.getY()-9;y--) if(room(level,new BlockPos(x,y,z))) return y;
            break;
        }
        return room(level,top.above()) ? feet : Integer.MIN_VALUE;
    }
    /** Reads the sixteen cells of one loaded chunk. */
    public void sample(ServerLevel level,int chunkX,int chunkZ) {
        int[] heights=new int[16];
        for(int cellZ=0;cellZ<4;cellZ++) for(int cellX=0;cellX<4;cellX++) {
            int walkable=0,roads=0,canopy=0,water=0,best=-1,bestScore=Integer.MAX_VALUE;
            for(int dz=0;dz<4;dz++) for(int dx=0;dx<4;dx++) {
                int x=chunkX*16+cellX*4+dx,z=chunkZ*16+cellZ*4+dz;
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                BlockPos surface=new BlockPos(x,top-1,z);
                BlockState ground=level.getBlockState(surface);
                // Open water and lava: walkable only where a bridge deck lies on top, which the heightmap then reports.
                if(ground.getCollisionShape(level,surface).isEmpty() && !ground.getFluidState().isEmpty()) { water++; continue; }
                int feet=standing(level,x,z,top);
                if(feet==Integer.MIN_VALUE) continue;
                BlockPos stand=new BlockPos(x,feet,z);
                boolean paved=RoadSurface.preferred(level,stand);
                if(paved) roads++;
                // Forest means leaves overhead; a roof over a road is not woodland.
                int crown=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
                if(crown>feet+1 && level.getBlockState(new BlockPos(x,crown,z)).is(BlockTags.LEAVES)) canopy++;
                heights[walkable++]=feet;
                int score=(paved ? 0 : 8)+Math.abs(2*dx-3)+Math.abs(2*dz-3);
                if(score<bestScore) { bestScore=score; best=dz*4+dx; }
            }
            int flags=Cell.SEEN,height=0;
            if(water>0) flags|=Cell.WATER;
            if(walkable>0) {
                flags|=Cell.PASSABLE;
                Arrays.sort(heights,0,walkable);
                height=heights[walkable/2];
                if(roads>=3) flags|=Cell.ROAD;
                if(canopy*2>=walkable) flags|=Cell.FOREST;
            }
            put(chunkX*4+cellX,chunkZ*4+cellZ,height,flags,walkable>0 ? best : -1);
        }
        sampledAt.put(key(chunkX,chunkZ),level.getGameTime());
        setDirty();
    }
    /** Reads every loaded chunk in this block area now, as a player looking over the land would let traders do. */
    public void survey(ServerLevel level,BlockPos corner,BlockPos opposite) {
        for(int cx=Math.floorDiv(Math.min(corner.getX(),opposite.getX()),16);cx<=Math.floorDiv(Math.max(corner.getX(),opposite.getX()),16);cx++)
            for(int cz=Math.floorDiv(Math.min(corner.getZ(),opposite.getZ()),16);cz<=Math.floorDiv(Math.max(corner.getZ(),opposite.getZ()),16);cz++)
                if(loaded(level,cx,cz)) sample(level,cx,cz);
    }
    private void consider(ServerLevel level,int chunkX,int chunkZ,long now) {
        long chunk=key(chunkX,chunkZ);
        if(queue.size()>=MAX_QUEUE || sampledAt.getOrDefault(chunk,Long.MIN_VALUE/2)+RESAMPLE>now || !loaded(level,chunkX,chunkZ)) return;
        queue.add(chunk);
    }
    private void around(ServerLevel level,BlockPos pos,int radius,int minX,int maxX,int minZ,int maxZ,long now) {
        int x=Math.floorDiv(pos.getX(),16),z=Math.floorDiv(pos.getZ(),16);
        for(int cx=Math.max(minX,x-radius);cx<=Math.min(maxX,x+radius);cx++)
            for(int cz=Math.max(minZ,z-radius);cz<=Math.min(maxZ,z+radius);cz++) consider(level,cx,cz,now);
    }
    /** Queues loaded land along every connected route, and forgets land no route crosses any more. */
    private void refresh(ServerLevel level,long now) {
        sampledAt.values().removeIf(time -> time+RESAMPLE*4L<now);
        blockedUntil.values().removeIf(time -> time<=now);
        Set<Long> wanted=new HashSet<>();
        for(Settlement town:SettlementData.get(level).settlements) {
            Settlement partner=TradeRoutes.partner(level,town);
            if(partner==null || town.id.compareTo(partner.id)>0) continue;
            Station a=TradeRoutes.checkpoint(town),b=TradeRoutes.checkpoint(partner);
            if(a==null || b==null) continue;
            BlockPos from=a.position(),to=b.position();
            int margin=margin(from,to);
            int minX=Math.floorDiv(Math.min(from.getX(),to.getX())-margin,16),maxX=Math.floorDiv(Math.max(from.getX(),to.getX())+margin,16);
            int minZ=Math.floorDiv(Math.min(from.getZ(),to.getZ())-margin,16),maxZ=Math.floorDiv(Math.max(from.getZ(),to.getZ())+margin,16);
            for(int rx=Math.floorDiv(minX*4,REGION);rx<=Math.floorDiv(maxX*4+3,REGION);rx++)
                for(int rz=Math.floorDiv(minZ*4,REGION);rz<=Math.floorDiv(maxZ*4+3,REGION);rz++) wanted.add(key(rx,rz));
            if((long)(maxX-minX+1)*(maxZ-minZ+1)<=MAX_CORRIDOR_CHUNKS) {
                for(int cx=minX;cx<=maxX;cx++) for(int cz=minZ;cz<=maxZ;cz++) consider(level,cx,cz,now);
                continue;
            }
            for(ServerPlayer player:level.players()) around(level,player.blockPosition(),8,minX,maxX,minZ,maxZ,now);
            for(BlockPos place:new BlockPos[]{from,to,town.trading.runnerPos,partner.trading.runnerPos})
                if(place!=null) around(level,place,2,minX,maxX,minZ,maxZ,now);
        }
        if(regions.keySet().removeIf(region -> !wanted.contains(region))) { lastRegionKey=Long.MIN_VALUE; setDirty(); }
    }
    private void tick(ServerLevel level) {
        long now=level.getGameTime();
        clock=now;
        if(now%REFRESH_TICKS==0) refresh(level,now);
        Iterator<Long> next=queue.iterator();
        for(int n=0;n<SAMPLES_PER_TICK && next.hasNext();n++) {
            long chunk=next.next();
            next.remove();
            int cx=(int)(chunk>>32),cz=(int)chunk;
            if(loaded(level,cx,cz)) sample(level,cx,cz);
        }
    }
    /** Reads loaded land along trade routes a few chunks each tick. */
    public static final class Survey {
        @SubscribeEvent public void tick(LevelTickEvent.Post event) {
            if(event.getLevel() instanceof ServerLevel level) get(level).tick(level);
        }
    }
}

package io.github.swishhyy.vc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.vc.Config;
import io.github.swishhyy.vc.core.StructureRole;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Original and desired blocks are saved together; a player edit pauses a funded project. */
public final class NpcConstruction {
    public record Step(BlockPos pos,BlockState original,BlockState desired,boolean protect) {
        public static final Codec<Step> CODEC=RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Step::pos),BlockState.CODEC.fieldOf("original").forGetter(Step::original),
            BlockState.CODEC.fieldOf("desired").forGetter(Step::desired),Codec.BOOL.fieldOf("protect").forGetter(Step::protect)).apply(i,Step::new));
    }
    public static final Codec<NpcConstruction> CODEC=RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("kind").forGetter(p -> p.kind),Station.CODEC.fieldOf("station").forGetter(p -> p.station),
        Step.CODEC.listOf().fieldOf("steps").forGetter(p -> p.steps),Codec.INT.optionalFieldOf("cursor",0).forGetter(p -> p.cursor)
    ).apply(i,NpcConstruction::new));
    public final String kind;
    public final Station station;
    public final List<Step> steps;
    public int cursor;
    public NpcConstruction(String kind,Station station,List<Step> steps,int cursor) {
        if(steps.size()>8192) throw new IllegalArgumentException("NPC project is too large");
        this.kind=kind; this.station=station; this.steps=List.copyOf(steps); this.cursor=Math.clamp(cursor,0,steps.size());
    }
    public boolean complete() { return cursor>=steps.size(); }
    public static boolean away(ServerLevel level,BlockPos pos) {
        int distance=Config.NPC_BUILD_DISTANCE.get();
        return level.players().stream().noneMatch(p -> !p.isSpectator() && flat(p.blockPosition(),pos)<(double)distance*distance);
    }
    private static double flat(BlockPos a,BlockPos b) { double x=a.getX()-b.getX(),z=a.getZ()-b.getZ(); return x*x+z*z; }
    private static boolean natural(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)
                || s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.MUD) || s.is(Blocks.CLAY) || s.is(Blocks.TERRACOTTA);
    }
    private static boolean unchanged(BlockState expected,BlockState current) {
        return current.equals(expected) || (expected.is(Blocks.GRASS_BLOCK) || expected.is(Blocks.DIRT))
                && (current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.DIRT));
    }
    public static NpcConstruction plan(ServerLevel level,Settlement town,String kind) {
        for(int[] offset:new int[][]{{36,0},{0,-36},{-36,0},{0,36},{36,36},{-36,36},{-36,-36},{36,-36}}) {
            NpcConstruction plan=at(level,town,town.center.offset(offset[0],0,offset[1]),kind);
            if(plan!=null) return plan;
        }
        return null;
    }
    /** Read-only planning; no chunks, items or blocks are changed when a site is rejected. */
    public static NpcConstruction at(ServerLevel level,Settlement town,BlockPos probe,String kind) {
        int high=Integer.MIN_VALUE,low=Integer.MAX_VALUE;
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
            BlockPos column=probe.offset(x,0,z);
            if(!level.hasChunkAt(column) || !town.contains(column) || SettlementData.get(level).at(column)!=town) return null;
            int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,column.getX(),column.getZ());
            high=Math.max(high,y); low=Math.min(low,y);
        }
        if(high-low>6 || high<level.getMinY()+4 || high+10>=level.getMaxY()) return null;
        BlockPos center=new BlockPos(probe.getX(),high,probe.getZ());
        int dx=center.getX()-town.center.getX(),dz=center.getZ()-town.center.getZ();
        Direction front=Math.abs(dx)>=Math.abs(dz) ? dx>0 ? Direction.WEST : Direction.EAST : dz>0 ? Direction.NORTH : Direction.SOUTH;
        Map<BlockPos,NpcSettlements.Placement> blocks=new LinkedHashMap<>();
        for(var p:NpcVillagePlan.addition(town,center,kind,front)) blocks.put(p.pos(),p);
        // Connect the entrance to an existing street, keeping the approach outside the new room.
        BlockPos door=center.relative(front,6);
        // A walled village's additions stand outside its wall, so their path leads to the nearest gate instead.
        BlockPos street=NpcVillagePlan.walled(town) ? NpcVillagePlan.entrance(town,dx,dz)
                : Math.abs(dx)>=Math.abs(dz) ? town.center.offset(dx>0 ? 22 : -22,0,6) : town.center.offset(0,0,dz>0 ? 22 : -22);
        int x=door.getX(),z=door.getZ();
        while(x!=street.getX() || z!=street.getZ()) {
            if(z!=street.getZ()) z+=Integer.signum(street.getZ()-z); else x+=Integer.signum(street.getX()-x);
            for(int w=-1;w<=1;w++) {
                BlockPos column=new BlockPos(x+w,0,z);
                if(!level.hasChunkAt(column)) return null;
                int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,column.getX(),column.getZ());
                int distance=Math.max(0,Math.abs(x+w-center.getX())-4)+Math.max(0,Math.abs(z-center.getZ())-4);
                y=Math.max(y,high-distance);
                for(int h=0;h<3;h++) {
                    BlockPos pos=new BlockPos(x+w,y+h-1,z);
                    blocks.put(pos,new NpcSettlements.Placement(pos,h==0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.AIR.defaultBlockState(),h==0));
                }
            }
        }
        // Fill the foundation down to the original terrain, instead of leaving a floating cottage.
        for(var p:List.copyOf(blocks.values())) if(!p.state().isAir() && p.pos().getY()==high-1) {
            BlockPos column=p.pos(); if(!level.hasChunkAt(column)) return null;
            int ground=level.getHeight(Heightmap.Types.WORLD_SURFACE,column.getX(),column.getZ());
            if(high-ground>6) return null;
            for(int y=ground-1;y<high-2;y++) {
                BlockPos pos=new BlockPos(column.getX(),y,column.getZ());
                blocks.put(pos,new NpcSettlements.Placement(pos,Blocks.COBBLESTONE.defaultBlockState(),true));
            }
        }
        List<Step> steps=new ArrayList<>(); var protection=WorldWorkData.get(level);
        for(var p:blocks.values()) {
            BlockPos pos=p.pos();
            if(!level.hasChunkAt(pos) || !town.contains(pos) || SettlementData.get(level).at(pos)!=town || !level.getWorldBorder().isWithinBounds(pos)) return null;
            BlockState old=level.getBlockState(pos);
            if(level.getBlockEntity(pos)!=null || !level.getFluidState(pos).isEmpty()) return null;
            if(protection.protectedBlocks.contains(pos) && !old.equals(p.state()) || !natural(old)) return null;
            steps.add(new Step(pos,old,p.state(),p.protect()));
        }
        StructureRole role=kind.equals("cottage") ? StructureRole.HOUSING : kind.equals("workshop") ? StructureRole.CRAFTSMAN : StructureRole.COOK;
        return new NpcConstruction(kind,new Station(center,role,front),steps,0);
    }
    /** One globally budgeted batch. A blocked project retains its exact cursor and paid materials. */
    public int build(ServerLevel level,Settlement town,int budget) {
        if(!away(level,station.position()) || DefenseService.alarmed(town)) return 0;
        var protection=WorldWorkData.get(level); int done=0;
        while(!complete() && done<budget) {
            Step s=steps.get(cursor);
            if(!level.hasChunkAt(s.pos()) || !away(level,s.pos())) return done;
            BlockState current=level.getBlockState(s.pos());
            if(!current.equals(s.desired()) && (protection.protectedBlocks.contains(s.pos()) || !unchanged(s.original(),current))) return done;
            if(!current.equals(s.desired())) level.setBlock(s.pos(),s.desired(),2);
            if(s.protect()) protection.protect(s.pos());
            cursor++; done++;
        }
        if(done>0) SettlementData.get(level).setDirty();
        return done;
    }
}

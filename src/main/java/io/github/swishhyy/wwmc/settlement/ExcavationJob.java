package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Persist progress only after a real world change; out-of-order crew completions survive reloads. */
public final class ExcavationJob {
    /** Quarry plans saved before staircases have none until their rim height is measured. */
    public static final int NO_STAIRS=Integer.MIN_VALUE;
    public static final Codec<ExcavationJob> CODEC=RecordCodecBuilder.create(i -> i.group(
        Settlement.UUID_CODEC.fieldOf("id").forGetter(j -> j.id),
        BlockPos.CODEC.fieldOf("station").forGetter(j -> j.station),
        Codec.STRING.xmap(StructureRole::valueOf,StructureRole::name).fieldOf("role").forGetter(j -> j.role),
        Codec.STRING.xmap(Direction::valueOf,Direction::name).fieldOf("facing").forGetter(j -> j.facing),
        Codec.INT.fieldOf("top_y").forGetter(j -> j.topY),
        Codec.INT.fieldOf("target_y").forGetter(j -> j.targetY),
        Codec.intRange(1,64).fieldOf("branch_length").forGetter(j -> j.branchLength),
        Codec.intRange(1,16).fieldOf("branch_pairs").forGetter(j -> j.branchPairs),
        Codec.intRange(0,Integer.MAX_VALUE).fieldOf("cursor").forGetter(j -> j.cursor),
        Codec.INT.listOf().fieldOf("completed").forGetter(j -> new ArrayList<>(j.completed)),
        Codec.BOOL.optionalFieldOf("automatic_depth",false).forGetter(j -> j.automaticDepth),
        Codec.INT.optionalFieldOf("stair_top",NO_STAIRS).forGetter(j -> j.stairTop)
    ).apply(i,ExcavationJob::new));
    public final UUID id;
    public final BlockPos station;
    public final StructureRole role;
    public final Direction facing;
    public final int topY,targetY,branchLength,branchPairs;
    public final boolean automaticDepth;
    private int cursor;
    private final Set<Integer> completed;
    private final List<MiningLayout.Cut> tunnels;
    private final RoomBounds quarry;
    private final int stairStart;
    private int stairTop;
    /** Whether the staircase was last found intact down to the working layer; not saved, rechecked on every scan. */
    private boolean stairsOpen=true;
    public ExcavationJob(UUID id,BlockPos station,StructureRole role,Direction facing,int topY,int targetY,
            int branchLength,int branchPairs,int cursor,List<Integer> completed) {
        this(id,station,role,facing,topY,targetY,branchLength,branchPairs,cursor,completed,false);
    }
    public ExcavationJob(UUID id,BlockPos station,StructureRole role,Direction facing,int topY,int targetY,
            int branchLength,int branchPairs,int cursor,List<Integer> completed,boolean automaticDepth) {
        this(id,station,role,facing,topY,targetY,branchLength,branchPairs,cursor,completed,automaticDepth,NO_STAIRS);
    }
    public ExcavationJob(UUID id,BlockPos station,StructureRole role,Direction facing,int topY,int targetY,
            int branchLength,int branchPairs,int cursor,List<Integer> completed,boolean automaticDepth,int stairTop) {
        if(!role.excavates()) throw new IllegalArgumentException("Not an excavation station");
        this.id=id; this.station=station.immutable(); this.role=role; this.facing=facing;
        this.topY=topY; this.targetY=targetY; this.branchLength=branchLength; this.branchPairs=branchPairs;
        this.automaticDepth=automaticDepth;
        int dx=facing.getStepX(),dz=facing.getStepZ();
        tunnels=role==StructureRole.MINE ? MiningLayout.tunnel(station.getX(),topY,station.getZ(),dx,dz,targetY,branchLength,branchPairs) : List.of();
        quarry=role==StructureRole.QUARRY ? MiningLayout.quarry(station.getX(),station.getZ(),dx,dz,topY,targetY) : null;
        stairStart=quarry==null ? -1 : MiningLayout.ringStart(quarry,station.getX(),station.getZ(),dx,dz);
        this.stairTop=quarry==null ? NO_STAIRS : stairTop;
        if(cursor<0 || cursor>size()) throw new IllegalArgumentException("Invalid excavation cursor");
        this.cursor=cursor; this.completed=new HashSet<>();
        for(int value:completed) if(value>=cursor && value<size()) this.completed.add(value);
    }
    public int size() { return quarry==null ? tunnels.size() : (topY-targetY+1)*256; }
    public int cursor() { return cursor; }
    public boolean done(int index) { return index<cursor || completed.contains(index); }
    public void complete(int index) {
        if(index<cursor || index>=size()) return;
        completed.add(index);
        while(completed.remove(cursor)) cursor++;
    }
    public MiningLayout.Cut cut(int index) {
        if(quarry==null) return tunnels.get(index);
        MiningLayout.Cell cell=MiningLayout.quarryCell(quarry,index);
        return new MiningLayout.Cut(cell,new MiningLayout.Cell(station.getX(),station.getY(),station.getZ()),0);
    }
    public RoomBounds bounds() { return quarry; }
    public boolean hasStairs() { return stairTop!=NO_STAIRS; }
    public boolean stairsOpen() { return hasStairs() && stairsOpen; }
    public void stairsOpen(boolean open) { stairsOpen=open; }
    /** Y of the layer being dug, or the bottom once the pit is finished. */
    public int floorY() { return cursor<size() ? cut(cursor).block().y() : targetY; }
    public int stairTop() { return stairTop; }
    /** Set once from the rim height; the staircase never moves afterwards. */
    public void measureStairs(int top) { if(quarry!=null && stairTop==NO_STAIRS) stairTop=Math.max(targetY,Math.min(topY,top)); }
    public boolean stair(int x,int y,int z) { return hasStairs() && MiningLayout.stair(quarry,stairStart,stairTop,x,y,z); }
    public int lastStep() { return hasStairs() ? stairTop-targetY : -1; }
    public MiningLayout.Cell stairStand(int step) { return MiningLayout.stairStand(quarry,stairStart,stairTop,Math.clamp(step,0,lastStep())); }
    /** Ground the crew walks in from: just outside the first step. */
    public MiningLayout.Cell rim() {
        var first=MiningLayout.ringCell(quarry,stairStart,stairTop+1);
        return first.offset(-facing.getStepX(),0,-facing.getStepZ());
    }
    /** Staircase step level with a position inside the dug pit, or -1 outside it, above the stairs or below the working floor. */
    public int stepAt(int x,int y,int z) {
        if(!hasStairs() || x<quarry.minX() || x>quarry.maxX() || z<quarry.minZ() || z>quarry.maxZ() || y>stairTop+1 || y<floorY()) return -1;
        return Math.clamp(stairTop+1-y,0,lastStep());
    }
    public int windowEnd() {
        if(quarry!=null) return Math.min(size(),(cursor/256+1)*256);
        // Complete the stairs and shared main tunnel before opening independent branch fronts.
        int access=(topY-targetY)*3+branchPairs*3*2;
        return Math.min(size(),cursor<access ? Math.min(access,cursor+9) : cursor+128);
    }
}

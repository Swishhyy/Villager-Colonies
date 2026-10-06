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
        Codec.INT.listOf().fieldOf("completed").forGetter(j -> new ArrayList<>(j.completed))
    ).apply(i,ExcavationJob::new));
    public final UUID id;
    public final BlockPos station;
    public final StructureRole role;
    public final Direction facing;
    public final int topY,targetY,branchLength,branchPairs;
    private int cursor;
    private final Set<Integer> completed;
    private final List<MiningLayout.Cut> tunnels;
    private final RoomBounds quarry;
    public ExcavationJob(UUID id,BlockPos station,StructureRole role,Direction facing,int topY,int targetY,
            int branchLength,int branchPairs,int cursor,List<Integer> completed) {
        if(!role.excavates()) throw new IllegalArgumentException("Not an excavation station");
        this.id=id; this.station=station.immutable(); this.role=role; this.facing=facing;
        this.topY=topY; this.targetY=targetY; this.branchLength=branchLength; this.branchPairs=branchPairs;
        int dx=facing.getStepX(),dz=facing.getStepZ();
        tunnels=role==StructureRole.MINE ? MiningLayout.tunnel(station.getX(),topY,station.getZ(),dx,dz,targetY,branchLength,branchPairs) : List.of();
        quarry=role==StructureRole.QUARRY ? MiningLayout.quarry(station.getX(),station.getZ(),dx,dz,topY,targetY) : null;
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
    public int windowEnd() {
        if(quarry!=null) return Math.min(size(),(cursor/256+1)*256);
        // Complete the stairs and shared main tunnel before opening independent branch fronts.
        int access=(topY-targetY)*3+branchPairs*3*2;
        return Math.min(size(),cursor<access ? Math.min(access,cursor+9) : cursor+128);
    }
}

package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class ExcavationService {
    public record Ticket(UUID job,int index,BlockPos target,BlockPos lease,BlockPos stand,boolean support,boolean quarry) {}
    private ExcavationService() {}
    private static BlockPos pos(MiningLayout.Cell cell) { return new BlockPos(cell.x(),cell.y(),cell.z()); }
    private static boolean loaded(ServerLevel level,Settlement town,BlockPos pos) {
        return pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY() && town.contains(pos) && level.hasChunkAt(pos);
    }
    public static ExcavationJob create(ServerLevel level,Settlement town,Station station,int requestedY) {
        int top=station.position().getY(),target;
        if(station.role()==StructureRole.QUARRY) {
            var footprint=MiningLayout.quarry(station.position().getX(),station.position().getZ(),
                    station.facing().getStepX(),station.facing().getStepZ(),top,top);
            if(!town.contains(new BlockPos(footprint.minX(),top,footprint.minZ()))
                    || !town.contains(new BlockPos(footprint.maxX(),top,footprint.maxZ()))
                    || !level.hasChunkAt(new BlockPos(footprint.minX(),top,footprint.minZ()))) return null;
            top=level.getMinY();
            for(int x=footprint.minX();x<=footprint.maxX();x++) for(int z=footprint.minZ();z<=footprint.maxZ();z++)
                top=Math.max(top,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1);
            target=Math.max(level.getMinY(),Math.min(top,requestedY));
        } else target=Math.max(level.getMinY()+2,Math.min(top-1,requestedY));
        if(target>=top && station.role()==StructureRole.MINE) return null;
        ExcavationJob job=new ExcavationJob(UUID.randomUUID(),station.position(),station.role(),station.facing(),top,target,
                Config.MINE_BRANCH_LENGTH.get(),Config.MINE_BRANCH_PAIRS.get(),0,List.of());
        if(station.role()==StructureRole.MINE) {
            for(int i=0;i<job.size();i++) {
                var cut=job.cut(i);
                if(!town.contains(pos(cut.foot())) || !town.contains(pos(cut.stand()))) return null;
            }
        }
        return job;
    }
    public static ExcavationJob job(ServerLevel level,Settlement town,Station station) {
        WorldWorkData data=WorldWorkData.get(level);
        ExcavationJob current=data.excavations.get(station.position());
        if(current!=null && current.role==station.role() && current.facing==station.facing()) return current;
        if(!station.role().excavates()) return null;
        int depth=station.role()==StructureRole.MINE ? Config.MINE_TARGET_Y.get() : Config.QUARRY_TARGET_Y.get();
        current=create(level,town,station,depth);
        if(current!=null) { data.excavations.put(station.position(),current); data.setDirty(); }
        return current;
    }
    private static boolean standable(ServerLevel level,Settlement town,BlockPos stand) {
        if(!loaded(level,town,stand) || !loaded(level,town,stand.above()) || !loaded(level,town,stand.below())) return false;
        return level.getFluidState(stand).isEmpty() && level.getFluidState(stand.above()).isEmpty()
                && level.getBlockState(stand).getCollisionShape(level,stand).isEmpty()
                && level.getBlockState(stand.above()).getCollisionShape(level,stand.above()).isEmpty()
                && level.getBlockState(stand.below()).isFaceSturdy(level,stand.below(),Direction.UP);
    }
    public static boolean safeBlock(ServerLevel level,Settlement town,BlockPos target) {
        if(!loaded(level,town,target) || SettlementService.protectedFurniture(town,target)
                || WorldWorkData.get(level).protectedBlocks.contains(target) || level.getBlockEntity(target)!=null
                || !level.getFluidState(target).isEmpty()) return false;
        var state=level.getBlockState(target);
        if(state.getDestroySpeed(level,target)<0 || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return false;
        for(Direction direction:Direction.values()) {
            BlockPos neighbor=target.relative(direction);
            if(!level.hasChunkAt(neighbor) || !level.getFluidState(neighbor).isEmpty()) return false;
        }
        // Never remove a living entity's occupied block or its footing.
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(target).expandTowards(0,2,0)).isEmpty();
    }
    public static Ticket next(ServerLevel level,Settlement town,Station station,UUID worker) {
        ExcavationJob job=job(level,town,station); if(job==null) return null;
        var book=SettlementService.reservations(level);
        int end=job.windowEnd(),scanned=0;
        for(int index=job.cursor();index<end && scanned++<256;index++) {
            if(job.done(index)) continue;
            var cut=job.cut(index);
            BlockPos target=pos(cut.block()),foot=pos(cut.foot()),stand=pos(cut.stand());
            if(!loaded(level,town,target)) continue;
            if(!job.role.equals(StructureRole.QUARRY)) {
                if(!standable(level,town,stand) || !loaded(level,town,foot.below())) continue;
                if(level.getBlockState(foot.below()).getCollisionShape(level,foot.below()).isEmpty()) {
                    BlockPos floor=foot.below();
                    if(safeBlock(level,town,floor) && book.claimAll(List.of(foot,floor),worker,level.getGameTime(),200))
                        return new Ticket(job.id,index,floor,foot,stand,true,false);
                    continue;
                }
                if(!level.getBlockState(foot.below()).isFaceSturdy(level,foot.below(),Direction.UP)) continue;
            }
            var state=level.getBlockState(target);
            if(state.isAir() && level.getFluidState(target).isEmpty() || state.is(Blocks.BEDROCK) && job.role==StructureRole.QUARRY) {
                job.complete(index); WorldWorkData.get(level).setDirty(); continue;
            }
            if(!safeBlock(level,town,target)) continue;
            if(book.claimAll(List.of(foot,target),worker,level.getGameTime(),200))
                return new Ticket(job.id,index,target,foot,stand,false,job.role==StructureRole.QUARRY);
        }
        return null;
    }
    public static boolean valid(ServerLevel level,Settlement town,Station station,Ticket ticket) {
        ExcavationJob job=WorldWorkData.get(level).excavations.get(station.position());
        return job!=null && job.id.equals(ticket.job()) && !job.done(ticket.index())
                && safeBlock(level,town,ticket.target())
                && (ticket.quarry() || standable(level,town,ticket.stand()));
    }
    public static boolean supportMaterial(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && (stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE)
                || stack.is(Items.STONE) || stack.is(Items.DIRT) || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE));
    }
    public static boolean placeSupport(ServerLevel level,Settlement town,Station station,Ticket ticket,ItemStack supply) {
        if(!ticket.support() || !supportMaterial(supply) || !valid(level,town,station,ticket)
                || !level.getBlockState(ticket.target()).isAir()) return false;
        var state=((BlockItem)supply.getItem()).getBlock().defaultBlockState();
        if(!level.setBlock(ticket.target(),state,3)) return false;
        supply.shrink(1); return true;
    }
    public static void completed(ServerLevel level,Station station,Ticket ticket) {
        ExcavationJob job=WorldWorkData.get(level).excavations.get(station.position());
        if(job!=null && job.id.equals(ticket.job())) { job.complete(ticket.index()); WorldWorkData.get(level).setDirty(); }
    }
    public static String status(ServerLevel level,Settlement town,Station station) {
        ExcavationJob job=job(level,town,station);
        if(job==null) return "layout is outside the claim or its target chunk is unloaded";
        if(job.cursor()>=job.size()) return "excavation complete at Y "+job.targetY;
        String area=job.role==StructureRole.QUARRY ? "chunk "+Math.floorDiv(job.bounds().minX(),16)+", "+Math.floorDiv(job.bounds().minZ(),16) : "descending access + branch tunnels";
        return area+", target Y "+job.targetY+", "+job.cursor()+"/"+job.size()+" excavation steps; blocked faces need clear access, dry terrain, and suitable tools";
    }
}

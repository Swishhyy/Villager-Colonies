package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.MiningLayout;
import io.github.swishhyy.wwmc.core.AutomaticDepth;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class ExcavationService {
    /** A reserved cut. Quarry crews stand next to their block inside the pit; {@code remote} cuts are worked from the control block. */
    public record Ticket(UUID job,int index,BlockPos target,BlockPos lease,BlockPos stand,boolean support,boolean quarry,boolean remote) {
        public Ticket fromControlBlock(BlockPos station) { return new Ticket(job,index,target,target,station,support,quarry,true); }
    }
    /** Why a block cannot be cut now: WAIT may clear up; SKIP never will, so a quarry leaves that block in place. */
    private enum Blockage { NONE, WAIT, SKIP }
    /** Staircase steps a worker climbs or descends per leg; longer trips go step by step. */
    private static final int STAIR_LEG=6;
    private ExcavationService() {}
    private static BlockPos pos(MiningLayout.Cell cell) { return new BlockPos(cell.x(),cell.y(),cell.z()); }
    private static boolean loaded(ServerLevel level,Settlement town,BlockPos pos) {
        return pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY() && town.contains(pos) && level.hasChunkAt(pos);
    }
    public static ExcavationJob create(ServerLevel level,Settlement town,Station station) {
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
            target=Math.max(level.getMinY(),Math.min(top,Config.QUARRY_TARGET_Y.get()));
        } else {
            var chosen=AutomaticDepth.choose(Config.MINE_MIN_Y.get(),Config.MINE_MAX_Y.get(),level.getMinY(),top,level.getRandom()::nextInt);
            if(chosen.isEmpty()) return null;
            target=chosen.getAsInt();
        }
        if(target>=top && station.role()==StructureRole.MINE) return null;
        ExcavationJob job=new ExcavationJob(UUID.randomUUID(),station.position(),station.role(),station.facing(),top,target,
                Config.MINE_BRANCH_LENGTH.get(),Config.MINE_BRANCH_PAIRS.get(),0,List.of(),station.role()==StructureRole.MINE);
        if(station.role()==StructureRole.QUARRY) measureStairs(level,town,job);
        if(station.role()==StructureRole.MINE) {
            for(int i=0;i<job.size();i++) {
                var cut=job.cut(i);
                if(!town.contains(pos(cut.foot())) || !town.contains(pos(cut.stand()))
                        || quarryColumn(town,cut.foot().x(),cut.foot().z()) || quarryColumn(town,cut.stand().x(),cut.stand().z())) return null;
            }
        }
        return job;
    }
    /** The staircase begins level with the ground outside the first step. Older pits already dug below it keep working from the control block. */
    private static void measureStairs(ServerLevel level,Settlement town,ExcavationJob job) {
        if(job.hasStairs() || job.cursor()>=job.size()) return;
        var rim=job.rim();
        if(!level.hasChunkAt(new BlockPos(rim.x(),job.topY,rim.z()))) return;
        int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,rim.x(),rim.z())-1;
        // A fresh plan gets stairs when the crew can stand at the first step's level outside the edge; an older pit only if it is not yet below the rim.
        boolean fresh=job.cursor()<256;
        if(fresh ? standable(level,town,new BlockPos(rim.x(),Math.min(job.topY,ground)+1,rim.z())) : job.cut(job.cursor()).block().y()>=ground)
            job.measureStairs(ground);
    }
    /** Columns of the chunk a quarry station targets; mines and cave work stay out of them so the pit stays level. */
    public static boolean quarryColumn(Settlement town,int x,int z) {
        for(Station station:town.stations) {
            if(station.role()!=StructureRole.QUARRY) continue;
            var footprint=MiningLayout.quarry(station.position().getX(),station.position().getZ(),station.facing().getStepX(),station.facing().getStepZ(),0,0);
            if(x>=footprint.minX() && x<=footprint.maxX() && z>=footprint.minZ() && z<=footprint.maxZ()) return true;
        }
        return false;
    }
    public static ExcavationJob job(ServerLevel level,Settlement town,Station station) {
        WorldWorkData data=WorldWorkData.get(level);
        ExcavationJob current=data.excavations.get(station.position());
        if(current!=null && current.role==station.role() && current.facing==station.facing()
                && (station.role()!=StructureRole.MINE || current.automaticDepth)) {
            if(current.role==StructureRole.QUARRY && !current.hasStairs()) {
                measureStairs(level,town,current);
                if(current.hasStairs()) data.setDirty();
            }
            return current;
        }
        if(!station.role().excavates()) return null;
        current=create(level,town,station);
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
    public static boolean safeBlock(ServerLevel level,Settlement town,BlockPos target) { return blockage(level,town,target,false)==Blockage.NONE; }
    /** Quarries also clear natural trees; player-placed or protected logs stay protected like other construction. */
    private static Blockage blockage(ServerLevel level,Settlement town,BlockPos target,boolean quarry) {
        if(!loaded(level,town,target)) return Blockage.WAIT;
        if(SettlementService.protectedFurniture(town,target) || WorldWorkData.get(level).protectedBlocks.contains(target)
                || level.getBlockEntity(target)!=null || !level.getFluidState(target).isEmpty()) return Blockage.SKIP;
        var state=level.getBlockState(target);
        if(state.getDestroySpeed(level,target)<0 || state.is(BlockTags.PLANKS) || !quarry && state.is(BlockTags.LOGS)) return Blockage.SKIP;
        for(Direction direction:Direction.values()) {
            BlockPos neighbor=target.relative(direction);
            if(!level.hasChunkAt(neighbor)) return Blockage.WAIT;
            // Removing a block beside water or lava would flood the workings.
            if(!level.getFluidState(neighbor).isEmpty()) return Blockage.SKIP;
        }
        // Never remove a living entity's occupied block or its footing.
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(target).expandTowards(0,2,0)).isEmpty() ? Blockage.NONE : Blockage.WAIT;
    }
    /** Mining time from the block's hardness and the tool, like a player's, bounded by the configured work time. */
    public static int breakTicks(ServerLevel level,BlockPos target,ItemStack tool) {
        var state=level.getBlockState(target);
        float speed=Math.max(1.0F,tool.getDestroySpeed(state));
        return Math.clamp(Math.round(state.getDestroySpeed(level,target)*30.0F/speed),10,Config.WORK_TICKS.get());
    }
    private static BlockPos pos(ExcavationJob job,int index) { return pos(job.cut(index).block()); }
    /** A prospective worker position has no entity yet; ray tracing must use an explicit collision context. */
    public static ClipContext quarrySight(BlockPos stand,BlockPos target) {
        Vec3 eye=new Vec3(stand.getX()+0.5,stand.getY()+1.6,stand.getZ()+0.5);
        return new ClipContext(eye,Vec3.atCenterOf(target),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty());
    }
    /** Closest dry, headroom-clear spot within two blocks of the cut with a clear view of it, never on top of it or in a step's place. */
    private static BlockPos quarryStand(ServerLevel level,Settlement town,ExcavationJob job,BlockPos target,BlockPos from) {
        BlockPos best=null; double distance=Double.MAX_VALUE;
        for(int dy=0;dy<=1;dy++) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            if(dx==0 && dz==0) continue;
            BlockPos stand=target.offset(dx,dy,dz);
            if(stand.distSqr(from)>=distance || job.stair(stand.getX(),stand.getY(),stand.getZ()) || !standable(level,town,stand)) continue;
            if(!level.clip(quarrySight(stand,target)).getBlockPos().equals(target)) continue;
            best=stand; distance=stand.distSqr(from);
        }
        return best;
    }
    /** A missing step is placed by a worker standing on the step above it, or on the rim for the first step. */
    private static BlockPos stepStand(ExcavationJob job,BlockPos step) {
        int index=job.stairTop()-step.getY();
        return pos(index==0 ? job.rim() : job.stairStand(index-1));
    }
    private static boolean vacant(ServerLevel level,BlockPos pos) { return level.getEntitiesOfClass(LivingEntity.class,new AABB(pos)).isEmpty(); }
    /** A step that has gone missing above the working layer, and where to stand to put it back. */
    private record StairRepair(BlockPos step,BlockPos stand) {}
    /**
     * Walk the staircase from the rim down to the working layer. Crews only enter when every step is solid with two
     * clear blocks above it. A missing step that can be rebuilt is returned as a repair; anything else closes the stairs.
     */
    private static StairRepair checkStairs(ServerLevel level,Settlement town,ExcavationJob job) {
        job.stairsOpen(false);
        if(!job.hasStairs() || !standable(level,town,pos(job.rim()))) return null;
        for(int step=0;step<=job.lastStep();step++) {
            BlockPos stand=pos(job.stairStand(step)),block=stand.below();
            // The working layer's own step is handled with the rest of that layer.
            if(block.getY()<=job.floorY()) break;
            if(!loaded(level,town,block)) return null;
            var state=level.getBlockState(block);
            if(!state.isFaceSturdy(level,block,Direction.UP) || !level.getFluidState(block).isEmpty()) {
                // Collapsed sand or gravel, or a step someone broke: rebuild it from the step above.
                BlockPos from=step==0 ? pos(job.rim()) : pos(job.stairStand(step-1));
                return state.canBeReplaced() && level.getFluidState(block).isEmpty() && standable(level,town,from) ? new StairRepair(block,from) : null;
            }
            // Walking between steps the body passes three blocks of each step's column.
            if(!clear(level,stand) || !clear(level,stand.above()) || step>0 && !clear(level,stand.above(2))) return null;
        }
        job.stairsOpen(true); return null;
    }
    private static boolean clear(ServerLevel level,BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level,pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
    private static Ticket nextQuarry(ServerLevel level,Settlement town,ExcavationJob job,UUID worker,BlockPos from,java.util.function.Predicate<BlockPos> avoid) {
        var book=SettlementService.reservations(level);
        long now=level.getGameTime();
        StairRepair repair=checkStairs(level,town,job);
        if(repair!=null && !avoid.test(repair.step()) && vacant(level,repair.step())
                && book.claimAll(List.of(repair.step(),repair.stand().below()),worker,now,200))
            return new Ticket(job.id,-1,repair.step(),repair.stand().below(),repair.stand(),true,true,false);
        List<Integer> open=new ArrayList<>();
        boolean changed=false;
        for(int index=job.cursor(),end=job.windowEnd();index<end;index++) {
            if(job.done(index)) continue;
            BlockPos target=pos(job,index);
            if(!loaded(level,town,target)) continue;
            var state=level.getBlockState(target);
            if(job.stair(target.getX(),target.getY(),target.getZ())) {
                // Steps stay solid. A missing one is rebuilt; a flooded or unreachable one is given up.
                boolean solid=state.isFaceSturdy(level,target,Direction.UP) && level.getFluidState(target).isEmpty();
                // With the staircase closed nobody can come down to place this step, so the layer carries on without it;
                // once the floor is below it, checkStairs offers it as a repair from the step above.
                if(solid || !state.canBeReplaced() || !level.getFluidState(target).isEmpty() || !standable(level,town,stepStand(job,target))
                        || !job.stairsOpen() && job.floorY()<job.stairTop()) {
                    job.complete(index); changed=true;
                } else if(book.available(target,worker,now) && !avoid.test(target) && vacant(level,target)) open.add(index);
                continue;
            }
            if(state.isAir() && level.getFluidState(target).isEmpty() || state.is(Blocks.BEDROCK)) { job.complete(index); changed=true; continue; }
            Blockage blockage=blockage(level,town,target,true);
            // Unsafe blocks are left standing so one tree, pond edge or chest can no longer stall the whole layer.
            if(blockage==Blockage.SKIP) { job.complete(index); changed=true; }
            else if(blockage==Blockage.NONE && book.available(target,worker,now) && !avoid.test(target)) open.add(index);
        }
        if(changed) WorldWorkData.get(level).setDirty();
        open.sort(Comparator.comparingDouble(index -> pos(job,index).distSqr(from)));
        // Crews only go down while the staircase is whole; above the rim they walk in over the ground.
        boolean enter=job.stairsOpen() || job.floorY()>=job.stairTop() && job.hasStairs();
        for(int n=0;enter && n<open.size() && n<16;n++) {
            int index=open.get(n);
            BlockPos target=pos(job,index);
            boolean step=job.stair(target.getX(),target.getY(),target.getZ());
            BlockPos stand=step ? stepStand(job,target) : quarryStand(level,town,job,target,from);
            if(stand!=null && book.claimAll(List.of(target,stand.below()),worker,now,200))
                return new Ticket(job.id,index,target,stand.below(),stand,step,true,false);
        }
        // No stairs, broken stairs, or nowhere to stand: work from the control block.
        for(int index:open) {
            BlockPos target=pos(job,index);
            if(!job.stair(target.getX(),target.getY(),target.getZ()) && book.claim(target,worker,now,200))
                return new Ticket(job.id,index,target,target,job.station,false,true,true);
        }
        return null;
    }
    public static Ticket next(ServerLevel level,Settlement town,Station station,UUID worker,BlockPos from,java.util.function.Predicate<BlockPos> avoid) {
        ExcavationJob job=job(level,town,station); if(job==null) return null;
        if(job.role==StructureRole.QUARRY) return nextQuarry(level,town,job,worker,from,avoid);
        var book=SettlementService.reservations(level);
        int end=job.windowEnd(),scanned=0;
        for(int index=job.cursor();index<end && scanned++<256;index++) {
            if(job.done(index)) continue;
            var cut=job.cut(index);
            BlockPos target=pos(cut.block()),foot=pos(cut.foot()),stand=pos(cut.stand());
            if(!loaded(level,town,target)) continue;
            if(!standable(level,town,stand) || !loaded(level,town,foot.below())) continue;
            if(level.getBlockState(foot.below()).getCollisionShape(level,foot.below()).isEmpty()) {
                BlockPos floor=foot.below();
                if(safeBlock(level,town,floor) && book.claimAll(List.of(foot,floor),worker,level.getGameTime(),200))
                    return new Ticket(job.id,index,floor,foot,stand,true,false,false);
                continue;
            }
            if(!level.getBlockState(foot.below()).isFaceSturdy(level,foot.below(),Direction.UP)) continue;
            var state=level.getBlockState(target);
            if(state.isAir() && level.getFluidState(target).isEmpty()) {
                job.complete(index); WorldWorkData.get(level).setDirty(); continue;
            }
            if(!safeBlock(level,town,target)) continue;
            if(book.claimAll(List.of(foot,target),worker,level.getGameTime(),200))
                return new Ticket(job.id,index,target,foot,stand,false,false,false);
        }
        return null;
    }
    public static boolean valid(ServerLevel level,Settlement town,Station station,Ticket ticket) {
        ExcavationJob job=WorldWorkData.get(level).excavations.get(station.position());
        if(job==null || !job.id.equals(ticket.job())) return false;
        // Steps (including repairs of finished layers, index -1) are only placed into an empty, unoccupied space.
        if(ticket.quarry() && ticket.support()) return (ticket.index()<0 || !job.done(ticket.index())) && level.getBlockState(ticket.target()).canBeReplaced()
                && level.getFluidState(ticket.target()).isEmpty() && vacant(level,ticket.target()) && standable(level,town,ticket.stand());
        if(job.done(ticket.index())) return false;
        if(ticket.quarry()) return blockage(level,town,ticket.target(),true)==Blockage.NONE && (ticket.remote() || standable(level,town,ticket.stand()));
        return safeBlock(level,town,ticket.target()) && standable(level,town,ticket.stand());
    }
    /** Inside a quarry pit, where a fall is caught by the crew's safety lines. */
    public static boolean inPit(ServerLevel level,Settlement town,BlockPos pos) {
        for(Station station:town.stations) {
            if(station.role()!=StructureRole.QUARRY) continue;
            ExcavationJob job=WorldWorkData.get(level).excavations.get(station.position());
            if(job!=null && job.bounds().contains(pos.getX(),Math.min(pos.getY(),job.topY),pos.getZ())) return true;
        }
        return false;
    }
    /**
     * Next staircase stop between two points when either lies deep in a quarry pit. Paths are planned a few steps at a
     * time because a long spiral is beyond ordinary pathfinding range. Returns null when a direct path is fine.
     */
    public static BlockPos waypoint(ServerLevel level,Settlement town,BlockPos from,BlockPos to) {
        for(Station station:town.stations) {
            if(station.role()!=StructureRole.QUARRY) continue;
            ExcavationJob job=WorldWorkData.get(level).excavations.get(station.position());
            if(job==null || !job.stairsOpen()) continue;
            int start=job.stepAt(from.getX(),from.getY(),from.getZ()),end=job.stepAt(to.getX(),to.getY(),to.getZ());
            if(start<0 && end<0) continue;
            start=Math.max(0,start); end=Math.max(0,end);
            if(Math.abs(end-start)<=STAIR_LEG) continue;
            return pos(job.stairStand(start+Integer.signum(end-start)*STAIR_LEG));
        }
        return null;
    }
    public static boolean supportMaterial(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && (stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE)
                || stack.is(Items.STONE) || stack.is(Items.DIRT) || stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE));
    }
    public static boolean placeSupport(ServerLevel level,Settlement town,Station station,Ticket ticket,ItemStack supply) {
        if(!ticket.support() || !supportMaterial(supply) || !valid(level,town,station,ticket)
                || !level.getBlockState(ticket.target()).canBeReplaced()) return false;
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
        if(job==null) return "needs a loaded target inside the claim, a mine entrance above the configured depth band, and tunnels that stay out of quarry chunks";
        if(job.cursor()>=job.size()) return "excavation complete at Y "+job.targetY;
        if(job.role==StructureRole.QUARRY) {
            String access=job.hasStairs() ? "crews take the spiral stairs from Y "+(job.stairTop()+1) : "crews work from the control block";
            return "chunk "+Math.floorDiv(job.bounds().minX(),16)+", "+Math.floorDiv(job.bounds().minZ(),16)+", digging layer Y "+job.cut(job.cursor()).block().y()
                    +" toward Y "+job.targetY+" ("+job.cursor()+"/"+job.size()+" cells); "+access+". Blocks beside water or lava, containers and player builds are left standing";
        }
        return "descending access + branch tunnels, target Y "+job.targetY+", "+job.cursor()+"/"+job.size()+" excavation steps; blocked faces need clear access, dry terrain, and suitable tools";
    }
}

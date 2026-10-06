package io.github.swishhyy.wwmc.settlement;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Bounded tree recognition rejects placed logs, buildings, and incomplete/unloaded trees. */
public final class ForestryService {
    public static final int MAX_LOGS=256,MAX_HEIGHT=40,CROWN_RADIUS=8;
    public record Tree(BlockPos root,List<BlockPos> logs,TreeSpecies species,int width) {}
    public record Task(BlockPos target,Tree tree,PlantingSite planting) {}
    private ForestryService() {}
    private static boolean loaded(ServerLevel level,Settlement town,BlockPos pos) {
        return pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY() && town.contains(pos) && level.hasChunkAt(pos);
    }
    private static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }
    public static boolean naturalLeaf(BlockState state,TreeSpecies species) {
        return state.is(species.leaves) && !state.getValue(LeavesBlock.PERSISTENT);
    }
    public interface TreeView {
        BlockState state(BlockPos pos);
        boolean available(BlockPos pos);
        boolean protectedAt(BlockPos pos);
        boolean furnitureAt(BlockPos pos);
        boolean blockEntityAt(BlockPos pos);
    }
    public static Tree tree(ServerLevel level,Settlement town,BlockPos root) {
        WorldWorkData data=WorldWorkData.get(level);
        return tree(new TreeView() {
            public BlockState state(BlockPos pos) { return level.getBlockState(pos); }
            public boolean available(BlockPos pos) { return loaded(level,town,pos); }
            public boolean protectedAt(BlockPos pos) { return data.protectedBlocks.contains(pos); }
            public boolean furnitureAt(BlockPos pos) { return SettlementService.protectedFurniture(town,pos); }
            public boolean blockEntityAt(BlockPos pos) { return level.getBlockEntity(pos)!=null; }
        },root);
    }
    public static Tree tree(TreeView world,BlockPos root) {
        if(!world.available(root) || !world.available(root.below())) return null;
        TreeSpecies species=TreeSpecies.ofLog(world.state(root));
        if(species==null || !soil(world.state(root.below()))) return null;
        if(!world.available(root.above(2)) || !world.state(root.above()).is(species.log)
                || !world.state(root.above(2)).is(species.log)) return null;
        Set<BlockPos> logs=new LinkedHashSet<>(),roots=new HashSet<>();
        Set<BlockPos> canopy=new HashSet<>();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>(); queue.add(root.immutable());
        while(!queue.isEmpty()) {
            BlockPos pos=queue.remove();
            if(!logs.add(pos)) continue;
            if(logs.size()>MAX_LOGS || !world.available(pos) || world.protectedAt(pos)
                    || world.furnitureAt(pos)) return null;
            if(soil(world.state(pos.below()))) roots.add(pos);
            if(canopy.size()<4 && pos.getY()>=root.getY()+2) {
                for(BlockPos leaf:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,3,2)))
                    if(world.available(leaf) && naturalLeaf(world.state(leaf),species)
                            && !world.protectedAt(leaf)) canopy.add(leaf.immutable());
            }
            for(BlockPos adjacent:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))) {
                if(adjacent.equals(pos)) continue;
                // Truncating a tree at an unloaded chunk would leave floating trunks.
                if(!world.available(adjacent)) return null;
                BlockState state=world.state(adjacent);
                if(state.is(BlockTags.PLANKS) || world.blockEntityAt(adjacent)
                        || world.protectedAt(adjacent) && !state.isAir() && !soil(state)) return null;
                if(!state.is(BlockTags.LOGS) || logs.contains(adjacent)) continue;
                if(!state.is(species.log) || !world.available(adjacent)
                        || Math.abs(adjacent.getX()-root.getX())>CROWN_RADIUS
                        || Math.abs(adjacent.getZ()-root.getZ())>CROWN_RADIUS
                        || adjacent.getY()<root.getY() || adjacent.getY()>root.getY()+MAX_HEIGHT) return null;
                queue.add(adjacent.immutable());
            }
        }
        if(canopy.size()<4 || roots.isEmpty() || roots.size()!=1 && roots.size()!=4) return null;
        int minX=roots.stream().mapToInt(BlockPos::getX).min().orElse(root.getX());
        int minZ=roots.stream().mapToInt(BlockPos::getZ).min().orElse(root.getZ());
        int width=roots.size()==4 ? 2 : species.width;
        if(roots.size()==4) for(int x=0;x<2;x++) for(int z=0;z<2;z++)
            if(!roots.contains(new BlockPos(minX+x,root.getY(),minZ+z))) return null;
        if(species.width==2 && roots.size()!=4) return null;
        return new Tree(new BlockPos(minX,root.getY(),minZ),List.copyOf(logs),species,width);
    }
    public static boolean canPlant(ServerLevel level,Settlement town,Station station,PlantingSite site) {
        WorldWorkData data=WorldWorkData.get(level);
        for(int x=0;x<site.width();x++) for(int z=0;z<site.width();z++) {
            BlockPos pos=site.root().offset(x,0,z);
            if(!loaded(level,town,pos) || !station.contains(pos) || !loaded(level,town,pos.below())
                    || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                    || data.protectedBlocks.contains(pos) || SettlementService.protectedFurniture(town,pos)
                    || !site.species().sapling.defaultBlockState().canSurvive(level,pos)) return false;
        }
        for(BlockPos pos:BlockPos.betweenClosed(site.root().offset(-1,1,-1),site.root().offset(site.width(),5,site.width()))) {
            if(!loaded(level,town,pos) || !level.getBlockState(pos).isAir()) return false;
        }
        return true;
    }
    public static Task find(ServerLevel level,Settlement town,Station station,Predicate<BlockPos> accessible) {
        WorldWorkData data=WorldWorkData.get(level);
        // Replant harvested sites before moving on to another tree.
        for(PlantingSite site:data.plantings) if(site.station().equals(station.position())
                && canPlant(level,town,station,site) && accessible.test(site.root()))
            return new Task(site.root(),null,site);
        List<BlockPos> roots=new ArrayList<>();
        for(BlockPos pos:SettlementService.cells(station)) {
            if(!loaded(level,town,pos) || !loaded(level,town,pos.below())) continue;
            if(TreeSpecies.ofLog(level.getBlockState(pos))!=null && soil(level.getBlockState(pos.below()))) roots.add(pos.immutable());
        }
        roots.sort(Comparator.comparingDouble(p -> p.distSqr(station.position())));
        Set<BlockPos> checked=new HashSet<>();
        for(BlockPos root:roots) {
            if(checked.contains(root) || !SettlementService.ownsBlock(level,town,station,root) || !accessible.test(root)) continue;
            Tree tree=tree(level,town,root);
            if(tree!=null) {
                checked.addAll(tree.logs());
                // A whole-tree reservation is shared by all four trunks of a large tree.
                if(SettlementService.ownsBlock(level,town,station,tree.root())
                        && station.contains(tree.root().offset(tree.width()-1,0,tree.width()-1)) && accessible.test(tree.root()))
                    return new Task(tree.root(),tree,null);
            }
        }
        // With no accessible tree, plant from the actual saplings already available to the town.
        var storage=SettlementService.storage(level,town);
        for(TreeSpecies species:TreeSpecies.values()) {
            int available=0;
            for(var container:storage) for(int slot=0;slot<container.getContainerSize();slot++)
                if(container.getItem(slot).is(species.seed)) available+=container.getItem(slot).getCount();
            if(available<species.width*species.width) continue;
            for(BlockPos pos:SettlementService.cells(station)) {
                if(!loaded(level,town,pos) || !accessible.test(pos)) continue;
                PlantingSite site=new PlantingSite(station.position(),pos,species,species.width);
                if(!canPlant(level,town,station,site)) continue;
                boolean crowded=false;
                for(BlockPos nearby:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,2,3))) {
                    if(!level.hasChunkAt(nearby)) { crowded=true; break; }
                    var state=level.getBlockState(nearby);
                    if(state.getBlock() instanceof SaplingBlock || state.is(BlockTags.LOGS)) { crowded=true; break; }
                }
                if(!crowded && SettlementService.ownsBlock(level,town,station,pos)) {
                    data.queue(site); return new Task(pos.immutable(),null,site);
                }
            }
        }
        return null;
    }
    public static int durability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage()-stack.getDamageValue() : Integer.MAX_VALUE;
    }
    public static List<ItemStack> fell(ServerLevel level,Settlement town,Station station,BlockPos root,LivingEntity worker) {
        Tree tree=tree(level,town,root);
        if(tree==null || !worker.getMainHandItem().is(ItemTags.AXES) || !SettlementService.ownsBlock(level,town,station,tree.root())
                || durability(worker.getMainHandItem())<tree.logs().size()) return null;
        Set<BlockPos> foliage=new LinkedHashSet<>(),logs=new HashSet<>(tree.logs());
        for(BlockPos log:tree.logs()) {
            if(!level.getEntitiesOfClass(LivingEntity.class,new AABB(log).expandTowards(0,2,0)).isEmpty()) return null;
            for(BlockPos leaf:BlockPos.betweenClosed(log.offset(-3,-1,-3),log.offset(3,3,3))) {
                if(foliage.size()>=512) break;
                if(loaded(level,town,leaf) && naturalLeaf(level.getBlockState(leaf),tree.species())
                        && !WorldWorkData.get(level).protectedBlocks.contains(leaf) && !SettlementService.protectedFurniture(town,leaf)) foliage.add(leaf.immutable());
            }
        }
        List<ItemStack> drops=new ArrayList<>();
        int removed=0;
        for(BlockPos log:tree.logs()) {
            var state=level.getBlockState(log);
            var blockDrops=Block.getDrops(state,level,log,null,worker,worker.getMainHandItem());
            if(level.destroyBlock(log,false,worker)) { drops.addAll(blockDrops); removed++; }
        }
        worker.getMainHandItem().hurtAndBreak(removed,worker,EquipmentSlot.MAINHAND);
        for(BlockPos leaf:foliage) {
            boolean otherTree=false;
            for(BlockPos neighbor:BlockPos.betweenClosed(leaf.offset(-1,-1,-1),leaf.offset(1,1,1))) {
                if(level.hasChunkAt(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS) && !logs.contains(neighbor)) { otherTree=true; break; }
            }
            if(otherTree || !naturalLeaf(level.getBlockState(leaf),tree.species())
                    || !level.getEntitiesOfClass(LivingEntity.class,new AABB(leaf).expandTowards(0,2,0)).isEmpty()) continue;
            var blockDrops=Block.getDrops(level.getBlockState(leaf),level,leaf,null,worker,ItemStack.EMPTY);
            if(level.destroyBlock(leaf,false,worker)) drops.addAll(blockDrops);
        }
        if(removed>0) WorldWorkData.get(level).queue(new PlantingSite(station.position(),tree.root(),tree.species(),tree.width()));
        return drops;
    }
    public static boolean plant(ServerLevel level,Settlement town,Station station,PlantingSite site,ItemStack seeds) {
        if(!seeds.is(site.species().seed) || seeds.getCount()<site.cost() || !canPlant(level,town,station,site)) return false;
        if(!site.apply(seeds,pos -> level.setBlock(pos,site.species().sapling.defaultBlockState(),3),
                pos -> level.setBlock(pos,Blocks.AIR.defaultBlockState(),3))) return false;
        WorldWorkData data=WorldWorkData.get(level); data.plantings.remove(site); data.setDirty(); return true;
    }
    public static int protectConnectedLogs(ServerLevel level,Settlement town,BlockPos start) {
        Set<BlockPos> seen=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>(); queue.add(start.immutable());
        WorldWorkData data=WorldWorkData.get(level);
        while(!queue.isEmpty() && seen.size()<4096) {
            BlockPos pos=queue.remove();
            if(!loaded(level,town,pos) || !level.getBlockState(pos).is(BlockTags.LOGS) || !seen.add(pos)) continue;
            data.protect(pos);
            for(BlockPos adjacent:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))) if(!seen.contains(adjacent)) queue.add(adjacent.immutable());
        }
        return seen.size();
    }
}

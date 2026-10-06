package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class ForestryMiningChecks {
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    private static final class TreeWorld implements ForestryService.TreeView {
        final Map<BlockPos,BlockState> states=new HashMap<>();
        final Set<BlockPos> protectedLogs=new HashSet<>(),unloaded=new HashSet<>(),furniture=new HashSet<>();
        public BlockState state(BlockPos p) { return states.getOrDefault(p,Blocks.AIR.defaultBlockState()); }
        public boolean available(BlockPos p) { return !unloaded.contains(p); }
        public boolean protectedAt(BlockPos p) { return protectedLogs.contains(p); }
        public boolean furnitureAt(BlockPos p) { return furniture.contains(p); }
        public boolean blockEntityAt(BlockPos p) { return false; }
    }
    private static TreeWorld oak(BlockPos root) {
        TreeWorld world=new TreeWorld();
        world.states.put(root.below(),Blocks.GRASS_BLOCK.defaultBlockState());
        for(int y=0;y<4;y++) world.states.put(root.above(y),Blocks.OAK_LOG.defaultBlockState());
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0 || z!=0)
            world.states.put(root.offset(x,3,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,false));
        return world;
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void treeProtectionPlantingAndProgress(MinecraftServer server) {
        BlockPos root=new BlockPos(0,65,0);
        TreeWorld world=oak(root);
        var tree=ForestryService.tree(world,root);
        check(tree!=null && tree.logs().size()==4,"A rooted natural canopy is recognized (soil tag="
                +world.state(root.below()).is(BlockTags.DIRT)+", log tag="+world.state(root).is(BlockTags.LOGS)
                +", log identity="+world.state(root).is(TreeSpecies.OAK.log)+", natural leaves="
                +ForestryService.naturalLeaf(world.state(root.offset(1,3,0)),TreeSpecies.OAK)+")");
        world.states.put(root.offset(1,4,0),Blocks.OAK_LOG.defaultBlockState());
        world.states.put(root.offset(2,5,0),Blocks.OAK_LOG.defaultBlockState());
        world.states.put(root.offset(3,6,0),Blocks.OAK_LOG.defaultBlockState());
        world.states.put(root.offset(4,6,0),Blocks.OAK_LOG.defaultBlockState());
        tree=ForestryService.tree(world,root);
        check(tree!=null && tree.logs().contains(root.offset(4,6,0)),"Whole-tree traversal includes branches beyond the seven-block detection cube");
        world.protectedLogs.add(root.offset(4,6,0));
        check(ForestryService.tree(world,root)==null,"A single player-placed branch prevents felling the connected tree");
        world.protectedLogs.clear(); world.unloaded.add(root.offset(4,6,0));
        check(ForestryService.tree(world,root)==null,"An unloaded branch cannot be silently truncated");
        world=oak(root); world.furniture.add(root.above(2));
        check(ForestryService.tree(world,root)==null,"A tree touching a protected housing/storage volume is skipped");
        world=oak(root); world.states.put(root.offset(-1,1,0),Blocks.OAK_PLANKS.defaultBlockState());
        check(ForestryService.tree(world,root)==null,"Adjacent construction prevents treating an older log structure as a tree");
        world=oak(root);
        world.states.replaceAll((p,state) -> state.is(Blocks.OAK_LEAVES) ? state.setValue(LeavesBlock.PERSISTENT,true) : state);
        check(ForestryService.tree(world,root)==null,"Decorative player leaves do not prove a natural tree");
        world=oak(root); world.states.put(root.below(),Blocks.STONE.defaultBlockState());
        check(ForestryService.tree(world,root)==null,"A log column on a stone foundation is not a rooted tree");
        check(!ForestryService.naturalLeaf(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),TreeSpecies.OAK),"Persistent leaves never enter canopy harvesting");
        ItemStack axe=new ItemStack(Items.IRON_AXE); axe.setDamageValue(7);
        check(ForestryService.durability(axe)==axe.getMaxDamage()-7,"Felling requires enough remaining real axe durability");

        PlantingSite group=new PlantingSite(BlockPos.ZERO,root,TreeSpecies.DARK_OAK,2);
        Set<BlockPos> saplings=new HashSet<>();
        ItemStack seeds=new ItemStack(Items.DARK_OAK_SAPLING,4);
        check(group.apply(seeds,saplings::add,saplings::remove) && seeds.isEmpty() && saplings.size()==4,"A large-tree planting consumes four actual saplings");
        saplings.clear(); seeds=new ItemStack(Items.DARK_OAK_SAPLING,3);
        check(!group.apply(seeds,saplings::add,saplings::remove) && saplings.isEmpty() && seeds.getCount()==3,"Incomplete stock cannot create free saplings");
        saplings.clear(); seeds=new ItemStack(Items.DARK_OAK_SAPLING,4);
        check(!group.apply(seeds,p -> saplings.size()<2 && saplings.add(p),saplings::remove)
                && saplings.isEmpty() && seeds.getCount()==4,"A failed grouped placement rolls back its blocks and preserves supplies");
        ItemStack wrong=new ItemStack(Items.OAK_SAPLING,4);
        check(!group.apply(wrong,saplings::add,saplings::remove) && wrong.getCount()==4,"The planting recipe cannot consume a different species");
        PlantingSite single=new PlantingSite(BlockPos.ZERO,root,TreeSpecies.BIRCH,1);
        ItemStack one=new ItemStack(Items.BIRCH_SAPLING,2);
        check(single.apply(one,saplings::add,saplings::remove) && one.getCount()==1,"An ordinary tree uses one sapling");

        Station quarryStation=new Station(new BlockPos(-1,70,-1),StructureRole.QUARRY,Direction.EAST);
        ExcavationJob job=new ExcavationJob(UUID.randomUUID(),quarryStation.position(),quarryStation.role(),Direction.EAST,70,-64,24,4,0,List.of());
        job.complete(1); job.complete(5);
        check(job.cursor()==0 && job.done(1) && job.windowEnd()==256,"Parallel crews can finish blocks out of order without skipping a layer");
        job.complete(0);
        check(job.cursor()==2 && job.done(0) && job.done(1),"Only contiguous completed work advances the quarry");
        WorldWorkData work=new WorldWorkData(List.of(root,root.above()),List.of(group),List.of(job));
        var encoded=WorldWorkData.CODEC.encodeStart(JsonOps.INSTANCE,work).getOrThrow();
        WorldWorkData restored=WorldWorkData.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        check(restored.protectedBlocks.equals(work.protectedBlocks),"Player placement provenance persists across restarts");
        check(restored.plantings.equals(work.plantings),"Replanting obligations survive saves");
        var resumed=restored.excavations.get(quarryStation.position());
        check(resumed.id.equals(job.id) && resumed.cursor()==2 && resumed.done(5) && !resumed.done(4),"A restart preserves parallel excavation progress without re-awarding blocks");
        resumed.complete(2); resumed.complete(3); resumed.complete(4);
        check(resumed.cursor()==6,"Saved out-of-order work joins the completed prefix after remaining blocks are mined");
        var stationJson=Station.CODEC.encodeStart(JsonOps.INSTANCE,quarryStation).getOrThrow();
        check(Station.CODEC.parse(JsonOps.INSTANCE,stationJson).getOrThrow().equals(quarryStation),"Quarry facing survives saves");
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Large town",new BlockPos(0,64,0),150,List.of(),List.of(quarryStation),"balanced",List.of(new BlockPos(150,70,150)));
        Settlement saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
        check(saved.radius==150 && saved.contains(new BlockPos(-150,100,150)) && !saved.contains(new BlockPos(151,64,0)),"The enlarged claim keeps explicit inclusive boundaries");
        check(saved.borderBanners.equals(town.borderBanners),"Placed corner markers are persisted instead of duplicated");
        check(ExcavationService.supportMaterial(new ItemStack(Items.COBBLESTONE)) && !ExcavationService.supportMaterial(new ItemStack(Items.BREAD)),"Only actual building materials can support tunnel floors");
        System.out.println("Passed "+checks+" forestry, resource and excavation persistence checks.");
    }
}

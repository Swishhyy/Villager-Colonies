package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

public final class CitizenReachChecks {
    @Test void reachUsesEyesAndSurfacesWithAnExactFourBlockLimit() {
        Vec3 eye=new Vec3(0,1.6,0);
        assertTrue(CitizenReach.within(eye,new BlockPos(4,1,0)));
        assertTrue(CitizenReach.within(eye,new BlockPos(-5,1,0)));
        assertFalse(CitizenReach.within(eye,new BlockPos(5,1,0)));
        assertFalse(CitizenReach.within(eye,new BlockPos(3,1,3)),"Four on each horizontal axis is beyond a four-block radius");
        assertFalse(CitizenReach.within(eye,new BlockPos(0,6,0)),"The same reach cap applies vertically");
        assertTrue(CitizenReach.within(eye,new AABB(4,0,-0.3,4.6,2,0.3)),"Melee reaches the enemy's body, not its center");
        assertFalse(CitizenReach.within(eye,new AABB(4.001,0,-0.3,4.6,2,0.3)));
        assertFalse(CitizenReach.within(eye,new AABB(0,6,-0.3,0.6,8,0.3)));
    }
    private static final class Terrain implements BlockGetter,ForestryService.TreeView,CitizenReach.StandingView {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        final Set<BlockPos> protectedAt=new HashSet<>(),unloaded=new HashSet<>(),furniture=new HashSet<>();
        public BlockState getBlockState(BlockPos p) { return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState()); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
        public BlockState state(BlockPos p) { return getBlockState(p); }
        public boolean available(BlockPos p) { return !unloaded.contains(p); }
        public boolean protectedAt(BlockPos p) { return protectedAt.contains(p); }
        public boolean furnitureAt(BlockPos p) { return furniture.contains(p); }
        public boolean blockEntityAt(BlockPos p) { return false; }
        public boolean clear(BlockPos p) { return getBlockState(p).getCollisionShape(this,p).isEmpty() && getFluidState(p).isEmpty(); }
        public boolean footing(BlockPos p) { return !getBlockState(p).is(Blocks.OAK_LEAVES)
                && getFluidState(p).isEmpty() && getBlockState(p).isFaceSturdy(this,p,net.minecraft.core.Direction.UP); }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void workReachKeepsWallsAndLeafObstructionsVisible(MinecraftServer server) {
        Terrain world=new Terrain();
        Vec3 eye=new Vec3(0.5,65.6,0.5); BlockPos trunk=new BlockPos(4,64,0);
        world.blocks.put(trunk,Blocks.OAK_LOG.defaultBlockState());
        world.blocks.put(trunk.above(),Blocks.OAK_LOG.defaultBlockState());
        assertTrue(CitizenReach.canUse(world,eye,trunk),"A trunk four block columns away can be worked from outside the canopy");
        BlockPos obstacle=new BlockPos(2,65,0);
        world.blocks.put(obstacle,Blocks.OAK_LEAVES.defaultBlockState());
        assertFalse(CitizenReach.canUse(world,eye,trunk),"Leaves are an actual obstruction to clear, not transparent work reach");
        assertEquals(obstacle,CitizenReach.hit(world,eye,trunk).getBlockPos());
        world.blocks.put(obstacle,Blocks.STONE.defaultBlockState());
        assertFalse(CitizenReach.canUse(world,eye,trunk),"Workers cannot mine through a wall");
        world.blocks.remove(obstacle);
        assertFalse(CitizenReach.canUse(world,new Vec3(-1,65.6,0.5),trunk),"An exposed block outside four blocks is still out of reach");
        BlockPos crop=new BlockPos(2,64,0); world.blocks.put(crop,Blocks.WHEAT.defaultBlockState());
        assertTrue(CitizenReach.canUse(world,eye,crop),"Thin crop outlines remain reachable");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void treeApproachesHaveClearBodiesAndFirmFooting(MinecraftServer server) {
        Terrain world=new Terrain(); BlockPos trunk=new BlockPos(0,64,0);
        for(int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) world.blocks.put(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState());
        world.blocks.put(trunk,Blocks.OAK_LOG.defaultBlockState()); world.blocks.put(trunk.above(),Blocks.OAK_LOG.defaultBlockState());
        BlockPos leaves=new BlockPos(1,65,0); world.blocks.put(leaves,Blocks.OAK_LEAVES.defaultBlockState());
        BlockPos water=new BlockPos(-1,64,0); world.blocks.put(water,Blocks.WATER.defaultBlockState());
        BlockPos unloaded=new BlockPos(0,64,1); world.unloaded.add(unloaded);
        var stands=CitizenReach.stands(world,trunk,new Vec3(4.5,64,0.5),1.6);
        assertFalse(stands.isEmpty());
        assertTrue(stands.contains(new BlockPos(3,64,0)),"Clear ground outside the canopy is a valid work spot");
        assertFalse(stands.contains(trunk)); assertFalse(stands.contains(trunk.above()));
        assertFalse(stands.contains(leaves.below()),"A leaf at head height excludes the spot below");
        assertFalse(stands.contains(water)); assertFalse(stands.contains(unloaded));
        for(BlockPos stand:stands) {
            assertTrue(CitizenReach.standing(world,stand));
            assertTrue(CitizenReach.within(Vec3.atBottomCenterOf(stand).add(0,1.6,0),trunk));
            assertFalse(stand.below().equals(trunk),"Workers cannot choose the block being mined as their footing");
        }
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void barrelApproachesUseTheActualSurfaceOfWalkableFloors(MinecraftServer server) {
        Terrain world=new Terrain(); BlockPos barrel=new BlockPos(0,64,0),stand=barrel.east(2);
        world.blocks.put(barrel,Blocks.BARREL.defaultBlockState());
        var ground=CitizenReach.ground(world,world::available);
        List<BlockState> floors=List.of(Blocks.DIRT_PATH.defaultBlockState(),Blocks.STONE_SLAB.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,SlabType.TOP),
                BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_carpet")).defaultBlockState());
        double[] surfaces={63.9375,63.5,64.0,63.0625};
        for(int n=0;n<floors.size();n++) {
            world.blocks.put(stand.below(),floors.get(n));
            world.blocks.put(stand.below(2),Blocks.STONE.defaultBlockState());
            BlockPos node=n==3 ? stand.below() : stand;
            assertTrue(CitizenReach.standing(ground,node),"Walkable floor was rejected: "+floors.get(n));
            assertEquals(surfaces[n],ground.feet(node).y,1.0E-7);
            var approaches=CitizenReach.stands(ground,barrel,new Vec3(12,64,0),1.6);
            assertTrue(approaches.contains(node),"No barrel approach over "+floors.get(n));
            assertTrue(CitizenReach.canUse(world,ground.feet(node).add(0,1.6,0),barrel));
        }
        assertFalse(CitizenReach.standing(ground,stand),"The air above carpet is not its walking node");
        world.blocks.put(stand.above(),Blocks.STONE.defaultBlockState());
        assertFalse(CitizenReach.standing(ground,stand.below()),"Carpet under a low ceiling must leave room for the citizen's head");
        world.blocks.remove(stand.above());
        world.blocks.put(stand,Blocks.STONE.defaultBlockState());
        assertFalse(CitizenReach.standing(ground,stand),"A blocked body still prevents standing");
        world.blocks.remove(stand);
        for(BlockState unsafe:List.of(Blocks.WATER.defaultBlockState(),Blocks.OAK_LEAVES.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState())) {
            world.blocks.put(stand.below(),unsafe);
            assertFalse(CitizenReach.standing(ground,stand),"Unsafe footing was accepted: "+unsafe);
        }
        world.blocks.put(stand.below(),Blocks.STONE_SLAB.defaultBlockState()); world.unloaded.add(stand.below());
        assertFalse(CitizenReach.standing(ground,stand),"Floor checks must not use unloaded terrain");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void leafClearingKeepsNaturalTreeProofAndConstructionProtection(MinecraftServer server) {
        Terrain world=new Terrain(); BlockPos root=new BlockPos(0,64,0);
        world.blocks.put(root.below(),Blocks.GRASS_BLOCK.defaultBlockState());
        for(int y=0;y<4;y++) world.blocks.put(root.above(y),Blocks.OAK_LOG.defaultBlockState());
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) if(x!=0 || z!=0)
            world.blocks.put(root.offset(x,3,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,false));
        var tree=ForestryService.tree(world,root); assertNotNull(tree);
        BlockPos leaf=root.offset(1,3,0);
        assertTrue(ForestryService.clearableLeaf(world,tree,leaf));
        world.protectedAt.add(leaf); assertFalse(ForestryService.clearableLeaf(world,tree,leaf)); world.protectedAt.clear();
        world.furniture.add(leaf); assertFalse(ForestryService.clearableLeaf(world,tree,leaf)); world.furniture.clear();
        world.unloaded.add(leaf); assertFalse(ForestryService.clearableLeaf(world,tree,leaf)); world.unloaded.clear();
        world.blocks.put(leaf,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
        assertFalse(ForestryService.clearableLeaf(world,tree,leaf),"Decorative leaves cannot be cleared for access");
        world.blocks.entrySet().removeIf(e -> e.getValue().is(Blocks.OAK_LEAVES));
        assertNull(ForestryService.tree(world,root),"New log columns still require a natural canopy");
        assertNotNull(ForestryService.verify(world,tree),"A previously proven tree stays recognized after its obstructing leaves are removed");
        var data=new WorldWorkData(); data.clearedTrees.put(root,tree);
        var encoded=WorldWorkData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow();
        var saved=WorldWorkData.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        assertNotNull(ForestryService.verify(world,saved.clearedTrees.get(root)),"Access clearing survives a restart without accepting arbitrary log columns");
        world.protectedAt.add(root.above());
        assertNull(ForestryService.verify(world,tree),"A newly protected player log still cancels the proven tree");
        world.protectedAt.clear(); world.blocks.put(root.offset(1,1,0),Blocks.OAK_PLANKS.defaultBlockState());
        assertNull(ForestryService.verify(world,tree),"New construction still cancels felling after leaves have been cleared");
        var legacy=encoded.getAsJsonObject(); legacy.remove("cleared_trees");
        assertTrue(WorldWorkData.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().clearedTrees.isEmpty());
    }
}

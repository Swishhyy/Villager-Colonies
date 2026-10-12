package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Each chunk receives only its own part of the furnished village during the FEATURES stage. Villages are placed after
 * trees grow, so a walled village first fells every tree standing on its land, whole, and trims canopy reaching over it.
 */
public final class NpcTownPiece extends StructurePiece {
    /** Wood and foliage felled with one tree at most, and how far its leaves may reach from the wood. */
    private static final int MAX_LOGS=1024,MAX_LEAVES=8192,LEAF_REACH=6,TREE_SPREAD=16;
    private final NpcTownPlan.Candidate candidate;
    private final NpcVillageTerrain.Site site;
    private final int radius;
    private volatile List<NpcSettlements.Placement> plan;
    private volatile Set<BlockPos> built;
    public NpcTownPiece(NpcTownPlan.Candidate candidate,NpcVillageTerrain.Site site,int radius) {
        super(SettlementStructures.TOWN_PIECE.get(),0,bounds(site));
        this.candidate=candidate; this.site=site; this.radius=radius;
    }
    public NpcTownPiece(StructurePieceSerializationContext context,CompoundTag tag) {
        super(SettlementStructures.TOWN_PIECE.get(),tag);
        var center=tag.read("center",BlockPos.CODEC).orElseThrow();
        candidate=new NpcTownPlan.Candidate(UUID.fromString(tag.getString("townId").orElseThrow()),center,
                tag.getStringOr("name","Settlement"),tag.getStringOr("specialty","farming"),tag.getString("region").orElseThrow());
        site=new NpcVillageTerrain.Site(center,tag.read("layout",NpcVillageLayout.CODEC).orElseThrow());
        radius=Math.clamp(tag.getIntOr("radius",120),16,512);
    }
    private static BoundingBox bounds(NpcVillageTerrain.Site site) {
        int e=site.layout().extent(),low=site.layout().ground().stream().mapToInt(Integer::intValue).min().orElse(0),
                high=site.layout().ground().stream().mapToInt(Integer::intValue).max().orElse(0);
        var p=site.center();
        return new BoundingBox(p.getX()-e,p.getY()+low-10,p.getZ()-e,p.getX()+e,p.getY()+high+12,p.getZ()+e);
    }
    public NpcTownPlan.Candidate candidate() { return candidate; }
    public Settlement town() { return NpcSettlements.createTown(candidate,site,radius); }
    @Override public BlockPos getLocatorPosition() { return site.center(); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.store("center",BlockPos.CODEC,site.center()); tag.store("layout",NpcVillageLayout.CODEC,site.layout());
        tag.putString("townId",candidate.id().toString()); tag.putString("name",candidate.name());
        tag.putString("specialty",candidate.specialty()); tag.putString("region",candidate.region()); tag.putInt("radius",radius);
    }
    public List<NpcSettlements.Placement> placements() {
        var result=plan;
        if(result==null) synchronized(this) {
            if(plan==null) plan=NpcVillagePlan.blueprint(town());
            result=plan;
        }
        return result;
    }
    /** Positions of the village's own blocks, which felling never touches. */
    private Set<BlockPos> built() {
        var result=built;
        if(result==null) synchronized(this) {
            if(built==null) {
                Set<BlockPos> positions=new HashSet<>();
                for(var p:placements()) if(!p.state().isAir()) positions.add(p.pos());
                built=positions;
            }
            result=built;
        }
        return result;
    }
    private static boolean wood(BlockState state) { return state.is(BlockTags.LOGS) || state.is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM); }
    private static boolean foliage(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(net.minecraft.world.level.block.Blocks.VINE) || state.is(net.minecraft.world.level.block.Blocks.COCOA)
                || state.is(BlockTags.BEEHIVES) || state.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK);
    }
    /** Fells trees growing on this chunk's share of the village land, and trims foliage over it. */
    private void clearTrees(WorldGenLevel level,BoundingBox chunkBounds,ChunkPos chunk) {
        var layout=site.layout(); BlockPos center=site.center(); int extent=layout.extent();
        Set<BlockPos> ours=built();
        for(int x=Math.max(chunkBounds.minX(),center.getX()-extent);x<=Math.min(chunkBounds.maxX(),center.getX()+extent);x++)
            for(int z=Math.max(chunkBounds.minZ(),center.getZ()-extent);z<=Math.min(chunkBounds.maxZ(),center.getZ()+extent);z++) {
                int ground=center.getY()+layout.ground(x-center.getX(),z-center.getZ());
                int top=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
                for(int y=ground-1;y<top;y++) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(ours.contains(pos)) continue;
                    BlockState state=level.getBlockState(pos);
                    if(wood(state)) fell(level,pos,ours,chunk);
                    else if(foliage(state)) level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
                }
            }
    }
    /** Trees may spill into the chunks around the one generating, which world generation may also change. */
    private static boolean writable(ChunkPos chunk,BlockPos pos) {
        return Math.abs((pos.getX()>>4)-chunk.x())<=1 && Math.abs((pos.getZ()>>4)-chunk.z())<=1;
    }
    /** Removes a whole tree: its connected wood, then the foliage that wood held up, sparing foliage beside other trees. */
    private static void fell(WorldGenLevel level,BlockPos start,Set<BlockPos> ours,ChunkPos chunk) {
        Set<BlockPos> logs=new HashSet<>(); var open=new ArrayDeque<BlockPos>();
        logs.add(start); open.add(start);
        while(!open.isEmpty() && logs.size()<MAX_LOGS) {
            BlockPos at=open.poll();
            for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
                BlockPos next=at.offset(dx,dy,dz);
                if(logs.contains(next) || ours.contains(next) || Math.abs(next.getX()-start.getX())>TREE_SPREAD
                        || Math.abs(next.getZ()-start.getZ())>TREE_SPREAD || !writable(chunk,next) || !wood(level.getBlockState(next))) continue;
                logs.add(next); open.add(next);
            }
        }
        Map<BlockPos,Integer> leaves=new HashMap<>(); var reach=new ArrayDeque<BlockPos>(logs);
        for(BlockPos log:logs) leaves.put(log,0);
        while(!reach.isEmpty() && leaves.size()<MAX_LEAVES) {
            BlockPos at=reach.poll(); int depth=leaves.get(at);
            if(depth>=LEAF_REACH) continue;
            for(Direction direction:Direction.values()) {
                BlockPos next=at.relative(direction);
                if(leaves.containsKey(next) || ours.contains(next) || !writable(chunk,next) || !foliage(level.getBlockState(next))) continue;
                leaves.put(next,depth+1); reach.add(next);
            }
        }
        for(BlockPos log:logs) level.setBlock(log,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
        for(BlockPos leaf:leaves.keySet()) {
            if(logs.contains(leaf) || besideOtherWood(level,leaf,chunk)) continue;
            level.setBlock(leaf,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
        }
    }
    /** Foliage touching wood still standing belongs to a neighbouring tree. */
    private static boolean besideOtherWood(WorldGenLevel level,BlockPos leaf,ChunkPos chunk) {
        for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) for(int dz=-1;dz<=1;dz++) {
            BlockPos near=leaf.offset(dx,dy,dz);
            if(writable(chunk,near) && wood(level.getBlockState(near))) return true;
        }
        return false;
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox chunkBounds,ChunkPos chunk,BlockPos pivot) {
        if(site.layout().design()>=NpcVillageLayout.WALLED) clearTrees(level,chunkBounds,chunk);
        Map<NpcVillagePlan.Column,BlockPos> supports=new HashMap<>();
        for(var placement:placements()) {
            BlockPos pos=placement.pos();
            if(!chunkBounds.isInside(pos) || !level.ensureCanWrite(pos)) continue;
            level.setBlock(pos,placement.state(),18);
            if(placement.protect() && !placement.state().isAir()) supports.merge(new NpcVillagePlan.Column(pos.getX(),pos.getZ()),pos,
                    (a,b) -> a.getY()<b.getY() ? a : b);
        }
        // Small noise-grid differences get real supports instead of leaving floating floor edges.
        for(var floor:supports.values()) for(int depth=1;depth<=8;depth++) {
            var pos=floor.below(depth);
            if(!chunkBounds.isInside(pos) || !level.ensureCanWrite(pos)) break;
            var state=level.getBlockState(pos);
            if(!state.isAir() && state.getFluidState().isEmpty() && !NaturalTerrain.vegetation(state)) break;
            level.setBlock(pos,Blocks.COBBLESTONE.defaultBlockState(),18);
        }
    }
}

package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Each chunk receives only its own part of the furnished village during the FEATURES stage, after trees grow. A walled
 * village then clears the trees standing on its land and the canopy reaching over it, and trims natural soil left above
 * its graded ground where the noise survey read the land a little low.
 */
public final class NpcTownPiece extends StructurePiece {
    private final NpcTownPlan.Candidate candidate;
    private final NpcVillageTerrain.Site site;
    private final int radius;
    private volatile List<NpcSettlements.Placement> plan;
    private volatile Map<BlockPos,Block> built;
    private volatile Map<Long,List<NpcSettlements.Placement>> byChunk;
    private volatile int[] grade;
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
    /** How far the village's land reaches from its center. */
    public int extent() { return site.layout().extent(); }
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
    /** The village's own blocks by position, which clearing never touches once they stand. */
    private Map<BlockPos,Block> built() {
        var result=built;
        if(result==null) synchronized(this) {
            if(built==null) {
                Map<BlockPos,Block> blocks=new HashMap<>();
                for(var p:placements()) if(!p.state().isAir()) blocks.put(p.pos(),p.state().getBlock());
                built=blocks;
            }
            result=built;
        }
        return result;
    }
    /** Placements grouped by chunk, so each chunk walks only its own share of a large village. */
    private List<NpcSettlements.Placement> inChunk(ChunkPos chunk) {
        var result=byChunk;
        if(result==null) synchronized(this) {
            if(byChunk==null) {
                Map<Long,List<NpcSettlements.Placement>> groups=new HashMap<>();
                for(var p:placements()) groups.computeIfAbsent(ChunkPos.containing(p.pos()).pack(),k -> new ArrayList<>()).add(p);
                byChunk=groups;
            }
            result=byChunk;
        }
        return result.getOrDefault(chunk.pack(),List.of());
    }
    private int[] grade() {
        var result=grade;
        if(result==null) synchronized(this) {
            if(grade==null) grade=NpcVillagePlan.grade(town());
            result=grade;
        }
        return result;
    }
    /** Trees may spill into the chunks around the one generating, which world generation may also change. */
    private static boolean writable(ChunkPos chunk,BlockPos pos) {
        return Math.abs((pos.getX()>>4)-chunk.x())<=1 && Math.abs((pos.getZ()>>4)-chunk.z())<=1;
    }
    /**
     * Natural soil above the graded ground of this chunk's untouched columns, with whatever grows on it. The survey reads
     * the land from noise every few blocks, so a column it read a block low keeps a plug of earth beside the streets.
     */
    private void trimStrays(WorldGenLevel level,BoundingBox chunkBounds) {
        int[] grade=grade(); if(grade==null) return;
        var layout=site.layout(); BlockPos center=site.center(); int extent=layout.extent();
        Map<BlockPos,Block> ours=built();
        for(int x=Math.max(chunkBounds.minX(),center.getX()-extent);x<=Math.min(chunkBounds.maxX(),center.getX()+extent);x++)
            for(int z=Math.max(chunkBounds.minZ(),center.getZ()-extent);z<=Math.min(chunkBounds.maxZ(),center.getZ()+extent);z++) {
                int floor=center.getY()+grade[layout.cell(x-center.getX(),z-center.getZ())];
                int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                boolean cut=false;
                for(int y=floor;y<top;y++) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(ours.containsKey(pos)) { cut=false; continue; }
                    BlockState state=level.getBlockState(pos);
                    if(NaturalTerrain.soil(state)) { level.setBlock(pos,Blocks.AIR.defaultBlockState(),18); cut=true; }
                    else if(cut && !state.isAir() && state.getFluidState().isEmpty() && state.canBeReplaced())
                        level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
                    else cut=false;
                }
            }
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox chunkBounds,ChunkPos chunk,BlockPos pivot) {
        Map<NpcVillagePlan.Column,BlockPos> supports=new HashMap<>();
        for(var placement:inChunk(chunk)) {
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
        if(site.layout().design()<NpcVillageLayout.WALLED) return;
        Map<BlockPos,Block> ours=built();
        VillageClearing.clear(level,site.center(),site.layout(),chunkBounds.minX(),chunkBounds.minZ(),chunkBounds.maxX(),chunkBounds.maxZ(),
                pos -> { Block block=ours.get(pos); return block!=null && level.getBlockState(pos).is(block); },pos -> writable(chunk,pos),18);
        // Earlier designs finish their partly generated chunks as they began; the activation pass still clears their trees.
        if(site.layout().design()>=NpcVillageLayout.LEVELLED) trimStrays(level,chunkBounds);
    }
}

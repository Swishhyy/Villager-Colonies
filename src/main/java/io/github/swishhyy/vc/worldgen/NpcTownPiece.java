package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.settlement.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

/** Each chunk receives only its own part of the furnished village during the FEATURES stage. */
public final class NpcTownPiece extends StructurePiece {
    private final NpcTownPlan.Candidate candidate;
    private final NpcVillageTerrain.Site site;
    private final int radius;
    private volatile List<NpcSettlements.Placement> plan;
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
        int e=NpcVillageLayout.EXTENT,low=site.layout().ground().stream().mapToInt(Integer::intValue).min().orElse(0),
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
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox chunkBounds,ChunkPos chunk,BlockPos pivot) {
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

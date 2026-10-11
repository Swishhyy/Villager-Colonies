package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.VC;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class WorldWorkData extends SavedData {
    public record VeinCooldown(BlockPos pos,long readyAt) {
        public static final Codec<VeinCooldown> CODEC=RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(VeinCooldown::pos),
            Codec.LONG.fieldOf("ready_at").forGetter(VeinCooldown::readyAt)).apply(i,VeinCooldown::new));
    }
    public static final Codec<WorldWorkData> CODEC=RecordCodecBuilder.create(i -> i.group(
        BlockPos.CODEC.listOf().optionalFieldOf("protected_blocks",List.of()).forGetter(d -> new ArrayList<>(d.protectedBlocks)),
        PlantingSite.CODEC.listOf().optionalFieldOf("plantings",List.of()).forGetter(d -> d.plantings),
        ExcavationJob.CODEC.listOf().optionalFieldOf("excavations",List.of()).forGetter(d -> new ArrayList<>(d.excavations.values())),
        GuardPosts.CODEC.listOf().optionalFieldOf("guard_posts",List.of()).forGetter(d -> new ArrayList<>(d.guardPosts.values())),
        ForestryService.Tree.CODEC.listOf().optionalFieldOf("cleared_trees",List.of()).forGetter(d -> new ArrayList<>(d.clearedTrees.values())),
        VeinCooldown.CODEC.listOf().optionalFieldOf("vein_cooldowns",List.of()).forGetter(d -> d.veins.entrySet().stream().map(e -> new VeinCooldown(e.getKey(),e.getValue())).toList())
    ).apply(i,WorldWorkData::new));
    private static final SavedDataType<WorldWorkData> TYPE=new SavedDataType<>(
        Identifier.fromNamespaceAndPath(VC.MODID,"world_work"),WorldWorkData::new,CODEC,null);
    public final Set<BlockPos> protectedBlocks;
    public final List<PlantingSite> plantings;
    public final Map<BlockPos,ExcavationJob> excavations=new LinkedHashMap<>();
    public final Map<BlockPos,GuardPosts> guardPosts=new LinkedHashMap<>();
    public final Map<BlockPos,ForestryService.Tree> clearedTrees=new LinkedHashMap<>();
    public final Map<BlockPos,Long> veins=new LinkedHashMap<>();
    public WorldWorkData() { this(List.of(),List.of(),List.of()); }
    public WorldWorkData(List<BlockPos> protectedBlocks,List<PlantingSite> plantings,List<ExcavationJob> excavations) {
        this(protectedBlocks,plantings,excavations,List.of());
    }
    public WorldWorkData(List<BlockPos> protectedBlocks,List<PlantingSite> plantings,List<ExcavationJob> excavations,List<GuardPosts> guardPosts) {
        this(protectedBlocks,plantings,excavations,guardPosts,List.of());
    }
    public WorldWorkData(List<BlockPos> protectedBlocks,List<PlantingSite> plantings,List<ExcavationJob> excavations,List<GuardPosts> guardPosts,List<ForestryService.Tree> clearedTrees) {
        this(protectedBlocks,plantings,excavations,guardPosts,clearedTrees,List.of());
    }
    private WorldWorkData(List<BlockPos> protectedBlocks,List<PlantingSite> plantings,List<ExcavationJob> excavations,List<GuardPosts> guardPosts,List<ForestryService.Tree> clearedTrees,List<VeinCooldown> cooldowns) {
        this.protectedBlocks=new HashSet<>();
        protectedBlocks.forEach(p -> this.protectedBlocks.add(p.immutable()));
        this.plantings=new ArrayList<>(plantings);
        excavations.forEach(j -> this.excavations.put(j.station,j));
        guardPosts.forEach(p -> this.guardPosts.put(p.station(),p));
        clearedTrees.forEach(t -> this.clearedTrees.put(t.root(),t));
        cooldowns.forEach(v -> this.veins.put(v.pos().immutable(),v.readyAt()));
    }
    public static WorldWorkData get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void queue(PlantingSite site) {
        if(!plantings.contains(site)) { plantings.add(site); setDirty(); }
    }
    public void protect(BlockPos pos) { if(protectedBlocks.add(pos.immutable())) setDirty(); }
}

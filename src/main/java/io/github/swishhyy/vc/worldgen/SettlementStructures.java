package io.github.swishhyy.vc.worldgen;

import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen workers place blocks; the server thread alone registers claims, supplies and AI. */
public final class SettlementStructures {
    private static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,VC.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,VC.MODID);
    private static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,VC.MODID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>,RoadFeature> ROADS=FEATURES.register("roads",RoadFeature::new);
    public static final DeferredHolder<StructureType<?>,StructureType<NpcTownStructure>> TOWN_TYPE=TYPES.register("settlement",() -> () -> NpcTownStructure.CODEC);
    public static final DeferredHolder<StructureType<?>,StructureType<NpcTownStructure>> CITY_TYPE=TYPES.register("settlement_city",() -> () -> NpcTownStructure.CITY_CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> TOWN_PIECE=PIECES.register("settlement",() -> NpcTownPiece::new);
    public static final ResourceKey<Structure> TOWN=ResourceKey.create(Registries.STRUCTURE,Identifier.fromNamespaceAndPath(VC.MODID,"settlement"));
    public static final ResourceKey<Structure> CITY=ResourceKey.create(Registries.STRUCTURE,Identifier.fromNamespaceAndPath(VC.MODID,"settlement_city"));
    private final Map<ServerLevel,Map<UUID,NpcTownPiece>> pending=new WeakHashMap<>();
    private int activationCursor;
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); FEATURES.register(bus); }

    @SubscribeEvent public void loaded(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        var pieces=event.getChunk().getAllStarts().values().stream().filter(s -> s.isValid() && s.getStructure() instanceof NpcTownStructure)
                .flatMap(s -> s.getPieces().stream()).filter(NpcTownPiece.class::isInstance).map(NpcTownPiece.class::cast).toList();
        if(pieces.isEmpty()) return;
        level.getServer().execute(() -> {
            var regions=NpcWorldData.get(level);
            for(var piece:pieces) if(!regions.generated.contains(piece.candidate().region()))
                pending.computeIfAbsent(level,l -> new LinkedHashMap<>()).putIfAbsent(piece.candidate().id(),piece);
        });
    }
    /** A start leaves the queue with the chunk that holds it, and returns when that chunk loads; a city's centre may lie a few chunks away. */
    @SubscribeEvent public void unloaded(ChunkEvent.Unload event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        var ids=event.getChunk().getAllStarts().values().stream().filter(s -> s.getStructure() instanceof NpcTownStructure)
                .flatMap(s -> s.getPieces().stream()).filter(NpcTownPiece.class::isInstance).map(p -> ((NpcTownPiece)p).candidate().id()).toList();
        if(ids.isEmpty()) return;
        level.getServer().execute(() -> {
            var queue=pending.get(level);
            if(queue!=null) ids.forEach(queue::remove);
        });
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%20!=0) return;
        var queue=pending.get(level); if(queue==null || queue.isEmpty()) return;
        var choices=queue.values().toArray(NpcTownPiece[]::new);
        for(int checked=0;checked<Math.min(16,choices.length);checked++) {
            var piece=choices[Math.floorMod(activationCursor++,choices.length)];
            if(!NpcSettlements.loaded(level,piece.candidate().center(),piece.extent())) continue;
            activate(level,piece); queue.remove(piece.candidate().id()); return;
        }
    }
    public static Settlement activate(ServerLevel level,NpcTownPiece piece) {
        if(!NpcSettlements.loaded(level,piece.candidate().center(),piece.extent())) return null;
        var data=SettlementData.get(level); var candidate=piece.candidate();
        var existing=data.byId(candidate.id()); if(existing!=null) return existing;
        var regions=NpcWorldData.get(level); if(regions.generated.contains(candidate.region())) return null;
        var town=piece.town();
        if(!level.getBlockState(town.center).is(VC.BANNER.get()) || NpcSettlements.stations(town).stream()
                .anyMatch(s -> !level.getBlockState(s.position()).is(VC.STATIONS.get(s.role()).get()))) {
            regions.generated(candidate.region());
            VC.LOGGER.warn("[VC][worldgen] Town {} was changed before activation; preserving its blocks",candidate.id()); return null;
        }
        if(data.settlements.stream().anyMatch(t -> t.overlaps(town.center,town.radius))) {
            regions.generated(candidate.region());
            VC.LOGGER.warn("[VC][worldgen] Town {} overlaps an existing claim; preserving ownership",candidate.id()); return null;
        }
        data.settlements.add(town); regions.generated(candidate.region());
        var protection=WorldWorkData.get(level);
        for(var p:piece.placements()) if(p.protect()) protection.protect(p.pos());
        // Every chunk of the land has finished generating, so no neighbour's trees can spill in after this pass.
        if(NpcVillagePlan.walled(town)) VillageClearing.clear(level,town,piece.placements());
        NpcSettlements.finishFounding(level,town); data.setDirty();
        RoadNetwork.connect(level,town);
        VC.LOGGER.info("[VC][worldgen] Activated town={} townId={} position={} citizens={}",town.name,town.id,town.center,town.citizens.size());
        return town;
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        pending.keySet().removeIf(l -> l.getServer()==event.getServer());
        RoadNetwork.forget(event.getServer());
    }
}

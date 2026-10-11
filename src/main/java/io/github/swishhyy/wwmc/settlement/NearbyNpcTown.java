package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One temporary nine-chunk search per world gives players a nearby NPC trading partner. */
public final class NearbyNpcTown {
    private static final TicketController CONTROLLER=new TicketController(
            Identifier.fromNamespaceAndPath(WWMC.MODID,"npc_discovery"),(level,helper) -> {
                // Search state is transient. Saved construction resumes through a new player search.
                for(UUID owner:new ArrayList<>(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
            });
    private final Map<ServerLevel,Search> active=new WeakHashMap<>();
    private final Map<UUID,Long> retryAt=new HashMap<>();
    private int playerCursor;

    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }
    private static boolean enabled() { return Config.RANDOM_TOWNS.get() && Config.NEARBY_NPC_TOWN.get() && Config.MAX_NPC_TOWNS.get()>0; }
    private static double distanceSquared(BlockPos a,BlockPos b) {
        double x=(double)a.getX()-b.getX(),z=(double)a.getZ()-b.getZ(); return x*x+z*z;
    }

    /** Returns whether a nearest-first search was started; existing completed neighbours need no work. */
    public boolean ensureNearby(ServerPlayer player) {
        if(!(player.level() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || !enabled()
                || player.isSpectator() || active.containsKey(level) || level.getGameTime()<retryAt.getOrDefault(player.getUUID(),0L)) return false;
        var data=SettlementData.get(level); BlockPos origin=player.blockPosition();
        long reach=2L*Config.NPC_SPACING.get();
        var nearby=data.settlements.stream().filter(t -> t.trading.npc && distanceSquared(t.center,origin)<=reach*reach).toList();
        if(nearby.stream().anyMatch(t -> t.trading.buildIndex<0)) return false;
        Settlement pending=nearby.stream().min(Comparator.comparingDouble(t -> distanceSquared(t.center,origin))).orElse(null);
        if(pending==null && data.settlements.stream().filter(t -> t.trading.npc).count()>=Config.MAX_NPC_TOWNS.get()) return false;
        var search=new Search(player.getUUID(),origin,NpcTownPlan.nearbyPlots(level.getSeed(),origin,Config.NPC_SPACING.get()));
        if(pending!=null) { search.building=pending; search.select(pending.center); }
        active.put(level,search);
        WWMC.LOGGER.info("[WWMC][npc-search] Started near player={} position={} resuming={}",player.getName().getString(),origin,pending!=null);
        return true;
    }

    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime()%5!=0) return;
        Search search=active.get(level);
        if(search!=null) {
            var player=level.getPlayerByUUID(search.owner);
            if(!enabled() || player==null || player.isSpectator()
                    || distanceSquared(player.blockPosition(),search.origin)>(long)Config.NPC_SPACING.get()*Config.NPC_SPACING.get()) {
                finish(level,search,200); return;
            }
            if(search.building==null) {
                long reach=2L*Config.NPC_SPACING.get();
                var neighbour=SettlementData.get(level).settlements.stream()
                        .filter(t -> t.trading.npc && distanceSquared(t.center,search.origin)<=reach*reach)
                        .min(Comparator.comparingDouble(t -> distanceSquared(t.center,search.origin))).orElse(null);
                if(neighbour!=null) {
                    if(!neighbour.center.equals(search.center)) { search.release(level); search.select(neighbour.center); }
                    search.building=neighbour; search.deadline=level.getGameTime()+1200;
                } else if(SettlementData.get(level).settlements.stream().filter(t -> t.trading.npc).count()>=Config.MAX_NPC_TOWNS.get()) {
                    finish(level,search,200); return;
                }
            }
            if(search.building!=null && search.building.trading.buildIndex<0) {
                WWMC.LOGGER.info("[WWMC][npc-search] Established town={} townId={} position={}",search.building.name,search.building.id,search.building.center);
                finish(level,search,200); return;
            }
            if(search.center==null && !nextPlot(level,search)) {
                WWMC.LOGGER.info("[WWMC][npc-search] No suitable plot near {}; retrying after ten loaded minutes",search.origin);
                finish(level,search,12000); return;
            }
            if(search.held.size()<search.chunks.size()) {
                long chunk=search.chunks.get(search.held.size()); ChunkPos pos=new ChunkPos((int)chunk,(int)(chunk>>32));
                CONTROLLER.forceChunk(level,search.owner,pos.x(),pos.z(),true,false); search.held.add(chunk);
                if(search.held.size()==search.chunks.size()) search.deadline=level.getGameTime()+(search.building==null ? 400 : 1200);
                return;
            }
            if(search.building==null && NpcSettlements.loaded(level,search.center)) {
                search.building=NpcSettlements.discover(level,search.plot.candidate(),List.of(search.center));
                if(search.building==null) { search.release(level); search.center=null; }
                else search.deadline=level.getGameTime()+1200;
            } else if(level.getGameTime()>search.deadline) {
                if(search.building!=null) {
                    WWMC.LOGGER.warn("[WWMC][npc-search] Construction paused town={} reason={}; releasing temporary chunks",search.building.name,search.building.trading.status);
                    finish(level,search,12000);
                } else { search.release(level); search.center=null; }
            }
            return;
        }
        if(!enabled() || level.getGameTime()%100!=0) return;
        var players=level.players().stream().filter(p -> !(p instanceof FakePlayer) && !p.isSpectator()).toList();
        if(!players.isEmpty()) ensureNearby(players.get(Math.floorMod(playerCursor++,players.size())));
    }

    private static boolean nextPlot(ServerLevel level,Search search) {
        var data=SettlementData.get(level); var generated=NpcWorldData.get(level).generated;
        while(!search.plots.isEmpty()) {
            var plot=search.plots.removeFirst(); BlockPos pos=plot.site();
            if(generated.contains(plot.candidate().region()) || distanceSquared(pos,search.origin)<256.0*256.0
                    || data.settlements.stream().anyMatch(t -> t.overlaps(pos,Config.SETTLEMENT_RADIUS.get()))
                    || !level.getWorldBorder().isWithinBounds(pos.offset(-NpcSettlements.EXTENT,0,-NpcSettlements.EXTENT))
                    || !level.getWorldBorder().isWithinBounds(pos.offset(NpcSettlements.EXTENT,0,NpcSettlements.EXTENT))) continue;
            search.plot=plot; search.select(pos);
            WWMC.LOGGER.debug("[WWMC][npc-search] Checking plot={} region={}",pos,plot.candidate().region()); return true;
        }
        return false;
    }
    private void finish(ServerLevel level,Search search,int delay) {
        search.release(level); active.remove(level); retryAt.put(search.owner,level.getGameTime()+delay);
    }
    public int heldChunks(ServerLevel level) { var search=active.get(level); return search==null ? 0 : search.held.size(); }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        for(var level:new ArrayList<>(active.keySet())) if(level.getServer()==event.getServer()) {
            active.get(level).release(level); active.remove(level);
        }
        retryAt.clear();
    }

    private static final class Search {
        final UUID owner; final BlockPos origin; final Deque<NpcTownPlan.Plot> plots; final Set<Long> held=new HashSet<>();
        NpcTownPlan.Plot plot; BlockPos center; Settlement building; List<Long> chunks=List.of(); long deadline=Long.MAX_VALUE;
        Search(UUID owner,BlockPos origin,List<NpcTownPlan.Plot> plots) {
            this.owner=owner; this.origin=origin.immutable(); this.plots=new ArrayDeque<>(plots);
        }
        void select(BlockPos pos) { center=pos; chunks=List.copyOf(TradeChunks.window(pos)); }
        void release(ServerLevel level) {
            for(long chunk:held) {
                var pos=new ChunkPos((int)chunk,(int)(chunk>>32)); CONTROLLER.forceChunk(level,owner,pos.x(),pos.z(),false,false);
            }
            held.clear();
        }
    }
}

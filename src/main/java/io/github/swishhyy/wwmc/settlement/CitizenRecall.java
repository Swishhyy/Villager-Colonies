package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Brings citizens back to work when they are stranded outside the ticking area while their station (or, for a citizen
 * without a job, the banner) is inside it: one frozen in a chunk at the edge of the loaded area, or one whose chunk
 * unloaded while it was away. Each town keeps the place its citizens were last seen; to fetch a citizen from an
 * unloaded chunk, a 3x3 window around that place is loaded for a few seconds, never longer. A citizen that cannot be
 * found there leaves the roster, so its job and population place open up; it rejoins if it ever turns up.
 */
public final class CitizenRecall {
    /** How often citizens are checked. */
    private static final int SCAN=20;
    /** A citizen must be missing or frozen this long first, so chunks still loading around a player are left alone. */
    static final int GRACE=200;
    /** Once the last place has loaded, how long to wait for the citizen to appear there. */
    static final int LOOK=200;
    /** Between searches of the same place. */
    static final int RETRY=600;
    /** Searches that find nobody before a citizen leaves the roster. */
    static final int SEARCHES=2;
    /** A citizen missing since before places were kept leaves the roster after five minutes. */
    static final int UNKNOWN=6000;
    /** A frozen citizen brought back is left alone this long, in case its errand leads straight back out. */
    private static final int COOLDOWN=1200;
    /** Places searched at once per level. */
    private static final int MAX_SEARCHES=3;
    private static final class Missing {
        final UUID town;
        final long since;
        BlockPos place;
        long heldAt=-1,loadedAt=-1,retryAt,nextWarning;
        int failed;
        boolean returnBlocked;
        Missing(UUID town,long since) { this.town=town; this.since=since; }
        boolean held() { return place!=null && heldAt>=0; }
    }
    private static final Map<ServerLevel,Map<UUID,Missing>> MISSING=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Long>> RECALLED=new WeakHashMap<>();
    private static final TicketController CONTROLLER=new TicketController(Identifier.fromNamespaceAndPath(WWMC.MODID,"recall"),(level,helper) -> {
        // A search never outlasts the session that started it.
        for(UUID owner:List.copyOf(helper.getEntityTickets().keySet())) helper.removeAllTickets(owner);
    });
    public static void register(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }

    /** Notes where a ticking citizen stands. One that left the roster while missing rejoins its town here. */
    public static void seen(ServerLevel level,Settlement town,CitizenEntity citizen) {
        UUID id=citizen.getUUID();
        BlockPos pos=citizen.blockPosition();
        boolean changed=false;
        if(!town.citizens.contains(id)) {
            town.citizens.add(id); changed=true;
            if(Config.SERVER_DIAGNOSTICS.get()) WWMC.LOGGER.info("[WWMC][citizen-rejoined] {} citizen=\"{}\" position={}",context(level,town,id),citizen.getName().getString(),pos);
        }
        BlockPos old=town.citizenPlaces.put(id,pos);
        if(old==null || Math.floorDiv(old.getX(),16)!=Math.floorDiv(pos.getX(),16) || Math.floorDiv(old.getZ(),16)!=Math.floorDiv(pos.getZ(),16)) changed=true;
        if(changed) SettlementData.get(level).setDirty();
    }
    /** Where a citizen belongs: its station, or the banner without one; null when that is not ticking, as when nobody is near. */
    public static BlockPos home(ServerLevel level,Settlement town,UUID id) {
        if(SquadService.assigned(town,id)) return null;
        BlockPos station=town.jobs.home(id);
        BlockPos home=station!=null && town.station(station)!=null ? station : town.center;
        return level.hasChunkAt(home) && level.isPositionEntityTicking(home) ? home : null;
    }
    /** What the town knows about a citizen that is not loaded, for its screens. */
    public static String whereabouts(ServerLevel level,Settlement town,UUID id) {
        BlockPos place=town.citizenPlaces.get(id);
        if(SquadService.assigned(town,id)) return "On a squad expedition"+(place==null ? "" : "; last seen at "+place.getX()+", "+place.getZ());
        Missing state=MISSING.getOrDefault(level,Map.of()).get(id);
        if(place==null) {
            long left=state==null ? UNKNOWN : Math.max(0,UNKNOWN-(level.getGameTime()-state.since));
            return "Out of range, place unknown: leaves the roster in "+(left+1199)/1200+" min";
        }
        String at=place.getX()+", "+place.getZ();
        if(state!=null && state.held()) return "Out of range at "+at+": bringing them back";
        if(state!=null && state.returnBlocked) return "Return blocked at "+at+": check standing room by the station/banner and active trips; retry in "+Math.max(0,(state.retryAt-level.getGameTime()+19)/20)+"s";
        if(state!=null && state.failed>0) return "Not found at "+at+": looking again soon";
        return "Out of range at "+at+": brought back soon";
    }
    /** Places being searched for this town's citizens. */
    public static int searching(ServerLevel level,Settlement town) {
        return (int)MISSING.getOrDefault(level,Map.of()).values().stream().filter(state -> state.town.equals(town.id) && state.held()).count();
    }

    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%SCAN!=0) return;
        long now=level.getGameTime();
        SettlementData data=SettlementData.get(level);
        Map<UUID,Missing> missing=MISSING.computeIfAbsent(level,l -> new HashMap<>());
        Map<UUID,Long> recalled=RECALLED.computeIfAbsent(level,l -> new HashMap<>());
        recalled.values().removeIf(at -> now-at>COOLDOWN);
        Set<UUID> watched=new HashSet<>();
        int searches=(int)missing.values().stream().filter(Missing::held).count();
        for(Settlement town:List.copyOf(data.settlements)) {
            if(town.citizenPlaces.keySet().retainAll(new HashSet<>(town.citizens))) data.setDirty();
            for(UUID id:List.copyOf(town.citizens)) {
                // A trader on a trip keeps its own window of loaded chunks and is never fetched home mid-route.
                if(id.equals(town.trading.runner) || SquadService.assigned(town,id)) continue;
                BlockPos home=home(level,town,id);
                if(home==null) continue;
                Missing state=missing.get(id);
                CitizenEntity citizen=level.getEntity(id) instanceof CitizenEntity found && found.isAlive() ? found : null;
                boolean held=state!=null && state.held();
                if(citizen!=null && !held && level.isPositionEntityTicking(citizen.blockPosition())) continue;
                watched.add(id);
                if(state==null) { state=new Missing(town.id,now); missing.put(id,state); }
                if(now-state.since<GRACE) continue;
                if(citizen!=null) {
                    // Found in a frozen chunk, or in the chunks loaded to look for it.
                    if(now<state.retryAt || !held && recalled.containsKey(id)) continue;
                    BlockPos from=citizen.blockPosition();
                    boolean back=citizen.recall(level,town,home);
                    if(held) searches--;
                    release(level,id,state);
                    // Without standing room by the station or banner, try again later rather than every scan.
                    if(back) {
                        recalled.put(id,now);
                        if(Config.SERVER_DIAGNOSTICS.get()) WWMC.LOGGER.info("[WWMC][citizen-recalled] {} from={} to={} job={} health={}/{}",
                                context(level,town,id),from,citizen.blockPosition(),town.jobs.home(id),citizen.getHealth(),citizen.getMaxHealth());
                        missing.remove(id); watched.remove(id);
                    } else {
                        state.returnBlocked=true;
                        state.retryAt=now+RETRY;
                        if(Config.SERVER_DIAGNOSTICS.get() && now>=state.nextWarning) {
                            WWMC.LOGGER.warn("[WWMC][recall-blocked] {} position={} home={} reason=no-standing-room-or-active-trip retrySeconds={}",context(level,town,id),from,home,RETRY/20);
                            state.nextWarning=now+Config.DIAGNOSTIC_REPEAT.get()*20L;
                        }
                    }
                    continue;
                }
                BlockPos place=town.citizenPlaces.get(id);
                if(place==null) {
                    if(now-state.since>=UNKNOWN) { strikeOff(level,town,id,"has been out of range for five minutes, and nobody knows where"); missing.remove(id); watched.remove(id); }
                    continue;
                }
                if(!held) {
                    if(now<state.retryAt || searches>=MAX_SEARCHES) continue;
                    state.place=place; state.heldAt=now; state.loadedAt=-1;
                    if(Config.SERVER_DIAGNOSTICS.get()) WWMC.LOGGER.debug("[WWMC][citizen-search] {} lastSeen={} attempt={}/{}",context(level,town,id),place,state.failed+1,SEARCHES);
                    force(level,id,place,true); searches++;
                    continue;
                }
                if(state.loadedAt<0 && level.hasChunkAt(state.place)) state.loadedAt=now;
                boolean looked=state.loadedAt>=0 ? now-state.loadedAt>=LOOK : now-state.heldAt>=3*LOOK;
                if(!looked) continue;
                BlockPos searched=state.place;
                release(level,id,state); searches--;
                if(++state.failed>=SEARCHES) {
                    strikeOff(level,town,id,"was not found where last seen, near "+searched.getX()+", "+searched.getZ());
                    missing.remove(id); watched.remove(id);
                } else state.retryAt=now+RETRY;
            }
        }
        // Citizens back in range, gone from a roster, or whose town nobody is near any more: stop looking for them.
        for(var it=missing.entrySet().iterator();it.hasNext();) {
            var entry=it.next();
            if(watched.contains(entry.getKey())) continue;
            release(level,entry.getKey(),entry.getValue()); it.remove();
        }
    }
    private static String context(ServerLevel level,Settlement town,UUID id) {
        return "dimension="+level.dimension()+" town=\""+town.name+"\" townId="+town.id+" citizen=\""+town.citizenNames.getOrDefault(id,"unknown")+"\" citizenId="+id;
    }
    private static void force(ServerLevel level,UUID id,BlockPos place,boolean add) {
        int x=Math.floorDiv(place.getX(),16),z=Math.floorDiv(place.getZ(),16);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) CONTROLLER.forceChunk(level,id,x+dx,z+dz,add,false);
    }
    private static void release(ServerLevel level,UUID id,Missing state) {
        if(state.held()) force(level,id,state.place,false);
        state.heldAt=-1; state.loadedAt=-1;
    }
    /** Takes a citizen nobody can find off the roster: its job and population place open up. */
    private static void strikeOff(ServerLevel level,Settlement town,UUID id,String why) {
        String name=town.citizenNames.getOrDefault(id,"A citizen");
        if(Config.SERVER_DIAGNOSTICS.get()) WWMC.LOGGER.warn("[WWMC][citizen-missing] {} lastSeen={} job={} reason=\"{}\" action=removed-from-roster; citizen rejoins if found",
                context(level,town,id),town.citizenPlaces.get(id),town.jobs.home(id),why);
        town.citizens.remove(id); town.citizenNames.remove(id); town.citizenPlaces.remove(id); town.jobs.release(id);
        SettlementData.get(level).setDirty();
        ServerPlayer owner=level.getServer().getPlayerList().getPlayer(town.owner);
        if(owner!=null) SettlementService.tell(owner,name+" "+why+", so they left "+town.name+"'s roster and their job is open. If they turn up, they rejoin.");
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        MISSING.keySet().removeIf(level -> level.getServer()==event.getServer());
        RECALLED.keySet().removeIf(level -> level.getServer()==event.getServer());
    }
}

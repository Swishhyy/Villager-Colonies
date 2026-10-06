package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.AlarmState;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Citizens count the hostiles they can see. When the count reaches the alarm threshold, the guard nearest a bell
 * runs to ring it; civilians then take cover until no hostile has been sighted for {@link AlarmState#ALL_CLEAR_TICKS}.
 */
public final class DefenseService {
    private static final int INTERVAL=20,GUARD_SIGHT=24,ALERT_SIGHT=32,CIVILIAN_SIGHT=8,BELL_SEARCH=96,REVEAL_RANGE=48,WARNING_TICKS=2400;
    private static final class Alert {
        final AlarmState state=new AlarmState();
        /** Guards who failed a bell run, and the game time they may be sent again. */
        final Map<UUID,Long> failedRunners=new HashMap<>();
        long now;
        UUID runner;
        BlockPos bell,lastBell;
        long retryAt,warnedAt=Long.MIN_VALUE/2;
        int sighted;
    }
    private static final Map<UUID,Alert> ALERTS=new HashMap<>();
    /** Two missed runs' worth of time before the same guard is sent to a bell again. */
    private static final long RUNNER_BACKOFF=AlarmState.RUN_TICKS*2L;
    private record BellRing(ServerLevel level,BlockPos position) {}
    /** Ignore our own alarm/all-clear rings; physical bell sounds otherwise raise the alarm. */
    private static final Set<BellRing> INTERNAL_RINGS=new HashSet<>();
    public static boolean alarmed(Settlement town) {
        Alert alert=ALERTS.get(town.id); return alert!=null && alert.state.ringing();
    }
    /** The bell this guard is assigned to ring, or null. */
    public static BlockPos bellRun(Settlement town,UUID guard) {
        Alert alert=ALERTS.get(town.id);
        return alert!=null && alert.state.phase()==AlarmState.Phase.RAISING && guard.equals(alert.runner) ? alert.bell : null;
    }
    public static String status(Settlement town) {
        Alert alert=ALERTS.get(town.id);
        if(alert==null || alert.state.phase()==AlarmState.Phase.CALM) return "calm";
        return alert.state.ringing() ? "ALARM, "+alert.sighted+" hostiles in sight" : "a guard is running to the bell";
    }
    /** Nearest housing or barracks station where civilians wait out an alarm; the banner otherwise. */
    public static BlockPos refuge(ServerLevel level,Settlement town,BlockPos from) {
        return town.stations.stream().filter(s -> s.role().providesHousing() && SettlementService.active(level,s))
                .min(Comparator.comparingDouble(s -> s.position().distSqr(from))).map(Station::position).orElse(town.center);
    }
    public static void ring(ServerLevel level,Settlement town,CitizenEntity guard,BlockPos bell) {
        Alert alert=ALERTS.get(town.id);
        if(alert==null || !guard.getUUID().equals(alert.runner) || !bell.equals(alert.bell)) return;
        // A bell destroyed on the way sends another guard to a different one.
        if(!ringBell(level,bell,guard)) { abandon(town,guard.getUUID()); return; }
        alert.runner=null;
        alert.failedRunners.clear(); alert.lastBell=bell;
        if(!alert.state.ring()) return;
        wakeGuards(level,town);
        // Like a vanilla raid bell, the warning briefly outlines the hostiles near it.
        for(Monster monster:level.getEntitiesOfClass(Monster.class,new AABB(bell).inflate(REVEAL_RANGE),m -> m.isAlive() && town.contains(m.blockPosition())))
            monster.addEffect(new MobEffectInstance(MobEffects.GLOWING,200));
        announce(level,town,guard.getName().getString()+" rang the alarm bell: "+alert.sighted+" hostiles sighted near "+town.name
                +". Citizens are taking cover until the all-clear.");
    }
    /** The runner could not reach the bell; another guard is sent at the next check if the threat remains. */
    public static void abandon(Settlement town,UUID guard) {
        Alert alert=ALERTS.get(town.id);
        if(alert==null || !guard.equals(alert.runner)) return;
        alert.failedRunners.put(guard,alert.now+RUNNER_BACKOFF); alert.runner=null; alert.retryAt=0; alert.state.calm();
    }
    /** Owner command: sound the alarm without waiting for a runner, or call the all-clear early. */
    public static boolean toggle(ServerLevel level,Settlement town) {
        Alert alert=ALERTS.computeIfAbsent(town.id,id -> new Alert());
        if(alert.state.ringing()) { allClear(level,town,alert); return false; }
        alert.runner=null; alert.state.ring();
        wakeGuards(level,town);
        announce(level,town,"The alarm is raised in "+town.name+". Citizens are taking cover until the all-clear.");
        return true;
    }
    private static boolean ringBell(ServerLevel level,BlockPos pos,Entity ringer) {
        BellRing key=new BellRing(level,pos);
        INTERNAL_RINGS.add(key);
        try {
            return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof BellBlock bell && bell.attemptToRing(ringer,level,pos,null);
        } finally { INTERNAL_RINGS.remove(key); }
    }
    private static void wakeGuards(ServerLevel level,Settlement town) {
        for(CitizenEntity citizen:loadedCitizens(level,town)) if(citizen.isGuard()) citizen.wakeForAlarm();
    }
    @SubscribeEvent public void bellSound(PlayLevelSoundEvent.AtPosition event) {
        if(event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || event.getSound()==null
                || !event.getSound().value().equals(SoundEvents.BELL_BLOCK)) return;
        BlockPos pos=BlockPos.containing(event.getPosition());
        if(INTERNAL_RINGS.contains(new BellRing(level,pos)) || !level.hasChunkAt(pos)
                || !(level.getBlockState(pos).getBlock() instanceof BellBlock)) return;
        Settlement town=SettlementData.get(level).at(pos);
        if(town==null) return;
        Alert alert=ALERTS.computeIfAbsent(town.id,id -> new Alert());
        boolean fresh=!alert.state.ringing();
        alert.runner=null; alert.lastBell=pos; alert.failedRunners.clear(); alert.state.ring();
        wakeGuards(level,town);
        if(fresh) announce(level,town,"The bell raised the alarm in "+town.name+". All guards are on duty until the all-clear.");
    }
    private static BlockPos bellNear(ServerLevel level,Settlement town,BlockPos from) {
        return level.getPoiManager().findClosest(type -> type.is(PoiTypes.MEETING),
                pos -> town.contains(pos) && level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof BellBlock,
                from,BELL_SEARCH,PoiManager.Occupancy.ANY).orElse(null);
    }
    private static ServerPlayer owner(ServerLevel level,Settlement town) { return level.getServer().getPlayerList().getPlayer(town.owner); }
    private static void announce(ServerLevel level,Settlement town,String text) {
        ServerPlayer owner=owner(level,town);
        if(owner!=null) SettlementService.tell(owner,text);
    }
    static List<CitizenEntity> loadedCitizens(ServerLevel level,Settlement town) {
        List<CitizenEntity> result=new ArrayList<>();
        for(UUID id:town.citizens) if(level.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive()) result.add(citizen);
        return result;
    }
    /** Guards watch far, more so on alert; civilians only notice what is close. Each hostile counts once. */
    private static Set<Monster> sighted(ServerLevel level,Settlement town,List<CitizenEntity> citizens,boolean alert) {
        Set<Monster> seen=new HashSet<>();
        for(CitizenEntity citizen:citizens) {
            int range=citizen.isGuard() ? (alert ? ALERT_SIGHT : GUARD_SIGHT) : CIVILIAN_SIGHT;
            for(Monster monster:level.getEntitiesOfClass(Monster.class,citizen.getBoundingBox().inflate(range),
                    m -> m.isAlive() && !seen.contains(m) && town.contains(m.blockPosition()))) {
                if(citizen.distanceToSqr(monster)<=range*range && citizen.hasLineOfSight(monster)) seen.add(monster);
            }
        }
        return seen;
    }
    private static void dispatch(ServerLevel level,Settlement town,Alert alert,List<CitizenEntity> citizens) {
        long now=level.getGameTime();
        if(now<alert.retryAt) return;
        alert.retryAt=now+200;
        CitizenEntity runner=null; BlockPos bell=null; double best=Double.MAX_VALUE;
        for(CitizenEntity guard:citizens) {
            if(!guard.isGuard() || alert.failedRunners.getOrDefault(guard.getUUID(),Long.MIN_VALUE)>now) continue;
            BlockPos found=bellNear(level,town,guard.blockPosition());
            if(found!=null && guard.distanceToSqr(Vec3.atCenterOf(found))<best) { best=guard.distanceToSqr(Vec3.atCenterOf(found)); runner=guard; bell=found; }
        }
        if(runner==null) {
            // Failed runners get another chance once their back-off ends, so one bad path cannot disable the alarm for a whole attack.
            alert.failedRunners.values().removeIf(until -> until<=now);
            if(now-alert.warnedAt>=WARNING_TICKS) {
                alert.warnedAt=now;
                announce(level,town,alert.sighted+" hostiles sighted near "+town.name+", but no guard can reach a bell to raise the alarm. "
                        +"Place a bell inside the town and staff a Guard Station.");
            }
            return;
        }
        alert.runner=runner.getUUID(); alert.bell=bell; alert.state.dispatched(now);
        announce(level,town,runner.getName().getString()+" spotted "+alert.sighted+" hostiles and is running to ring the bell at "+bell.toShortString()+".");
    }
    private static void allClear(ServerLevel level,Settlement town,Alert alert) {
        alert.state.calm(); alert.runner=null; alert.failedRunners.clear();
        if(alert.lastBell!=null) ringBell(level,alert.lastBell,null);
        announce(level,town,"All clear in "+town.name+". Citizens are returning to work.");
    }
    private static void assess(ServerLevel level,Settlement town) {
        List<CitizenEntity> citizens=loadedCitizens(level,town);
        Alert alert=ALERTS.get(town.id);
        // With nobody left to see anything, a raised alarm still counts down to the all-clear.
        if(citizens.isEmpty() && alert==null) return;
        int threshold=Config.ALARM_THRESHOLD.get();
        Set<Monster> seen=sighted(level,town,citizens,alert!=null && alert.state.ringing());
        if(alert==null) {
            if(seen.size()<threshold) return;
            alert=new Alert(); ALERTS.put(town.id,alert);
        }
        alert.sighted=seen.size(); alert.now=level.getGameTime();
        if(alert.state.phase()==AlarmState.Phase.RAISING && alert.runner!=null
                && !(level.getEntity(alert.runner) instanceof CitizenEntity runner && runner.isAlive() && runner.isGuard())) {
            abandon(town,alert.runner);
        }
        switch(alert.state.observe(alert.sighted,threshold,level.getGameTime(),INTERVAL)) {
            case DISPATCH -> dispatch(level,town,alert,citizens);
            case STAND_DOWN -> {
                // A runner who ran out of time while hostiles remain is replaced; a vanished threat needs nobody.
                if(alert.sighted>0 && alert.runner!=null) alert.failedRunners.put(alert.runner,alert.now+RUNNER_BACKOFF); else alert.failedRunners.clear();
                alert.runner=null;
            }
            case ALL_CLEAR -> allClear(level,town,alert);
            case NONE -> {}
        }
        if(alert.state.phase()==AlarmState.Phase.CALM && alert.sighted==0 && level.getGameTime()-alert.warnedAt>=WARNING_TICKS) ALERTS.remove(town.id);
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%INTERVAL!=0) return;
        for(Settlement town:SettlementData.get(level).settlements) assess(level,town);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { ALERTS.clear(); INTERNAL_RINGS.clear(); }
}

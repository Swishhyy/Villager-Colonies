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
import net.minecraft.world.entity.NeutralMob;
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
 * Below the threshold, civilians report the hostiles they spot to the guards, who send up to two on-duty guards to
 * each; wave attackers that linger are reported the same way.
 */
public final class DefenseService {
    private static final int INTERVAL=20,GUARD_SIGHT=24,ALERT_SIGHT=32,CIVILIAN_SIGHT=8,BELL_SEARCH=96,REVEAL_RANGE=48,WARNING_TICKS=2400;
    /** Civilians report hostiles this close, farther than they count them toward the alarm, since they flee at twelve blocks. */
    private static final int REPORT_SIGHT=16;
    /** A report lasts this long after the hostile was last seen; guards stop answering a call after two missed checks. */
    private static final int REPORT_TICKS=600,RESPONDER_TICKS=40;
    /** Guards sent to one report, and how far they go for it. */
    private static final int RESPONDERS=2,RESPONSE_RANGE=160;
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
    /** A hostile someone reported: where it was last seen, by whom, and the guards answering. */
    private static final class Threat {
        final UUID mob;
        String reporter="";
        boolean wave;
        long until;
        final Map<UUID,Long> responders=new HashMap<>();
        Threat(UUID mob) { this.mob=mob; }
    }
    /** Reports per town, keyed by the hostile. */
    private static final Map<UUID,Map<UUID,Threat>> THREATS=new HashMap<>();
    /** A guard's call: the hostile, who reported it ("" for a wave attacker), and whether it came with a wave. */
    public record Call(Monster mob,String reporter,boolean wave) {}
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
        int reported=threats(town);
        String calls=reported==0 ? "" : ", "+reported+(reported==1 ? " hostile" : " hostiles")+" reported to the guards";
        if(alert==null || alert.state.phase()==AlarmState.Phase.CALM) return "calm"+calls;
        return (alert.state.ringing() ? "ALARM, "+alert.sighted+" hostiles in sight" : "a guard is running to the bell")+calls;
    }
    /** An enderman or other neutral mob that is not angry and hunting nobody is no reason to call the guards. */
    private static boolean calm(Monster monster) {
        return monster instanceof NeutralMob neutral && !neutral.isAngry() && monster.getTarget()==null;
    }
    /** Hostiles currently reported in this town. */
    public static int threats(Settlement town) { Map<UUID,Threat> threats=THREATS.get(town.id); return threats==null ? 0 : threats.size(); }
    /** Ask the guards to deal with a hostile; a fresh sighting keeps the report alive. */
    public static void report(Settlement town,Monster mob,String reporter,boolean wave,long now) {
        Threat threat=THREATS.computeIfAbsent(town.id,id -> new HashMap<>()).computeIfAbsent(mob.getUUID(),Threat::new);
        threat.reporter=reporter; threat.wave|=wave; threat.until=now+REPORT_TICKS;
    }
    /**
     * The reported hostile this on-duty guard should handle: the one it already answers, or else the nearest live
     * report inside the town with fewer than two guards on it. Null when there is nothing to answer.
     */
    public static Call assignment(ServerLevel level,Settlement town,CitizenEntity guard,java.util.function.Predicate<UUID> ignored) {
        Map<UUID,Threat> threats=THREATS.get(town.id);
        if(threats==null || threats.isEmpty()) return null;
        long now=level.getGameTime();
        Threat chosen=null; Monster target=null; double best=(double)RESPONSE_RANGE*RESPONSE_RANGE;
        for(Threat threat:threats.values()) {
            threat.responders.values().removeIf(asked -> asked<now-RESPONDER_TICKS);
            if(threat.until<now || !(level.getEntity(threat.mob) instanceof Monster mob) || !mob.isAlive()
                    || !town.contains(mob.blockPosition()) || ignored.test(threat.mob)) {
                threat.responders.remove(guard.getUUID()); continue;
            }
            if(threat.responders.containsKey(guard.getUUID())) { threat.responders.put(guard.getUUID(),now); return new Call(mob,threat.reporter,threat.wave); }
            double distance=guard.distanceToSqr(mob);
            if(threat.responders.size()<RESPONDERS && distance<best) { best=distance; chosen=threat; target=mob; }
        }
        if(chosen==null) return null;
        chosen.responders.put(guard.getUUID(),now);
        return new Call(target,chosen.reporter,chosen.wave);
    }
    /** The guard gives up on this report, so another may take it. */
    public static void release(Settlement town,UUID mob,UUID guard) {
        Map<UUID,Threat> threats=THREATS.get(town.id);
        Threat threat=threats==null ? null : threats.get(mob);
        if(threat!=null) threat.responders.remove(guard);
    }
    private static void forgetThreats(ServerLevel level,Settlement town) {
        Map<UUID,Threat> threats=THREATS.get(town.id);
        if(threats==null) return;
        long now=level.getGameTime();
        threats.values().removeIf(threat -> threat.until<now || level.getEntity(threat.mob) instanceof Monster mob && !mob.isAlive());
        if(threats.isEmpty()) THREATS.remove(town.id);
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
        CampaignService.record(level,town,text);
    }
    public static List<CitizenEntity> loadedCitizens(ServerLevel level,Settlement town) {
        List<CitizenEntity> result=new ArrayList<>();
        for(UUID id:town.citizens) if(level.getEntity(id) instanceof CitizenEntity citizen && citizen.isAlive()) result.add(citizen);
        return result;
    }
    /**
     * Guards watch far, more so on alert; civilians only count what is close toward the alarm. Each hostile counts
     * once. Civilians also report every hostile they can see within {@link #REPORT_SIGHT} blocks to the guards.
     */
    private static Set<Monster> sighted(ServerLevel level,Settlement town,List<CitizenEntity> citizens,boolean alert) {
        Set<Monster> seen=new HashSet<>(),reported=new HashSet<>();
        boolean guards=citizens.stream().anyMatch(CitizenEntity::isGuard);
        long now=level.getGameTime();
        for(CitizenEntity citizen:citizens) {
            boolean guard=citizen.isGuard();
            BlockPos post=guard ? town.jobs.home(citizen.getUUID()) : null;
            // A watchtower's guard sees much farther across the town.
            int range=guard ? (post!=null && GuardRoles.watchtower(level,post) ? GuardRoles.towerSight(town) : alert ? ALERT_SIGHT : GUARD_SIGHT) : CIVILIAN_SIGHT,scan=guard ? range : REPORT_SIGHT;
            for(Monster monster:level.getEntitiesOfClass(Monster.class,citizen.getBoundingBox().inflate(scan),
                    m -> m.isAlive() && town.contains(m.blockPosition()) && !(seen.contains(m) && (guard || reported.contains(m))))) {
                double distance=citizen.distanceToSqr(monster);
                if(distance>scan*scan || !citizen.hasLineOfSight(monster)) continue;
                if(distance<=range*range) seen.add(monster);
                if(!guard && calm(monster)) continue;
                if(!guard && reported.add(monster)) {
                    report(town,monster,citizen.getName().getString(),false,now);
                    citizen.called(monster,guards);
                }
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
        forgetThreats(level,town);
        List<CitizenEntity> citizens=loadedCitizens(level,town);
        Alert alert=ALERTS.get(town.id);
        // With nobody left to see anything, a raised alarm still counts down to the all-clear.
        if(citizens.isEmpty() && alert==null) return;
        int threshold=Config.ALARM_THRESHOLD.get();
        GuardRoles.lookout(level,town,citizens);
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
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { ALERTS.clear(); INTERNAL_RINGS.clear(); THREATS.clear(); GuardRoles.forget(); }
}

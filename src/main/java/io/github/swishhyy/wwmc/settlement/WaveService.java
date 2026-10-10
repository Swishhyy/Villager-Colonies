package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.WavePlan;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Hostile waves sized by population and by the town's population upgrades. They gather beyond furnished stations and
 * registered defenses in loaded terrain at nightfall,
 * only while the owner is home, then march on the banner and attack citizens. Wave mobs glow so they are easy to find,
 * and guards are mobilized and given their targets immediately. A minute later the owner hears about stragglers. Wave mobs carry
 * entity tags so they keep marching after a reload.
 */
public final class WaveService {
    public static final String WAVE_TAG="wwmc_wave";
    private static final String TOWN_TAG=WAVE_TAG+":";
    private static final int INTERVAL=100,RETRY_TICKS=1200,SPREAD=4,MIN_DISTANCE=40,MAX_DISTANCE=64;
    /** Attackers still alive this long after a wave arrives are reported to the guards. */
    private static final int LINGER_TICKS=1200;
    /** Glowing lasts an hour and is renewed whenever a wave mob loads. */
    private static final int GLOW_TICKS=72000;
    /** Wave mobs still alive per town, so the owner hears when a wave is beaten. */
    private static final Map<UUID,Integer> ACTIVE=new HashMap<>();
    /** When each town's current wave arrived, and the towns whose owner already heard the guards are hunting stragglers. */
    private static final Map<UUID,Long> STARTED=new HashMap<>();
    private static final Set<UUID> HUNTED=new HashSet<>();
    private static final Set<UUID> BLOCKED_APPROACHES=new HashSet<>();
    private static final Map<UUID,Long> LAST_APPROACH_WARNING=new HashMap<>();
    public record Perimeter(int minX,int maxX,int minZ,int maxZ) {
        public boolean contains(int x,int z) { return x>=minX && x<=maxX && z>=minZ && z<=maxZ; }
        Perimeter include(BlockPos pos,int padding) {
            return new Perimeter(Math.min(minX,pos.getX()-padding),Math.max(maxX,pos.getX()+padding),
                    Math.min(minZ,pos.getZ()-padding),Math.max(maxZ,pos.getZ()+padding));
        }
    }
    /** Claims are much wider than ticking player terrain. Use the actual town footprint without force-loading spawns. */
    public static Perimeter perimeter(ServerLevel level,Settlement town) {
        int core=Math.min(64,Math.max(16,town.radius-24));
        Perimeter bounds=new Perimeter(town.center.getX()-core,town.center.getX()+core,town.center.getZ()-core,town.center.getZ()+core);
        for(Station station:town.stations) {
            if(level.hasChunkAt(station.position()) && !SettlementService.active(level,station)) continue;
            bounds=bounds.include(station.position(),station.radius()+8);
        }
        for(var trap:town.progress.traps) {
            if(level.hasChunkAt(trap.pos()) && !(level.getBlockState(trap.pos()).getBlock() instanceof io.github.swishhyy.wwmc.block.TrapBlock)) continue;
            bounds=bounds.include(trap.pos(),8);
        }
        return bounds;
    }
    private static Mob create(ServerLevel level,WavePlan.Attacker attacker) {
        var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(attacker.name().toLowerCase(Locale.ROOT)));
        return type.create(level,EntitySpawnReason.EVENT) instanceof Mob mob ? mob : null;
    }
    public static int size(Settlement town) {
        return WavePlan.size(town.citizens.size(),Config.WAVE_BASE_MOBS.get(),Config.WAVE_MOBS_PER_CITIZEN.get(),Config.WAVE_MAX_MOBS.get(),
                SettlementService.populationLevel(town),Config.WAVE_MOBS_PER_UPGRADE.get());
    }
    public static String status(ServerLevel level,Settlement town) {
        if(!Config.WAVES.get()) return "enemy waves are disabled";
        int active=ACTIVE.getOrDefault(town.id,0);
        if(active>0) return active+" wave attackers remain";
        int needed=Config.WAVE_MIN_POPULATION.get();
        if(town.citizens.size()<needed || town.nextWave==0) return "waves begin once the town has "+needed+" citizens";
        long minutes=Math.max(0,town.nextWave-level.getGameTime())/1200;
        return "next wave of about "+size(town)+" hostiles in "+(minutes==0 ? "under a minute" : "about "+minutes+" min")+" (arrives at night)"
                +(BLOCKED_APPROACHES.contains(town.id) ? "; waiting for a safe loaded approach beyond the defenses" : "");
    }
    private static ServerPlayer ownerHome(ServerLevel level,Settlement town) {
        // A present co-manager can defend the shared town; absent players' towns remain protected.
        return level.players().stream().filter(p -> TownAccess.manages(town,p.getUUID()) && p.isAlive() && !p.isSpectator()
                && town.overlaps(p.blockPosition(),64)).findFirst().orElse(null);
    }
    private static BlockPos ground(ServerLevel level,Settlement town,int x,int z) {
        BlockPos column=new BlockPos(x,town.center.getY(),z);
        if(!town.contains(column) || !level.hasChunkAt(column) || !level.isPositionEntityTicking(column)) return null;
        BlockPos pos=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
        if(pos.getY()<=level.getMinY() || pos.getY()+1>=level.getMaxY() || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) return null;
        var surface=level.getBlockState(pos.below());
        if(!surface.is(Blocks.GRASS_BLOCK) && !surface.is(Blocks.DIRT) && !surface.is(net.minecraft.tags.BlockTags.DIRT) && !surface.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                && !surface.is(net.minecraft.tags.BlockTags.SAND) && !surface.is(Blocks.GRAVEL) && !surface.is(Blocks.SNOW_BLOCK)
                && !surface.is(Blocks.FARMLAND) && !surface.is(Blocks.DIRT_PATH)) return null;
        // Keep spawns outdoors and away from furnished stations and town furniture.
        if(town.stations.stream().anyMatch(s -> s.position().distSqr(pos)<16*16)) return null;
        return pos;
    }
    public static BlockPos arrivalSite(ServerLevel level,Settlement town,ServerPlayer owner) {
        var random=level.getRandom();
        Perimeter bounds=perimeter(level,town);
        for(int attempt=0;attempt<64;attempt++) {
            int side=random.nextInt(4),margin=12+random.nextInt(9);
            int x=side==0 ? bounds.minX()-margin : side==1 ? bounds.maxX()+margin : bounds.minX()+random.nextInt(bounds.maxX()-bounds.minX()+1);
            int z=side==2 ? bounds.minZ()-margin : side==3 ? bounds.maxZ()+margin : bounds.minZ()+random.nextInt(bounds.maxZ()-bounds.minZ()+1);
            BlockPos pos=ground(level,town,x,z);
            if(pos!=null && (owner==null || owner.distanceToSqr(Vec3.atCenterOf(pos))>=24*24)) return pos;
        }
        return null;
    }
    /** Spawn the next wave now. Returns the number of attackers placed. */
    public static int launch(ServerLevel level,Settlement town) {
        if(level.getDifficulty()==Difficulty.PEACEFUL) return 0;
        ServerPlayer owner=ownerHome(level,town);
        BlockPos site=arrivalSite(level,town,owner);
        if(site==null) {
            BLOCKED_APPROACHES.add(town.id); long now=level.getGameTime();
            if(Config.SERVER_DIAGNOSTICS.get() && now-LAST_APPROACH_WARNING.getOrDefault(town.id,now-Config.DIAGNOSTIC_REPEAT.get()*20L)>=Config.DIAGNOSTIC_REPEAT.get()*20L) {
                LAST_APPROACH_WARNING.put(town.id,now);
                io.github.swishhyy.wwmc.WWMC.LOGGER.warn("[WWMC][wave-approach] town={} center={} No safe ticking natural ground beyond the defense perimeter; wave postponed",town.name,town.center.toShortString());
            }
            return 0;
        }
        BLOCKED_APPROACHES.remove(town.id);
        var random=level.getRandom();
        int spawned=0;
        Perimeter bounds=perimeter(level,town);
        for(var entry:WavePlan.compose(size(town),town.citizens.size(),SettlementService.populationLevel(town)).entrySet()) {
            for(int i=0;i<entry.getValue();i++) {
                BlockPos pos=null;
                for(int attempt=0;attempt<8 && pos==null;attempt++) {
                    int x=site.getX()+random.nextInt(SPREAD*2+1)-SPREAD,z=site.getZ()+random.nextInt(SPREAD*2+1)-SPREAD;
                    if(!bounds.contains(x,z)) pos=ground(level,town,x,z);
                }
                if(pos==null) pos=site;
                Mob mob=create(level,entry.getKey());
                if(mob==null) continue;
                mob.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
                mob.setYRot(random.nextFloat()*360.0F);
                if(!level.noCollision(mob)) { mob.discard(); continue; }
                mob.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),EntitySpawnReason.EVENT,null);
                enlist(mob,town);
                for(Entity rider:mob.getPassengers()) if(rider instanceof Mob passenger) enlist(passenger,town);
                if(level.tryAddFreshEntityWithPassengers(mob)) {
                    spawned++;
                    if(mob instanceof Monster monster) DefenseService.report(town,monster,"",true,level.getGameTime());
                }
            }
        }
        if(spawned>0) {
            DefenseService.waveAlarm(level,town);
            town.waves++; ACTIVE.merge(town.id,spawned,Integer::sum);
            STARTED.put(town.id,level.getGameTime()); HUNTED.remove(town.id);
            String direction=WavePlan.compass(site.getX()-town.center.getX(),site.getZ()-town.center.getZ());
            if(owner!=null) SettlementService.notify(owner,"Wave "+town.waves+": "+spawned+" glowing hostiles are attacking "+town.name+" from the "+direction+"!");
        }
        return spawned;
    }
    private static void enlist(Mob mob,Settlement town) {
        mob.setPersistenceRequired(); mob.addTag(WAVE_TAG); mob.addTag(TOWN_TAG+town.id); glow(mob);
    }
    /** An outline through walls, like a bell's warning, so the owner can find every attacker. */
    private static void glow(Mob mob) { mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,GLOW_TICKS,0,false,false)); }
    private static Settlement townOf(ServerLevel level,Mob mob) {
        for(String tag:mob.entityTags()) if(tag.startsWith(TOWN_TAG)) {
            try { return SettlementData.get(level).byId(UUID.fromString(tag.substring(TOWN_TAG.length()))); }
            catch(IllegalArgumentException e) { return null; }
        }
        return null;
    }
    private static List<Mob> attackers(ServerLevel level,Settlement town) {
        int reach=town.radius+MAX_DISTANCE;
        AABB area=new AABB(town.center.getX()-reach,level.getMinY(),town.center.getZ()-reach,town.center.getX()+reach+1,level.getMaxY(),town.center.getZ()+reach+1);
        String tag=TOWN_TAG+town.id;
        return level.getEntitiesOfClass(Mob.class,area,m -> m.isAlive() && m.entityTags().contains(tag));
    }
    /** Report every attacker still inside the claim to the guards; the owner hears about it once per wave. */
    private static void hunt(ServerLevel level,Settlement town,List<Mob> alive,boolean notify) {
        int reported=0;
        for(Mob mob:alive) if(mob instanceof Monster monster && town.contains(mob.blockPosition())) {
            DefenseService.report(town,monster,"",true,level.getGameTime()); reported++;
        }
        if(reported>0 && notify && HUNTED.add(town.id)) {
            ServerPlayer owner=level.getServer().getPlayerList().getPlayer(town.owner);
            if(owner!=null) SettlementService.notify(owner,reported+" wave "+(reported==1 ? "attacker is" : "attackers are")+" still at large in "+town.name+". They glow; the guards are hunting them.");
        }
    }
    private static void schedule(ServerLevel level,Settlement town) {
        town.nextWave=level.getGameTime()+WavePlan.delay(Config.WAVE_INTERVAL_DAYS.get(),level.getRandom()::nextInt);
        SettlementData.get(level).setDirty();
    }
    /** Owner command: bring the next wave forward to now. */
    public static int callNow(ServerLevel level,Settlement town) {
        int spawned=launch(level,town);
        if(spawned>0) schedule(level,town);
        return spawned;
    }
    private static void update(ServerLevel level,Settlement town) {
        List<Mob> attackers=town.waves>0 ? attackers(level,town) : List.of();
        int alive=attackers.size();
        Integer before=alive>0 ? ACTIVE.put(town.id,alive) : ACTIVE.remove(town.id);
        if(alive==0) { STARTED.remove(town.id); HUNTED.remove(town.id); }
        else {
            hunt(level,town,attackers,level.getGameTime()-STARTED.computeIfAbsent(town.id,id -> level.getGameTime())>=LINGER_TICKS);
            if(!DefenseService.alarmed(town)) DefenseService.waveAlarm(level,town);
        }
        if(before!=null && before>0 && alive==0 && level.hasChunkAt(town.center)) {
            TutorialProgress.record(level,town,"defense");
            ServerPlayer owner=level.getServer().getPlayerList().getPlayer(town.owner);
            if(owner!=null) SettlementService.notify(owner,town.name+" has repelled the wave.");
        }
        if(!Config.WAVES.get()) return;
        if(town.citizens.size()<Config.WAVE_MIN_POPULATION.get()) {
            if(town.nextWave!=0) { town.nextWave=0; SettlementData.get(level).setDirty(); }
            return;
        }
        if(town.nextWave==0) { schedule(level,town); return; }
        // Waves never overlap, wait for the owner to be home, and arrive after sunset.
        if(level.getGameTime()<town.nextWave || alive>0 || ownerHome(level,town)==null || !SettlementService.night(level)) return;
        if(launch(level,town)>0) schedule(level,town);
        else { town.nextWave=level.getGameTime()+RETRY_TICKS; SettlementData.get(level).setDirty(); }
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%INTERVAL!=0 || !level.dimension().equals(Level.OVERWORLD)) return;
        for(Settlement town:SettlementData.get(level).settlements) update(level,town);
    }
    /** Goals are not saved with entities, so wave mobs receive their orders whenever they enter the level. */
    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob) || !mob.entityTags().contains(WAVE_TAG)) return;
        Settlement town=townOf(level,mob);
        if(town==null) return;
        glow(mob);
        if(mob instanceof Monster monster) DefenseService.report(town,monster,"",true,level.getGameTime());
        mob.targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(mob,CitizenEntity.class,true));
        if(mob instanceof PathfinderMob walker) mob.goalSelector.addGoal(4,new MarchGoal(walker,town.center));
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        ACTIVE.clear(); STARTED.clear(); HUNTED.clear(); BLOCKED_APPROACHES.clear(); LAST_APPROACH_WARNING.clear();
    }
    /** Without a target, walk toward the town banner. */
    private static final class MarchGoal extends Goal {
        private final PathfinderMob mob;
        private final BlockPos rally;
        private int repath,cooldown;
        MarchGoal(PathfinderMob mob,BlockPos rally) { this.mob=mob; this.rally=rally; setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() {
            // A mob that cannot reach the banner waits a little before planning again rather than repathing every tick.
            if(cooldown>0) { cooldown--; return false; }
            return mob.getTarget()==null && mob.distanceToSqr(Vec3.atCenterOf(rally))>8*8;
        }
        @Override public boolean canContinueToUse() { return canUse() && !mob.getNavigation().isDone(); }
        @Override public void start() { repath=0; mob.getNavigation().moveTo(rally.getX()+0.5,rally.getY(),rally.getZ()+0.5,1.0); }
        @Override public void tick() {
            if(++repath%40==0) mob.getNavigation().moveTo(rally.getX()+0.5,rally.getY(),rally.getZ()+0.5,1.0);
        }
        @Override public void stop() { mob.getNavigation().stop(); cooldown=40; }
    }
}

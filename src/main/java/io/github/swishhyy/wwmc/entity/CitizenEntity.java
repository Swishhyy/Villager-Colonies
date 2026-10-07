package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.CitizenNames;
import io.github.swishhyy.wwmc.core.WorkCadence;
import io.github.swishhyy.wwmc.menu.Panels;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Villager-styled citizen with visible equipment and an independent station-driven work routine. */
public final class CitizenEntity extends Villager {
    private enum Action { HARVEST,FELL,PLANT,EXCAVATE,SUPPORT,CAVE,VEIN }
    private Action action=Action.HARVEST;
    private ForestryService.Task forestTask;
    private ExcavationService.Ticket excavation;
    private BlockPos targetLease;
    /** Arrows a guard with a bow keeps in their bag. */
    private static final int ARROW_STOCK=32;
    /** Extra attack damage a stored weapon needs before a guard swaps for it; a fresher copy of the same sword is not worth the trip. */
    private static final double MELEE_UPGRADE=0.5;
    private int minimumAxeDurability=1,guardAttackTicks,patrolTicks,gearTicks,patrolVisits,scavengeTicks,armoryTicks;
    private boolean armoryStocked;
    private boolean guardWasActive;
    private UUID gearStand;
    private int gearPathTicks,healingTicks;
    private final Map<UUID,Long> ignoredStands=new HashMap<>();
    private long gearReturnAt,shiftGearUntil,nextSmithAt;
    /** This is the original item taken for repair, saved separately until it is returned. */
    private ItemStack repairItem=ItemStack.EMPTY;
    private UUID repairStand;
    private EquipmentSlot repairSlot;
    private BlockPos repairAnvil;
    private boolean repairDelivery;
    private long nextFoodTripAt;
    /** Ticks a craftsman works one batch at the bench. */
    private static final int CRAFT_TICKS=40;
    private static final int MAX_FAILED_TARGETS=2048;
    /** A cook's bread batch. */
    private Crafting.Recipe order;
    /** A craftsman's learned order and the recipe chosen for it. */
    private Workshop.Job craftJob;
    /** A courier's current errand: the job station whose barrels it serves, and whether it is bringing supplies. */
    private BlockPos haulStation;
    private boolean haulSupply;
    private int haulTicks;
    /** The storage a supply or delivery trip is walking to, and for how long; an unreachable job barrel is skipped for a minute. */
    private BlockPos depotTarget;
    private int depotTicks;
    private static final int BARREL_WALK_TICKS=400;
    /** An enchanter's item, kept apart from the bag until it is delivered, with the work done on it and the level rolled for it. */
    private ItemStack enchantItem=ItemStack.EMPTY;
    private int enchantTicks,enchantLevel;
    private boolean enchantDone;
    private BlockPos enchantTable;
    private long nextEnchantAt;
    /** Kinds of item no enchantment fit, skipped until the given game time. */
    private final Map<Item,Long> unenchantable=new HashMap<>();
    /** Reported hostiles this guard could not reach, ignored until the given game time, and how long it has chased the current one out of sight. */
    private final Map<UUID,Long> ignoredThreats=new HashMap<>();
    private UUID respondTarget;
    private int respondTicks;
    /** A civilian's call to the guards, shown instead of the activity for a few seconds. */
    private String callNote="";
    private long callNoteUntil;
    /** The job worked last; a change sends that job's tools, weapons and armor back to the warehouse. */
    private StructureRole lastRole;
    private boolean returningGear;
    /** Loose items a guard could not reach, ignored until the given game time. */
    private final Map<UUID,Long> ignoredLoot=new HashMap<>();
    private UUID scavengeTarget;
    private int scavengePathTicks;
    /** Citizens who keep trying to walk without getting anywhere are lifted onto the banner after this long. */
    private static final int STUCK_TICKS=600;
    private Vec3 stuckAnchor;
    private int stuckTicks,lastWalkTick=-1000;
    private BlockPos patrolTarget,activePost;
    private BlockPos processor;
    private boolean processingDelivery,processingSupplied;
    private long nextProcessingAt,guardSupplyAt;
    private int processingIdle;
    private final Map<BlockPos,Long> idleStations=new HashMap<>();
    private UUID settlementId;
    private BlockPos workplace, target, sleepingBed,workStand,clearingLeaf;
    private final CitizenInventory cargo=new CitizenInventory(this::canOpenInventory);
    private final Map<BlockPos,Long> failedTargets=new HashMap<>();
    private int searchDelay, workProgress, pathTicks, mealTicks=2400;
    private final WorkCadence.ReachBudget reachBudget=new WorkCadence.ReachBudget();
    private long nextPathAt;
    private BlockPos pathDestination;
    private String activity="Waiting for a job station";
    private TradeShipment tradeShipment=new TradeShipment();
    private final TradeNavigation tradeNavigation=new TradeNavigation();
    private long nextTradeAt;
    private int lastNpcHurt=-1;
    public CitizenEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level); setPersistenceRequired(); setCanPickUpLoot(false);
        for(EquipmentSlot slot:EquipmentSlot.values()) setDropChance(slot,0);
    }
    public void join(UUID id) { settlementId=id; mealTicks=Config.RATION_TICKS.get(); }
    public Settlement town(ServerLevel level) { return settlementId==null ? null : SettlementData.get(level).byId(settlementId); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        // The villager brain that normally opens doors is disabled for citizens, so doors are handled here.
        goalSelector.addGoal(1,new OpenDoorGoal(this,true));
        goalSelector.addGoal(1,new AvoidEntityGoal<>(this,Monster.class,12.0F,0.8,1.0) {
            @Override public boolean canUse() { return !isGuard() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isGuard() && super.canContinueToUse(); }
        });
        // An alarm makes civilians notice and flee hostiles from much farther away.
        goalSelector.addGoal(1,new AvoidEntityGoal<>(this,Monster.class,20.0F,0.9,1.1) {
            @Override public boolean canUse() { return !isGuard() && alarmed() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !isGuard() && alarmed() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(2,new ShelterGoal());
        goalSelector.addGoal(2,new RestGoal());
        goalSelector.addGoal(3,new WorkGoal());
        goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,6.0F));
        goalSelector.addGoal(5,new RandomLookAroundGoal(this));
    }
    // Keep ordinary villager trades, breeding, and POI jobs out of the custom work scheduler.
    @Override protected void customServerAiStep(ServerLevel level) {}
    @Override public void tick() {
        if(level() instanceof ServerLevel server && isGuard() && WorkCadence.due(server.getGameTime(),getId(),10)) {
            Settlement town=town(server); Station station=town.station(workplace);
            // Sleeping reserves do not run their work goal, but still belong to this station's roster.
            SettlementService.workers(server).claim(workplace,getUUID(),server.getGameTime(),200,SettlementService.workerLimit(town,station));
            if(GuardService.onDuty(server,town,workplace,getUUID()) || DefenseService.bellRun(town,getUUID())!=null
                    || isSleeping() && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !getItemBySlot(slot).isEmpty())) wakeForAlarm();
            else if(sleepingBed!=null) {
                if(SettlementService.housingBeds(server,town).contains(sleepingBed))
                    SettlementService.reservations(server).claim(sleepingBed,getUUID(),server.getGameTime(),200);
                else leaveBed();
            }
        }
        super.tick();
        if(level() instanceof ServerLevel server) {
            if(mealTicks>0) mealTicks--;
            if(healingTicks>0) healingTicks--;
            if(guardAttackTicks>0) guardAttackTicks--;
            if(WorkCadence.due(server.getGameTime(),getId(),20)) {
                cargo.flush();
                Settlement town=town(server);
                if(town!=null) {
                    if(town.trading.npc && getLastHurtByMob() instanceof Player attacker && getLastHurtByMobTimestamp()>lastNpcHurt) {
                        lastNpcHurt=getLastHurtByMobTimestamp();
                        TradeRoutes.attacked(town,attacker.getUUID(),SettlementData.get(server).settlements); SettlementData.get(server).setDirty();
                    }
                    if(!cargo.isOpen()) eatFrom(List.of(cargo));
                    if(getCustomName()==null || CitizenNames.numbered(getCustomName().getString()))
                        setCustomName(Component.literal(SettlementService.citizenName(server,town,getUUID())));
                    String name=getCustomName().getString();
                    if(!name.equals(town.citizenNames.put(getUUID(),name))) SettlementData.get(server).setDirty();
                    checkStuck(server,town);
                }
            }
        }
    }
    /** Trying to walk (a recent walk() call) while staying within a block and a half counts as stuck. */
    private void checkStuck(ServerLevel level,Settlement town) {
        // Trade trips have their own short waypoints; do not teleport a convoy across the countryside.
        if(tradeShipment.travelling()) return;
        boolean trying=tickCount-lastWalkTick<=40 && !isSleeping() && !isPassenger();
        if(!trying || stuckAnchor==null || position().distanceToSqr(stuckAnchor)>2.25) { stuckAnchor=position(); stuckTicks=0; return; }
        stuckTicks+=20;
        if(stuckTicks<STUCK_TICKS) return;
        stuckAnchor=null; stuckTicks=0;
        BlockPos spot=rescueSpot(level,town);
        if(spot==null) { activity="Stuck, and the settlement banner has no free standing room"; return; }
        getNavigation().stop();
        // Abandon the trip that went wrong so the citizen does not walk straight back into the same trap.
        if(action==Action.EXCAVATE && excavation!=null && excavation.quarry() && !excavation.remote() && workplace!=null) {
            SettlementService.reservations(level).release(excavation.lease(),getUUID());
            excavation=excavation.fromControlBlock(workplace); targetLease=excavation.lease(); pathTicks=0;
        } else if(target!=null) cancelTarget(level,true);
        else if(returningGear) returningGear=false; // the gear stays in the bag and goes back with the next delivery
        else if(isGuard()) patrolTarget=null;
        else if(workplace!=null) { idleStations.put(workplace,level.getGameTime()+200); releaseWork(level); }
        setPos(spot.getX()+0.5,spot.getY(),spot.getZ()+0.5); resetFallDistance();
        activity="Got stuck and returned to the settlement banner";
    }
    /** On top of the banner, or failing that a clear spot with firm footing right beside it; never in an unloaded or frozen chunk. */
    private static BlockPos rescueSpot(ServerLevel level,Settlement town) {
        BlockPos banner=town.center;
        if(!level.hasChunkAt(banner) || !level.isPositionEntityTicking(banner)) return null;
        List<BlockPos> spots=new ArrayList<>(List.of(banner.above()));
        for(int dy=1;dy>=-1;dy--) for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) if(dx!=0 || dz!=0) spots.add(banner.offset(dx,dy,dz));
        for(BlockPos spot:spots) {
            if(!level.hasChunkAt(spot) || !level.hasChunkAt(spot.above()) || !level.isPositionEntityTicking(spot)) continue;
            if(clear(level,spot) && clear(level,spot.above()) && level.getBlockState(spot.below()).isFaceSturdy(level,spot.below(),net.minecraft.core.Direction.UP)) return spot;
        }
        return null;
    }
    private static boolean clear(ServerLevel level,BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level,pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
    // Citizens never shove each other, so crews can pass on narrow quarry stairs and walkways without knocking anyone off.
    @Override protected void doPush(Entity entity) { if(!(entity instanceof CitizenEntity)) super.doPush(entity); }
    public boolean inQuarry(ServerLevel level) { Settlement town=town(level); return town!=null && ExcavationService.inPit(level,town,blockPosition()); }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level() instanceof ServerLevel server && hand==InteractionHand.MAIN_HAND) {
            Settlement town=town(server);
            if(town!=null && town.owner.equals(player.getUUID()) && player.isShiftKeyDown()) {
                releaseWork(server); searchDelay=0;
                SettlementService.notify(player,getName().getString()+" released their job and will choose an available station.");
            } else if(canOpenInventory(player) && player.getItemInHand(hand).isEmpty()) {
                if(player instanceof ServerPlayer viewer) Panels.openCitizen(viewer,this,cargo);
            } else if(canOpenInventory(player) && FoodHealing.food(player.getItemInHand(hand))) {
                if(getHealth()<getMaxHealth() && healingTicks<=0) {
                    ItemStack held=player.getItemInHand(hand);
                    ItemStack meal=player.getAbilities().instabuild ? held.copyWithCount(1) : held.split(1);
                    consumeMeal(meal);
                    if(!player.getAbilities().instabuild) {
                        var remainder=meal.get(DataComponents.USE_REMAINDER);
                        if(remainder!=null) cargo.offer(remainder.convertInto().create());
                    }
                } else SettlementService.notify(player,getName().getString()+": "+(getHealth()>=getMaxHealth() ? "Already healthy." : "Finishing the last meal."));
            } else {
                SettlementService.notify(player,getName().getString()+": "+activity+
                        (workplace==null ? "" : " at "+workplace.toShortString()));
            }
        }
        return InteractionResult.SUCCESS;
    }
    private boolean canOpenInventory(Player player) {
        if(!(level() instanceof ServerLevel server) || !isAlive() || distanceToSqr(player)>64) return false;
        Settlement town=town(server); return town!=null && town.owner.equals(player.getUUID());
    }
    /** Role of the station this citizen works at, or null. */
    private StructureRole role() {
        if(!(level() instanceof ServerLevel server) || workplace==null) return null;
        Settlement town=town(server); Station station=town==null ? null : town.station(workplace);
        return station==null ? null : station.role();
    }
    public boolean isGuard() {
        if(!(level() instanceof ServerLevel server) || workplace==null) return false;
        Settlement town=town(server); Station station=town==null ? null : town.station(workplace);
        return station!=null && station.role()==StructureRole.GUARD && SettlementService.active(server,station);
    }
    private boolean guardVacancy(ServerLevel level,Settlement town) {
        return town.stations.stream().anyMatch(s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s)
                && SettlementService.workers(level).count(s.position(),level.getGameTime())<SettlementService.workerLimit(town,s));
    }
    private boolean night(ServerLevel level) { return SettlementService.night(level); }
    private boolean alarmed() {
        if(!(level() instanceof ServerLevel server)) return false;
        Settlement town=town(server); return town!=null && DefenseService.alarmed(town);
    }
    private boolean near(BlockPos pos) { return distanceToSqr(Vec3.atCenterOf(pos))<=6.25; }
    private boolean visible(ServerLevel level,BlockPos pos) {
        return CitizenReach.visible(level,getEyePosition(),pos);
    }
    private boolean canUse(ServerLevel level,BlockPos pos) { return CitizenReach.canUse(level,getEyePosition(),pos); }
    private boolean handNear(BlockPos pos) { return CitizenReach.within(getEyePosition(),pos); }
    private boolean walk(BlockPos pos) { return walk(pos,0.65); }
    private boolean walk(BlockPos pos,double speed) {
        lastWalkTick=tickCount;
        // Trips into or out of a deep quarry follow its spiral stairs a few steps at a time.
        if(level() instanceof ServerLevel server && town(server)!=null) {
            BlockPos via=ExcavationService.waypoint(server,town(server),blockPosition(),pos);
            if(via!=null) pos=via;
        }
        long now=level().getGameTime();
        if(getNavigation().isDone() || !pos.equals(pathDestination) || now>=nextPathAt) {
            var path=getNavigation().createPath(pos,1);
            if(path==null) return false;
            getNavigation().moveTo(path,speed);
            pathDestination=pos.immutable(); nextPathAt=now+40;
        }
        getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return true;
    }
    private void releaseWork(ServerLevel level) {
        Settlement home=town(level);
        if(home!=null && getUUID().equals(home.trading.runner) && !tradeShipment.travelling()) {
            home.trading.runner=null; home.trading.runnerPos=null; SettlementData.get(level).setDirty();
        }
        leaveBed();
        var book=SettlementService.reservations(level);
        if(workplace!=null) SettlementService.workers(level).release(workplace,getUUID());
        if(target!=null) book.release(target,getUUID());
        if(targetLease!=null) book.release(targetLease,getUUID());
        if(isUsingItem()) stopUsingItem();
        targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1; order=null; craftJob=null;
        haulStation=null; haulSupply=false; haulTicks=0;
        workStand=null; clearingLeaf=null;
        processor=null; processingDelivery=false; processingSupplied=false; processingIdle=0; nextProcessingAt=0;
        workplace=null; target=null; patrolTarget=null; activePost=null; setTarget(null); workProgress=0; pathTicks=0; getNavigation().stop();
    }
    private Station chooseJob(ServerLevel level,Settlement town) {
        var book=SettlementService.workers(level);
        idleStations.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        List<Station> jobs=new ArrayList<>(town.stations.stream()
                .filter(s -> s.role().providesWork() && SettlementService.active(level,s) && !idleStations.containsKey(s.position())
                        && (s.role()!=StructureRole.TRADER || TradeRoutes.canDepart(level,town)
                            && (town.trading.runner==null || town.trading.runner.equals(getUUID())))
                        && (!night(level) || s.role()==StructureRole.GUARD)).toList());
        jobs.sort(Comparator.comparingInt((Station s) -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.TRADER ? 1 : 2)
                .thenComparingInt(s -> switch(town.priority) {
            case "food" -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.FARM || s.role()==StructureRole.COOK ? 1 : 2;
            case "materials" -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.FARM || s.role()==StructureRole.COOK ? 2 : 1;
            default -> 0;
        }).thenComparingInt(s -> book.count(s.position(),level.getGameTime()))
                .thenComparingDouble(s -> distanceToSqr(Vec3.atCenterOf(s.position()))));
        // An enchanter part-way through an item goes back to a table rather than starting other work.
        if(!enchantItem.isEmpty()) for(Station station:jobs) if(station.role()==StructureRole.ENCHANTER
                && book.claim(station.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,station))) return station;
        for(Station station:jobs) if(book.claim(station.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,station))) return station;
        return null;
    }
    private boolean toolFits(StructureRole role,ItemStack stack) {
        if(target==null || action==Action.PLANT || action==Action.SUPPORT || role==StructureRole.FARM) return true;
        if((role==StructureRole.LUMBER || role.excavates()) && GuardEquipment.worn(stack)) return false;
        if(role==StructureRole.LUMBER) return stack.is(ItemTags.AXES) && ForestryService.durability(stack)>=minimumAxeDurability;
        if(!role.excavates() || !stack.is(ItemTags.PICKAXES)) return false;
        BlockState state=target==null ? Blocks.STONE.defaultBlockState() : level().getBlockState(target);
        return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
    }
    private boolean properTool(StructureRole role) { return toolFits(role,getMainHandItem()); }
    private boolean needsSupply() {
        ItemStack supply=getOffhandItem();
        if(action==Action.PLANT) return forestTask==null || !supply.is(forestTask.planting().species().seed) || supply.getCount()<forestTask.planting().cost();
        return action==Action.SUPPORT && (supply.isEmpty() || !ExcavationService.supportMaterial(supply));
    }
    private boolean deliverCargo() { return cargo.needsDelivery(); }
    private boolean canReach(BlockPos pos) {
        if(near(pos)) return true;
        long now=level().getGameTime();
        if(failedTargets.getOrDefault(pos,0L)>now) return false;
        return reachBudget.check(() -> {
            var path=getNavigation().createPath(pos,1);
            if(path!=null && path.canReach()) return true;
            // Retain failed probes long enough for a bounded search to advance past an obstructed group.
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(pos.immutable(),now+1200);
            return false;
        });
    }
    private CitizenReach.StandingView standingView(ServerLevel level,Settlement town) {
        return new CitizenReach.StandingView() {
            public boolean available(BlockPos p) { return town.contains(p) && p.getY()>=level.getMinY() && p.getY()<level.getMaxY() && level.hasChunkAt(p); }
            public boolean clear(BlockPos p) { return CitizenEntity.clear(level,p); }
            public boolean footing(BlockPos p) { return !level.getBlockState(p).is(BlockTags.LEAVES)
                    && level.getFluidState(p).isEmpty() && level.getBlockState(p).isFaceSturdy(level,p,net.minecraft.core.Direction.UP); }
        };
    }
    private boolean reachableStand(BlockPos pos) {
        if(pos.equals(blockPosition())) return true;
        if(failedTargets.containsKey(pos)) return false;
        return reachBudget.check(() -> {
            var path=getNavigation().createPath(pos,0);
            if(path!=null && path.canReach()) return true;
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(pos.immutable(),level().getGameTime()+1200);
            return false;
        });
    }
    /** Select ground from which the resource is in reach, instead of trying to enter a log or canopy. */
    private boolean workAccessible(ServerLevel level,Settlement town,BlockPos pos) {
        BlockPos touch=level.getBlockState(pos).isAir() ? pos.below() : pos;
        var view=standingView(level,town);
        boolean lumber=TreeSpecies.ofLog(level.getBlockState(touch))!=null;
        if(handNear(touch) && workSight(level,town,getEyePosition(),touch)
                && (CitizenReach.standing(view,blockPosition()) || lumber)) { workStand=blockPosition(); return true; }
        for(BlockPos stand:CitizenReach.stands(view,touch,position(),getEyeHeight())) {
            if(reachBudget.deferred()) break;
            if(!reachableStand(stand)) continue;
            Vec3 eye=Vec3.atBottomCenterOf(stand).add(0,getEyeHeight(),0);
            if(workSight(level,town,eye,touch)) { workStand=stand; return true; }
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(stand,level.getGameTime()+1200);
        }
        return false;
    }
    private boolean workSight(ServerLevel level,Settlement town,Vec3 eye,BlockPos pos) {
        if(CitizenReach.canUse(level,eye,pos)) return true;
        TreeSpecies species=TreeSpecies.ofLog(level.getBlockState(pos));
        if(species==null) return false;
        var hit=CitizenReach.hit(level,eye,pos);
        BlockPos obstacle=hit.getBlockPos();
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK && CitizenReach.within(eye,obstacle)
                && level.hasChunkAt(obstacle) && town.contains(obstacle) && ForestryService.naturalLeaf(level.getBlockState(obstacle),species)
                && !WorldWorkData.get(level).protectedBlocks.contains(obstacle) && !SettlementService.protectedFurniture(town,obstacle);
    }
    /** The first natural leaf in reach, including leaves intersecting a worker who was already stuck in a canopy. */
    private BlockPos blockingLeaf(ServerLevel level,Settlement town) {
        if(forestTask==null || forestTask.tree()==null) return null;
        AABB body=getBoundingBox().deflate(0.001);
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),BlockPos.containing(body.maxX,body.maxY,body.maxZ)))
            if(CitizenReach.within(getEyePosition(),p) && ForestryService.clearableLeaf(level,town,forestTask.tree(),p)) return p.immutable();
        var hit=CitizenReach.hit(level,getEyePosition(),target);
        BlockPos p=hit.getBlockPos();
        return !p.equals(target) && hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                && handNear(p) && getEyePosition().distanceToSqr(hit.getLocation())<=CitizenReach.BLOCKS*CitizenReach.BLOCKS+1.0E-7
                && ForestryService.clearableLeaf(level,town,forestTask.tree(),p) ? p : null;
    }
    private boolean food(ItemStack stack) {
        return FoodHealing.food(stack);
    }
    private boolean wantsMeal() { return FoodHealing.due(mealTicks,healingTicks,getHealth(),getMaxHealth()); }
    private void consumeMeal(ItemStack meal) {
        float amount=FoodHealing.healing(meal,getHealth(),getMaxHealth());
        if(amount>0) {
            heal(amount);
            if(level() instanceof ServerLevel server) server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+1.5,getZ(),3,0.3,0.2,0.3,0);
        }
        mealTicks=Config.RATION_TICKS.get(); healingTicks=FoodHealing.COOLDOWN;
        playSound(SoundEvents.GENERIC_EAT.value(),0.5F,1.0F);
    }
    private void eatFrom(List<Container> supplies) {
        if(!wantsMeal()) return;
        ItemStack meal=FoodHealing.take(supplies,cargo::offer);
        if(!meal.isEmpty()) consumeMeal(meal);
    }
    /** Citizens keep only what their current job uses; everything else, including finished craft goods, goes to the warehouse. */
    private boolean retainSupply(ItemStack stack) {
        StructureRole role=role();
        if(role==StructureRole.BLACKSMITH && !repairItem.isEmpty() && BlacksmithRepair.material(repairItem,stack)) return true;
        if(gear(stack) && GuardEquipment.worn(stack)) return false;
        if(level() instanceof ServerLevel level && role!=null && role.processes() && ProcessingService.supply(level,role,stack)) return true;
        boolean guard=role==StructureRole.GUARD;
        return stack.is(ItemTags.AXES) && role==StructureRole.LUMBER
                || stack.is(ItemTags.PICKAXES) && role!=null && role.excavates()
                || guard && (GuardWeapons.melee(stack) && GuardWeapons.score(stack)>=bestMelee() || GuardWeapons.bow(stack) || GuardWeapons.arrow(stack)
                    || armor(stack) && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.upgrade(stack,getItemBySlot(slot),slot)))
                || order!=null && order.uses(stack)
                || craftJob!=null && role==StructureRole.CRAFTSMAN && craftJob.plan().uses(stack)
                || role==StructureRole.COURIER && haulSupply && haulingInput(stack)
                || role==StructureRole.ENCHANTER && Enchanting.lapis(stack)
                || action==Action.PLANT && forestTask!=null && stack.is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(stack);
    }
    private static boolean armor(ItemStack stack) { return Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.armor(stack,slot)); }
    /** Supplies a courier is carrying to a smeltery or kitchen barrel stay in the bag until they arrive. */
    private boolean haulingInput(ItemStack stack) {
        if(!(level() instanceof ServerLevel server) || haulStation==null) return false;
        Settlement town=town(server); Station job=town==null ? null : town.station(haulStation);
        return job!=null && JobStorage.input(JobStorage.Supplies.of(server),job.role(),stack);
    }
    /** Tools, weapons and armor that belong to particular jobs. */
    private static boolean gear(ItemStack stack) {
        return GuardWeapons.weapon(stack) || GuardWeapons.arrow(stack) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || armor(stack);
    }
    /** A new job: put away the previous one's equipment and return it before starting. */
    private void changeRole(StructureRole role) {
        if(role!=StructureRole.BLACKSMITH && !repairItem.isEmpty()) { cargo.offer(repairItem); repairItem=ItemStack.EMPTY; repairStand=null; repairSlot=null; repairAnvil=null; }
        if(role!=StructureRole.ENCHANTER && !enchantItem.isEmpty()) { cargo.offer(enchantItem); enchantItem=ItemStack.EMPTY; enchantTicks=0; enchantLevel=0; enchantDone=false; }
        if(role!=StructureRole.BLACKSMITH) { repairStand=null; repairSlot=null; repairAnvil=null; repairDelivery=false; }
        guardWasActive=false;
        if(isUsingItem()) stopUsingItem();
        if(role!=StructureRole.GUARD) for(EquipmentSlot slot:GuardEquipment.ARMOR) if(!getItemBySlot(slot).isEmpty()) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        for(EquipmentSlot hand:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) if(!getItemBySlot(hand).isEmpty()) {
            cargo.offer(getItemBySlot(hand)); setItemSlot(hand,ItemStack.EMPTY);
        }
        returningGear=hasReturnableGear(); pathTicks=0;
    }
    private boolean hasReturnableGear() {
        for(int slot=0;slot<cargo.getContainerSize();slot++) {
            ItemStack stack=cargo.getItem(slot);
            if(!stack.isEmpty() && gear(stack) && !retainSupply(stack)) return true;
        }
        return false;
    }
    /** Carry gear the new job does not use to the warehouse so the next worker in the old job finds it; drop it if no warehouse can be reached. */
    private boolean returnGear(ServerLevel level,Settlement town) {
        if(!hasReturnableGear()) { returningGear=false; return true; }
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null && town.stations.stream().anyMatch(s -> s.role()==StructureRole.WAREHOUSE)) {
            // The warehouse is only unloaded: keep the gear in the bag; ordinary deliveries hand it in later.
            returningGear=false; return true;
        }
        if(warehouse!=null && !canUse(level,warehouse)) {
            pathTicks+=10;
            // A path cannot be planned mid-jump; only a long failure or one on solid ground means the warehouse is out of reach.
            if((walk(warehouse) || !onGround()) && pathTicks<=1200) { activity="Returning my previous job's gear to the warehouse"; return false; }
            warehouse=null;
        }
        getNavigation().stop(); pathTicks=0;
        List<Container> storage=warehouse==null ? List.of() : SettlementService.storageAt(level,town,warehouse);
        for(int slot=0;slot<cargo.getContainerSize();slot++) {
            ItemStack stack=cargo.getItem(slot);
            if(stack.isEmpty() || !gear(stack) || retainSupply(stack)) continue;
            ItemStack rest=stack.copy();
            for(Container container:storage) rest=InventoryOps.insert(container,rest);
            if(!rest.isEmpty() && GuardEquipment.worn(stack)) { cargo.setItem(slot,rest); continue; }
            if(!rest.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),rest);
            cargo.setItem(slot,ItemStack.EMPTY);
        }
        returningGear=false; return true;
    }
    private void useLocalSupplies(StructureRole role) {
        eatFrom(List.of(cargo));
        if(!properTool(role)) {
            ItemStack tool=InventoryOps.takeOne(List.of(cargo),s -> toolFits(role,s));
            if(!tool.isEmpty()) { cargo.offer(getMainHandItem()); setItemSlot(EquipmentSlot.MAINHAND,tool); }
        }
        if(needsSupply()) {
            ItemStack supply=getOffhandItem();
            java.util.function.Predicate<ItemStack> matches=stack -> action==Action.PLANT ? stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack);
            if(!supply.isEmpty() && !matches.test(supply)) { cargo.offer(supply); supply=ItemStack.EMPTY; }
            int cost=action==Action.PLANT ? forestTask.planting().cost() : 1;
            while(supply.getCount()<cost) {
                ItemStack held=supply;
                ItemStack next=InventoryOps.takeOne(List.of(cargo),s -> matches.test(s) && (held.isEmpty() || ItemStack.isSameItemSameComponents(held,s)));
                if(next.isEmpty()) break;
                if(supply.isEmpty()) supply=next; else supply.grow(1);
            }
            setItemSlot(EquipmentSlot.OFFHAND,supply);
        }
    }
    private boolean visitWarehouse(ServerLevel level,Settlement town,StructureRole role) {
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null) { activity="Needs a loaded warehouse with a chest or barrel in range"; return false; }
        return visitStorage(level,town,role,warehouse,SettlementService.storageAt(level,town,warehouse),true);
    }
    private boolean supplyFor(ItemStack stack) {
        return action==Action.PLANT ? forestTask!=null && stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack);
    }
    private BlockPos nearest(List<BlockPos> positions) {
        return positions.stream().min(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p)))).orElse(null);
    }
    /** The closest job barrel this citizen has not recently failed to reach, or null. */
    private BlockPos nearestBarrel(List<BlockPos> barrels) {
        return nearest(barrels.stream().filter(barrel -> !failedTargets.containsKey(barrel)).toList());
    }
    /** Goods this citizen should hand in: a full enough bag, or for cooks anything they baked or cooked. */
    private boolean carryingGoods(StructureRole role) {
        return cargo.hasDeliverable(this::retainSupply,this::food) || role==StructureRole.COOK && !cargo.first(stack -> !retainSupply(stack)).isEmpty();
    }
    /** Finished goods may stay in job barrels while couriers collect them, or when there is no warehouse to take them to. */
    private boolean dropOff(ServerLevel level,Settlement town,List<Container> barrels) {
        return !barrels.isEmpty() && JobStorage.freeSlots(barrels)>0
                && (SettlementService.couriers(level,town) || SettlementService.warehouse(level,town,blockPosition())==null);
    }
    /**
     * A supply or delivery trip. The job's own barrels serve it when they hold the missing tool or supply, or when they
     * may take the goods (see {@link #dropOff}); the warehouse serves everything else.
     */
    private boolean visitDepot(ServerLevel level,Settlement town,Station station) {
        StructureRole role=station.role();
        List<BlockPos> barrels=SettlementService.jobBarrels(level,town,station);
        if(!barrels.isEmpty()) {
            List<Container> local=SettlementService.jobStorage(level,town,station);
            boolean pickUp=!properTool(role) && InventoryOps.count(local,s -> toolFits(role,s))>0
                    || needsSupply() && InventoryOps.count(local,this::supplyFor)>0;
            boolean deposit=dropOff(level,town,local) && carryingGoods(role);
            BlockPos barrel=nearestBarrel(barrels);
            if(barrel!=null && (pickUp || deposit)) return visitStorage(level,town,role,barrel,local,deposit);
        }
        return visitWarehouse(level,town,role);
    }
    /** Walk to the storage, hand in goods (if {@code deposit}), and collect food, tools and supplies this job needs. */
    private boolean visitStorage(ServerLevel level,Settlement town,StructureRole role,BlockPos depot,List<Container> storage,boolean deposit) {
        Station place=town.station(depot);
        boolean warehouse=place!=null && place.role()==StructureRole.WAREHOUSE;
        if(!canUse(level,depot)) {
            if(!depot.equals(depotTarget)) { depotTarget=depot.immutable(); depotTicks=0; }
            depotTicks+=10;
            boolean moving=walk(depot);
            activity=warehouse ? "Carrying supplies / returning for food or tools" : "Walking to the job's barrel";
            // A barrel behind a trapdoor or under a carpet is skipped for a minute; the warehouse serves meanwhile.
            if(!warehouse && (depotTicks>BARREL_WALK_TICKS || !moving && onGround())) {
                if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(depotTarget,level.getGameTime()+1200);
                depotTarget=null; depotTicks=0; activity="Cannot reach the job's barrel";
            }
            return false;
        }
        depotTarget=null; depotTicks=0;
        getNavigation().stop();
        int[] foodReserve={role==StructureRole.COOK ? 0 : 8};
        int[] fuelReserve={ProcessingService.FUEL_LOAD};
        if(deposit) cargo.deposit(storage,stack -> {
            if(role.processes() && ProcessingService.fuel(level,stack)) {
                int keep=Math.min(fuelReserve[0],stack.getCount()); fuelReserve[0]-=keep; return keep;
            }
            if(retainSupply(stack)) return stack.getCount();
            if(food(stack)) { int keep=Math.min(foodReserve[0],stack.getCount()); foodReserve[0]-=keep; return keep; }
            return 0;
        });
        boolean keepSupply=action==Action.PLANT && forestTask!=null && getOffhandItem().is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(getOffhandItem());
        if(!keepSupply && !getOffhandItem().isEmpty()) {
            ItemStack leftover=getOffhandItem();
            // A job barrel may be full; leftover supplies then ride along to the warehouse instead.
            if(warehouse) for(Container container:storage) leftover=InventoryOps.insert(container,leftover);
            else { cargo.offer(leftover); leftover=ItemStack.EMPTY; }
            setItemSlot(EquipmentSlot.OFFHAND,leftover);
            if(!leftover.isEmpty()) { activity="Needs storage space for planting/building supplies"; return false; }
        }
        if(deposit && cargo.needsDelivery()) {
            activity=warehouse ? "Warehouse is full; keeping supplies in my inventory" : "The job's barrels are full";
            return false;
        }
        if(wantsMeal()) {
            ItemStack ration=FoodHealing.take(storage,cargo::offer);
            if(!ration.isEmpty()) consumeMeal(ration);
            // Farmers, cooks and craftsmen can make food themselves, so they never wait on an empty pantry.
            // A job's barrel without food is no reason to wait; the warehouse is the next stop.
            else if(warehouse && mealTicks<=0 && role!=StructureRole.FARM && role!=StructureRole.COOK && role!=StructureRole.GUARD && role!=StructureRole.CRAFTSMAN && role!=StructureRole.BLACKSMITH) { activity="Waiting for food in the warehouse"; return false; }
        }
        int rations=0;
        for(int slot=0;slot<cargo.getContainerSize();slot++) if(food(cargo.getItem(slot))) rations+=cargo.getItem(slot).getCount();
        for(int count=rations;count<(role==StructureRole.COOK ? 0 : 8);count++) {
            ItemStack ration=InventoryOps.takeOne(storage,this::food);
            if(ration.isEmpty()) break;
            cargo.offer(ration);
        }
        if(role==StructureRole.GUARD && guardWasActive) stockArmory(storage);
        if(!properTool(role)) {
            ItemStack held=getMainHandItem();
            // At a job barrel the replaced tool rides along in the bag and reaches the warehouse with the next delivery.
            if(warehouse) for(Container container:storage) held=InventoryOps.insert(container,held);
            else { cargo.offer(held); held=ItemStack.EMPTY; }
            setItemSlot(EquipmentSlot.MAINHAND,held);
            if(!held.isEmpty()) { activity="Needs storage space to change tools"; return false; }
            ItemStack tool=InventoryOps.takeOne(storage,s -> toolFits(role,s));
            setItemSlot(EquipmentSlot.MAINHAND,tool);
            if(tool.isEmpty()) { activity="Needs an "+(role==StructureRole.LUMBER ? "axe with at least "+minimumAxeDurability+" durability" : "appropriate pickaxe")+" in storage"; return false; }
        }
        if(needsSupply()) {
            ItemStack supply=getOffhandItem();
            int needed=action==Action.PLANT ? forestTask.planting().cost() : 1;
            for(int i=supply.getCount();i<needed;i++) {
                ItemStack next=InventoryOps.takeOne(storage,stack -> action==Action.PLANT ? stack.is(forestTask.planting().species().seed) : ExcavationService.supportMaterial(stack));
                if(next.isEmpty()) break;
                if(supply.isEmpty()) supply=next; else if(ItemStack.isSameItemSameComponents(supply,next)) supply.grow(1);
                else { // Keep different building materials in cargo until the next delivery.
                    cargo.offer(next);
                }
            }
            setItemSlot(EquipmentSlot.OFFHAND,supply);
            if(needsSupply()) { activity=action==Action.PLANT ? "Needs "+needed+" "+forestTask.planting().species().name().toLowerCase(Locale.ROOT)+" saplings in storage" : "Needs cobblestone, stone, or dirt to support the tunnel floor"; return false; }
        }
        return true;
    }
    private boolean harvestable(ServerLevel level,Settlement town,StructureRole role,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !town.contains(pos) || SettlementService.protectedFurniture(town,pos)
                || level.getBlockEntity(pos)!=null) return false;
        BlockState state=level.getBlockState(pos);
        return role==StructureRole.FARM && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && seed(state)!=null;
    }
    private Item seed(BlockState state) {
        if(state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if(state.is(Blocks.CARROTS)) return Items.CARROT;
        if(state.is(Blocks.POTATOES)) return Items.POTATO;
        if(state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }
    private BlockPos findTarget(ServerLevel level,Settlement town,Station station) {
        var book=SettlementService.reservations(level);
        if(station.role()==StructureRole.LUMBER) {
            forestTask=ForestryService.find(level,town,station,p -> !failedTargets.containsKey(p)
                    && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && workAccessible(level,town,p),
                    item -> cargo.count(item)+(getOffhandItem().is(item) ? getOffhandItem().getCount() : 0));
            if(forestTask==null || !book.claim(forestTask.target(),getUUID(),level.getGameTime(),200)) return null;
            action=forestTask.planting()==null ? Action.FELL : Action.PLANT;
            minimumAxeDurability=forestTask.tree()==null ? 1 : forestTask.tree().logs().size();
            return forestTask.target();
        }
        if(station.role()==StructureRole.MINE) {
            // A mine beside an ore works only that vein, one miner at a time.
            BlockPos vein=OreVeins.find(level,town,station);
            if(vein!=null) {
                if(!book.available(vein,getUUID(),level.getGameTime()) || failedTargets.containsKey(vein)
                        || !workAccessible(level,town,vein) || !book.claim(vein,getUUID(),level.getGameTime(),200)) return null;
                action=Action.VEIN; return vein;
            }
        }
        if(station.role().excavates()) {
            ExcavationJob job=ExcavationService.job(level,town,station);
            if(station.role()==StructureRole.MINE && job!=null && getY()<=job.targetY+6) {
                BlockPos ore=CaveMining.find(level,town,blockPosition(),p -> !failedTargets.containsKey(p)
                        && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && workAccessible(level,town,p));
                if(ore!=null && book.claim(ore,getUUID(),level.getGameTime(),200)) { action=Action.CAVE; return ore; }
                if(reachBudget.deferred()) return null;
            }
            excavation=ExcavationService.next(level,town,station,getUUID(),blockPosition(),failedTargets::containsKey);
            if(excavation==null) return null;
            action=excavation.support() ? Action.SUPPORT : Action.EXCAVATE;
            targetLease=excavation.lease(); return excavation.target();
        }
        action=Action.HARVEST;
        List<BlockPos> candidates=new ArrayList<>();
        for(BlockPos pos:SettlementService.cells(station)) if(!failedTargets.containsKey(pos)
                && SettlementService.ownsBlock(level,town,station,pos) && harvestable(level,town,station.role(),pos)) candidates.add(pos.immutable());
        candidates.sort(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p))));
        for(BlockPos pos:candidates) if(book.claim(pos,getUUID(),level.getGameTime(),200)) return pos;
        return null;
    }
    private String idleReason(ServerLevel level,Settlement town,Station station) {
        if(station.role()==StructureRole.MINE) {
            BlockPos vein=OreVeins.find(level,town,station);
            if(vein!=null) return SettlementService.reservations(level).available(vein,getUUID(),level.getGameTime())
                    ? "Cannot reach the "+OreVeins.name(level.getBlockState(vein))+" vein: it needs open standing room beside it"
                    : "Another miner is already working this vein";
        }
        return station.role().excavates() ? ExcavationService.status(level,town,station)
                : station.role()==StructureRole.LUMBER ? "No accessible natural tree; needs saplings and clear soil in range" : "No mature accessible crops";
    }
    private void cancelTarget(ServerLevel level,boolean failed) {
        if(target!=null) {
            SettlementService.reservations(level).release(target,getUUID());
            if(targetLease!=null) SettlementService.reservations(level).release(targetLease,getUUID());
            if(failed && failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(target,level.getGameTime()+1200);
        }
        target=null; targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1;
        workStand=null; clearingLeaf=null;
        workProgress=0; pathTicks=0; searchDelay=10; getNavigation().stop();
    }
    private void storeDrops(ServerLevel level,List<ItemStack> drops) {
        for(ItemStack stack:drops) cargo.offer(stack);
    }
    private boolean validTarget(ServerLevel level,Settlement town,Station station) {
        return switch(action) {
            case HARVEST -> SettlementService.ownsBlock(level,town,station,target) && harvestable(level,town,station.role(),target);
            // The complete construction/provenance check runs again inside fell before any block changes.
            // While walking or swinging, checking the root avoids retraversing an entire tree twice a second.
            case FELL -> forestTask!=null && forestTask.tree()!=null && level.hasChunkAt(target)
                    && level.getBlockState(target).is(forestTask.tree().species().log)
                    && SettlementService.ownsBlock(level,town,station,target);
            case PLANT -> forestTask!=null && ForestryService.canPlant(level,town,station,forestTask.planting())
                    && SettlementService.ownsBlock(level,town,station,target);
            case EXCAVATE,SUPPORT -> excavation!=null && ExcavationService.valid(level,town,station,excavation);
            case CAVE -> CaveMining.ore(level.getBlockState(target)) && ExcavationService.safeBlock(level,town,target);
            case VEIN -> target.equals(OreVeins.find(level,town,station));
        };
    }
    private void harvest(ServerLevel level,Settlement town,Station station) {
        if(!validTarget(level,town,station) || !properTool(station.role()) || needsSupply()) { cancelTarget(level,false); return; }
        swing(InteractionHand.MAIN_HAND);
        if(action==Action.PLANT) {
            boolean planted=ForestryService.plant(level,town,station,forestTask.planting(),getOffhandItem());
            cancelTarget(level,!planted); return;
        }
        if(action==Action.SUPPORT) {
            boolean placed=ExcavationService.placeSupport(level,town,station,excavation,getOffhandItem());
            cancelTarget(level,!placed); return;
        }
        if(action==Action.FELL) {
            List<ItemStack> drops=ForestryService.fell(level,town,station,target,this);
            if(drops!=null) storeDrops(level,drops);
            cancelTarget(level,drops==null); return;
        }
        if(action==Action.VEIN) {
            // The ore yields its normal drops, then stays in place for the next yield.
            BlockState ore=level.getBlockState(target);
            List<ItemStack> drops=Block.getDrops(ore,level,target,null,this,getMainHandItem());
            getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
            level.levelEvent(2001,target,Block.getId(ore));
            OreVeins.worked(level,target,ore);
            storeDrops(level,drops); workProgress=0; return;
        }
        BlockState state=level.getBlockState(target);
        List<ItemStack> drops=new ArrayList<>(Block.getDrops(state,level,target,null,this,getMainHandItem()));
        if(action==Action.HARVEST) {
            Item seed=seed(state);
            ItemStack reserved=drops.stream().filter(s -> s.is(seed) && !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            if(reserved.isEmpty()) { cancelTarget(level,true); return; }
            reserved.shrink(1);
            if(!level.setBlock(target,((CropBlock)state.getBlock()).getStateForAge(0),3)) { cancelTarget(level,true); return; }
        } else {
            if(!level.destroyBlock(target,false,this)) { cancelTarget(level,true); return; }
            getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
            if(action==Action.EXCAVATE) ExcavationService.completed(level,station,excavation);
        }
        level.levelEvent(2001,target,Block.getId(state));
        storeDrops(level,drops); cancelTarget(level,false);
    }
    private static GuardEquipment.Equipment equipment(LivingEntity entity) {
        return new GuardEquipment.Equipment() {
            public ItemStack get(EquipmentSlot slot) { return entity.getItemBySlot(slot); }
            public void set(EquipmentSlot slot,ItemStack stack) { entity.setItemSlot(slot,stack); }
        };
    }
    private void wearLocalArmor() {
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            ItemStack next=InventoryOps.takeBest(List.of(cargo),s -> GuardEquipment.upgrade(s,getItemBySlot(slot),slot),GuardEquipment::protection);
            if(!next.isEmpty()) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,next); }
        }
    }
    private int arrows() { return InventoryOps.count(List.of(cargo),GuardWeapons::arrow); }
    private boolean carries(Predicate<ItemStack> kind) { return GuardEquipment.usable(getMainHandItem()) && kind.test(getMainHandItem())
            || InventoryOps.count(List.of(cargo),s -> GuardEquipment.usable(s) && kind.test(s))>0; }
    /** Best melee weapon score carried in hand or bag; zero without one. */
    private double bestMelee() {
        double best=GuardWeapons.melee(getMainHandItem()) && GuardEquipment.usable(getMainHandItem()) ? GuardWeapons.score(getMainHandItem()) : 0;
        for(int slot=0;slot<cargo.getContainerSize();slot++) if(GuardWeapons.melee(cargo.getItem(slot)) && GuardEquipment.usable(cargo.getItem(slot))) best=Math.max(best,GuardWeapons.score(cargo.getItem(slot)));
        return best;
    }
    /** Gear a guard is still looking for: armor for empty slots, a better melee weapon, one bow, and arrows for that bow. */
    private boolean needs(ItemStack stack) {
        if(!GuardEquipment.usable(stack)) return false;
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.armor(stack,slot))
            return GuardEquipment.upgrade(stack,getItemBySlot(slot),slot)
                    && InventoryOps.count(List.of(cargo),s -> GuardEquipment.armor(s,slot) && GuardEquipment.usable(s)
                        && GuardEquipment.protection(s)>=GuardEquipment.protection(stack))==0;
        if(GuardWeapons.melee(stack)) return GuardWeapons.score(stack)>bestMelee()+MELEE_UPGRADE;
        if(GuardWeapons.bow(stack)) return !carries(GuardWeapons::bow);
        return GuardWeapons.arrow(stack) && carries(GuardWeapons::bow) && arrows()<ARROW_STOCK;
    }
    private int wanted(ItemStack stack) { return GuardWeapons.arrow(stack) ? Math.min(stack.getCount(),ARROW_STOCK-arrows()) : 1; }
    private void wield(ItemStack next) {
        ItemStack previous=getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND,next);
        if(!previous.isEmpty()) cargo.offer(previous);
    }
    private boolean hold(Predicate<ItemStack> kind) {
        if(kind.test(getMainHandItem()) && GuardEquipment.usable(getMainHandItem())) return true;
        ItemStack next=InventoryOps.takeBest(List.of(cargo),s -> kind.test(s) && GuardEquipment.usable(s),GuardWeapons::score);
        if(next.isEmpty()) return false;
        wield(next); return true;
    }
    /** Off the firing line a guard carries their strongest melee weapon, or the bow if that is all they have. */
    private void readyMelee() {
        double held=GuardWeapons.score(getMainHandItem());
        ItemStack better=InventoryOps.takeBest(List.of(cargo),s -> GuardEquipment.usable(s) && GuardWeapons.score(s)>held,GuardWeapons::score);
        if(!better.isEmpty()) wield(better);
        else if(!GuardWeapons.weapon(getMainHandItem())) hold(GuardWeapons::bow);
    }
    private void stockArmory(List<Container> storage) {
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            ItemStack next=InventoryOps.takeBest(storage,s -> GuardEquipment.upgrade(s,getItemBySlot(slot),slot),GuardEquipment::protection);
            if(!next.isEmpty()) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,next); }
        }
        double best=bestMelee()+MELEE_UPGRADE;
        ItemStack weapon=InventoryOps.takeBest(storage,s -> GuardEquipment.usable(s) && GuardWeapons.melee(s) && GuardWeapons.score(s)>best,GuardWeapons::score);
        if(!weapon.isEmpty()) cargo.offer(weapon);
        if(!carries(GuardWeapons::bow)) {
            ItemStack bow=InventoryOps.takeOne(storage,s -> GuardWeapons.bow(s) && GuardEquipment.usable(s));
            if(!bow.isEmpty()) cargo.offer(bow);
        }
        while(carries(GuardWeapons::bow) && arrows()<ARROW_STOCK) {
            ItemStack arrow=InventoryOps.takeOne(storage,GuardWeapons::arrow);
            if(arrow.isEmpty()) break;
            cargo.offer(arrow);
        }
        readyMelee();
        // A replaced weapon goes straight back into storage for someone else.
        cargo.deposit(storage,s -> gear(s) && !retainSupply(s) ? 0 : s.getCount());
    }
    private boolean stocks(Container container) {
        for(int slot=0;slot<container.getContainerSize();slot++) if(needs(container.getItem(slot))) return true;
        return false;
    }
    private boolean equipFromStand(ServerLevel level,Settlement town,Station station) {
        if(gearTicks>0) { gearTicks-=10; return false; }
        gearTicks=40;
        var stands=new ArrayList<>(GuardService.stands(level,town,station));
        stands.sort(Comparator.comparingDouble(this::distanceToSqr));
        for(ArmorStand stand:stands) {
            boolean missing=Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> needs(stand.getItemBySlot(slot)))
                    || needs(stand.getItemBySlot(EquipmentSlot.MAINHAND)) || needs(stand.getItemBySlot(EquipmentSlot.OFFHAND));
            if(!missing) continue;
            if(!standNear(stand)) { if(approachStand(level,stand,"Collecting better gear from a stand")) { gearTicks=0; return true; } continue; }
            gearStand=null; gearPathTicks=0;
            for(EquipmentSlot slot:GuardEquipment.ARMOR) GuardEquipment.upgrade(equipment(stand),equipment(this),slot);
            // Stands double as weapon racks: take a needed weapon, or arrows, from either hand.
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
                ItemStack held=stand.getItemBySlot(slot);
                if(!needs(held)) continue;
                cargo.offer(held.split(wanted(held)));
                stand.setItemSlot(slot,held.isEmpty() ? ItemStack.EMPTY : held);
            }
            readyMelee();
            swing(InteractionHand.MAIN_HAND); return false;
        }
        return false;
    }
    private boolean standNear(ArmorStand stand) { return CitizenReach.within(getEyePosition(),stand.getBoundingBox()) && hasLineOfSight(stand); }
    private boolean approachStand(ServerLevel level,ArmorStand stand,String status) {
        if(ignoredStands.getOrDefault(stand.getUUID(),0L)>level.getGameTime()) return false;
        if(!stand.getUUID().equals(gearStand)) { gearStand=stand.getUUID(); gearPathTicks=0; }
        gearPathTicks+=10;
        if(gearPathTicks>400 || !canReach(stand.blockPosition()) || !walk(stand.blockPosition()) && onGround()) {
            ignoredStands.put(stand.getUUID(),level.getGameTime()+200); gearStand=null; gearPathTicks=0; return false;
        }
        activity=status; return true;
    }
    private ItemStack returnableArmor(EquipmentSlot slot,boolean all) {
        ItemStack worn=getItemBySlot(slot);
        if(!worn.isEmpty() && (all || GuardEquipment.worn(worn))) return worn;
        return cargo.first(stack -> GuardEquipment.armor(stack,slot) && (all || !GuardEquipment.upgrade(stack,worn,slot)));
    }
    private boolean hasArmorToReturn(boolean all) { return Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !returnableArmor(slot,all).isEmpty()); }
    private GuardEquipment.Equipment returningArmor(boolean all) {
        Map<EquipmentSlot,ItemStack> sources=new EnumMap<>(EquipmentSlot.class);
        Set<EquipmentSlot> worn=new HashSet<>();
        for(EquipmentSlot slot:GuardEquipment.ARMOR) {
            sources.put(slot,returnableArmor(slot,all));
            if(!getItemBySlot(slot).isEmpty() && (all || GuardEquipment.worn(getItemBySlot(slot)))) worn.add(slot);
        }
        return new GuardEquipment.Equipment() {
            public ItemStack get(EquipmentSlot slot) { return sources.getOrDefault(slot,ItemStack.EMPTY); }
            public void set(EquipmentSlot slot,ItemStack stack) {
                if(worn.contains(slot)) setItemSlot(slot,stack); else cargo.replace(sources.get(slot),stack);
            }
        };
    }
    /** Leave the real armor for the next shift. A full rack falls back to warehouse storage, never overwrites a piece. */
    private boolean returnGuardArmor(ServerLevel level,Settlement town,Station station,boolean all) {
        if(!hasArmorToReturn(all)) return false;
        if(level.getGameTime()<gearReturnAt) {
            if(all) for(EquipmentSlot slot:GuardEquipment.ARMOR) { cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY); }
            return false;
        }
        List<ArmorStand> stands=new ArrayList<>(GuardService.stands(level,town,station));
        stands.sort(Comparator.comparingDouble(this::distanceToSqr));
        for(ArmorStand stand:stands) {
            if(Arrays.stream(GuardEquipment.ARMOR).noneMatch(slot -> stand.getItemBySlot(slot).isEmpty() && !returnableArmor(slot,all).isEmpty())) continue;
            if(!standNear(stand)) { if(approachStand(level,stand,all ? "Returning armor for the next shift" : "Returning armor for repair")) return true; continue; }
            getNavigation().stop(); gearStand=null; gearPathTicks=0;
            for(EquipmentSlot slot:GuardEquipment.ARMOR) GuardEquipment.deposit(returningArmor(all),equipment(stand),slot);
            if(!hasArmorToReturn(all)) return false;
        }
        // No accessible empty stand slot: remove the equipment before sleeping and keep it until a real destination accepts it.
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(!getItemBySlot(slot).isEmpty() && (all || GuardEquipment.worn(getItemBySlot(slot)))) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse!=null) {
            if(!canUse(level,warehouse)) {
                gearPathTicks+=10;
                if(gearPathTicks<=400 && canReach(warehouse) && walk(warehouse)) { activity="Returning spare armor to the warehouse"; return true; }
            } else cargo.deposit(SettlementService.storageAt(level,town,warehouse),s -> armor(s) && (all || !retainSupply(s)) ? 0 : s.getCount());
        }
        gearPathTicks=0; gearReturnAt=level.getGameTime()+100; return false;
    }
    private boolean wornWeapons() {
        return GuardWeapons.weapon(getMainHandItem()) && GuardEquipment.worn(getMainHandItem())
                || !cargo.first(s -> GuardWeapons.weapon(s) && GuardEquipment.worn(s)).isEmpty();
    }
    private void retireWeapon() {
        if(GuardWeapons.weapon(getMainHandItem()) && GuardEquipment.worn(getMainHandItem())) { cargo.offer(getMainHandItem()); setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); }
    }
    /** Pick up loose weapons and arrows, such as a fallen skeleton's bow, that have lain in town for a few seconds. */
    private boolean scavenge(ServerLevel level,Settlement town) {
        if(scavengeTicks>0) { scavengeTicks-=10; return false; }
        ignoredLoot.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        ItemEntity loot=level.getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(12,4,12),
                e -> e.isAlive() && e.getAge()>=100 && town.contains(e.blockPosition()) && !ignoredLoot.containsKey(e.getUUID()) && needs(e.getItem())).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(loot==null) { scavengeTicks=60; return false; }
        if(!loot.getUUID().equals(scavengeTarget)) { scavengeTarget=loot.getUUID(); scavengePathTicks=0; }
        if(distanceToSqr(loot)>2.25) {
            // Items on roofs or behind walls are skipped for a minute instead of holding the guard in place.
            scavengePathTicks+=10;
            if(scavengePathTicks==10 && !canReach(loot.blockPosition()) || scavengePathTicks>300 || !walk(loot.blockPosition()) && onGround()) {
                ignoredLoot.put(loot.getUUID(),level.getGameTime()+1200); scavengeTarget=null; scavengePathTicks=0; scavengeTicks=60; return false;
            }
            activity="Picking up a weapon or gear"; return true;
        }
        ItemStack stack=loot.getItem();
        int amount=wanted(stack);
        take(loot,amount);
        cargo.offer(stack.split(amount));
        if(stack.isEmpty()) loot.discard(); else loot.setItem(stack);
        readyMelee(); return false;
    }
    private BlockPos patrolPoint(ServerLevel level,Settlement town,BlockPos post) {
        // Visit furnished work sites around town, returning to the active shift post every third leg.
        if(++patrolVisits%3==0) return post;
        for(int attempt=0;attempt<12;attempt++) {
            BlockPos anchor=attempt<6 && !town.stations.isEmpty()
                    ? town.stations.get(getRandom().nextInt(town.stations.size())).position() : blockPosition();
            int spread=attempt<6 ? 3 : 12;
            int x=anchor.getX()+getRandom().nextInt(spread*2+1)-spread;
            int z=anchor.getZ()+getRandom().nextInt(spread*2+1)-spread;
            BlockPos column=new BlockPos(x,anchor.getY(),z);
            if(!town.contains(column) || !level.hasChunkAt(column)) continue;
            for(int dy=2;dy>=-3;dy--) {
                BlockPos candidate=column.offset(0,dy,0);
                if(GuardService.walkable(level,town,candidate) && canReach(candidate)) return candidate;
            }
        }
        return post;
    }
    /** No citizen or player may stand within a block of the arrow's straight path. */
    private boolean clearShot(ServerLevel level,LivingEntity enemy) {
        Vec3 from=getEyePosition(),to=enemy.getBoundingBox().getCenter(),path=to.subtract(from);
        for(LivingEntity other:level.getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1.0),
                e -> e!=this && e!=enemy && e.isAlive() && (e instanceof CitizenEntity || e instanceof Player))) {
            Vec3 point=other.getBoundingBox().getCenter();
            double along=Math.clamp(point.subtract(from).dot(path)/Math.max(1.0E-6,path.lengthSqr()),0.0,1.0);
            if(point.distanceTo(from.add(path.scale(along)))<1.0) return false;
        }
        return true;
    }
    private void shoot(ServerLevel level,LivingEntity enemy) {
        ItemStack ammo=InventoryOps.takeOne(List.of(cargo),GuardWeapons::arrow);
        if(ammo.isEmpty()) return;
        ItemStack bow=getMainHandItem();
        var arrow=ProjectileUtil.getMobArrow(this,ammo,1.0F,bow);
        double dx=enemy.getX()-getX(),dy=enemy.getY(1.0/3.0)-arrow.getY(),dz=enemy.getZ()-getZ();
        arrow.shoot(dx,dy+Math.sqrt(dx*dx+dz*dz)*0.2,dz,1.6F,4.0F);
        level.addFreshEntity(arrow);
        playSound(SoundEvents.ARROW_SHOOT,1.0F,1.0F/(getRandom().nextFloat()*0.4F+0.8F));
        bow.hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
    }
    private void fight(ServerLevel level,LivingEntity enemy) {
        setTarget(enemy); activity="Defending the settlement";
        getLookControl().setLookAt(enemy,30.0F,30.0F);
        double distance=distanceTo(enemy);
        // Archers shoot at range with real arrows, switching to a melee weapon once the enemy closes in.
        if(distance>GuardWeapons.BOW_MIN_RANGE && distance<=GuardWeapons.BOW_MAX_RANGE && arrows()>0
                && carries(GuardWeapons::bow) && clearShot(level,enemy) && hold(GuardWeapons::bow)) {
            getNavigation().stop(); activity="Shooting at an attacker";
            if(!isUsingItem()) { if(guardAttackTicks==0) startUsingItem(InteractionHand.MAIN_HAND); }
            else if(getTicksUsingItem()>=20) { stopUsingItem(); shoot(level,enemy); guardAttackTicks=20; }
            return;
        }
        if(isUsingItem()) stopUsingItem();
        // Without a melee weapon, put the bow away and fight with fists instead of wearing it out.
        if(!hold(GuardWeapons::melee) && GuardWeapons.bow(getMainHandItem())) wield(ItemStack.EMPTY);
        if(CitizenReach.within(getEyePosition(),enemy.getBoundingBox()) && hasLineOfSight(enemy)) {
            getNavigation().stop();
            if(guardAttackTicks==0) {
                swing(InteractionHand.MAIN_HAND);
                if(doHurtTarget(level,enemy) && GuardWeapons.melee(getMainHandItem())) getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
                guardAttackTicks=20;
            }
        } else walk(enemy.blockPosition(),0.8);
    }
    /**
     * Head for a hostile a citizen reported, or a wave straggler, and fight it once it is in sight. One that stays out
     * of reach for a minute, or has no path at all, is left to the other guards for two minutes.
     */
    private boolean respond(ServerLevel level,Settlement town,DefenseService.Call call) {
        Monster enemy=call.mob();
        String name=enemy.getName().getString();
        if(!enemy.getUUID().equals(respondTarget)) { respondTarget=enemy.getUUID(); respondTicks=0; }
        if(distanceToSqr(enemy)<=GuardWeapons.BOW_MAX_RANGE*GuardWeapons.BOW_MAX_RANGE && hasLineOfSight(enemy)) {
            respondTicks=0;
            fight(level,enemy);
            activity=call.wave() ? "Fighting a straggler from the wave" : "Dealing with the "+name+" "+call.reporter()+" reported";
            return true;
        }
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        respondTicks+=10;
        activity=(call.wave() ? "Hunting a glowing "+name+" left from the wave" : "Answering "+call.reporter()+"'s call about a "+name)
                +" near "+enemy.blockPosition().toShortString();
        if(respondTicks>1800 || !walk(enemy.blockPosition(),0.8) && onGround()) {
            ignoredThreats.put(enemy.getUUID(),level.getGameTime()+2400);
            DefenseService.release(town,enemy.getUUID(),getUUID());
            respondTarget=null; respondTicks=0; return false;
        }
        return true;
    }
    /** A civilian who spots a hostile near their work calls the guards to deal with it. */
    public void called(Monster monster,boolean guards) {
        if(isGuard()) return;
        callNote=guards ? "Called the guards about a "+monster.getName().getString()+" nearby"
                : "Spotted a "+monster.getName().getString()+" nearby, but the town has no guards";
        callNoteUntil=level().getGameTime()+100;
    }
    private void runToBell(ServerLevel level,Settlement town,BlockPos bell) {
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        if(canUse(level,bell)) {
            getNavigation().stop(); getLookControl().setLookAt(bell.getX()+0.5,bell.getY()+0.5,bell.getZ()+0.5);
            swing(InteractionHand.MAIN_HAND); activity="Ringing the alarm bell";
            DefenseService.ring(level,town,this,bell); return;
        }
        activity="Running to ring the alarm bell";
        // Mid-jump or mid-fall a path cannot be planned; only give up once on the ground.
        if(!walk(bell,0.9) && onGround()) DefenseService.abandon(town,getUUID());
    }
    private void guard(ServerLevel level,Settlement town,Station station) {
        ignoredStands.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        retireWeapon();
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.worn(getItemBySlot(slot))) {
            cargo.offer(getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
        }
        BlockPos bell=DefenseService.bellRun(town,getUUID());
        if(bell!=null) { wakeForAlarm(); runToBell(level,town,bell); return; }
        boolean alarm=DefenseService.alarmed(town);
        if(!GuardService.onDuty(level,town,station.position(),getUUID())) {
            guardWasActive=false;
            if(activePost!=null) { activePost=null; patrolTarget=null; getNavigation().stop(); }
            setTarget(null); if(isUsingItem()) stopUsingItem();
            if(returnGuardArmor(level,town,station,true)) return;
            if(wornWeapons() && level.getGameTime()>=guardSupplyAt) {
                BlockPos depot=SettlementService.warehouse(level,town,blockPosition());
                if(depot!=null && (handNear(depot) || canReach(depot)) && !visitWarehouse(level,town,StructureRole.GUARD) && !canUse(level,depot)) return;
                guardSupplyAt=level.getGameTime()+200;
            }
            eatFrom(List.of(cargo)); rest(level,town);
            activity="Off duty: "+activity; return;
        }
        wakeForAlarm();
        if(!guardWasActive) {
            guardWasActive=true; gearTicks=0; armoryTicks=0; guardSupplyAt=0; gearReturnAt=0;
            gearStand=null; gearPathTicks=0; shiftGearUntil=level.getGameTime()+400;
        }
        wearLocalArmor(); readyMelee();
        if(town.trading.npc) {
            Player attacker=level.getEntitiesOfClass(Player.class,getBoundingBox().inflate(24),p -> p.isAlive() && !p.isSpectator()
                    && !p.getAbilities().instabuild && town.contains(p.blockPosition()) && town.trading.relations.getOrDefault(p.getUUID(),0)<0 && hasLineOfSight(p))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            if(attacker!=null) { fight(level,attacker); activity="Defending the town against an attacker"; return; }
        }
        Monster enemy=level.getEntitiesOfClass(Monster.class,getBoundingBox().inflate(alarm ? 32 : 16),
                m -> m.isAlive() && town.contains(m.blockPosition()) && hasLineOfSight(m)).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(enemy!=null) { fight(level,enemy); return; }
        ignoredThreats.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        DefenseService.Call call=DefenseService.assignment(level,town,this,ignoredThreats::containsKey);
        if(call!=null && respond(level,town,call)) return;
        respondTarget=null; respondTicks=0;
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        if(returnGuardArmor(level,town,station,false)) return;
        if(equipFromStand(level,town,station) || scavenge(level,town)) return;
        useLocalSupplies(StructureRole.GUARD);
        if(armoryTicks>0) armoryTicks-=10;
        else {
            armoryTicks=100;
            BlockPos depot=SettlementService.warehouse(level,town,blockPosition());
            armoryStocked=depot!=null && SettlementService.storageAt(level,town,depot).stream().anyMatch(this::stocks);
        }
        // While the alarm rings, only an unarmed guard leaves the defense to resupply.
        boolean foodTrip=wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0;
        boolean resupply=alarm ? !carries(GuardWeapons::weapon) && (armoryStocked || wornWeapons())
                : deliverCargo() || mealTicks<=0 || foodTrip || armoryStocked || wornWeapons();
        if(resupply && level.getGameTime()>=guardSupplyAt) {
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            if(warehouse!=null && (handNear(warehouse) || canReach(warehouse))
                    && !visitWarehouse(level,town,StructureRole.GUARD) && !canUse(level,warehouse)) return;
            // An empty pantry must not leave the station's only sentry waiting there forever.
            guardSupplyAt=level.getGameTime()+200;
            armoryStocked=false;
        }
        // Give the outgoing guard time to bring back the shared set. Missing stock never suspends defense indefinitely.
        if(!alarm && level.getGameTime()<shiftGearUntil && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> getItemBySlot(slot).isEmpty())) {
            if(!near(station.position())) walk(station.position()); else getNavigation().stop();
            activity="Starting shift: checking for the shared armor set"; return;
        }
        String shift=(alarm ? "On alert: " : "")+(night(level) ? "night" : "day");
        BlockPos post=GuardService.posts(level,station).active(night(level));
        if(!Objects.equals(activePost,post)) { activePost=post; patrolTarget=post; patrolTicks=0; pathTicks=0; getNavigation().stop(); }
        if(!town.contains(post) || !level.hasChunkAt(post)) { activity="Waiting for the shift post to be loaded"; return; }
        if(patrolTicks>0) { patrolTicks-=10; activity="Guarding the "+shift+" post"; return; }
        if(patrolTarget==null) { patrolTarget=patrolPoint(level,town,post); pathTicks=0; }
        if(near(patrolTarget)) {
            getNavigation().stop(); patrolTarget=null; patrolTicks=alarm ? 20 : 40; pathTicks=0;
            activity="Patrolling the "+shift+" route"; return;
        }
        activity="Walking the "+shift+" patrol"; pathTicks+=10;
        if(!walk(patrolTarget,alarm ? 0.8 : 0.65) || pathTicks>600) { patrolTarget=null; patrolTicks=60; pathTicks=0; }
    }
    /** Craftsmen fill warehouse shortages from real materials, one trip of batches at a time. */
    private void craft(ServerLevel level,Settlement town,Station station,BlockPos bench) {
        if(order!=null && (town.disabledRecipes.contains(order.id()) || !Crafting.ready(cargo,order))) order=null;
        if(order==null) {
            // Hand in what was baked where finished goods go, then gather wheat: from the kitchen's barrels first.
            if(carryingGoods(station.role()) && !visitDepot(level,town,station)) return;
            List<Container> stock=SettlementService.townStorage(level,town);
            List<Container> local=SettlementService.jobStorage(level,town,station);
            BlockPos barrel=nearestBarrel(SettlementService.jobBarrels(level,town,station));
            List<Container> storage;
            if(barrel!=null && Crafting.choose(stock,local,town.disabledRecipes,station.role())!=null) {
                if(!visitStorage(level,town,station.role(),barrel,local,false)) return;
                storage=local;
            } else {
                if(!visitWarehouse(level,town,station.role())) return;
                BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
                storage=warehouse==null ? List.of() : SettlementService.storageAt(level,town,warehouse);
            }
            Crafting.Recipe next=Crafting.choose(stock,storage,town.disabledRecipes,station.role());
            if(next==null || Crafting.fetch(stock,storage,cargo,next)==0) {
                activity="Nothing to craft: the warehouse is stocked or lacks materials";
                idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return;
            }
            order=next; workProgress=0; pathTicks=0;
        }
        if(!canUse(level,bench)) {
            activity="Carrying materials for "+order.label()+" to the workbench"; pathTicks+=10;
            if(!walk(bench) || pathTicks>1200) { idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(bench.getX()+0.5,bench.getY()+0.5,bench.getZ()+0.5);
        activity="Crafting "+order.label();
        workProgress+=10;
        if(workProgress>=CRAFT_TICKS) { workProgress=0; swing(InteractionHand.MAIN_HAND); cargo.offer(Crafting.craft(cargo,order)); }
    }
    /**
     * Craftsmen fill learned orders in the owner's priority order: deliver what they made, then gather materials for one
     * trip, from the workshop's own barrels when they hold enough and from the warehouse otherwise, and craft at the bench.
     */
    private void craftsman(ServerLevel level,Settlement town,Station station) {
        BlockPos bench=station.position();
        if(craftJob!=null && (Workshop.find(town,craftJob.order().item())<0 || town.craftOrders.get(Workshop.find(town,craftJob.order().item())).target()<=0
                || !Workshop.ready(cargo,craftJob.plan()))) craftJob=null;
        if(craftJob==null) {
            if(carryingGoods(station.role()) && !visitDepot(level,town,station)) return;
            List<Container> stock=SettlementService.townStorage(level,town);
            BlockPos barrel=nearestBarrel(SettlementService.jobBarrels(level,town,station));
            List<Container> local=SettlementService.jobStorage(level,town,station);
            Workshop.Job next=barrel==null ? null : Workshop.choose(Workshop.Recipes.of(level),town.craftOrders,stock,local);
            List<Container> sources;
            if(next!=null) {
                if(!visitStorage(level,town,station.role(),barrel,local,false)) return;
                sources=local;
            } else {
                BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
                List<Container> stored=warehouse==null ? List.of() : SettlementService.storageAt(level,town,warehouse);
                if(warehouse==null || Workshop.choose(Workshop.Recipes.of(level),town.craftOrders,stock,stored)==null) {
                    activity="Nothing to craft: every order is stocked or lacks materials";
                    idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return;
                }
                if(!visitWarehouse(level,town,station.role())) return;
                sources=SettlementService.storageAt(level,town,warehouse);
                next=Workshop.choose(Workshop.Recipes.of(level),town.craftOrders,stock,sources);
            }
            if(next==null || Workshop.fetch(Workshop.Recipes.of(level),town.craftOrders,next,stock,sources,cargo)==0) {
                activity="Nothing to craft: every order is stocked or lacks materials";
                idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return;
            }
            craftJob=next; workProgress=0; pathTicks=0;
        }
        String product=craftJob.plan().result().getHoverName().getString();
        if(!canUse(level,bench)) {
            activity="Carrying materials for "+product+" to the workbench"; pathTicks+=10;
            if(!walk(bench) || pathTicks>1200) { idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(bench.getX()+0.5,bench.getY()+0.5,bench.getZ()+0.5);
        activity="Crafting "+product;
        workProgress+=10;
        if(workProgress>=CRAFT_TICKS) {
            workProgress=0;
            if(Workshop.craft(level,cargo,craftJob.plan(),cargo::offer)) swing(InteractionHand.MAIN_HAND);
            else craftJob=null;
        }
    }
    /** Couriers carry finished goods from job barrels to the warehouse and keep smelters' and cooks' barrels supplied. */
    private void courier(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null) {
            activity="Needs a loaded warehouse to carry goods to";
            idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return;
        }
        Station job=haulStation==null ? null : town.station(haulStation);
        List<BlockPos> barrels=job==null ? List.of() : SettlementService.jobBarrels(level,town,job);
        if(barrels.isEmpty() || nearestBarrel(barrels)==null) {
            haulStation=null; haulSupply=false;
            // Goods already carried reach the warehouse before the next errand.
            if(cargo.hasDeliverable(this::retainSupply,this::food)) { visitWarehouse(level,town,StructureRole.COURIER); return; }
            job=errand(level,town,warehouse);
            if(job==null) {
                activity="No goods waiting in job barrels";
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); searchDelay=20; return;
            }
            barrels=SettlementService.jobBarrels(level,town,job);
        }
        var book=SettlementService.reservations(level);
        BlockPos claim=barrels.getFirst(),barrel=nearestBarrel(barrels);
        // One courier per job's barrels at a time.
        if(!book.claim(claim,getUUID(),level.getGameTime(),200)) { haulStation=null; haulSupply=false; return; }
        if((haulTicks+=10)>2400) {
            // An errand that drags on is dropped, and its barrel skipped for a while.
            if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
            book.release(claim,getUUID()); haulStation=null; haulSupply=false; return;
        }
        List<Container> local=SettlementService.jobStorage(level,town,job);
        if(haulSupply && InventoryOps.count(List.of(cargo),this::haulingInput)==0) {
            // Pick up the supplies first.
            if(!visitWarehouse(level,town,StructureRole.COURIER)) return;
            if(JobStorage.load(JobStorage.Supplies.of(level),job.role(),local,SettlementService.storageAt(level,town,warehouse),cargo)==0) {
                book.release(claim,getUUID()); haulStation=null; haulSupply=false;
            } else activity="Carrying supplies to the "+job.role().id()+" station";
            return;
        }
        if(!canUse(level,barrel)) {
            activity=haulSupply ? "Carrying supplies to the "+job.role().id()+" station" : "Walking to collect goods at the "+job.role().id()+" station";
            if(!walk(barrel) && onGround()) {
                if(failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
                book.release(claim,getUUID()); haulStation=null; haulSupply=false;
            }
            return;
        }
        getNavigation().stop(); swing(InteractionHand.MAIN_HAND);
        if(haulSupply) {
            StructureRole role=job.role();
            JobStorage.Supplies supplies=JobStorage.Supplies.of(level);
            cargo.deposit(local,stack -> JobStorage.input(supplies,role,stack) ? 0 : stack.getCount());
            activity="Stocked the "+role.id()+" station's barrels";
        } else {
            int moved=JobStorage.collect(JobStorage.Supplies.of(level),town,job.role(),local,cargo);
            activity="Collected "+moved+" goods from the "+job.role().id()+" station";
        }
        book.release(claim,getUUID());
        haulStation=null; haulSupply=false;
    }
    /**
     * The next courier errand: the largest worthwhile load, otherwise supplies for a smeltery or kitchen, otherwise any
     * waiting goods at all, so a pair of new tools or a few ingots never wait for a full load.
     */
    private Station errand(ServerLevel level,Settlement town,BlockPos warehouse) {
        List<Container> stored=SettlementService.storageAt(level,town,warehouse);
        int pantry=InventoryOps.count(stored,FoodHealing::food);
        JobStorage.Supplies supplies=JobStorage.Supplies.of(level);
        var book=SettlementService.reservations(level);
        Station worthwhile=null,small=null,supply=null;
        int most=0,fewest=0;
        for(Station candidate:town.stations) {
            List<BlockPos> spots=SettlementService.jobBarrels(level,town,candidate);
            if(spots.isEmpty() || nearestBarrel(spots)==null || !book.available(spots.getFirst(),getUUID(),level.getGameTime())) continue;
            List<Container> local=SettlementService.jobStorage(level,town,candidate);
            var pickups=JobStorage.collectable(supplies,town,candidate.role(),local);
            int goods=JobStorage.goods(pickups);
            if(goods>most && JobStorage.worthCollecting(pickups,JobStorage.freeSlots(local),pantry)) { worthwhile=candidate; most=goods; }
            else if(goods>fewest) { small=candidate; fewest=goods; }
            if(supply==null && JobStorage.needsSupplies(supplies,candidate.role(),local,stored)) supply=candidate;
        }
        Station chosen=worthwhile!=null ? worthwhile : supply!=null ? supply : small;
        if(chosen!=null) { haulStation=chosen.position(); haulSupply=worthwhile==null && chosen==supply; haulTicks=0; }
        return chosen;
    }
    private void process(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> devices=SettlementService.processingDevices(level,town,station);
        if(devices.isEmpty()) {
            activity=station.role()==StructureRole.COOK ? "Needs a smoker or lit campfire within three blocks" : "Needs a furnace or blast furnace within three blocks";
            idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); return;
        }
        if(processor==null || !devices.contains(processor)) {
            processor=devices.stream().sorted(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p))))
                    .filter(this::canReach).findFirst().orElse(null);
            if(processor==null) {
                if(reachBudget.deferred()) { activity="Checking reachable cooking/smelting blocks"; return; }
                activity="Cannot reach the station's appliances";
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); return;
            }
            pathTicks=0;
        }
        if(station.role()==StructureRole.COOK && order!=null) { craft(level,town,station,processor); return; }
        if(station.role()==StructureRole.COOK && !town.disabledRecipes.contains("bread")
                && Crafting.ready(cargo,Crafting.byId("bread"))) {
            order=Crafting.byId("bread"); craft(level,town,station,processor); return;
        }
        if(level.getGameTime()<nextProcessingAt) { activity="Waiting for the next cooking/smelting batch"; return; }
        if(processingDelivery || !processingSupplied) {
            BlockPos barrel=nearestBarrel(SettlementService.jobBarrels(level,town,station));
            List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
            boolean localDrop=dropOff(level,town,local);
            // A furnace or smoker also needs fuel: ore without fuel in the barrel means a warehouse trip for both.
            boolean fuelled=!(level.getBlockEntity(processor) instanceof AbstractFurnaceBlockEntity)
                    || InventoryOps.count(local,s -> ProcessingService.fuel(level,s))+InventoryOps.count(List.of(cargo),s -> ProcessingService.fuel(level,s))>0;
            boolean localSupply=!local.isEmpty() && (ProcessingService.hasInputs(level,station.role(),processor,local) && fuelled
                    || station.role()==StructureRole.COOK && Crafting.choose(SettlementService.townStorage(level,town),local,town.disabledRecipes,StructureRole.COOK)!=null);
            // Finished goods go to the barrels only when they may stay there; supplies come from the barrels first.
            boolean useLocal=processingDelivery ? localDrop : localSupply;
            List<Container> storage;
            if(useLocal) {
                if(!visitStorage(level,town,station.role(),barrel,local,localDrop)) return;
                storage=local;
            } else {
                if(!visitWarehouse(level,town,station.role())) return;
                storage=SettlementService.storageAt(level,town,SettlementService.warehouse(level,town,blockPosition()));
            }
            processingDelivery=false;
            processingSupplied=true;
            if(useLocal && !localSupply) {
                // Delivered to an empty barrel: fetch ingredients from the warehouse next.
                processingSupplied=false; return;
            }
            ProcessingService.fetch(level,station.role(),processor,storage,cargo);
            if(station.role()==StructureRole.COOK && !ProcessingService.hasInputs(level,station.role(),processor,List.of(cargo))) {
                List<Container> stock=SettlementService.townStorage(level,town);
                Crafting.Recipe bread=Crafting.choose(stock,storage,town.disabledRecipes,StructureRole.COOK);
                if(bread!=null && Crafting.fetch(stock,storage,cargo,bread)>0) { order=bread; workProgress=0; craft(level,town,station,processor); return; }
            }
        }
        if(!canUse(level,processor)) {
            activity="Carrying ingredients/fuel to the "+station.role().id()+" appliance"; pathTicks+=10;
            if(!walk(processor) || pathTicks>1200) {
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level);
            }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        int collected=ProcessingService.service(level,station.role(),processor,cargo,this);
        swing(InteractionHand.MAIN_HAND);
        activity=station.role()==StructureRole.COOK ? "Supplying the kitchen and collecting cooked food" : "Supplying furnaces and collecting smelted ores";
        if(collected==0 && !ProcessingService.busy(level,processor) && !ProcessingService.hasInputs(level,station.role(),processor,List.of(cargo))) {
            if(++processingIdle>=devices.size()) {
                activity="No ingredients to process; checking other jobs";
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); return;
            }
        } else processingIdle=0;
        if(ProcessingService.needsFuel(level,processor)) activity="Waiting for furnace/smoker fuel in the warehouse";
        processingDelivery=collected>0 || cargo.needsDelivery();
        processingSupplied=false;
        nextProcessingAt=processingDelivery ? 0 : level.getGameTime()+40;
        // Rotate through the station's appliances; progress stays in their block entities.
        processor=devices.get((devices.indexOf(processor)+1)%devices.size());
    }
    public String activity() { return level().getGameTime()<callNoteUntil ? callNote : activity; }
    /** Station this citizen works at, or null. */
    public BlockPos workplace() { return workplace; }
    /** Ticks until the next scheduled meal. */
    public int mealTicks() { return mealTicks; }
    /** Overflow from a large harvest waiting for bag space. */
    public boolean overflowing() { return cargo.hasPending(); }
    private ArmorStand repairStandEntity(ServerLevel level,Settlement town) {
        if(repairStand==null || !(level.getEntity(repairStand) instanceof ArmorStand stand) || !stand.isAlive()) return null;
        Station owner=town.nearestStation(stand.blockPosition(),s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s));
        return owner!=null && GuardService.stands(level,town,owner).contains(stand) ? stand : null;
    }
    private void finishRepair(ServerLevel level,Settlement town) {
        ArmorStand stand=repairStandEntity(level,town);
        if(stand!=null && repairSlot!=null && stand.getItemBySlot(repairSlot).isEmpty() && GuardEquipment.armor(repairItem,repairSlot)) {
            if(!standNear(stand)) { if(approachStand(level,stand,"Returning repaired armor to its stand")) return; }
            else { getNavigation().stop(); stand.setItemSlot(repairSlot,repairItem); repairItem=ItemStack.EMPTY; }
        }
        if(!repairItem.isEmpty()) {
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            if(warehouse==null) { activity="Holding repaired equipment: needs warehouse storage"; return; }
            if(!canUse(level,warehouse)) { activity="Returning repaired equipment to the warehouse"; walk(warehouse); return; }
            getNavigation().stop();
            for(Container storage:SettlementService.storageAt(level,town,warehouse)) repairItem=InventoryOps.insert(storage,repairItem);
            if(!repairItem.isEmpty()) { activity="Holding repaired equipment: warehouse is full"; return; }
        }
        repairStand=null; repairSlot=null; repairAnvil=null; repairDelivery=false; workProgress=0; nextSmithAt=level.getGameTime()+20;
        activity="Repair delivered";
    }
    private void blacksmith(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        if(!repairItem.isEmpty() && (repairDelivery || !BlacksmithRepair.damaged(repairItem))) { finishRepair(level,town); return; }
        if(level.getGameTime()<nextSmithAt) return;
        List<BlockPos> anvils=SettlementService.anvils(level,town,station);
        if(repairAnvil==null || !anvils.contains(repairAnvil)) {
            repairAnvil=anvils.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition())))
                    .filter(p -> handNear(p) || canReach(p)).findFirst().orElse(null);
        }
        if(repairAnvil==null) { activity="Needs an accessible anvil within three blocks of the Blacksmith Station"; nextSmithAt=level.getGameTime()+100; return; }
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        if(warehouse==null) { activity="Needs a loaded warehouse with repair materials"; nextSmithAt=level.getGameTime()+100; return; }
        List<Container> storage=SettlementService.storageAt(level,town,warehouse);
        // A chosen stand stays the destination until pickup, rather than restarting the warehouse trip each update.
        if(repairItem.isEmpty() && repairStand!=null) {
            ArmorStand stand=repairStandEntity(level,town);
            ItemStack selected=stand==null || repairSlot==null ? ItemStack.EMPTY : stand.getItemBySlot(repairSlot);
            if(stand==null || !GuardEquipment.worn(selected) || !BlacksmithRepair.supplied(selected,storage)) {
                repairStand=null; repairSlot=null; nextSmithAt=level.getGameTime()+40; return;
            }
            if(!standNear(stand)) {
                if(approachStand(level,stand,"Collecting worn armor for repair")) return;
                repairStand=null; repairSlot=null; nextSmithAt=level.getGameTime()+100; return;
            }
            getNavigation().stop(); repairItem=selected.split(1);
            stand.setItemSlot(repairSlot,selected.isEmpty() ? ItemStack.EMPTY : selected);
        }
        boolean supplied=!repairItem.isEmpty() && BlacksmithRepair.supplied(repairItem,List.of(cargo));
        if(repairItem.isEmpty() || !supplied || cargo.needsDelivery()) {
            if(!visitWarehouse(level,town,StructureRole.BLACKSMITH)) return;
            storage=SettlementService.storageAt(level,town,warehouse);
            if(repairItem.isEmpty()) {
                List<Container> stock=storage;
                repairItem=InventoryOps.takeBest(storage,s -> BlacksmithRepair.supplied(s,stock),s -> s.getDamageValue()/(double)s.getMaxDamage());
                repairStand=null; repairSlot=null;
                if(repairItem.isEmpty()) {
                    // Guard racks hold retired armor. Only pieces below 25% are borrowed, leaving usable shared sets for the next shift.
                    for(Station guard:town.stations) if(guard.role()==StructureRole.GUARD && SettlementService.active(level,guard))
                        for(ArmorStand stand:GuardService.stands(level,town,guard)) {
                            if(ignoredStands.getOrDefault(stand.getUUID(),0L)>level.getGameTime()) continue;
                            for(EquipmentSlot slot:GuardEquipment.ARMOR) if(GuardEquipment.worn(stand.getItemBySlot(slot))
                                    && BlacksmithRepair.supplied(stand.getItemBySlot(slot),storage)) {
                                repairStand=stand.getUUID(); repairSlot=slot;
                                activity="Collecting retired guard armor"; return;
                            }
                        }
                    activity="Waiting for damaged equipment and matching repair materials in the warehouse";
                    nextSmithAt=level.getGameTime()+100; return;
                }
            }
            int needed=BlacksmithRepair.materialsNeeded(repairItem)-InventoryOps.count(List.of(cargo),s -> BlacksmithRepair.material(repairItem,s));
            for(int count=0;count<needed;count++) {
                ItemStack material=InventoryOps.takeOne(storage,s -> BlacksmithRepair.material(repairItem,s));
                if(material.isEmpty()) break;
                cargo.offer(material);
            }
            if(!BlacksmithRepair.supplied(repairItem,List.of(cargo))) { activity="Waiting for this item's matching repair material"; nextSmithAt=level.getGameTime()+100; return; }
        }
        if(!canUse(level,repairAnvil)) { activity="Carrying equipment to the anvil"; walk(repairAnvil); return; }
        getNavigation().stop(); activity="Repairing equipment at the anvil"; workProgress+=10;
        if(workProgress>=BlacksmithRepair.WORK_TICKS) {
            if(BlacksmithRepair.repair(repairItem,cargo)) { swing(InteractionHand.MAIN_HAND); playSound(SoundEvents.ANVIL_USE,0.4F,1.0F); }
            workProgress=0;
        }
        if(!BlacksmithRepair.damaged(repairItem)
                || !BlacksmithRepair.supplied(repairItem,List.of(cargo)) && !BlacksmithRepair.supplied(repairItem,storage)) {
            repairDelivery=true; finishRepair(level,town);
        }
    }
    private int lapisCarried() { return InventoryOps.count(List.of(cargo),Enchanting::lapis); }
    /**
     * Enchanters take one unenchanted item at a time, from their own barrels first and then the warehouse, armor and
     * weapons before tools and books, and work it at the enchanting table for minutes before lapis seals the enchantment.
     */
    private void enchanter(ServerLevel level,Settlement town,Station station) {
        eatFrom(List.of(cargo));
        if(!enchantItem.isEmpty() && enchantDone) { deliverEnchanted(level,town,station); return; }
        if(level.getGameTime()<nextEnchantAt) return;
        unenchantable.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        List<BlockPos> tables=SettlementService.enchantingTables(level,town,station);
        if(enchantTable==null || !tables.contains(enchantTable)) {
            enchantTable=tables.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(blockPosition())))
                    .filter(p -> handNear(p) || canReach(p)).findFirst().orElse(null);
            if(enchantTable==null) {
                if(reachBudget.deferred()) { activity="Looking for a reachable enchanting table"; return; }
                activity=tables.isEmpty() ? "Needs an enchanting table within "+station.radius()+" blocks of the Enchanter Station" : "Cannot reach the enchanting table";
                nextEnchantAt=level.getGameTime()+100; return;
            }
            pathTicks=0;
        }
        int cap=Config.ENCHANTER_MAX_LEVEL.get();
        if((enchantItem.isEmpty() || lapisCarried()<Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap))
                && !gatherForEnchanting(level,town,station,cap)) return;
        String name=enchantItem.getHoverName().getString();
        if(!canUse(level,enchantTable)) {
            activity="Carrying "+name+" to the enchanting table"; pathTicks+=10;
            if(!walk(enchantTable) && onGround() || pathTicks>1200) {
                enchantTable=null; pathTicks=0;
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level);
            }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        getLookControl().setLookAt(enchantTable.getX()+0.5,enchantTable.getY()+0.75,enchantTable.getZ()+0.5);
        if(enchantLevel<=0) enchantLevel=Enchanting.level(getRandom(),Enchanting.power(level,enchantTable),enchantItem,cap);
        if(enchantLevel<=0) {
            unenchantable.put(enchantItem.getItem(),level.getGameTime()+12000);
            enchantDone=true; activity="Nothing can enchant "+name; return;
        }
        int duration=Enchanting.ticks(enchantItem,Config.ENCHANT_MINUTES.get());
        enchantTicks=Math.min(duration,enchantTicks+10);
        if(enchantTicks%40==0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,enchantTable.getX()+0.5,enchantTable.getY()+1.3,enchantTable.getZ()+0.5,6,0.5,0.3,0.5,0.6);
        if(enchantTicks<duration) {
            int minutes=(duration-enchantTicks+Enchanting.TICKS_PER_MINUTE-1)/Enchanting.TICKS_PER_MINUTE;
            activity="Enchanting "+name+" at level "+enchantLevel+": "+enchantTicks*100/duration+"% done, about "+minutes+" min left";
            return;
        }
        int lapis=Enchanting.lapisCost(enchantLevel);
        if(lapisCarried()<lapis) { activity="Needs "+lapis+" lapis lazuli to finish "+name; return; }
        ItemStack result=Enchanting.enchant(level.registryAccess(),getRandom(),enchantItem,enchantLevel);
        if(result.isEmpty()) {
            unenchantable.put(enchantItem.getItem(),level.getGameTime()+12000);
            enchantDone=true; activity="No enchantment fits "+name; return;
        }
        for(int spent=0;spent<lapis;spent++) InventoryOps.takeOne(List.of(cargo),Enchanting::lapis);
        enchantItem=result; enchantDone=true;
        swing(InteractionHand.MAIN_HAND);
        playSound(SoundEvents.ENCHANTMENT_TABLE_USE,1.0F,1.0F);
        activity="Enchanted "+result.getHoverName().getString();
    }
    /** Collect an item to enchant and lapis for it: from this station's barrels when they hold what is missing, else from the warehouse. */
    private boolean gatherForEnchanting(ServerLevel level,Settlement town,Station station,int cap) {
        Predicate<ItemStack> skipped=stack -> unenchantable.containsKey(stack.getItem());
        int needed=Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap);
        boolean wantItem=enchantItem.isEmpty(),wantLapis=lapisCarried()<needed;
        BlockPos barrel=nearestBarrel(SettlementService.jobBarrels(level,town,station));
        List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
        BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
        List<Container> stored=warehouse==null ? List.of() : SettlementService.storageAt(level,town,warehouse);
        boolean itemLocal=Enchanting.waiting(local,skipped),itemStored=Enchanting.waiting(stored,skipped);
        if(wantItem && !itemLocal && !itemStored) {
            activity="Nothing to enchant: put unenchanted gear or books in the warehouse or this station's barrel";
            idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return false;
        }
        int lapisLocal=InventoryOps.count(local,Enchanting::lapis),lapisStored=InventoryOps.count(stored,Enchanting::lapis);
        if(wantLapis && lapisCarried()+lapisLocal+lapisStored<needed) {
            activity="Needs "+needed+" lapis lazuli in the warehouse or this station's barrel";
            nextEnchantAt=level.getGameTime()+200; return false;
        }
        boolean useBarrel=barrel!=null && (wantItem && itemLocal || wantLapis && lapisLocal>0);
        BlockPos depot=useBarrel ? barrel : warehouse;
        List<Container> source=useBarrel ? local : stored;
        if(depot==null) { activity="Needs a loaded warehouse"; nextEnchantAt=level.getGameTime()+200; return false; }
        if(!visitStorage(level,town,StructureRole.ENCHANTER,depot,source,!useBarrel)) return false;
        if(wantItem) {
            ItemStack next=Enchanting.takeNext(source,skipped);
            if(!next.isEmpty()) { enchantItem=next; enchantTicks=0; enchantLevel=0; enchantDone=false; }
        }
        for(int count=lapisCarried();count<Enchanting.LAPIS_CARRY;count++) {
            ItemStack lapis=InventoryOps.takeOne(source,Enchanting::lapis);
            if(lapis.isEmpty()) break;
            cargo.offer(lapis);
        }
        return !enchantItem.isEmpty() && lapisCarried()>=Enchanting.lapisCost(enchantLevel>0 ? enchantLevel : cap);
    }
    /** Bring a finished item to this station's barrels when goods may stay there (see {@link #dropOff}), else to the warehouse. */
    private void deliverEnchanted(ServerLevel level,Settlement town,Station station) {
        String name=enchantItem.getHoverName().getString();
        BlockPos barrel=nearestBarrel(SettlementService.jobBarrels(level,town,station));
        List<Container> local=barrel==null ? List.of() : SettlementService.jobStorage(level,town,station);
        boolean toBarrel=barrel!=null && dropOff(level,town,local);
        BlockPos depot=toBarrel ? barrel : SettlementService.warehouse(level,town,blockPosition());
        if(depot==null) { activity="Holding the finished "+name+": needs a warehouse, or a barrel by the station"; nextEnchantAt=level.getGameTime()+100; return; }
        if(!canUse(level,depot)) {
            activity="Delivering the finished "+name; pathTicks+=10;
            if(!walk(depot) && onGround() || pathTicks>1200) {
                if(toBarrel && failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(barrel,level.getGameTime()+1200);
                pathTicks=0; nextEnchantAt=level.getGameTime()+100;
            }
            return;
        }
        getNavigation().stop(); pathTicks=0;
        ItemStack rest=enchantItem;
        for(Container container:toBarrel ? local : SettlementService.storageAt(level,town,depot)) rest=InventoryOps.insert(container,rest);
        enchantItem=rest;
        if(!rest.isEmpty()) { activity="Storage is full; holding the finished "+name; nextEnchantAt=level.getGameTime()+100; return; }
        enchantTicks=0; enchantLevel=0; enchantDone=false;
        swing(InteractionHand.MAIN_HAND);
        activity="Delivered the finished "+name;
    }
    /** The item an enchanter is working on, or empty. */
    public ItemStack enchanting() { return enchantItem; }
    /** The level rolled for that item, or zero before it reaches the table. */
    public int enchantLevel() { return enchantLevel; }
    /** Work done on that item, from 0 to 1. */
    public float enchantProgress() {
        if(enchantItem.isEmpty()) return 0F;
        return enchantDone ? 1F : enchantTicks/(float)Math.max(1,Enchanting.ticks(enchantItem,Config.ENCHANT_MINUTES.get()));
    }
    /** Station role this citizen works, or "none". */
    public String job() {
        if(!(level() instanceof ServerLevel server) || workplace==null || town(server)==null) return "none";
        Station station=town(server).station(workplace);
        return station==null ? "none" : station.role().id();
    }
    public int tradeCargoCount() { return tradeShipment.items().stream().mapToInt(ItemStack::getCount).sum(); }
    /** Town positions remain known across chunk boundaries; the carrier physically visits each storage station. */
    private BlockPos tradeWarehouse(Settlement town) {
        return town.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE)
                .min(Comparator.comparingDouble(s -> s.position().distSqr(blockPosition()))).map(Station::position).orElse(null);
    }
    private void tradeNote(Settlement home,String message) { activity=message; home.trading.status=message; }
    private boolean tradeArrive(ServerLevel level,Settlement home,BlockPos destination) {
        if(canUse(level,destination)) { getNavigation().stop(); tradeNavigation.reset(); return true; }
        lastWalkTick=tickCount;
        if(!tradeNavigation.walk(level,this,destination,0.75))
            tradeNote(home,"Trade route blocked: clear a walkable path or build a road");
        return false;
    }
    /** A saved itinerary, with isolated goods that neither meals nor another job can consume. */
    private void trader(ServerLevel level,Settlement home,Station checkpoint) {
        if(!TradeChunks.keep(level,home,blockPosition())) { tradeNote(home,"Waiting for a server trader slot"); return; }
        boolean changed=!getUUID().equals(home.trading.runner) || home.trading.runnerPos==null
                || (Math.floorDiv(home.trading.runnerPos.getX(),16)!=Math.floorDiv(blockPosition().getX(),16) || Math.floorDiv(home.trading.runnerPos.getZ(),16)!=Math.floorDiv(blockPosition().getZ(),16));
        home.trading.runner=getUUID(); home.trading.runnerPos=blockPosition().immutable();
        if(changed) SettlementData.get(level).setDirty();
        if(checkpoint!=null) SettlementService.workers(level).claim(checkpoint.position(),getUUID(),level.getGameTime(),200,1);
        if(cargo.isOpen()) { getNavigation().stop(); tradeNote(home,"Waiting while my inventory is open"); return; }
        eatFrom(List.of(cargo));
        if(level.getGameTime()<nextTradeAt) return;
        Settlement destination=tradeShipment.destination==null ? null : SettlementData.get(level).byId(tradeShipment.destination);
        Station arrival=TradeRoutes.checkpoint(destination);
        boolean broken=checkpoint==null || level.hasChunkAt(checkpoint.position()) && !SettlementService.active(level,checkpoint)
                || !TradeRoutes.agreed(home,destination) || arrival==null
                || level.hasChunkAt(arrival.position()) && !SettlementService.active(level,arrival);
        if(tradeShipment.travelling() && !List.of("return","home").contains(tradeShipment.stage) && broken) {
            tradeShipment.stage="return"; tradeNavigation.reset();
        }
        switch(tradeShipment.stage) {
            case "idle" -> {
                destination=TradeRoutes.partner(level,home);
                if(destination==null || !TradeRoutes.canDepart(level,home)) { tradeNote(home,"Waiting for a connected, unpaused route"); releaseWork(level); nextTradeAt=level.getGameTime()+100; return; }
                BlockPos warehouse=tradeWarehouse(home);
                if(warehouse==null) { tradeNote(home,"Needs a warehouse with storage"); nextTradeAt=level.getGameTime()+100; return; }
                tradeNote(home,"Collecting exports from the warehouse");
                if(!tradeArrive(level,home,warehouse)) return;
                eatFrom(SettlementService.storageAt(level,home,warehouse));
                var stock=SettlementService.storageAt(level,home,warehouse);
                int rations=InventoryOps.count(List.of(cargo),this::food);
                for(int n=rations;n<8;n++) {
                    ItemStack ration=InventoryOps.takeOne(stock,this::food);
                    if(ration.isEmpty()) break;
                    cargo.offer(ration);
                }
                int moved=TradeGoods.load(SettlementService.storageAt(level,home,warehouse),home.trading.exports,tradeShipment);
                if(moved==0) { tradeNote(home,"Waiting for warehouse goods above the export reserves"); nextTradeAt=level.getGameTime()+200; return; }
                tradeShipment.destination=destination.id; tradeShipment.stage="checkpoint"; tradeNavigation.reset();
            }
            case "checkpoint" -> {
                tradeNote(home,"Taking "+tradeCargoCount()+" items to the home checkpoint");
                if(tradeArrive(level,home,checkpoint.position())) { tradeShipment.stage="outbound"; tradeNavigation.reset(); }
            }
            case "outbound" -> {
                tradeNote(home,"Travelling to "+destination.name+" with "+tradeCargoCount()+" items");
                if(tradeArrive(level,home,arrival.position())) { tradeShipment.stage="deliver"; tradeNavigation.reset(); }
            }
            case "deliver" -> {
                BlockPos warehouse=tradeWarehouse(destination);
                if(warehouse==null) { tradeNote(home,"Destination needs warehouse storage; cargo retained"); nextTradeAt=level.getGameTime()+100; return; }
                tradeNote(home,"Delivering to "+destination.name+"'s warehouse");
                if(!tradeArrive(level,home,warehouse)) return;
                int moved=TradeGoods.unload(tradeShipment,SettlementService.storageAt(level,destination,warehouse));
                home.trading.delivered+=moved;
                if(moved>0 && destination.trading.npc) destination.trading.relations.merge(home.owner,moved,(a,b) -> Math.min(1000,a+b));
                if(moved>0) SettlementData.get(level).setDirty();
                if(tradeShipment.isEmpty()) { tradeShipment.stage="return"; tradeNavigation.reset(); }
                else { tradeNote(home,"Destination storage is full; keeping "+tradeCargoCount()+" items"); nextTradeAt=level.getGameTime()+100; }
            }
            case "return" -> {
                tradeNote(home,"Returning to "+home.name+(tradeShipment.isEmpty() ? "" : " with undelivered goods"));
                BlockPos rally=checkpoint==null ? home.center : checkpoint.position();
                if(!handNear(rally) && !tradeArrive(level,home,rally)) return;
                tradeShipment.stage="home"; tradeNavigation.reset();
            }
            case "home" -> {
                if(!tradeShipment.isEmpty()) {
                    BlockPos warehouse=tradeWarehouse(home);
                    if(warehouse==null) { tradeNote(home,"Home needs storage for the returned goods"); return; }
                    if(!tradeArrive(level,home,warehouse)) return;
                    TradeGoods.unload(tradeShipment,SettlementService.storageAt(level,home,warehouse));
                    if(!tradeShipment.isEmpty()) { tradeNote(home,"Home storage is full; returned goods are safe in the trade load"); nextTradeAt=level.getGameTime()+100; return; }
                }
                tradeShipment.finish();
                if(!TradeRoutes.canDepart(level,home)) { home.trading.runner=null; home.trading.runnerPos=null; }
                tradeNavigation.reset(); nextTradeAt=level.getGameTime()+200; SettlementData.get(level).setDirty();
                tradeNote(home,"Returned; preparing the next trip");
            }
            default -> tradeShipment.stage="return";
        }
    }
    private void work(ServerLevel level) {
        reachBudget.reset();
        failedTargets.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        Settlement town=town(level);
        if(town==null) { activity="Settlement unavailable"; return; }
        // The checkpoint may be unloaded behind the carrier. Its saved itinerary owns the job until it returns.
        if(tradeShipment.travelling()) { leaveBed(); trader(level,town,TradeRoutes.checkpoint(town)); return; }
        if(!isGuard() && guardVacancy(level,town)) releaseWork(level);
        if(isGuard()) {
            Station empty=GuardService.uncovered(level,town);
            if(empty!=null && !empty.position().equals(workplace)
                    && SettlementService.workers(level).count(workplace,level.getGameTime())>1
                    && SettlementService.workers(level).claim(empty.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,empty))) {
                releaseWork(level); workplace=empty.position();
            }
        }
        var book=SettlementService.reservations(level);
        Station station=workplace==null ? null : town.station(workplace);
        if(station==null || !SettlementService.active(level,station)
                || !SettlementService.workers(level).claim(workplace,getUUID(),level.getGameTime(),200,SettlementService.workerLimit(town,station))) {
            releaseWork(level);
            if(searchDelay>0) { searchDelay-=10; return; }
            station=chooseJob(level,town);
            if(station==null) {
                // Stations that just reported no work are retried after a short pause rather than counted as full.
                activity=idleStations.isEmpty() ? "Waiting for a free crew slot" : "Open stations have no work I can reach right now; checking again soon";
                searchDelay=40; return;
            }
            workplace=station.position();
        }
        if(cargo.isOpen() && !(station.role()==StructureRole.GUARD && DefenseService.alarmed(town))) {
            getNavigation().stop(); return;
        }
        StructureRole role=station.role();
        // Armor outside the guard job, or a tool or weapon this job does not use, also counts as a change (e.g. after a reload).
        boolean wrongKit=role!=StructureRole.GUARD && Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> !getItemBySlot(slot).isEmpty())
                || gear(getMainHandItem()) && !retainSupply(getMainHandItem())
                || role!=StructureRole.BLACKSMITH && !repairItem.isEmpty()
                || role!=StructureRole.ENCHANTER && !enchantItem.isEmpty();
        if(lastRole!=null && lastRole!=role || wrongKit) changeRole(role);
        lastRole=role;
        // A guard drafted during an alarm defends first and returns old gear afterwards.
        if(returningGear && !(role==StructureRole.GUARD && DefenseService.alarmed(town)) && !returnGear(level,town)) return;
        if(station.role()==StructureRole.GUARD) { guard(level,town,station); return; }
        if(station.role()==StructureRole.BLACKSMITH) { blacksmith(level,town,station); return; }
        if(station.role()==StructureRole.COURIER) { courier(level,town,station); return; }
        if(station.role()==StructureRole.TRADER) { trader(level,town,station); return; }
        if(station.role()==StructureRole.ENCHANTER) { enchanter(level,town,station); return; }
        useLocalSupplies(station.role());
        if(getHealth()<getMaxHealth() && wantsMeal() && InventoryOps.count(List.of(cargo),this::food)==0
                && level.getGameTime()>=nextFoodTripAt) {
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            if(warehouse!=null && (handNear(warehouse) || canReach(warehouse)) && !visitWarehouse(level,town,station.role()) && !canUse(level,warehouse)) return;
            nextFoodTripAt=level.getGameTime()+200;
        }
        // Deliver only full loads; use personal supplies before returning for replacements.
        if(deliverCargo() || mealTicks<=0 && station.role()!=StructureRole.FARM && station.role()!=StructureRole.COOK && station.role()!=StructureRole.CRAFTSMAN) {
            if(!visitDepot(level,town,station)) return;
        }
        if(station.role()==StructureRole.CRAFTSMAN) { craftsman(level,town,station); return; }
        if(station.role().processes()) { process(level,town,station); return; }
        if(target==null) {
            if(searchDelay>0) { searchDelay-=10; return; }
            if(!station.role().excavates() && !handNear(station.position())) {
                activity="Returning to the "+station.role().id()+" worksite"; pathTicks+=10;
                if(!walk(station.position()) || pathTicks>1200) {
                    idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level);
                }
                return;
            }
            target=findTarget(level,town,station);
            if(target==null) {
                if(reachBudget.deferred()) { activity="Checking accessible work nearby"; return; }
                if(cargo.hasDeliverable(this::retainSupply,this::food)) { visitDepot(level,town,station); return; }
                activity=idleReason(level,town,station);
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); searchDelay=20; return;
            }
            pathTicks=0;
        }
        if(!validTarget(level,town,station) || !book.claimAll(targetLease==null ? List.of(target) : List.of(target,targetLease),getUUID(),level.getGameTime(),200)) {
            cancelTarget(level,false); return;
        }
        useLocalSupplies(station.role());
        if(!properTool(station.role()) || needsSupply()) { if(!visitDepot(level,town,station)) return; }
        if(action==Action.FELL) {
            BlockPos leaf=blockingLeaf(level,town);
            if(leaf!=null) {
                getNavigation().stop(); activity="Clearing natural leaves to reach the trunk";
                if(!leaf.equals(clearingLeaf)) { clearingLeaf=leaf; workProgress=0; }
                workProgress+=10;
                if(workProgress>=20) {
                    swing(InteractionHand.MAIN_HAND);
                    var drops=ForestryService.clearLeaf(level,town,station,forestTask.tree(),leaf,this);
                    if(drops==null) { cancelTarget(level,true); return; }
                    storeDrops(level,drops); workProgress=0; clearingLeaf=null;
                }
                return;
            }
        }
        clearingLeaf=null;
        BlockPos approach=excavation==null ? (workStand==null ? target : workStand) : excavation.stand();
        BlockPos visibleAt=action==Action.PLANT ? target.below() : excavation!=null && excavation.remote() ? station.position()
                : target;
        BlockPos touch=excavation!=null && excavation.remote() ? station.position() : target;
        if(!handNear(touch) || !canUse(level,visibleAt)) {
            activity=action==Action.PLANT ? "Walking to plant saplings" : "Walking to "+station.role().id()+" work"; pathTicks+=10;
            // Standing beside the work without a clear view will not fix itself; give up sooner than a long walk.
            if(!walk(approach) || pathTicks>(handNear(touch) ? 100 : 1200)) {
                if(action==Action.EXCAVATE && excavation.quarry() && !excavation.remote()) {
                    // No way into the pit from here: keep the quarry moving from its control block instead.
                    SettlementService.reservations(level).release(excavation.lease(),getUUID());
                    excavation=excavation.fromControlBlock(station.position()); targetLease=excavation.lease(); pathTicks=0;
                } else cancelTarget(level,true);
            }
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(visibleAt.getX()+0.5,visibleAt.getY()+0.5,visibleAt.getZ()+0.5);
        activity=switch(action) {
            case FELL -> "Felling a whole natural tree";
            case PLANT -> "Planting saplings";
            case SUPPORT -> excavation.quarry() ? "Rebuilding a quarry step" : "Supporting the tunnel floor";
            case EXCAVATE -> excavation.remote() ? "Operating the quarry from its control block" : excavation.quarry() ? "Quarrying" : "Excavating a tunnel";
            case HARVEST -> "Harvesting crops";
            case CAVE -> "Mining accessible cave ore";
            case VEIN -> "Mining the "+OreVeins.name(level.getBlockState(target))+" vein";
        };
        if(action==Action.VEIN && level.getGameTime()<OreVeins.readyAt(level,target)) {
            workProgress=0;
            activity="Waiting for the "+OreVeins.name(level.getBlockState(target))+" vein to replenish ("
                    +(OreVeins.readyAt(level,target)-level.getGameTime()+19)/20+"s)";
            return;
        }
        workProgress+=10;
        // Stone yields to a pickaxe in about a second; harder blocks and weaker tools take longer.
        int required=action==Action.EXCAVATE || action==Action.CAVE || action==Action.VEIN ? ExcavationService.breakTicks(level,target,getMainHandItem())
                : action==Action.SUPPORT ? 20 : Config.WORK_TICKS.get();
        if(workProgress>=required) harvest(level,town,station);
    }
    private final class WorkGoal extends Goal {
        WorkGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(!(level() instanceof ServerLevel l) || town(l)==null) return false;
            // Civilians stop work during an alarm; a vacant guard post still draws a volunteer.
            return tradeShipment.travelling() || isGuard() || guardVacancy(l,town(l)) || !night(l) && !DefenseService.alarmed(town(l));
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if(level() instanceof ServerLevel l && WorkCadence.due(l.getGameTime(),getId(),10)) work(l); }
        @Override public void stop() { if(level() instanceof ServerLevel l) releaseWork(l); }
    }
    /** "Duck and cover": during a daytime alarm civilians wait at the nearest housing until the all-clear. Night alarms find them in bed. */
    private final class ShelterGoal extends Goal {
        private BlockPos refuge;
        ShelterGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(!(level() instanceof ServerLevel l)) return false;
            Settlement town=town(l);
            return town!=null && !tradeShipment.travelling() && !isGuard() && DefenseService.alarmed(town) && !night(l) && !guardVacancy(l,town);
        }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { refuge=null; }
        @Override public void tick() {
            if(!(level() instanceof ServerLevel level) || !WorkCadence.due(level.getGameTime(),getId(),20)) return;
            Settlement town=town(level); if(town==null) return;
            if(refuge==null) refuge=DefenseService.refuge(level,town,blockPosition());
            if(distanceToSqr(Vec3.atCenterOf(refuge))<=9.0 || !walk(refuge,0.9)) {
                getNavigation().stop(); activity="Taking cover until the all-clear"; return;
            }
            activity="Running for cover";
        }
        @Override public void stop() { refuge=null; getNavigation().stop(); }
    }
    private final class RestGoal extends Goal {
        RestGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return !tradeShipment.travelling() && level() instanceof ServerLevel l && town(l)!=null && night(l) && !isGuard() && !guardVacancy(l,town(l)); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(!(level() instanceof ServerLevel level) || !WorkCadence.due(level.getGameTime(),getId(),20)) return;
            Settlement town=town(level); if(town==null) return;
            rest(level,town);
        }
        @Override public void stop() { leaveBed(); }
    }
    private void rest(ServerLevel level,Settlement town) {
        if(!cargo.isOpen()) eatFrom(List.of(cargo));
        var beds=SettlementService.housingBeds(level,town);
        var book=SettlementService.reservations(level);
        if(sleepingBed!=null && (!beds.contains(sleepingBed) || !book.claim(sleepingBed,getUUID(),level.getGameTime(),200))) leaveBed();
        if(sleepingBed==null) for(BlockPos bed:beds) {
            if(!level.getBlockState(bed).getValue(BedBlock.OCCUPIED) && book.claim(bed,getUUID(),level.getGameTime(),200)) { sleepingBed=bed; break; }
        }
        if(sleepingBed==null) { activity="Needs a loaded housing bed"; return; }
        activity="Resting";
        if(distanceToSqr(Vec3.atCenterOf(sleepingBed))<=4) { getNavigation().stop(); if(!isSleeping()) startSleeping(sleepingBed); }
        else walk(sleepingBed);
    }
    private void leaveBed() {
        if(isSleeping()) stopSleeping();
        if(level() instanceof ServerLevel l && sleepingBed!=null) SettlementService.reservations(l).release(sleepingBed,getUUID());
        sleepingBed=null;
    }
    /** Called synchronously when the bell rings, including for guards whose sleeping AI is paused. */
    public void wakeForAlarm() { leaveBed(); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if(settlementId!=null) output.putString("wwmc_settlement",settlementId.toString());
        if(workplace!=null) output.store("wwmc_workplace",BlockPos.CODEC,workplace);
        output.putInt("wwmc_meal_ticks",mealTicks);
        output.putInt("wwmc_healing_ticks",healingTicks);
        output.store("wwmc_repair_item",ItemStack.OPTIONAL_CODEC,repairItem);
        output.putBoolean("wwmc_repair_delivery",repairDelivery);
        output.store("wwmc_enchant_item",ItemStack.OPTIONAL_CODEC,enchantItem);
        output.putInt("wwmc_enchant_ticks",enchantTicks);
        output.putInt("wwmc_enchant_level",enchantLevel);
        output.putBoolean("wwmc_enchant_done",enchantDone);
        if(repairStand!=null) output.putString("wwmc_repair_stand",repairStand.toString());
        if(repairSlot!=null) output.putString("wwmc_repair_slot",repairSlot.name());
        output.store("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.contents());
        output.store("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.pendingItems());
        output.store("wwmc_trade_shipment",TradeShipment.CODEC,tradeShipment);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String id=input.getStringOr("wwmc_settlement","");
        try { settlementId=id.isEmpty() ? null : UUID.fromString(id); } catch(IllegalArgumentException e) { settlementId=null; }
        workplace=input.read("wwmc_workplace",BlockPos.CODEC).orElse(null);
        mealTicks=input.getIntOr("wwmc_meal_ticks",2400);
        healingTicks=Math.clamp(input.getIntOr("wwmc_healing_ticks",0),0,FoodHealing.COOLDOWN);
        repairItem=input.read("wwmc_repair_item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        repairDelivery=input.getBooleanOr("wwmc_repair_delivery",false);
        enchantItem=input.read("wwmc_enchant_item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        enchantTicks=Math.max(0,input.getIntOr("wwmc_enchant_ticks",0));
        enchantLevel=Math.clamp(input.getIntOr("wwmc_enchant_level",0),0,30);
        enchantDone=input.getBooleanOr("wwmc_enchant_done",false);
        try { repairStand=UUID.fromString(input.getStringOr("wwmc_repair_stand","")); } catch(IllegalArgumentException e) { repairStand=null; }
        try { repairSlot=EquipmentSlot.valueOf(input.getStringOr("wwmc_repair_slot","")); } catch(IllegalArgumentException e) { repairSlot=null; }
        var stacks=input.read("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        cargo.restore(stacks,input.read("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        tradeShipment=input.read("wwmc_trade_shipment",TradeShipment.CODEC).orElseGet(TradeShipment::new);
    }
    @Override public void die(DamageSource source) {
        if(level() instanceof ServerLevel level) {
            Settlement home=town(level);
            if(home!=null && source.getEntity() instanceof Player attacker) TradeRoutes.attacked(home,attacker.getUUID(),SettlementData.get(level).settlements);
            if(home!=null && getUUID().equals(home.trading.runner)) { home.trading.runner=null; home.trading.runnerPos=null; SettlementData.get(level).setDirty(); }
            for(ItemStack stack:tradeShipment.items()) if(!stack.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),stack);
            tradeShipment.clearContent();
            Containers.dropItemStack(level,getX(),getY(),getZ(),repairItem); repairItem=ItemStack.EMPTY;
            Containers.dropItemStack(level,getX(),getY(),getZ(),enchantItem); enchantItem=ItemStack.EMPTY;
            Settlement town=town(level);
            if(town!=null) { town.citizens.remove(getUUID()); town.citizenNames.remove(getUUID()); SettlementData.get(level).setDirty(); }
            releaseWork(level);
            Containers.dropItemStack(level,getX(),getY(),getZ(),getOffhandItem()); setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            for(int i=0;i<cargo.getContainerSize();i++) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),cargo.removeItemNoUpdate(i));
            }
            for(ItemStack stack:cargo.pendingItems()) Containers.dropItemStack(level,getX(),getY(),getZ(),stack);
            cargo.restore(List.of(),List.of());
            for(EquipmentSlot slot:GuardEquipment.ARMOR) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),getItemBySlot(slot)); setItemSlot(slot,ItemStack.EMPTY);
            }
            Containers.dropItemStack(level,getX(),getY(),getZ(),getMainHandItem());
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        super.die(source);
    }
}

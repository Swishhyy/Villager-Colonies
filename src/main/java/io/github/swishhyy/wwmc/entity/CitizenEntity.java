package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.CitizenNames;
import io.github.swishhyy.wwmc.core.WorkCadence;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Villager-styled citizen with visible equipment and an independent station-driven work routine. */
public final class CitizenEntity extends Villager {
    private enum Action { HARVEST,FELL,PLANT,EXCAVATE,SUPPORT,CAVE }
    private Action action=Action.HARVEST;
    private ForestryService.Task forestTask;
    private ExcavationService.Ticket excavation;
    private BlockPos targetLease;
    /** Arrows a guard with a bow keeps in their bag. */
    private static final int ARROW_STOCK=32;
    private int minimumAxeDurability=1,guardAttackTicks,patrolTicks,gearTicks,patrolVisits,scavengeTicks,armoryTicks;
    private boolean armoryStocked;
    /** Ticks a craftsman works one batch at the bench. */
    private static final int CRAFT_TICKS=40;
    private static final int MAX_FAILED_TARGETS=2048;
    private Crafting.Recipe order;
    private BlockPos patrolTarget,activePost;
    private BlockPos processor;
    private boolean processingDelivery,processingSupplied;
    private long nextProcessingAt,guardSupplyAt;
    private int processingIdle;
    private final Map<BlockPos,Long> idleStations=new HashMap<>();
    private UUID settlementId;
    private BlockPos workplace, target, sleepingBed;
    private final CitizenInventory cargo=new CitizenInventory(this::canOpenInventory);
    private final Map<BlockPos,Long> failedTargets=new HashMap<>();
    private int searchDelay, workProgress, pathTicks, mealTicks=2400;
    private final WorkCadence.ReachBudget reachBudget=new WorkCadence.ReachBudget();
    private long nextPathAt;
    private BlockPos pathDestination;
    private String activity="Waiting for a job station";
    public CitizenEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level); setPersistenceRequired(); setCanPickUpLoot(false);
        for(EquipmentSlot slot:EquipmentSlot.values()) setDropChance(slot,0);
    }
    public void join(UUID id) { settlementId=id; mealTicks=Config.RATION_TICKS.get(); }
    private Settlement town(ServerLevel level) { return settlementId==null ? null : SettlementData.get(level).byId(settlementId); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
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
            SettlementService.workers(server).claim(workplace,getUUID(),server.getGameTime(),200,SettlementService.workerLimit(station));
            if(GuardService.onDuty(server,town,workplace,getUUID()) || DefenseService.bellRun(town,getUUID())!=null) wakeForAlarm();
            else if(sleepingBed!=null) {
                if(SettlementService.housingBeds(server,town).contains(sleepingBed))
                    SettlementService.reservations(server).claim(sleepingBed,getUUID(),server.getGameTime(),200);
                else leaveBed();
            }
        }
        super.tick();
        if(level() instanceof ServerLevel server) {
            if(mealTicks>0) mealTicks--;
            if(guardAttackTicks>0) guardAttackTicks--;
            if(WorkCadence.due(server.getGameTime(),getId(),20)) {
                cargo.flush();
                Settlement town=town(server);
                if(town!=null) {
                    if(getCustomName()==null || CitizenNames.numbered(getCustomName().getString()))
                        setCustomName(Component.literal(SettlementService.citizenName(server,town,getUUID())));
                    String name=getCustomName().getString();
                    if(!name.equals(town.citizenNames.put(getUUID(),name))) SettlementData.get(server).setDirty();
                }
            }
        }
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level() instanceof ServerLevel server && hand==InteractionHand.MAIN_HAND) {
            Settlement town=town(server);
            if(town!=null && town.owner.equals(player.getUUID()) && player.isShiftKeyDown()) {
                releaseWork(server); searchDelay=0;
                SettlementService.tell(player,"Worker released their job and will choose an available station.");
            } else if(canOpenInventory(player) && player.getItemInHand(hand).isEmpty()) {
                player.openMenu(new SimpleMenuProvider(cargo::createMenu,getName().copy().append(" — Inventory")));
                SettlementService.tell(player,getName().getString()+": "+activity+(cargo.hasPending() ? ". Large harvest waiting for bag space" : ""));
            } else {
                SettlementService.tell(player,getName().getString()+": "+activity+
                        (workplace==null ? "" : " at "+workplace.toShortString()));
            }
        }
        return InteractionResult.SUCCESS;
    }
    private boolean canOpenInventory(Player player) {
        if(!(level() instanceof ServerLevel server) || !isAlive() || distanceToSqr(player)>64) return false;
        Settlement town=town(server); return town!=null && town.owner.equals(player.getUUID());
    }
    public boolean isGuard() {
        if(!(level() instanceof ServerLevel server) || workplace==null) return false;
        Settlement town=town(server); Station station=town==null ? null : town.station(workplace);
        return station!=null && station.role()==StructureRole.GUARD && SettlementService.active(server,station);
    }
    private boolean guardVacancy(ServerLevel level,Settlement town) {
        return town.stations.stream().anyMatch(s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s)
                && SettlementService.workers(level).count(s.position(),level.getGameTime())<SettlementService.workerLimit(s));
    }
    private boolean night(ServerLevel level) { return SettlementService.night(level); }
    private boolean alarmed() {
        if(!(level() instanceof ServerLevel server)) return false;
        Settlement town=town(server); return town!=null && DefenseService.alarmed(town);
    }
    private boolean near(BlockPos pos) { return distanceToSqr(Vec3.atCenterOf(pos))<=6.25; }
    private boolean visible(ServerLevel level,BlockPos pos) {
        return level.clip(new ClipContext(getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getBlockPos().equals(pos);
    }
    private boolean walk(BlockPos pos) { return walk(pos,0.65); }
    private boolean walk(BlockPos pos,double speed) {
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
        leaveBed();
        var book=SettlementService.reservations(level);
        if(workplace!=null) SettlementService.workers(level).release(workplace,getUUID());
        if(target!=null) book.release(target,getUUID());
        if(targetLease!=null) book.release(targetLease,getUUID());
        targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1; order=null;
        processor=null; processingDelivery=false; processingSupplied=false; processingIdle=0; nextProcessingAt=0;
        workplace=null; target=null; patrolTarget=null; activePost=null; setTarget(null); workProgress=0; pathTicks=0; getNavigation().stop();
    }
    private Station chooseJob(ServerLevel level,Settlement town) {
        var book=SettlementService.workers(level);
        idleStations.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        List<Station> jobs=new ArrayList<>(town.stations.stream()
                .filter(s -> s.role().providesWork() && SettlementService.active(level,s) && !idleStations.containsKey(s.position())
                        && (!night(level) || s.role()==StructureRole.GUARD)).toList());
        jobs.sort(Comparator.comparingInt((Station s) -> s.role()==StructureRole.GUARD ? 0 : 1)
                .thenComparingInt(s -> switch(town.priority) {
            case "food" -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.FARM || s.role()==StructureRole.COOK ? 1 : 2;
            case "materials" -> s.role()==StructureRole.GUARD ? 0 : s.role()==StructureRole.FARM || s.role()==StructureRole.COOK ? 2 : 1;
            default -> 0;
        }).thenComparingInt(s -> book.count(s.position(),level.getGameTime()))
                .thenComparingDouble(s -> distanceToSqr(Vec3.atCenterOf(s.position()))));
        for(Station station:jobs) if(book.claim(station.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(station))) return station;
        return null;
    }
    private boolean toolFits(StructureRole role,ItemStack stack) {
        if(target==null || action==Action.PLANT || action==Action.SUPPORT || role==StructureRole.FARM) return true;
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
    private boolean food(ItemStack stack) {
        var nutrition=stack.get(DataComponents.FOOD);
        return nutrition!=null && nutrition.nutrition()>0 && !stack.is(Items.ROTTEN_FLESH)
                && !stack.is(Tags.Items.FOODS_RAW_MEAT) && !stack.is(Tags.Items.FOODS_RAW_FISH)
                && !stack.is(Items.SPIDER_EYE) && !stack.is(Items.POISONOUS_POTATO) && !stack.is(Items.PUFFERFISH);
    }
    private boolean retainSupply(ItemStack stack) {
        if(level() instanceof ServerLevel level && workplace!=null && town(level)!=null) {
            Station station=town(level).station(workplace);
            if(station!=null && station.role().processes() && ProcessingService.supply(level,station.role(),stack)) return true;
        }
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || GuardWeapons.weapon(stack)
                || isGuard() && GuardWeapons.arrow(stack) || order!=null && order.uses(stack)
                || action==Action.PLANT && forestTask!=null && stack.is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(stack)
                || isGuard() && java.util.Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> GuardEquipment.armor(stack,slot));
    }
    private void useLocalSupplies(StructureRole role) {
        if(mealTicks<=0 && !InventoryOps.takeOne(List.of(cargo),this::food).isEmpty()) mealTicks=Config.RATION_TICKS.get();
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
        if(!near(warehouse) || !visible(level,warehouse)) {
            activity="Carrying supplies / returning for food or tools";
            walk(warehouse); return false;
        }
        getNavigation().stop();
        List<Container> storage=SettlementService.storageAt(level,town,warehouse);
        int[] foodReserve={role==StructureRole.COOK ? 0 : 8};
        cargo.deposit(storage,stack -> {
            if(retainSupply(stack)) return stack.getCount();
            if(food(stack)) { int keep=Math.min(foodReserve[0],stack.getCount()); foodReserve[0]-=keep; return keep; }
            return 0;
        });
        boolean keepSupply=action==Action.PLANT && forestTask!=null && getOffhandItem().is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(getOffhandItem());
        if(!keepSupply && !getOffhandItem().isEmpty()) {
            ItemStack leftover=getOffhandItem();
            for(Container container:storage) leftover=InventoryOps.insert(container,leftover);
            setItemSlot(EquipmentSlot.OFFHAND,leftover);
            if(!leftover.isEmpty()) { activity="Needs storage space for planting/building supplies"; return false; }
        }
        if(cargo.needsDelivery()) { activity="Warehouse is full; keeping supplies in my inventory"; return false; }
        if(mealTicks<=0) {
            ItemStack ration=InventoryOps.takeOne(storage,this::food);
            if(!ration.isEmpty()) mealTicks=Config.RATION_TICKS.get();
            else if(role!=StructureRole.FARM && role!=StructureRole.COOK && role!=StructureRole.GUARD) { activity="Waiting for food in the warehouse"; return false; }
        }
        int rations=0;
        for(int slot=0;slot<cargo.getContainerSize();slot++) if(food(cargo.getItem(slot))) rations+=cargo.getItem(slot).getCount();
        for(int count=rations;count<(role==StructureRole.COOK ? 0 : 8);count++) {
            ItemStack ration=InventoryOps.takeOne(storage,this::food);
            if(ration.isEmpty()) break;
            cargo.offer(ration);
        }
        if(role==StructureRole.GUARD) stockArmory(storage);
        if(!properTool(role)) {
            ItemStack held=getMainHandItem();
            for(Container container:storage) held=InventoryOps.insert(container,held);
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
                    && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && canReach(p),
                    item -> cargo.count(item)+(getOffhandItem().is(item) ? getOffhandItem().getCount() : 0));
            if(forestTask==null || !book.claim(forestTask.target(),getUUID(),level.getGameTime(),200)) return null;
            action=forestTask.planting()==null ? Action.FELL : Action.PLANT;
            minimumAxeDurability=forestTask.tree()==null ? 1 : forestTask.tree().logs().size();
            return forestTask.target();
        }
        if(station.role().excavates()) {
            ExcavationJob job=ExcavationService.job(level,town,station);
            if(station.role()==StructureRole.MINE && job!=null && getY()<=job.targetY+6) {
                BlockPos ore=CaveMining.find(level,town,blockPosition(),p -> !failedTargets.containsKey(p)
                        && !reachBudget.deferred() && book.available(p,getUUID(),level.getGameTime()) && canReach(p));
                if(ore!=null && book.claim(ore,getUUID(),level.getGameTime(),200)) { action=Action.CAVE; return ore; }
                if(reachBudget.deferred()) return null;
            }
            excavation=ExcavationService.next(level,town,station,getUUID(),blockPosition());
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
    private void cancelTarget(ServerLevel level,boolean failed) {
        if(target!=null) {
            SettlementService.reservations(level).release(target,getUUID());
            if(targetLease!=null) SettlementService.reservations(level).release(targetLease,getUUID());
            if(failed && failedTargets.size()<MAX_FAILED_TARGETS) failedTargets.put(target,level.getGameTime()+1200);
        }
        target=null; targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1;
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
        for(EquipmentSlot slot:GuardEquipment.ARMOR) if(getItemBySlot(slot).isEmpty()) {
            ItemStack armor=InventoryOps.takeOne(List.of(cargo),s -> GuardEquipment.armor(s,slot));
            if(!armor.isEmpty()) setItemSlot(slot,armor);
        }
    }
    private int arrows() { return InventoryOps.count(List.of(cargo),GuardWeapons::arrow); }
    private boolean carries(Predicate<ItemStack> kind) { return kind.test(getMainHandItem()) || InventoryOps.count(List.of(cargo),kind)>0; }
    /** Weapons a guard is still looking for: one melee weapon, one bow, and arrows for that bow. */
    private boolean needs(ItemStack stack) {
        if(GuardWeapons.melee(stack)) return !carries(GuardWeapons::melee);
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
        if(kind.test(getMainHandItem())) return true;
        ItemStack next=InventoryOps.takeBest(List.of(cargo),kind,GuardWeapons::score);
        if(next.isEmpty()) return false;
        wield(next); return true;
    }
    /** Off the firing line a guard carries their strongest melee weapon, or the bow if that is all they have. */
    private void readyMelee() {
        double held=GuardWeapons.score(getMainHandItem());
        ItemStack better=InventoryOps.takeBest(List.of(cargo),s -> GuardWeapons.score(s)>held,GuardWeapons::score);
        if(!better.isEmpty()) wield(better);
        else if(!GuardWeapons.weapon(getMainHandItem())) hold(GuardWeapons::bow);
    }
    private void stockArmory(List<Container> storage) {
        if(!carries(GuardWeapons::melee)) {
            ItemStack weapon=InventoryOps.takeBest(storage,GuardWeapons::melee,GuardWeapons::score);
            if(!weapon.isEmpty()) cargo.offer(weapon);
        }
        if(!carries(GuardWeapons::bow)) {
            ItemStack bow=InventoryOps.takeOne(storage,GuardWeapons::bow);
            if(!bow.isEmpty()) cargo.offer(bow);
        }
        while(carries(GuardWeapons::bow) && arrows()<ARROW_STOCK) {
            ItemStack arrow=InventoryOps.takeOne(storage,GuardWeapons::arrow);
            if(arrow.isEmpty()) break;
            cargo.offer(arrow);
        }
        readyMelee();
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
            boolean missing=Arrays.stream(GuardEquipment.ARMOR).anyMatch(slot -> getItemBySlot(slot).isEmpty()
                    && GuardEquipment.armor(stand.getItemBySlot(slot),slot))
                    || needs(stand.getItemBySlot(EquipmentSlot.MAINHAND)) || needs(stand.getItemBySlot(EquipmentSlot.OFFHAND));
            if(!missing) continue;
            if(distanceToSqr(stand)>6.25 || !hasLineOfSight(stand)) {
                if(!canReach(stand.blockPosition())) continue;
                activity="Collecting gear from a stand"; walk(stand.blockPosition()); gearTicks=0; return true;
            }
            for(EquipmentSlot slot:GuardEquipment.ARMOR) GuardEquipment.transfer(equipment(stand),equipment(this),slot);
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
    /** Pick up loose weapons and arrows, such as a fallen skeleton's bow, that have lain in town for a few seconds. */
    private boolean scavenge(ServerLevel level,Settlement town) {
        if(scavengeTicks>0) { scavengeTicks-=10; return false; }
        ItemEntity loot=level.getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(12,4,12),
                e -> e.isAlive() && e.getAge()>=100 && town.contains(e.blockPosition()) && needs(e.getItem())).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(loot==null) { scavengeTicks=60; return false; }
        if(distanceToSqr(loot)>2.25) {
            if(!walk(loot.blockPosition())) { scavengeTicks=200; return false; }
            activity="Picking up a weapon"; return true;
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
    private void fight(ServerLevel level,Monster enemy) {
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
        double reach=GuardWeapons.reach(getMainHandItem());
        if(distanceToSqr(enemy)<=reach*reach) {
            getNavigation().stop();
            if(guardAttackTicks==0) {
                swing(InteractionHand.MAIN_HAND);
                if(doHurtTarget(level,enemy) && GuardWeapons.melee(getMainHandItem())) getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
                guardAttackTicks=20;
            }
        } else walk(enemy.blockPosition(),0.8);
    }
    private void runToBell(ServerLevel level,Settlement town,BlockPos bell) {
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        if(distanceToSqr(Vec3.atCenterOf(bell))<=9.0 && visible(level,bell)) {
            getNavigation().stop(); getLookControl().setLookAt(bell.getX()+0.5,bell.getY()+0.5,bell.getZ()+0.5);
            swing(InteractionHand.MAIN_HAND); activity="Ringing the alarm bell";
            DefenseService.ring(level,town,this,bell); return;
        }
        activity="Running to ring the alarm bell";
        if(!walk(bell,0.9)) DefenseService.abandon(town,getUUID());
    }
    private void guard(ServerLevel level,Settlement town,Station station) {
        wearLocalArmor();
        BlockPos bell=DefenseService.bellRun(town,getUUID());
        if(bell!=null) { wakeForAlarm(); runToBell(level,town,bell); return; }
        boolean alarm=DefenseService.alarmed(town);
        if(!GuardService.onDuty(level,town,station.position(),getUUID())) {
            setTarget(null); if(isUsingItem()) stopUsingItem();
            useLocalSupplies(StructureRole.GUARD); rest(level,town);
            activity="Off duty: "+activity; return;
        }
        wakeForAlarm();
        Monster enemy=level.getEntitiesOfClass(Monster.class,getBoundingBox().inflate(alarm ? 32 : 16),
                m -> m.isAlive() && town.contains(m.blockPosition()) && hasLineOfSight(m)).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if(enemy!=null) { fight(level,enemy); return; }
        setTarget(null);
        if(isUsingItem()) stopUsingItem();
        readyMelee();
        if(scavenge(level,town) || equipFromStand(level,town,station)) return;
        useLocalSupplies(StructureRole.GUARD);
        if(armoryTicks>0) armoryTicks-=10;
        else { armoryTicks=100; armoryStocked=SettlementService.storage(level,town).stream().anyMatch(this::stocks); }
        // While the alarm rings, only an unarmed guard leaves the defense to resupply.
        boolean resupply=alarm ? armoryStocked && !carries(GuardWeapons::weapon) : deliverCargo() || mealTicks<=0 || armoryStocked;
        if(resupply && level.getGameTime()>=guardSupplyAt) {
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            if(!visitWarehouse(level,town,StructureRole.GUARD) && warehouse!=null && !near(warehouse)) return;
            // An empty pantry must not leave the station's only sentry waiting there forever.
            guardSupplyAt=level.getGameTime()+200;
            armoryStocked=false;
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
            // Deliver finished goods, then collect materials for the most pressing shortage.
            if(!visitWarehouse(level,town,station.role())) return;
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            List<Container> storage=warehouse==null ? List.of() : SettlementService.storageAt(level,town,warehouse);
            Crafting.Recipe next=Crafting.choose(storage,town.disabledRecipes,station.role());
            if(next==null || Crafting.fetch(storage,cargo,next)==0) {
                activity="Nothing to craft: the warehouse is stocked or lacks materials";
                idleStations.put(station.position(),level.getGameTime()+400); releaseWork(level); searchDelay=20; return;
            }
            order=next; workProgress=0; pathTicks=0;
        }
        if(!near(bench) || station.role()==StructureRole.COOK && !visible(level,bench)) {
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
            if(!visitWarehouse(level,town,station.role())) return;
            processingDelivery=false;
            processingSupplied=true;
            BlockPos warehouse=SettlementService.warehouse(level,town,blockPosition());
            List<Container> storage=SettlementService.storageAt(level,town,warehouse);
            ProcessingService.fetch(level,station.role(),processor,storage,cargo);
            if(station.role()==StructureRole.COOK && !ProcessingService.hasInputs(level,station.role(),processor,List.of(cargo))) {
                Crafting.Recipe bread=Crafting.choose(storage,town.disabledRecipes,StructureRole.COOK);
                if(bread!=null && Crafting.fetch(storage,cargo,bread)>0) { order=bread; workProgress=0; craft(level,town,station,processor); return; }
            }
        }
        if(!near(processor) || !visible(level,processor)) {
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
    public String activity() { return activity; }
    /** Station role this citizen works, or "none". */
    public String job() {
        if(!(level() instanceof ServerLevel server) || workplace==null || town(server)==null) return "none";
        Station station=town(server).station(workplace);
        return station==null ? "none" : station.role().id();
    }
    private void work(ServerLevel level) {
        reachBudget.reset();
        failedTargets.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        Settlement town=town(level);
        if(town==null) { activity="Settlement unavailable"; return; }
        if(!isGuard() && guardVacancy(level,town)) releaseWork(level);
        if(isGuard()) {
            Station empty=GuardService.uncovered(level,town);
            if(empty!=null && !empty.position().equals(workplace)
                    && SettlementService.workers(level).count(workplace,level.getGameTime())>1
                    && SettlementService.workers(level).claim(empty.position(),getUUID(),level.getGameTime(),200,SettlementService.workerLimit(empty))) {
                releaseWork(level); workplace=empty.position();
            }
        }
        var book=SettlementService.reservations(level);
        Station station=workplace==null ? null : town.station(workplace);
        if(station==null || !SettlementService.active(level,station)
                || !SettlementService.workers(level).claim(workplace,getUUID(),level.getGameTime(),200,SettlementService.workerLimit(station))) {
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
        if(station.role()==StructureRole.GUARD) { guard(level,town,station); return; }
        useLocalSupplies(station.role());
        // Deliver only full loads; use personal supplies before returning for replacements.
        if(deliverCargo() || mealTicks<=0 && station.role()!=StructureRole.FARM && station.role()!=StructureRole.COOK) {
            if(!visitWarehouse(level,town,station.role())) return;
        }
        if(station.role()==StructureRole.CRAFTSMAN) { craft(level,town,station,station.position()); return; }
        if(station.role().processes()) { process(level,town,station); return; }
        if(target==null) {
            if(searchDelay>0) { searchDelay-=10; return; }
            if(!station.role().excavates() && !near(station.position())) {
                activity="Returning to the "+station.role().id()+" worksite"; pathTicks+=10;
                if(!walk(station.position()) || pathTicks>1200) {
                    idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level);
                }
                return;
            }
            target=findTarget(level,town,station);
            if(target==null) {
                if(reachBudget.deferred()) { activity="Checking accessible work nearby"; return; }
                if(cargo.hasDeliverable(this::retainSupply,this::food)) { visitWarehouse(level,town,station.role()); return; }
                activity=station.role().excavates() ? ExcavationService.status(level,town,station)
                        : station.role()==StructureRole.LUMBER ? "No accessible natural tree; needs saplings and clear soil in range" : "No mature accessible crops";
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); searchDelay=20; return;
            }
            pathTicks=0;
        }
        if(!validTarget(level,town,station) || !book.claimAll(targetLease==null ? List.of(target) : List.of(target,targetLease),getUUID(),level.getGameTime(),200)) {
            cancelTarget(level,false); return;
        }
        useLocalSupplies(station.role());
        if(!properTool(station.role()) || needsSupply()) { if(!visitWarehouse(level,town,station.role())) return; }
        BlockPos approach=excavation==null ? target : excavation.stand();
        BlockPos visibleAt=action==Action.PLANT ? target.below() : excavation!=null && excavation.remote() ? station.position()
                : excavation!=null && excavation.quarry() ? target : action==Action.SUPPORT ? excavation.stand().below() : target;
        if(!near(approach) || !visible(level,visibleAt)) {
            activity=action==Action.PLANT ? "Walking to plant saplings" : "Walking to "+station.role().id()+" work"; pathTicks+=10;
            // Standing beside the work without a clear view will not fix itself; give up sooner than a long walk.
            if(!walk(approach) || pathTicks>(near(approach) ? 100 : 1200)) {
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
        };
        workProgress+=10;
        // Stone yields to a pickaxe in about a second; harder blocks and weaker tools take longer.
        int required=action==Action.EXCAVATE || action==Action.CAVE ? ExcavationService.breakTicks(level,target,getMainHandItem())
                : action==Action.SUPPORT ? 20 : Config.WORK_TICKS.get();
        if(workProgress>=required) harvest(level,town,station);
    }
    private final class WorkGoal extends Goal {
        WorkGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() {
            if(!(level() instanceof ServerLevel l) || town(l)==null) return false;
            // Civilians stop work during an alarm; a vacant guard post still draws a volunteer.
            return isGuard() || guardVacancy(l,town(l)) || !night(l) && !DefenseService.alarmed(town(l));
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
            return town!=null && !isGuard() && DefenseService.alarmed(town) && !night(l) && !guardVacancy(l,town);
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
        @Override public boolean canUse() { return level() instanceof ServerLevel l && town(l)!=null && night(l) && !isGuard() && !guardVacancy(l,town(l)); }
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
        output.store("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.contents());
        output.store("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf(),cargo.pendingItems());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String id=input.getStringOr("wwmc_settlement","");
        try { settlementId=id.isEmpty() ? null : UUID.fromString(id); } catch(IllegalArgumentException e) { settlementId=null; }
        workplace=input.read("wwmc_workplace",BlockPos.CODEC).orElse(null);
        mealTicks=input.getIntOr("wwmc_meal_ticks",2400);
        var stacks=input.read("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        cargo.restore(stacks,input.read("wwmc_pending_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
    }
    @Override public void die(DamageSource source) {
        if(level() instanceof ServerLevel level) {
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

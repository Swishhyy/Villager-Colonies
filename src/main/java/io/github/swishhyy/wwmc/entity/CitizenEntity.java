package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
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

/** Vanilla villager visuals, with an independent station-driven work routine. */
public final class CitizenEntity extends Villager {
    private enum Action { HARVEST,FELL,PLANT,EXCAVATE,SUPPORT }
    private Action action=Action.HARVEST;
    private ForestryService.Task forestTask;
    private ExcavationService.Ticket excavation;
    private BlockPos targetLease;
    private int minimumAxeDurability=1,deliveryTicks;
    private final Map<BlockPos,Long> idleStations=new HashMap<>();
    private UUID settlementId;
    private BlockPos workplace, target, sleepingBed;
    private final SimpleContainer cargo=new SimpleContainer(9);
    private final Map<BlockPos,Long> failedTargets=new HashMap<>();
    private int searchDelay, workProgress, pathTicks, mealTicks=2400;
    private String activity="Waiting for a job station";
    public CitizenEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level); setPersistenceRequired(); setCanPickUpLoot(false);
        setDropChance(EquipmentSlot.MAINHAND,0); setDropChance(EquipmentSlot.OFFHAND,0);
    }
    public void join(UUID id) { settlementId=id; mealTicks=Config.RATION_TICKS.get(); }
    private Settlement town(ServerLevel level) { return settlementId==null ? null : SettlementData.get(level).byId(settlementId); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new AvoidEntityGoal<>(this,Monster.class,12.0F,0.8,1.0));
        goalSelector.addGoal(2,new RestGoal());
        goalSelector.addGoal(3,new WorkGoal());
        goalSelector.addGoal(4,new LookAtPlayerGoal(this,Player.class,6.0F));
        goalSelector.addGoal(5,new RandomLookAroundGoal(this));
    }
    // Keep ordinary villager trades, breeding, and POI jobs out of the custom work scheduler.
    @Override protected void customServerAiStep(ServerLevel level) {}
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel) { if(mealTicks>0) mealTicks--; if(!cargo.isEmpty()) deliveryTicks++; else deliveryTicks=0; }
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(level() instanceof ServerLevel server && hand==InteractionHand.MAIN_HAND) {
            Settlement town=town(server);
            if(town!=null && town.owner.equals(player.getUUID()) && player.isShiftKeyDown()) {
                releaseWork(server); searchDelay=0;
                SettlementService.tell(player,"Worker released their job and will choose an available station.");
            } else {
                SettlementService.tell(player,getName().getString()+": "+activity+
                        (workplace==null ? "" : " at "+workplace.toShortString()));
            }
        }
        return InteractionResult.SUCCESS;
    }
    private boolean night(ServerLevel level) {
        long time=Math.floorMod(level.clockManager().getTotalTicks(level.registryAccess().getOrThrow(WorldClocks.OVERWORLD)),24000L);
        return time>=13000 && time<23000;
    }
    private boolean near(BlockPos pos) { return distanceToSqr(Vec3.atCenterOf(pos))<=6.25; }
    private boolean visible(ServerLevel level,BlockPos pos) {
        return level.clip(new ClipContext(getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getBlockPos().equals(pos);
    }
    private boolean walk(BlockPos pos) {
        if(getNavigation().isDone() || tickCount%40==0) {
            var path=getNavigation().createPath(pos,1);
            if(path==null) return false;
            getNavigation().moveTo(path,0.65);
        }
        getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return true;
    }
    private void releaseWork(ServerLevel level) {
        var book=SettlementService.reservations(level);
        if(workplace!=null) SettlementService.workers(level).release(workplace,getUUID());
        if(target!=null) book.release(target,getUUID());
        if(targetLease!=null) book.release(targetLease,getUUID());
        targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1;
        workplace=null; target=null; workProgress=0; pathTicks=0; getNavigation().stop();
    }
    private Station chooseJob(ServerLevel level,Settlement town) {
        var book=SettlementService.workers(level);
        idleStations.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        List<Station> jobs=new ArrayList<>(town.stations.stream()
                .filter(s -> s.role().providesWork() && SettlementService.active(level,s) && !idleStations.containsKey(s.position())).toList());
        jobs.sort(Comparator.comparingInt((Station s) -> switch(town.priority) {
            case "food" -> s.role()==StructureRole.FARM ? 0 : 1;
            case "materials" -> s.role()==StructureRole.FARM ? 1 : 0;
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
    private boolean deliverCargo() {
        int used=0; for(int slot=0;slot<cargo.getContainerSize();slot++) if(!cargo.getItem(slot).isEmpty()) used++;
        return used>=8 || used>0 && deliveryTicks>=600;
    }
    private boolean canReach(BlockPos pos) {
        if(near(pos)) return true;
        var path=getNavigation().createPath(pos,1); return path!=null && path.canReach();
    }
    private boolean food(ItemStack stack) {
        var nutrition=stack.get(DataComponents.FOOD);
        return nutrition!=null && nutrition.nutrition()>0 && !stack.is(Items.ROTTEN_FLESH)
                && !stack.is(Items.SPIDER_EYE) && !stack.is(Items.POISONOUS_POTATO) && !stack.is(Items.PUFFERFISH);
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
        for(int slot=0;slot<cargo.getContainerSize();slot++) {
            ItemStack remainder=cargo.getItem(slot);
            for(Container container:storage) remainder=InventoryOps.insert(container,remainder);
            cargo.setItem(slot,remainder);
        }
        boolean keepSupply=action==Action.PLANT && forestTask!=null && getOffhandItem().is(forestTask.planting().species().seed)
                || action==Action.SUPPORT && ExcavationService.supportMaterial(getOffhandItem());
        if(!keepSupply && !getOffhandItem().isEmpty()) {
            ItemStack leftover=getOffhandItem();
            for(Container container:storage) leftover=InventoryOps.insert(container,leftover);
            setItemSlot(EquipmentSlot.OFFHAND,leftover);
            if(!leftover.isEmpty()) { activity="Needs storage space for planting/building supplies"; return false; }
        }
        if(!cargo.isEmpty()) { activity="Warehouse is full or missing storage"; return false; }
        deliveryTicks=0;
        if(mealTicks<=0) {
            ItemStack ration=InventoryOps.takeOne(storage,this::food);
            if(!ration.isEmpty()) mealTicks=Config.RATION_TICKS.get();
            else if(role!=StructureRole.FARM) { activity="Waiting for food in the warehouse"; return false; }
        }
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
                    ItemStack leftover=InventoryOps.insert(cargo,next);
                    if(!leftover.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),leftover);
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
        failedTargets.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        var book=SettlementService.reservations(level);
        if(station.role()==StructureRole.LUMBER) {
            forestTask=ForestryService.find(level,town,station,p -> !failedTargets.containsKey(p)
                    && book.available(p,getUUID(),level.getGameTime()) && canReach(p));
            if(forestTask==null || !book.claim(forestTask.target(),getUUID(),level.getGameTime(),200)) return null;
            action=forestTask.planting()==null ? Action.FELL : Action.PLANT;
            minimumAxeDurability=forestTask.tree()==null ? 1 : forestTask.tree().logs().size();
            return forestTask.target();
        }
        if(station.role().excavates()) {
            excavation=ExcavationService.next(level,town,station,getUUID());
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
            if(failed && failedTargets.size()<128) failedTargets.put(target,level.getGameTime()+1200);
        }
        target=null; targetLease=null; forestTask=null; excavation=null; action=Action.HARVEST; minimumAxeDurability=1;
        workProgress=0; pathTicks=0; searchDelay=10; getNavigation().stop();
    }
    private void storeDrops(ServerLevel level,List<ItemStack> drops) {
        for(ItemStack stack:drops) {
            ItemStack remainder=InventoryOps.insert(cargo,stack);
            if(!remainder.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),remainder);
        }
    }
    private boolean validTarget(ServerLevel level,Settlement town,Station station) {
        return switch(action) {
            case HARVEST -> SettlementService.ownsBlock(level,town,station,target) && harvestable(level,town,station.role(),target);
            case FELL -> ForestryService.tree(level,town,target)!=null && SettlementService.ownsBlock(level,town,station,target);
            case PLANT -> forestTask!=null && ForestryService.canPlant(level,town,station,forestTask.planting())
                    && SettlementService.ownsBlock(level,town,station,target);
            case EXCAVATE,SUPPORT -> excavation!=null && ExcavationService.valid(level,town,station,excavation);
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
            ExcavationService.completed(level,station,excavation);
        }
        level.levelEvent(2001,target,Block.getId(state));
        storeDrops(level,drops); cancelTarget(level,false);
    }
    private void work(ServerLevel level) {
        Settlement town=town(level);
        if(town==null) { activity="Settlement unavailable"; return; }
        var book=SettlementService.reservations(level);
        Station station=workplace==null ? null : town.station(workplace);
        if(station==null || !SettlementService.active(level,station)
                || !SettlementService.workers(level).claim(workplace,getUUID(),level.getGameTime(),200,SettlementService.workerLimit(station))) {
            releaseWork(level);
            if(searchDelay>0) { searchDelay-=10; return; }
            station=chooseJob(level,town);
            if(station==null) { activity="Waiting for a free crew slot"; searchDelay=40; return; }
            workplace=station.position();
        }
        // Deliver batches, rather than walking home after every mined block.
        if(deliverCargo() || mealTicks<=0 && station.role()!=StructureRole.FARM) {
            if(!visitWarehouse(level,town,station.role())) return;
        }
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
                if(!cargo.isEmpty()) { visitWarehouse(level,town,station.role()); return; }
                activity=station.role().excavates() ? ExcavationService.status(level,town,station)
                        : station.role()==StructureRole.LUMBER ? "No accessible natural tree; needs saplings and clear soil in range" : "No mature accessible crops";
                idleStations.put(station.position(),level.getGameTime()+200); releaseWork(level); searchDelay=20; return;
            }
            pathTicks=0;
        }
        if(!validTarget(level,town,station) || !book.claimAll(targetLease==null ? List.of(target) : List.of(target,targetLease),getUUID(),level.getGameTime(),200)) {
            cancelTarget(level,false); return;
        }
        if(!properTool(station.role()) || needsSupply()) { if(!visitWarehouse(level,town,station.role())) return; }
        BlockPos approach=excavation==null ? target : excavation.stand();
        BlockPos visibleAt=action==Action.PLANT ? target.below() : action==Action.SUPPORT ? excavation.stand().below()
                : excavation!=null && excavation.quarry() ? station.position() : target;
        if(!near(approach) || !visible(level,visibleAt)) {
            activity=action==Action.PLANT ? "Walking to plant saplings" : "Walking to "+station.role().id()+" work"; pathTicks+=10;
            if(!walk(approach) || pathTicks>1200) cancelTarget(level,true);
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(visibleAt.getX()+0.5,visibleAt.getY()+0.5,visibleAt.getZ()+0.5);
        activity=switch(action) {
            case FELL -> "Felling a whole natural tree";
            case PLANT -> "Planting saplings";
            case SUPPORT -> "Supporting the tunnel floor";
            case EXCAVATE -> excavation.quarry() ? "Operating the quarry" : "Excavating a tunnel";
            case HARVEST -> "Harvesting crops";
        };
        workProgress+=10;
        if(workProgress>=Config.WORK_TICKS.get()) harvest(level,town,station);
    }
    private final class WorkGoal extends Goal {
        WorkGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return level() instanceof ServerLevel l && town(l)!=null && !night(l); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { if(tickCount%10==0 && level() instanceof ServerLevel l) work(l); }
        @Override public void stop() { if(level() instanceof ServerLevel l) releaseWork(l); }
    }
    private final class RestGoal extends Goal {
        RestGoal() { setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
        @Override public boolean canUse() { return level() instanceof ServerLevel l && town(l)!=null && night(l); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            if(tickCount%20!=0 || !(level() instanceof ServerLevel level)) return;
            Settlement town=town(level); if(town==null) return;
            var beds=SettlementService.housingBeds(level,town);
            var book=SettlementService.reservations(level);
            if(sleepingBed!=null && (!beds.contains(sleepingBed) || !book.claim(sleepingBed,getUUID(),level.getGameTime(),200))) {
                stopSleeping(); sleepingBed=null;
            }
            if(sleepingBed==null) for(BlockPos bed:beds) {
                if(!level.getBlockState(bed).getValue(BedBlock.OCCUPIED) && book.claim(bed,getUUID(),level.getGameTime(),200)) { sleepingBed=bed; break; }
            }
            if(sleepingBed==null) { activity="Needs a loaded housing bed"; return; }
            activity="Resting";
            if(distanceToSqr(Vec3.atCenterOf(sleepingBed))<=4) { getNavigation().stop(); if(!isSleeping()) startSleeping(sleepingBed); }
            else walk(sleepingBed);
        }
        @Override public void stop() {
            stopSleeping();
            if(level() instanceof ServerLevel l && sleepingBed!=null) SettlementService.reservations(l).release(sleepingBed,getUUID());
            sleepingBed=null;
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if(settlementId!=null) output.putString("wwmc_settlement",settlementId.toString());
        if(workplace!=null) output.store("wwmc_workplace",BlockPos.CODEC,workplace);
        output.putInt("wwmc_meal_ticks",mealTicks);
        List<ItemStack> stacks=new ArrayList<>();
        for(int i=0;i<cargo.getContainerSize();i++) stacks.add(cargo.getItem(i));
        output.store("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf(),stacks);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String id=input.getStringOr("wwmc_settlement","");
        try { settlementId=id.isEmpty() ? null : UUID.fromString(id); } catch(IllegalArgumentException e) { settlementId=null; }
        workplace=input.read("wwmc_workplace",BlockPos.CODEC).orElse(null);
        mealTicks=input.getIntOr("wwmc_meal_ticks",2400);
        var stacks=input.read("wwmc_cargo",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for(int i=0;i<cargo.getContainerSize();i++) cargo.setItem(i,i<stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
    }
    @Override public void die(DamageSource source) {
        if(level() instanceof ServerLevel level) {
            Settlement town=town(level);
            if(town!=null) { town.citizens.remove(getUUID()); SettlementData.get(level).setDirty(); }
            releaseWork(level);
            Containers.dropItemStack(level,getX(),getY(),getZ(),getOffhandItem()); setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            for(int i=0;i<cargo.getContainerSize();i++) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),cargo.removeItemNoUpdate(i));
            }
            Containers.dropItemStack(level,getX(),getY(),getZ(),getMainHandItem());
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        super.die(source);
    }
}

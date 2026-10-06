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
    private UUID settlementId;
    private BlockPos workplace, target, sleepingBed;
    private final SimpleContainer cargo=new SimpleContainer(9);
    private final Map<BlockPos,Long> failedTargets=new HashMap<>();
    private int searchDelay, workProgress, pathTicks, mealTicks=2400;
    private String activity="Waiting for a job station";
    public CitizenEntity(EntityType<? extends Villager> type,Level level) {
        super(type,level); setPersistenceRequired(); setCanPickUpLoot(false);
        setDropChance(EquipmentSlot.MAINHAND,0);
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
        if(level() instanceof ServerLevel && mealTicks>0) mealTicks--;
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
        if(workplace!=null) book.release(workplace,getUUID());
        if(target!=null) book.release(target,getUUID());
        workplace=null; target=null; workProgress=0; pathTicks=0; getNavigation().stop();
    }
    private Station chooseJob(ServerLevel level,Settlement town) {
        var book=SettlementService.reservations(level);
        List<Station> jobs=new ArrayList<>(town.stations.stream()
                .filter(s -> s.role().providesWork() && SettlementService.active(level,s)).toList());
        jobs.sort(Comparator.comparingInt((Station s) -> switch(town.priority) {
            case "food" -> s.role()==StructureRole.FARM ? 0 : 1;
            case "materials" -> s.role()==StructureRole.FARM ? 1 : 0;
            default -> 0;
        }).thenComparingDouble(s -> distanceToSqr(Vec3.atCenterOf(s.position()))));
        for(Station station:jobs) if(book.claim(station.position(),getUUID(),level.getGameTime(),200)) return station;
        return null;
    }
    private boolean properTool(StructureRole role) {
        return role==StructureRole.FARM || role==StructureRole.LUMBER && getMainHandItem().is(ItemTags.AXES)
                || role==StructureRole.MINE && getMainHandItem().is(ItemTags.PICKAXES);
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
        if(!cargo.isEmpty()) { activity="Warehouse is full or missing storage"; return false; }
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
            ItemStack tool=InventoryOps.takeOne(storage,s -> role==StructureRole.LUMBER ? s.is(ItemTags.AXES) : s.is(ItemTags.PICKAXES));
            setItemSlot(EquipmentSlot.MAINHAND,tool);
            if(tool.isEmpty()) { activity="Needs an "+(role==StructureRole.LUMBER ? "axe" : "appropriate pickaxe")+" in storage"; return false; }
        }
        return true;
    }
    private boolean harvestable(ServerLevel level,Settlement town,StructureRole role,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !town.contains(pos) || SettlementService.protectedFurniture(town,pos)
                || level.getBlockEntity(pos)!=null) return false;
        BlockState state=level.getBlockState(pos);
        if(role==StructureRole.FARM) {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && seed(state)!=null;
        }
        if(role==StructureRole.LUMBER) {
            if(!state.is(BlockTags.LOGS)) return false;
            for(BlockPos leaf:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,4,3))) {
                if(level.hasChunkAt(leaf) && level.getBlockState(leaf).is(BlockTags.LEAVES)) return true;
            }
            return false;
        }
        if(role==StructureRole.MINE) {
            // Start with exposed faces at foot level or higher; planned shafts need an excavation plan.
            if(pos.getY()<getBlockY()) return false;
            boolean rock=state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES);
            if(!rock || !getMainHandItem().isCorrectToolForDrops(state)) return false;
            for(var direction:net.minecraft.core.Direction.values()) {
                BlockPos adjacent=pos.relative(direction);
                if(level.hasChunkAt(adjacent) && level.getBlockState(adjacent).isAir()) return true;
            }
        }
        return false;
    }
    private Item seed(BlockState state) {
        if(state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if(state.is(Blocks.CARROTS)) return Items.CARROT;
        if(state.is(Blocks.POTATOES)) return Items.POTATO;
        if(state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }
    private BlockPos findTarget(ServerLevel level,Settlement town,Station station) {
        List<BlockPos> candidates=new ArrayList<>();
        failedTargets.entrySet().removeIf(e -> e.getValue()<=level.getGameTime());
        for(BlockPos pos:SettlementService.cells(station)) {
            if(!failedTargets.containsKey(pos) && SettlementService.ownsBlock(level,town,station,pos)
                    && harvestable(level,town,station.role(),pos)) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p))));
        var book=SettlementService.reservations(level);
        for(BlockPos pos:candidates) if(book.claim(pos,getUUID(),level.getGameTime(),200)) return pos;
        return null;
    }
    private void cancelTarget(ServerLevel level,boolean failed) {
        if(target!=null) {
            SettlementService.reservations(level).release(target,getUUID());
            if(failed && failedTargets.size()<128) failedTargets.put(target,level.getGameTime()+1200);
        }
        target=null; workProgress=0; pathTicks=0; searchDelay=40; getNavigation().stop();
    }
    private void harvest(ServerLevel level,Station station) {
        BlockState state=level.getBlockState(target);
        List<ItemStack> drops=new ArrayList<>(Block.getDrops(state,level,target,null,this,getMainHandItem()));
        if(station.role()==StructureRole.FARM) {
            Item seed=seed(state);
            ItemStack seedStack=drops.stream().filter(s -> s.is(seed) && !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            if(seedStack.isEmpty()) { cancelTarget(level,true); return; }
            seedStack.shrink(1);
            level.setBlock(target,((CropBlock)state.getBlock()).getStateForAge(0),3);
        } else {
            // Drop calculation precedes destruction; disabling vanilla drops prevents duplication.
            if(!level.destroyBlock(target,false,this)) { cancelTarget(level,true); return; }
            getMainHandItem().hurtAndBreak(1,this,EquipmentSlot.MAINHAND);
        }
        level.levelEvent(2001,target,Block.getId(state));
        for(ItemStack stack:drops) {
            ItemStack remainder=InventoryOps.insert(cargo,stack);
            if(!remainder.isEmpty()) Containers.dropItemStack(level,getX(),getY(),getZ(),remainder);
        }
        cancelTarget(level,false);
    }
    private void work(ServerLevel level) {
        Settlement town=town(level);
        if(town==null) { activity="Settlement unavailable"; return; }
        var book=SettlementService.reservations(level);
        Station station=workplace==null ? null : town.station(workplace);
        if(station==null || !SettlementService.active(level,station) || !book.claim(workplace,getUUID(),level.getGameTime(),200)) {
            releaseWork(level);
            if(searchDelay>0) { searchDelay-=10; return; }
            station=chooseJob(level,town);
            if(station==null) { activity="Waiting for an unoccupied job station"; searchDelay=100; return; }
            workplace=station.position();
        }
        // Hungry farmers keep gathering food and eat when they deliver it; otherwise the town could deadlock.
        if(!cargo.isEmpty() || mealTicks<=0 && station.role()!=StructureRole.FARM || !properTool(station.role())) {
            if(!visitWarehouse(level,town,station.role())) return;
        }
        if(target==null) {
            if(searchDelay>0) { searchDelay-=10; return; }
            target=findTarget(level,town,station);
            if(target==null) { activity="No harvestable resources near the "+station.role().id()+" station"; searchDelay=100; return; }
        }
        if(!SettlementService.ownsBlock(level,town,station,target) || !harvestable(level,town,station.role(),target)
                || !book.claim(target,getUUID(),level.getGameTime(),200)) { cancelTarget(level,false); return; }
        if(!near(target) || !visible(level,target)) {
            activity="Walking to "+station.role().id()+" work"; pathTicks+=10;
            if(!walk(target) || pathTicks>300) cancelTarget(level,true);
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(target.getX()+0.5,target.getY()+0.5,target.getZ()+0.5);
        activity="Working: "+station.role().id(); workProgress+=10;
        if(workProgress>=Config.WORK_TICKS.get()) harvest(level,station);
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
            for(int i=0;i<cargo.getContainerSize();i++) {
                Containers.dropItemStack(level,getX(),getY(),getZ(),cargo.removeItemNoUpdate(i));
            }
            Containers.dropItemStack(level,getX(),getY(),getZ(),getMainHandItem());
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        super.die(source);
    }
}

package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.WorkforceBook;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.entity.RoadSurface;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded, server-thread animal work. Workers use only their station's storage; couriers supply the next job. */
public final class AnimalWork {
    public static final int FEED_LOAD=8,CARCASS_LOAD=4;
    private static final Map<ServerLevel,WorkforceBook<UUID>> ANIMALS=new WeakHashMap<>();
    private UUID target;
    private long nextSearch,nextAttack;
    private int progress;
    private FishingSpot fishing;
    public record FishingSpot(BlockPos bank,BlockPos water) {}
    public void reset() { target=null; fishing=null; progress=0; nextSearch=0; nextAttack=0; }
    public static boolean weapon(ItemStack stack) { return GuardWeapons.melee(stack) || stack.is(ItemTags.AXES); }
    public static boolean feed(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) || stack.is(Items.DANDELION);
    }
    public static boolean supply(StructureRole role,ItemStack stack) {
        return switch(role) {
            case HUNTER -> weapon(stack) && !GuardEquipment.worn(stack);
            case FISHERMAN -> stack.is(Items.FISHING_ROD) && !GuardEquipment.worn(stack);
            case BUTCHER -> Carcasses.carcass(stack) || stack.is(ItemTags.AXES) && !GuardEquipment.worn(stack);
            case ANIMAL_KEEPER -> feed(stack) || weapon(stack) && !GuardEquipment.worn(stack);
            default -> false;
        };
    }
    public static int huntingRadius(Station station) { return Math.min(64,station.radius()*8); }
    public static boolean protectedAnimal(Settlement town,Animal animal) {
        return animal.hasCustomName() || animal.isLeashed() || town.stations.stream()
                .anyMatch(s -> s.role()==StructureRole.ANIMAL_KEEPER && s.contains(animal.blockPosition()));
    }
    public static boolean livestock(Animal animal) {
        var kind=Carcasses.kind(animal);
        return kind!=null && kind!=Carcasses.Kind.COD && kind!=Carcasses.Kind.SALMON;
    }
    public static boolean cullable(int adults,int breedingGroup,boolean baby,boolean named,boolean inLove) {
        return adults>Math.max(2,breedingGroup) && !baby && !named && !inLove;
    }
    private static List<Animal> animals(ServerLevel level,Settlement town,Station station,boolean hunting) {
        int radius=hunting ? huntingRadius(station) : station.radius();
        return level.getEntitiesOfClass(Animal.class,new AABB(station.position()).inflate(radius,hunting ? 12 : radius,radius),
                a -> a.isAlive() && livestock(a) && town.contains(a.blockPosition())
                    && (hunting ? !a.isBaby() && !protectedAnimal(town,a) : station.contains(a.blockPosition())));
    }
    public void tick(ServerLevel level,Settlement town,Station station,CitizenEntity worker) {
        CitizenInventory bag=worker.bag();
        if(SettlementService.jobBarrels(level,town,station).isEmpty()) {
            worker.workActivity("Needs a barrel within the station's range; couriers move its goods"); return;
        }
        if(!bag.first(s -> !FoodHealing.food(s) && !supply(station.role(),s)).isEmpty()) {
            worker.workDepot(level,town,station); return;
        }
        if(station.role()==StructureRole.BUTCHER) { butcher(level,town,station,worker); return; }
        if(station.role()==StructureRole.FISHERMAN) { fish(level,town,station,worker); return; }
        if(station.role()==StructureRole.ANIMAL_KEEPER) { keepAnimals(level,town,station,worker); return; }
        hunt(level,town,station,worker,false);
    }
    private void butcher(ServerLevel level,Settlement town,Station station,CitizenEntity worker) {
        if(InventoryOps.count(List.of(worker.bag()),Carcasses::carcass)==0) {
            if(!fetch(level,town,station,worker,Carcasses::carcass,CARCASS_LOAD)) {
                worker.workActivity("Waiting for carcasses in the butcher's barrel"); return;
            }
        }
        if(!worker.workAt(level,station.position())) { worker.workActivity("Walking to the cutting table"); worker.workWalk(station.position()); return; }
        worker.getNavigation().stop();
        worker.workActivity("Preparing carcasses into raw portions for the cook");
        progress+=10;
        if(progress>=Config.WORK_TICKS.get()) {
            progress=0; Carcasses.prepare(worker.bag()); worker.swing(InteractionHand.MAIN_HAND);
            worker.getMainHandItem().hurtAndBreak(1,worker,EquipmentSlot.MAINHAND);
        }
    }
    private void hunt(ServerLevel level,Settlement town,Station station,CitizenEntity worker,boolean culling) {
        Animal animal=target!=null && level.getEntity(target) instanceof Animal a ? a : null;
        if(animal==null || !animal.isAlive() || !town.contains(animal.blockPosition())
                || culling && !station.contains(animal.blockPosition()) || !culling && protectedAnimal(town,animal)) {
            target=null;
            if(level.getGameTime()<nextSearch) return;
            nextSearch=level.getGameTime()+80;
            List<Animal> herd=animals(level,town,station,!culling);
            var claims=ANIMALS.computeIfAbsent(level,l -> new WorkforceBook<>());
            animal=herd.stream().filter(a -> !culling || cullable((int)herd.stream().filter(b -> !b.isBaby() && b.getType()==a.getType()).count(),
                            Config.ANIMAL_BREEDERS.get(),a.isBaby(),a.hasCustomName(),a.isInLove()))
                    .sorted(Comparator.comparingDouble(worker::distanceToSqr))
                    .filter(a -> claims.claim(a.getUUID(),worker.getUUID(),level.getGameTime(),200)).findFirst().orElse(null);
            if(animal==null) { worker.workActivity(culling ? "Keeping the breeding animals; no surplus adults" : "No unprotected adult game in the hunting area"); return; }
            target=animal.getUUID();
        }
        ANIMALS.computeIfAbsent(level,l -> new WorkforceBook<>()).claim(target,worker.getUUID(),level.getGameTime(),200);
        if(culling) {
            var species=animal.getType();
            long adults=animals(level,town,station,false).stream().filter(a -> !a.isBaby() && a.getType()==species).count();
            if(!cullable((int)adults,Config.ANIMAL_BREEDERS.get(),animal.isBaby(),animal.hasCustomName(),animal.isInLove()) || animal.isLeashed()) {
                ANIMALS.get(level).release(target,worker.getUUID()); target=null; return;
            }
        }
        worker.getLookControl().setLookAt(animal,30,30);
        if(!CitizenReach.within(worker.getEyePosition(),animal.getBoundingBox()) || !worker.hasLineOfSight(animal)) {
            worker.workActivity(culling ? "Approaching a surplus animal" : "Tracking game"); worker.workWalk(animal.blockPosition()); return;
        }
        worker.getNavigation().stop(); worker.workActivity(culling ? "Harvesting a surplus animal for the butcher" : "Hunting a carcass for the butcher");
        if(level.getGameTime()>=nextAttack) {
            nextAttack=level.getGameTime()+20; worker.swing(InteractionHand.MAIN_HAND);
            if(worker.doHurtTarget(level,animal)) worker.getMainHandItem().hurtAndBreak(1,worker,EquipmentSlot.MAINHAND);
        }
    }
    private void keepAnimals(ServerLevel level,Settlement town,Station station,CitizenEntity worker) {
        List<Animal> herd=animals(level,town,station,false);
        if(target!=null || herd.stream().anyMatch(a -> cullable((int)herd.stream().filter(b -> !b.isBaby() && b.getType()==a.getType()).count(),
                Config.ANIMAL_BREEDERS.get(),a.isBaby(),a.hasCustomName(),a.isInLove()))) {
            if(!weapon(worker.getMainHandItem()) || GuardEquipment.worn(worker.getMainHandItem())) {
                ItemStack knife=InventoryOps.takeOne(List.of(worker.bag()),AnimalWork::weapon);
                if(!knife.isEmpty()) { worker.bag().offer(worker.getMainHandItem()); worker.setItemSlot(EquipmentSlot.MAINHAND,knife); }
                else { worker.workDepot(level,town,station); worker.workActivity("Needs a sword or axe in the keeper's barrel to harvest surplus adults"); return; }
            }
            hunt(level,town,station,worker,true); return;
        }
        if(level.getGameTime()<nextSearch) return;
        nextSearch=level.getGameTime()+40;
        List<Animal> ready=herd.stream().filter(a -> !a.isBaby() && !a.hasCustomName() && a.getAge()==0 && !a.isInLove()).toList();
        for(Animal first:ready) {
            if(herd.stream().filter(a -> a.getType()==first.getType()).count()>=Config.ANIMAL_BREEDERS.get()*3L) continue;
            Animal second=ready.stream().filter(a -> a!=first && a.getType()==first.getType()).findFirst().orElse(null);
            if(second==null) continue;
            if(InventoryOps.count(List.of(worker.bag()),first::isFood)<2 && !fetch(level,town,station,worker,first::isFood,FEED_LOAD)) {
                worker.workActivity("Waiting for breeding feed in the keeper's barrel"); return;
            }
            if(InventoryOps.count(List.of(worker.bag()),first::isFood)<2) continue;
            if(!CitizenReach.within(worker.getEyePosition(),first.getBoundingBox()) || !worker.hasLineOfSight(first)) {
                worker.workActivity("Bringing feed to the breeding animals"); worker.workWalk(first.blockPosition()); return;
            }
            if(!CitizenReach.within(worker.getEyePosition(),second.getBoundingBox()) || !worker.hasLineOfSight(second)) {
                worker.workWalk(second.blockPosition()); return;
            }
            var claims=ANIMALS.computeIfAbsent(level,l -> new WorkforceBook<>());
            if(!claims.claim(first.getUUID(),worker.getUUID(),level.getGameTime(),200)
                    || !claims.claim(second.getUUID(),worker.getUUID(),level.getGameTime(),200)) continue;
            worker.getNavigation().stop();
            InventoryOps.takeOne(List.of(worker.bag()),first::isFood); InventoryOps.takeOne(List.of(worker.bag()),second::isFood);
            first.setInLove(null); second.setInLove(null); worker.swing(InteractionHand.MAIN_HAND);
            worker.workActivity("Feeding a pair; keeping "+Config.ANIMAL_BREEDERS.get()+" adult breeders per species"); return;
        }
        worker.workActivity(herd.size()<2 ? "Bring a breeding pair into a fenced pen in range" : "Waiting for animals to grow or breeding cooldowns");
    }
    private boolean fetch(ServerLevel level,Settlement town,Station station,CitizenEntity worker,java.util.function.Predicate<ItemStack> ingredient,int maximum) {
        List<BlockPos> barrels=SettlementService.jobBarrels(level,town,station);
        BlockPos barrel=barrels.stream().min(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).orElse(null);
        if(barrel==null) return false;
        if(!worker.workAt(level,barrel)) { worker.workWalk(barrel); return false; }
        List<Container> storage=SettlementService.jobStorage(level,town,station);
        int have=InventoryOps.count(List.of(worker.bag()),ingredient);
        for(int n=have;n<maximum;n++) {
            ItemStack next=InventoryOps.takeOne(storage,ingredient);
            if(next.isEmpty()) break;
            worker.bag().offer(next);
        }
        return InventoryOps.count(List.of(worker.bag()),ingredient)>0;
    }
    public static boolean fishable(ServerLevel level,BlockPos water) {
        return level.hasChunkAt(water) && level.hasChunkAt(water.below())
                && level.getFluidState(water).is(FluidTags.WATER) && level.getFluidState(water).isSource()
                && level.getFluidState(water.below()).is(FluidTags.WATER)
                && level.getBlockState(water.above()).isAir() && level.canSeeSky(water.above());
    }
    public static FishingSpot findFishingSpot(ServerLevel level,Settlement town,Station station,CitizenEntity worker) {
        CitizenReach.StandingView view=new CitizenReach.StandingView() {
            public boolean available(BlockPos p) { return town.contains(p) && level.hasChunkAt(p) && p.getY()>=level.getMinY() && p.getY()<level.getMaxY(); }
            public boolean clear(BlockPos p) { return level.getBlockState(p).getCollisionShape(level,p).isEmpty() && level.getFluidState(p).isEmpty(); }
            public boolean footing(BlockPos p) { return !level.getBlockState(p).getCollisionShape(level,p).isEmpty() && !RoadSurface.openWater(level,p.above()); }
        };
        List<FishingSpot> spots=new ArrayList<>();
        int r=station.radius();
        for(BlockPos water:BlockPos.betweenClosed(station.position().offset(-r,-r,-r),station.position().offset(r,r,r))) {
            if(!fishable(level,water)) continue;
            for(int distance=1;distance<=3;distance++) for(int[] side:new int[][]{{distance,0},{-distance,0},{0,distance},{0,-distance}}) {
                BlockPos bank=water.offset(side[0],1,side[1]);
                if(CitizenReach.standing(view,bank) && CitizenReach.within(Vec3.atBottomCenterOf(bank).add(0,worker.getEyeHeight(),0),water))
                    spots.add(new FishingSpot(bank.immutable(),water.immutable()));
            }
        }
        int probes=0;
        for(FishingSpot spot:spots.stream().distinct().sorted(Comparator.comparingDouble(p -> p.bank().distSqr(worker.blockPosition()))).toList()) {
            if(worker.blockPosition().distSqr(spot.bank())<=1) return spot;
            if(probes++>=6) break;
            var path=worker.getNavigation().createPath(spot.bank(),1);
            if(path!=null && path.canReach()) return spot;
        }
        return null;
    }
    private void fish(ServerLevel level,Settlement town,Station station,CitizenEntity worker) {
        if(fishing==null || !fishable(level,fishing.water())) {
            fishing=null; progress=0;
            if(level.getGameTime()<nextSearch) return;
            nextSearch=level.getGameTime()+100;
            fishing=findFishingSpot(level,town,station,worker);
            if(fishing==null) { worker.workActivity("Needs a dry bank beside open, two-block-deep water in range"); return; }
        }
        if(worker.blockPosition().distSqr(fishing.bank())>1) { worker.workActivity("Walking to the fishing bank"); worker.workWalk(fishing.bank()); return; }
        var hit=level.clip(new ClipContext(worker.getEyePosition(),Vec3.atCenterOf(fishing.water()),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,worker));
        if(hit.getType()!=HitResult.Type.MISS && !hit.getBlockPos().equals(fishing.water())) { fishing=null; progress=0; return; }
        worker.getNavigation().stop();
        worker.getLookControl().setLookAt(fishing.water().getX()+0.5,fishing.water().getY()+0.9,fishing.water().getZ()+0.5);
        worker.workActivity("Fishing: "+progress/20+" / "+Config.FISHING_SECONDS.get()+" seconds");
        if(progress==0) worker.swing(InteractionHand.MAIN_HAND);
        progress+=10;
        if(progress>=Config.FISHING_SECONDS.get()*20) {
            progress=0; worker.bag().offer((worker.getRandom().nextBoolean() ? Carcasses.Kind.COD : Carcasses.Kind.SALMON).stack());
            worker.getMainHandItem().hurtAndBreak(1,worker,EquipmentSlot.MAINHAND); worker.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(ParticleTypes.SPLASH,fishing.water().getX()+0.5,fishing.water().getY()+0.9,fishing.water().getZ()+0.5,5,0.3,0.1,0.3,0);
        }
    }
}

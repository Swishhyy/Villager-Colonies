package io.github.swishhyy.wwmc.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.block.TrapBlock;
import io.github.swishhyy.wwmc.core.TrapKind;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** A bounded saved index of real blocks, not a scan of the claim. Unloaded defenses neither fire nor consume supplies. */
public final class TrapService {
    public record Entry(BlockPos pos,long readyAt) {
        public static final Codec<Entry> CODEC=RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),Codec.LONG.optionalFieldOf("ready_at",0L).forGetter(Entry::readyAt)
        ).apply(i,Entry::new));
        public Entry { pos=pos.immutable(); }
    }
    public record Material(Predicate<ItemStack> accepts,ItemStack icon,int count,boolean repair) {}
    private static final Map<UUID,Long> HIT_UNTIL=new HashMap<>();
    private static int index(Settlement town,BlockPos pos) {
        for(int i=0;i<town.progress.traps.size();i++) if(town.progress.traps.get(i).pos().equals(pos)) return i;
        return -1;
    }
    public static boolean hasRoom(Settlement town,BlockPos pos) { return index(town,pos)>=0 || town.progress.traps.size()<Config.MAX_TRAPS.get(); }
    public static boolean register(ServerLevel level,BlockPos pos) {
        Settlement town=SettlementData.get(level).at(pos);
        if(town==null || !(level.getBlockState(pos).getBlock() instanceof TrapBlock trap) || !hasRoom(town,pos)) return false;
        BlockState state=level.getBlockState(pos);
        if(state.getValue(TrapBlock.WEAR)>=trap.kind().uses && state.getValue(TrapBlock.ARMED)) {
            state=state.setValue(TrapBlock.ARMED,false); level.setBlockAndUpdate(pos,state);
        }
        long ready=state.getValue(TrapBlock.ARMED) || trap.kind().manual ? 0 : level.getGameTime()+trap.kind().cooldown;
        int i=index(town,pos);
        if(i<0) town.progress.traps.add(new Entry(pos,ready));
        else town.progress.traps.set(i,new Entry(pos,ready));
        SettlementData.get(level).setDirty(); return true;
    }
    public static boolean target(Entity entity) { return entity instanceof Monster monster && DefenseService.hostile(monster); }
    public static boolean needsMaintenance(BlockState state) {
        return state.getBlock() instanceof TrapBlock trap && (state.getValue(TrapBlock.WEAR)>=trap.kind().uses
                || trap.kind().manual && !state.getValue(TrapBlock.ARMED));
    }
    public static Material material(BlockState state) {
        if(!(state.getBlock() instanceof TrapBlock trap) || !needsMaintenance(state)) return null;
        TrapKind kind=trap.kind(); boolean repair=state.getValue(TrapBlock.WEAR)>=kind.uses;
        return repair ? new Material(kind::repairMaterial,new ItemStack(kind.repairItem()),kind.repairCount(),true)
                : new Material(s -> s.is(Items.STRING),new ItemStack(Items.STRING),1,false);
    }
    public static boolean supplied(Material material,List<Container> storage) {
        return material!=null && InventoryOps.count(storage,material.accepts())>=material.count();
    }
    private static boolean eligible(ServerLevel level,Settlement town,BlockPos pos) {
        return town.contains(pos) && level.hasChunkAt(pos) && level.isPositionEntityTicking(pos)
                && level.getBlockState(pos).getBlock() instanceof TrapBlock
                && AgeProgression.allowed(town,new ItemStack(level.getBlockState(pos).getBlock()));
    }
    private static void finishMaintenance(ServerLevel level,Settlement town,BlockPos pos,Material material) {
        BlockState state=level.getBlockState(pos).setValue(TrapBlock.ARMED,true);
        if(material.repair()) state=state.setValue(TrapBlock.WEAR,0);
        level.setBlockAndUpdate(pos,state);
        int i=index(town,pos); if(i>=0) town.progress.traps.set(i,new Entry(pos,0));
        SettlementData.get(level).setDirty();
        level.playSound(null,pos,SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,SoundSource.BLOCKS,0.35F,1.2F);
    }
    /** Supply validation and payment precede the state change; two workers cannot spend twice for the same repair. */
    public static boolean maintain(ServerLevel level,Settlement town,BlockPos pos,List<Container> storage) {
        if(!eligible(level,town,pos)) return false;
        Material material=material(level.getBlockState(pos));
        if(!supplied(material,storage)) return false;
        for(int i=0;i<material.count();i++) InventoryOps.takeOne(storage,material.accepts());
        finishMaintenance(level,town,pos,material); return true;
    }
    public static boolean maintainHeld(ServerLevel level,Settlement town,BlockPos pos,ItemStack held,boolean creative) {
        if(!eligible(level,town,pos)) return false;
        Material material=material(level.getBlockState(pos));
        if(material==null || !material.accepts().test(held) || !creative && held.getCount()<material.count()) return false;
        if(!creative) held.shrink(material.count());
        finishMaintenance(level,town,pos,material); return true;
    }
    public static BlockPos nextMaintenance(ServerLevel level,Settlement town,BlockPos from,List<Container> stock,Predicate<BlockPos> ignored) {
        return town.progress.traps.stream().map(Entry::pos).filter(pos -> !ignored.test(pos) && eligible(level,town,pos)
                && supplied(material(level.getBlockState(pos)),stock)).min(Comparator.comparingDouble(from::distSqr)).orElse(null);
    }
    public static List<Material> neededMaterials(ServerLevel level,Settlement town) {
        return town.progress.traps.stream().map(Entry::pos).filter(pos -> eligible(level,town,pos))
                .map(pos -> material(level.getBlockState(pos))).filter(Objects::nonNull).toList();
    }
    public static String describe(ServerLevel level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof TrapBlock trap)) return "Defense removed.";
        int left=Math.max(0,trap.kind().uses-state.getValue(TrapBlock.WEAR));
        Material material=material(state);
        return trap.kind().title+": "+left+" uses left; "+(material!=null ? "needs "+material.count()+" "+material.icon().getHoverName().getString()
                : state.getValue(TrapBlock.ARMED) ? "armed" : "resetting")+". "+trap.kind().effect()+".";
    }
    public static String status(ServerLevel level,Settlement town) {
        int loaded=0,ready=0,needed=0;
        for(Entry entry:town.progress.traps) if(level.hasChunkAt(entry.pos()) && level.isPositionEntityTicking(entry.pos())) {
            BlockState state=level.getBlockState(entry.pos()); if(!(state.getBlock() instanceof TrapBlock)) continue;
            loaded++; if(needsMaintenance(state)) needed++; else if(state.getValue(TrapBlock.ARMED)) ready++;
        }
        return town.progress.traps.size()+" placed; "+ready+" ready / "+loaded+" loaded; "+needed+" need maintenance";
    }
    public static void tickTown(ServerLevel level,Settlement town) {
        long now=level.getGameTime();
        for(int i=town.progress.traps.size()-1;i>=0;i--) {
            Entry entry=town.progress.traps.get(i); BlockPos pos=entry.pos();
            if(!level.hasChunkAt(pos) || !level.isPositionEntityTicking(pos)) continue;
            BlockState state=level.getBlockState(pos);
            if(!town.contains(pos) || !(state.getBlock() instanceof TrapBlock)) {
                town.progress.traps.remove(i); SettlementData.get(level).setDirty(); continue;
            }
            if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) {
                Block.dropResources(state,level,pos); level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                town.progress.traps.remove(i); SettlementData.get(level).setDirty(); continue;
            }
            TrapKind kind=((TrapBlock)state.getBlock()).kind();
            if(!AgeProgression.allowed(town,new ItemStack(state.getBlock())) || state.getValue(TrapBlock.WEAR)>=kind.uses) continue;
            if(!state.getValue(TrapBlock.ARMED)) {
                if(kind.manual) continue;
                if(entry.readyAt()>now) continue;
                state=state.setValue(TrapBlock.ARMED,true); level.setBlockAndUpdate(pos,state);
                town.progress.traps.set(i,new Entry(pos,0)); SettlementData.get(level).setDirty();
            }
            AABB contact=new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+0.45,pos.getZ()+1);
            List<Monster> candidates=level.getEntitiesOfClass(Monster.class,contact,m -> target(m) && HIT_UNTIL.getOrDefault(m.getUUID(),0L)<=now);
            if(candidates.isEmpty()) continue;
            Monster mob=candidates.getFirst();
            if(kind.damage>0 && !mob.hurtServer(level,level.damageSources().cactus(),kind.damage)) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,kind.slowTicks,kind.slowLevel));
            if(kind.manual) mob.setDeltaMovement(0,0,0);
            int wear=state.getValue(TrapBlock.WEAR)+1;
            level.setBlockAndUpdate(pos,state.setValue(TrapBlock.WEAR,wear).setValue(TrapBlock.ARMED,false));
            town.progress.traps.set(i,new Entry(pos,kind.manual ? 0 : now+kind.cooldown));
            HIT_UNTIL.put(mob.getUUID(),now+20); SettlementData.get(level).setDirty();
            level.playSound(null,pos,kind.age>0 ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF,SoundSource.BLOCKS,0.35F,1);
            level.sendParticles(ParticleTypes.CRIT,pos.getX()+0.5,pos.getY()+0.25,pos.getZ()+0.5,4,0.2,0.1,0.2,0.02);
        }
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%10!=0) return;
        long now=level.getGameTime();
        if(now%200==0) HIT_UNTIL.values().removeIf(until -> until<=now);
        for(Settlement town:SettlementData.get(level).settlements) tickTown(level,town);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { HIT_UNTIL.clear(); }
}

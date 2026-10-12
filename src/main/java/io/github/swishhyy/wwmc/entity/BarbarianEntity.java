package io.github.swishhyy.wwmc.entity;

import io.github.swishhyy.wwmc.core.WavePlan;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Native humanoid raiders: melee scouts, bowmen and armored veterans. No natural spawning or settlement inventory. */
public final class BarbarianEntity extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> ROLE=SynchedEntityData.defineId(BarbarianEntity.class,EntityDataSerializers.INT);
    private final MeleeAttackGoal melee=new MeleeAttackGoal(this,1.1,true);
    private final RangedBowAttackGoal<BarbarianEntity> bow=new RangedBowAttackGoal<>(this,1.0,45,15.0F);

    public BarbarianEntity(EntityType<? extends BarbarianEntity> type,Level level) {
        super(type,level); xpReward=5; combatGoal();
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,0.27)
                .add(Attributes.FOLLOW_RANGE,40).add(Attributes.ATTACK_DAMAGE,2);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ROLE,0);
    }
    public WavePlan.Attacker role() {
        int value=entityData.get(ROLE);
        return value>=0 && value<WavePlan.Attacker.values().length ? WavePlan.Attacker.values()[value] : WavePlan.Attacker.FOOT_SOLDIER;
    }
    public boolean archer() { return role()==WavePlan.Attacker.ARCHER; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,0.8));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this,BarbarianEntity.class));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,CitizenEntity.class,true));
        targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    /** Spawn equipment is real and visible, but cannot be farmed into free advanced gear. */
    public void equip(WavePlan.Attacker role) {
        entityData.set(ROLE,role.ordinal());
        for(EquipmentSlot slot:java.util.List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            setItemSlot(slot,ItemStack.EMPTY); setDropChance(slot,0);
        }
        setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(switch(role) {
            case FOOT_SOLDIER -> getRandom().nextBoolean() ? Items.STONE_SWORD : Items.WOODEN_SWORD;
            case ARCHER -> Items.BOW;
            case ARMORED -> Items.IRON_SWORD;
        }));
        if(role==WavePlan.Attacker.ARMORED) {
            setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CHAINMAIL_HELMET));
            setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.CHAINMAIL_LEGGINGS));
            setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(role==WavePlan.Attacker.ARMORED ? 24 : 20);
        setHealth(getMaxHealth()); setCanPickUpLoot(false); combatGoal();
    }
    private void combatGoal() {
        goalSelector.removeGoal(melee); goalSelector.removeGoal(bow);
        goalSelector.addGoal(2,archer() ? bow : melee);
    }
    @Override public void performRangedAttack(LivingEntity target,float power) {
        if(!(level() instanceof ServerLevel level) || !archer() || !getMainHandItem().is(Items.BOW) || !target.isAlive()) return;
        var arrow=ProjectileUtil.getMobArrow(this,new ItemStack(Items.ARROW),power,getMainHandItem());
        arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        double dx=target.getX()-getX(),dz=target.getZ()-getZ();
        double dy=target.getY(1.0/3.0)-arrow.getY()+Math.sqrt(dx*dx+dz*dz)*0.2;
        arrow.shoot(dx,dy,dz,1.6F,5.0F); level.addFreshEntity(arrow);
        playSound(SoundEvents.ARROW_SHOOT,1.0F,1.0F/(getRandom().nextFloat()*0.4F+0.8F));
    }
    @Override protected SoundEvent getAmbientSound() { return archer() ? SoundEvents.PILLAGER_AMBIENT : SoundEvents.VINDICATOR_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource damage) { return SoundEvents.VINDICATOR_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VINDICATOR_DEATH; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output); output.putString("vc_barbarian_role",role().name());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        WavePlan.Attacker role;
        try { role=WavePlan.Attacker.valueOf(input.getStringOr("vc_barbarian_role","FOOT_SOLDIER")); }
        catch(IllegalArgumentException e) { role=WavePlan.Attacker.FOOT_SOLDIER; }
        entityData.set(ROLE,role.ordinal()); combatGoal();
    }
}

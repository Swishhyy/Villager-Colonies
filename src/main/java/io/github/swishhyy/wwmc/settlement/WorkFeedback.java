package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

/** Small local cues, called only while a job is advancing. No items or durability are consumed by an animation. */
public final class WorkFeedback {
    public static final int NONE=0,MINING=1,CHOPPING=2,FARMING=3,ENCHANTING=4,CRAFTING=5,SMITHING=6,PROCESSING=7,FISHING=8,BUTCHERING=9,TREATING=10,
            FISHING_CAST=11,FISHING_REEL=12,RESEARCHING=13;
    private WorkFeedback() {}
    public static void pulse(ServerLevel level,CitizenEntity worker,BlockPos at,int kind) {
        if(worker.isSleeping() || worker.recovering() || !level.hasChunkAt(at)) return;
        worker.working(kind);
        long now=level.getGameTime();
        if(worker.feedbackPulse(now)) {
            if(kind!=ENCHANTING && kind!=FISHING && kind!=FISHING_CAST && kind!=FISHING_REEL && kind!=RESEARCHING) worker.swing(InteractionHand.MAIN_HAND);
            double x=at.getX()+0.5,y=at.getY()+0.7,z=at.getZ()+0.5;
            switch(kind) {
                case MINING,CHOPPING,FARMING,BUTCHERING -> {
                    var state=level.getBlockState(at);
                    if(!state.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),x,y,z,3,0.22,0.18,0.22,0.02);
                }
                case ENCHANTING -> level.sendParticles(ParticleTypes.ENCHANT,x,y+0.6,z,4,0.4,0.25,0.4,0.35);
                case SMITHING -> level.sendParticles(ParticleTypes.CRIT,x,y+0.3,z,2,0.18,0.08,0.18,0.04);
                case PROCESSING -> level.sendParticles(ParticleTypes.SMOKE,x,y+0.4,z,2,0.1,0.12,0.1,0.01);
                case FISHING -> level.sendParticles(ParticleTypes.BUBBLE,x,y+0.25,z,2,0.12,0.02,0.12,0);
                case RESEARCHING -> level.sendParticles(ParticleTypes.ENCHANT,x,y+0.4,z,1,0.15,0.05,0.15,0.02);
                case TREATING -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER,x,y+0.5,z,2,0.2,0.2,0.2,0);
                default -> { }
            }
        }
        if(worker.feedbackSound(now)) {
            switch(kind) {
                case MINING,CHOPPING,FARMING -> worker.playSound(level.getBlockState(at).getSoundType().getHitSound(),0.18F,0.95F+worker.getRandom().nextFloat()*0.1F);
                case ENCHANTING -> worker.playSound(SoundEvents.ENCHANTMENT_TABLE_USE,0.15F,1.25F);
                case SMITHING -> worker.playSound(SoundEvents.ANVIL_USE,0.15F,1.1F);
                case PROCESSING -> worker.playSound(SoundEvents.FURNACE_FIRE_CRACKLE,0.15F,1.0F);
                default -> { }
            }
        }
    }
}

package io.github.swishhyy.wwmc.settlement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Consent permits narrowly scoped player combat. It never opens storage, block placement, citizen attacks or research. */
public final class MultiplayerCombat {
    public static boolean claimException(ServerLevel level,Player attacker,net.minecraft.world.entity.Entity target) {
        return target instanceof Player defender && (PlayerDuels.allows(level,attacker,defender) || OutpostContests.allows(level,attacker,defender));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void attack(AttackEntityEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && event.getTarget() instanceof LivingEntity living
                && PlayerDuels.blocks(level,event.getEntity(),living)) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void incoming(LivingIncomingDamageEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level && event.getSource().getEntity() instanceof Player attacker
                && PlayerDuels.blocks(level,attacker,event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void finalDamage(LivingDamageEvent.Pre event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level=player.level();
        var duel=MultiplayerData.get(level).duel(player.getUUID());
        if(duel==null || !duel.accepted) return;
        if(!(event.getSource().getEntity() instanceof Player attacker) || !PlayerDuels.allows(level,attacker,player)) {
            PlayerDuels.finish(level,duel,null,"Outside interference"); return;
        }
        // Armor and effects have already reduced this value; absorption is consumed after this event.
        float maximum=Math.max(0,player.getHealth()+player.getAbsorptionAmount()-2);
        if(event.getNewDamage()>=maximum) {
            event.setNewDamage(maximum);
            PlayerDuels.finish(level,duel,attacker.getUUID(),"Opponent reached one heart");
        }
    }
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level=player.level();
            var duel=MultiplayerData.get(level).duel(player.getUUID());
            if(duel!=null && duel.accepted) PlayerDuels.finish(level,duel,duel.other(player.getUUID()),"Participant died");
        }
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime()%20!=0) return;
        PlayerContracts.cleanup(level); PlayerDuels.tick(level); OutpostContests.tick(level);
    }
}

package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Experienced guards hit harder and shrug off a little damage. Hostiles a citizen
 * kills give it guard experience, and the loss of a seasoned citizen is written in the town journal.
 */
public final class CitizenCombat {
    @SubscribeEvent public void damage(LivingIncomingDamageEvent event) {
        if(event.getSource().getEntity() instanceof CitizenEntity attacker && attacker.skillRole()==StructureRole.GUARD) {
            int bonus=CitizenSkill.guardDamage(attacker.skillLevel(StructureRole.GUARD));
            if(bonus>0) event.setAmount(event.getAmount()*(100+bonus)/100F);
        }
        if(event.getEntity() instanceof CitizenEntity defender && defender.skillRole()==StructureRole.GUARD) {
            int protection=CitizenSkill.guardProtection(defender.skillLevel(StructureRole.GUARD));
            if(protection>0) event.setAmount(event.getAmount()*Math.max(0,100-protection)/100F);
        }
    }
    @SubscribeEvent public void died(LivingDeathEvent event) {
        if(!(event.getEntity().level() instanceof ServerLevel level)) return;
        if(event.getEntity() instanceof Enemy && event.getSource().getEntity() instanceof CitizenEntity killer && killer.skillRole()==StructureRole.GUARD)
            killer.gainExperience(StructureRole.GUARD,3);
        if(event.getEntity() instanceof CitizenEntity lost) {
            Settlement town=lost.town(level);
            StructureRole best=null;
            for(StructureRole role:StructureRole.values()) if(best==null || lost.experience(role)>lost.experience(best)) best=role;
            int skill=best==null ? 0 : lost.skillLevel(best);
            if(town!=null && skill>=2) {
                String title=CitizenSkill.title(skill);
                CampaignService.record(level,town,"Lost "+lost.getName().getString()+", "+("AEIOU".indexOf(title.charAt(0))>=0 ? "an " : "a ")+title+" "
                        +best.title().toLowerCase(Locale.ROOT)+". Experienced citizens are worth bringing home.");
            }
        }
    }
}

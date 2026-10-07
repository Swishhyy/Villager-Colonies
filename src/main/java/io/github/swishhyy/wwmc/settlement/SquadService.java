package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Squads borrow existing guards, retaining their station assignments and real equipment. No duplicate soldiers. */
public final class SquadService {
    public static final List<String> ORDERS=List.of("follow","hold","defend","retreat");
    private SquadService() {}
    public static int limit(Settlement town) { return town.campaign.projects.contains("training") ? 6 : 4; }
    public static String muster(ServerLevel level,Settlement town,ServerPlayer leader,int requested) {
        if(!TownAccess.manages(town,leader.getUUID())) return "You need steward permission to lead this town's soldiers.";
        if(!town.campaign.projects.contains("armory")) return "Complete the Armory project at the town banner first.";
        if(requested<1 || requested>limit(town)) return "Choose 1 to "+limit(town)+" guards.";
        if(town.campaign.squads.stream().anyMatch(s -> s.leader().equals(leader.getUUID()))) return "You already lead a squad from this town. Release it before mustering another.";
        if(town.campaign.squads.size()>=8) return "This town already has eight squads deployed. Release one before forming another.";
        var available=DefenseService.loadedCitizens(level,town).stream().filter(c -> c.isAlive() && c.isGuard()
                && town.campaign.squad(c.getUUID())==null && c.distanceToSqr(leader)<64*64 && c.getHealth()>c.getMaxHealth()*0.5F
                && GuardWeapons.weapon(c.getMainHandItem())).sorted(Comparator.comparingDouble(c -> c.distanceToSqr(leader))).limit(requested).toList();
        if(available.size()<requested) return "Only "+available.size()+" equipped, healthy guards are available nearby. Supply the Guard Stations first.";
        for(CitizenEntity guard:available) guard.wakeForAlarm();
        town.campaign.squads.add(new CampaignState.Squad(leader.getUUID(),available.stream().map(CitizenEntity::getUUID).toList(),"follow",leader.blockPosition(),Optional.empty()));
        CampaignService.record(level,town,leader.getName().getString()+" mustered "+available.size()+" guards for an expedition.");
        return "Squad ready. Lead on, or choose hold, defend, escort or retreat.";
    }
    public static String order(ServerLevel level,Settlement town,ServerPlayer leader,String order) {
        if(!TownAccess.manages(town,leader.getUUID())) return "You no longer manage this town.";
        for(int n=0;n<town.campaign.squads.size();n++) {
            var squad=town.campaign.squads.get(n); if(!squad.leader().equals(leader.getUUID())) continue;
            if(order.equals("release")) { town.campaign.squads.set(n,squad.command("return",town.center)); }
            else if(order.equals("escort")) {
                if(!town.campaign.projects.contains("depot")) return "Complete a Transport Depot before assigning convoy escorts.";
                if(town.trading.runner==null) return "No loaded trader is travelling from this town.";
                town.campaign.squads.set(n,new CampaignState.Squad(squad.leader(),squad.guards(),"escort",town.center,Optional.of(town.trading.runner)));
            } else if(ORDERS.contains(order)) town.campaign.squads.set(n,squad.command(order,order.equals("retreat") ? town.center : leader.blockPosition()));
            else return "Choose follow, hold, defend, retreat, escort or release.";
            CampaignService.record(level,town,"Squad order: "+order+"."); return "Squad order: "+order+".";
        }
        return "Muster a squad first.";
    }
    public static boolean assigned(Settlement town,UUID guard) { return town!=null && town.campaign.squad(guard)!=null; }
    public static boolean act(ServerLevel level,Settlement town,CitizenEntity guard) {
        var squad=town.campaign.squad(guard.getUUID()); if(squad==null) return false;
        guard.wakeForAlarm();
        if(guard.bag().isOpen()) { guard.getNavigation().stop(); guard.workActivity("Waiting while my inventory is open"); return true; }
        ServerPlayer leader=level.getServer().getPlayerList().getPlayer(squad.leader());
        boolean returning=squad.order().equals("return") || squad.order().equals("retreat") || leader==null || leader.level()!=level || !leader.isAlive()
                || !TownAccess.manages(town,squad.leader()) || guard.getHealth()<guard.getMaxHealth()*0.3F;
        BlockPos target=returning ? town.center : squad.rally();
        CitizenEntity escort=null;
        if(!returning && squad.order().equals("escort")) {
            if(squad.escort().isPresent() && level.getEntity(squad.escort().get()) instanceof CitizenEntity trader && trader.isAlive()) escort=trader;
            else returning=true;
            target=returning ? town.center : escort.blockPosition();
        } else if(!returning && squad.order().equals("follow")) target=leader.blockPosition();
        boolean fight=!returning && !squad.order().equals("hold");
        if(!returning && squad.order().equals("hold") && guard.getLastHurtByMob() instanceof Monster attacker && attacker.isAlive()
                && guard.tickCount-guard.getLastHurtByMobTimestamp()<100 && guard.hasLineOfSight(attacker)
                && guard.distanceToSqr(attacker)<=CitizenReach.BLOCKS*CitizenReach.BLOCKS) {
            guard.expeditionFight(level,attacker); guard.workActivity("Holding position; defending myself"); return true;
        }
        if(fight) {
            Monster enemy=level.getEntitiesOfClass(Monster.class,guard.getBoundingBox().inflate(20),m -> m.isAlive()
                    && (!squad.order().equals("defend") || m.blockPosition().distSqr(squad.rally())<=24*24)
                    && guard.hasLineOfSight(m)).stream().min(Comparator.comparingDouble(guard::distanceToSqr)).orElse(null);
            if(enemy!=null) { guard.expeditionFight(level,enemy); guard.workActivity("Defending the squad from "+enemy.getName().getString()); return true; }
        }
        guard.setTarget(null);
        if(guard.isUsingItem()) guard.stopUsingItem();
        int index=squad.guards().indexOf(guard.getUUID());
        BlockPos formation=target.offset((index%3-1)*2,0,2+index/3*2);
        double distance=guard.distanceToSqr(Vec3.atCenterOf(target));
        if(returning && distance<100) {
            if(squad.order().equals("retreat") && leader!=null && leader.level()==level && guard.getHealth()>=guard.getMaxHealth()*0.3F) {
                guard.getNavigation().stop(); guard.workActivity("Regrouped at home; awaiting new orders"); return true;
            }
            remove(level,town,guard.getUUID()); guard.workActivity("Returned to normal guard duty"); return false;
        }
        if(escort!=null && guard.distanceToSqr(escort)>28*28) escort.getNavigation().stop();
        if(distance>16 || squad.order().equals("hold") && guard.distanceToSqr(Vec3.atCenterOf(formation))>4) {
            guard.expeditionWalk(level,formation); guard.workActivity(returning ? "Returning home with the squad" : "Squad: "+squad.order());
        } else { guard.getNavigation().stop(); guard.workActivity("Squad: "+squad.order()+"; in position"); }
        return true;
    }
    public static void remove(ServerLevel level,Settlement town,UUID guard) {
        for(int n=town.campaign.squads.size()-1;n>=0;n--) {
            var s=town.campaign.squads.get(n); if(!s.guards().contains(guard)) continue;
            List<UUID> remaining=s.guards().stream().filter(id -> !id.equals(guard)).toList();
            if(remaining.isEmpty()) town.campaign.squads.remove(n);
            else town.campaign.squads.set(n,new CampaignState.Squad(s.leader(),remaining,s.order(),s.rally(),s.escort()));
            SettlementData.get(level).setDirty();
        }
    }
    public static boolean convoyReady(ServerLevel level,Settlement town,CitizenEntity trader) {
        for(var squad:town.campaign.squads) if(squad.order().equals("escort") && squad.escort().filter(trader.getUUID()::equals).isPresent())
            for(UUID id:squad.guards()) if(level.getEntity(id) instanceof CitizenEntity guard && guard.isAlive() && guard.distanceToSqr(trader)>28*28) return false;
        return true;
    }
}

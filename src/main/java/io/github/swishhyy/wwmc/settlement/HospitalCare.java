package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Patients reserve actual hospital beds; a present medic spends one meal and one paper dressing per treatment. */
public final class HospitalCare {
    private HospitalCare() {}
    public static boolean needsCare(Settlement town,CitizenEntity citizen) {
        if(town!=null) { BlockPos job=town.jobs.home(citizen.getUUID()); Station station=job==null ? null : town.station(job);
            if(station!=null && station.role()==StructureRole.HOSPITAL) return false; }
        return town!=null && town.campaign.projects.contains("hospital") && (citizen.recovering() || citizen.getHealth()<citizen.getMaxHealth()*0.6F)
                && town.stations.stream().anyMatch(s -> s.role()==StructureRole.HOSPITAL);
    }
    public static boolean patient(ServerLevel level,Settlement town,CitizenEntity citizen) {
        if(!needsCare(town,citizen)) return false;
        if(citizen.getHealth()>=citizen.getMaxHealth()*0.95F) { citizen.recovering(false); return false; }
        if(SquadService.assigned(town,citizen.getUUID())) return false;
        List<BlockPos> beds=new ArrayList<>();
        for(Station station:town.stations) if(station.role()==StructureRole.HOSPITAL && SettlementService.active(level,station)) beds.addAll(SettlementService.beds(level,town,station));
        beds.sort(Comparator.comparingDouble(p -> citizen.distanceToSqr(Vec3.atCenterOf(p))));
        for(BlockPos bed:beds) if(SettlementService.reservations(level).claim(bed,citizen.getUUID(),level.getGameTime(),200)) {
            citizen.recovering(true); citizen.wakeForAlarm();
            if(citizen.workAt(level,bed)) { citizen.getNavigation().stop(); citizen.workActivity("At a hospital bed, awaiting the medic and supplies"); }
            else { citizen.workWalk(bed); citizen.workActivity("Walking to a hospital bed for treatment"); }
            return true;
        }
        citizen.workActivity("Hospital patient beds are occupied or unloaded"); return false;
    }
    public static void medic(ServerLevel level,Settlement town,Station station,CitizenEntity medic) {
        if(!town.campaign.projects.contains("hospital")) { medic.workActivity("Complete the Field Hospital project at the banner"); return; }
        List<BlockPos> beds=SettlementService.beds(level,town,station);
        CitizenEntity patient=medic.getHealth()<medic.getMaxHealth()*0.6F ? medic : DefenseService.loadedCitizens(level,town).stream().filter(c -> c!=medic && c.isAlive() && c.recovering()
                && c.getHealth()<c.getMaxHealth()*0.95F && beds.stream().anyMatch(b -> c.workAt(level,b)))
                .min(Comparator.comparingDouble(CitizenEntity::getHealth)).orElse(null);
        if(patient==null) { medic.workActivity("Hospital ready; no patient awaiting treatment"); return; }
        if(patient!=medic && (medic.distanceToSqr(patient)>CitizenReach.BLOCKS*CitizenReach.BLOCKS || !medic.hasLineOfSight(patient))) {
            medic.workWalk(patient.blockPosition()); medic.workActivity("Walking to treat "+patient.getName().getString()); return;
        }
        if(Math.floorMod(level.getGameTime()/10,10)!=Math.floorMod(medic.getId(),10)) return;
        List<Container> stock=SettlementService.jobStorage(level,town,station);
        if(InventoryOps.count(stock,FoodHealing::food)==0 || InventoryOps.count(stock,s -> s.is(Items.PAPER))==0) {
            medic.workActivity("Needs meals and paper dressings in the hospital barrel; couriers deliver them"); return;
        }
        FoodHealing.take(stock,medic.bag()::offer); InventoryOps.takeOne(stock,s -> s.is(Items.PAPER));
        patient.heal(4); medic.workActivity("Treated "+patient.getName().getString()+" with a meal and a dressing");
        if(patient.getHealth()>=patient.getMaxHealth()*0.95F) {
            patient.recovering(false); CampaignService.record(level,town,patient.getName().getString()+" recovered at the hospital and returned to duty.");
        }
    }
}

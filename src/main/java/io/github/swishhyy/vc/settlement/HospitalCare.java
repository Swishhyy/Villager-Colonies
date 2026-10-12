package io.github.swishhyy.vc.settlement;

import io.github.swishhyy.vc.core.StructureRole;
import io.github.swishhyy.vc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Badly injured citizens recover in a reserved hospital bed after combat, with optional assistance from a medic. Lighter
 * wounds do not interrupt work: they mend while the citizen sleeps in an ordinary bed.
 */
public final class HospitalCare {
    public static final int HEAL_TICKS=100;
    /** Below this share of full health a citizen leaves work for a hospital bed; once admitted, it stays until full. */
    public static final float ADMIT_SHARE=0.5F;
    /** Ticks of ordinary sleep per health point for lighter wounds, half the speed of a hospital bed. */
    public static final int REST_HEAL_TICKS=2*HEAL_TICKS;
    private HospitalCare() {}
    public static boolean needsCare(Settlement town,CitizenEntity citizen) {
        return town!=null && (citizen.recovering() || citizen.hospitalBed()!=null || seriouslyInjured(citizen))
                && !citizen.inCombat();
    }
    /** A fall or a scratch is not enough to abandon a trade trip or a job; losing half of one's health is. */
    public static boolean seriouslyInjured(CitizenEntity citizen) { return citizen.getHealth()<citizen.getMaxHealth()*ADMIT_SHARE; }
    public static boolean inBed(CitizenEntity citizen) {
        return citizen.hospitalBed()!=null && citizen.isSleeping() && citizen.getSleepingPos().filter(citizen.hospitalBed()::equals).isPresent();
    }
    private static void finish(ServerLevel level,Settlement town,CitizenEntity citizen) {
        boolean rested=citizen.hospitalBed()!=null;
        citizen.leaveHospitalBed(); citizen.recovering(false);
        if(rested) CampaignService.journal(level,town,citizen.getName().getString()+" recovered at the hospital and returned to duty.");
        if(rested) TutorialProgress.record(level,town,"recovery");
    }
    /** Called from the entity tick as well as its awake AI: sleeping villagers suspend their normal work goal. */
    public static boolean patient(ServerLevel level,Settlement town,CitizenEntity citizen) {
        if(!needsCare(town,citizen)) return false;
        if(citizen.getHealth()>=citizen.getMaxHealth()) { finish(level,town,citizen); return false; }
        if(!citizen.recovering()) { citizen.pauseForHospital(level); citizen.recovering(true); }
        List<BlockPos> beds=new ArrayList<>();
        for(Station station:town.stations) if(station.role()==StructureRole.HOSPITAL && SettlementService.active(level,station))
            beds.addAll(SettlementService.beds(level,town,station));
        if(citizen.hospitalBed()!=null && !beds.contains(citizen.hospitalBed())) citizen.leaveHospitalBed();
        beds.sort(Comparator.comparingDouble(p -> p.equals(citizen.hospitalBed()) ? -1 : citizen.distanceToSqr(Vec3.atCenterOf(p))));
        for(BlockPos bed:beds) {
            if(!citizen.hospitalCanTry(bed) || level.getBlockState(bed).getValue(BedBlock.OCCUPIED) && !bed.equals(citizen.hospitalBed())) continue;
            if(!SettlementService.reservations(level).claim(bed,citizen.getUUID(),level.getGameTime(),200)) continue;
            citizen.hospitalBed(bed);
            if(!citizen.hospitalRestAt(level,town,bed)) { citizen.leaveHospitalBed(); continue; }
            if(inBed(citizen)) {
                citizen.workActivity("Resting in hospital until fully healed ("+Math.round(citizen.getHealth())+"/"+Math.round(citizen.getMaxHealth())+")");
                if(citizen.hospitalHealingPulse()) citizen.heal(Research.has(town,"field_medicine") ? 2 : 1);
                if(citizen.getHealth()>=citizen.getMaxHealth()) finish(level,town,citizen);
            } else citizen.workActivity("Walking to a hospital bed for recovery");
            return true;
        }
        citizen.hospitalMeal(level,town);
        citizen.workActivity("Needs a free, reachable hospital bed to recover");
        return true;
    }
    public static void medic(ServerLevel level,Settlement town,Station station,CitizenEntity medic) {
        if(!town.campaign.projects.contains("hospital")) { medic.workActivity("Complete the Field Hospital project for assisted treatment"); return; }
        List<BlockPos> beds=SettlementService.beds(level,town,station);
        CitizenEntity patient=DefenseService.loadedCitizens(level,town).stream().filter(c -> c!=medic && c.isAlive() && inBed(c) && !c.inCombat()
                && c.getHealth()<c.getMaxHealth() && beds.contains(c.hospitalBed()))
                .min(Comparator.comparingDouble(CitizenEntity::getHealth)).orElse(null);
        if(patient==null) { medic.workActivity("Hospital ready; no patient awaiting treatment"); return; }
        if(medic.distanceToSqr(patient)>CitizenReach.BLOCKS*CitizenReach.BLOCKS || !medic.hasLineOfSight(patient)) {
            medic.workWalk(patient.blockPosition()); medic.workActivity("Walking to treat "+patient.getName().getString()); return;
        }
        if(Math.floorMod(level.getGameTime()/10,HEAL_TICKS/10)!=Math.floorMod(medic.getId(),HEAL_TICKS/10)) return;
        List<Container> stock=SettlementService.jobStorage(level,town,station);
        if(InventoryOps.count(stock,FoodHealing::food)==0 || InventoryOps.count(stock,s -> s.is(Items.PAPER))==0) {
            medic.workActivity("Beds heal slowly; meals and paper dressings let the medic help"); return;
        }
        FoodHealing.take(stock,medic.bag()::offer); InventoryOps.takeOne(stock,s -> s.is(Items.PAPER));
        patient.heal(1); medic.workActivity("Treated "+patient.getName().getString()+" with a meal and a dressing");
        WorkFeedback.pulse(level,medic,patient.blockPosition(),WorkFeedback.TREATING);
        if(patient.getHealth()>=patient.getMaxHealth()) finish(level,town,patient);
    }
}

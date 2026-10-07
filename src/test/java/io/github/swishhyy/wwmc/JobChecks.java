package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Job priorities, their presets, and the job each citizen keeps. */
public final class JobChecks {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }

    @Test void presets() {
        JobBoard balanced=JobBoard.preset("balanced");
        check(balanced.level(StructureRole.GUARD)==JobBoard.HIGH && balanced.level(StructureRole.TRADER)==JobBoard.HIGH,"Guards and traders come first by default");
        check(balanced.level(StructureRole.FARM)==JobBoard.NORMAL && balanced.level(StructureRole.MINE)==JobBoard.NORMAL,"Other jobs are normal");
        check(balanced.level(StructureRole.HOUSING)==JobBoard.OFF,"Stations without work have no priority");
        JobBoard food=JobBoard.preset("food");
        check(food.level(StructureRole.FARM)==JobBoard.HIGH && food.level(StructureRole.COOK)==JobBoard.HIGH && food.level(StructureRole.LUMBER)==JobBoard.NORMAL,
                "The food preset puts farms and cooks first");
        JobBoard materials=JobBoard.preset("materials");
        check(materials.level(StructureRole.FARM)==JobBoard.LOW && materials.level(StructureRole.QUARRY)==JobBoard.NORMAL,"The materials preset puts farms and cooks last");
        check(JobBoard.preset(JobBoard.CUSTOM).matchingPreset().equals("balanced"),"Unknown presets start balanced");
        food.setLevel(StructureRole.MINE,JobBoard.HIGH);
        check(food.matchingPreset().equals(JobBoard.CUSTOM),"Changing one job makes the priorities custom");
        food.setLevel(StructureRole.MINE,JobBoard.NORMAL);
        check(food.matchingPreset().equals("food"),"Changing it back matches the preset again");
        food.setLevel(StructureRole.MINE,9);
        check(food.level(StructureRole.MINE)==JobBoard.HIGH && JobBoard.levelName(-1).equals("Off"),"Levels stay between Off and High");
        int revision=food.revision();
        food.apply("balanced");
        check(food.revision()>revision && food.matchingPreset().equals("balanced"),"Applying a preset sets every job, and citizens notice the change");
        System.out.println("Passed "+checks+" job preset checks.");
    }

    @Test void assignments() {
        BlockPos farm=new BlockPos(10,64,0),mine=new BlockPos(20,64,0),guard=new BlockPos(30,64,0),house=new BlockPos(40,64,0);
        UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3),gone=new UUID(0,4);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Job town",BlockPos.ZERO,240,List.of(a,b,c),
                List.of(new Station(farm,StructureRole.FARM),new Station(mine,StructureRole.MINE),new Station(guard,StructureRole.GUARD),new Station(house,StructureRole.HOUSING)),"balanced");
        JobBoard jobs=town.jobs;
        jobs.assign(a,mine); jobs.assign(b,mine); jobs.assign(c,farm); jobs.assign(gone,farm);
        check(mine.equals(jobs.home(a)) && jobs.assigned(mine)==2 && jobs.crew(mine).equals(List.of(a,b)),"Each citizen keeps one station; a crew lists in a fixed order");
        check(jobs.holdsPlace(a,town.station(mine),1) && !jobs.holdsPlace(b,town.station(mine),1),"When a crew shrinks, the same citizens keep their places");
        check(jobs.prune(town,station -> 1) && jobs.home(gone)==null,"Citizens who left lose their job");
        check(jobs.home(b)==null && mine.equals(jobs.home(a)) && farm.equals(jobs.home(c)),"Only places beyond a crew's size are freed");
        check(!jobs.prune(town,station -> 1),"A tidy board is left alone");
        town.stations.removeIf(station -> station.position().equals(farm));
        check(jobs.prune(town,station -> 5) && jobs.home(c)==null,"A removed station frees its crew");
        jobs.assign(b,mine);
        jobs.setLevel(StructureRole.MINE,JobBoard.OFF);
        check(jobs.releaseRole(town,StructureRole.MINE)==2 && jobs.home(a)==null && jobs.home(b)==null,"Switching a job off frees everyone in it");
        jobs.assign(a,mine);
        check(jobs.prune(town,station -> 5) && jobs.home(a)==null,"Nobody stays in a job that is switched off");
        System.out.println("Passed "+checks+" job assignment checks.");
    }

    @Test void openOrder() {
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Order town",BlockPos.ZERO,240,List.of(),List.of(),"balanced");
        Station farm=new Station(new BlockPos(1,64,0),StructureRole.FARM),mine=new Station(new BlockPos(2,64,0),StructureRole.MINE);
        Station guard=new Station(new BlockPos(3,64,0),StructureRole.GUARD),trader=new Station(new BlockPos(4,64,0),StructureRole.TRADER);
        JobBoard jobs=town.jobs;
        List<Station> order=new ArrayList<>(List.of(farm,mine,trader,guard));
        order.sort(jobs.openOrder());
        check(order.get(0)==guard && order.get(1)==trader,"Among equal priorities, guard posts fill first, then the trader");
        jobs.assign(UUID.randomUUID(),farm.position());
        order.sort(jobs.openOrder());
        check(order.get(2)==mine && order.get(3)==farm,"Among equal priorities, the emptier station fills first");
        jobs.setLevel(StructureRole.FARM,JobBoard.HIGH); jobs.setLevel(StructureRole.GUARD,JobBoard.LOW);
        order.sort(jobs.openOrder());
        check(order.get(0)==trader && order.get(1)==farm && order.get(3)==guard,"Higher priority always fills first");
        System.out.println("Passed "+checks+" job order checks.");
    }

    @Test void saved() {
        BlockPos mine=new BlockPos(5,64,5);
        UUID miner=UUID.randomUUID();
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Saved jobs",BlockPos.ZERO,240,List.of(miner),List.of(new Station(mine,StructureRole.MINE)),"food");
        town.jobs.assign(miner,mine); town.jobs.setLevel(StructureRole.QUARRY,JobBoard.OFF);
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement reloaded=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(mine.equals(reloaded.jobs.home(miner)) && reloaded.jobs.level(StructureRole.QUARRY)==JobBoard.OFF
                && reloaded.jobs.level(StructureRole.FARM)==JobBoard.HIGH,"Jobs and priorities survive a restart");
        check(reloaded.name.equals("Saved jobs") && reloaded.stations.size()==1 && reloaded.citizens.equals(List.of(miner)) && reloaded.priority.equals("food"),
                "The rest of the town still loads beside its jobs");
        var legacy=json.getAsJsonObject();
        legacy.remove("jobs");
        Settlement old=Settlement.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(old.jobs.home(miner)==null && old.jobs.level(StructureRole.FARM)==JobBoard.HIGH && old.jobs.level(StructureRole.QUARRY)==JobBoard.NORMAL,
                "A town from before job priorities takes its preset's levels");
        System.out.println("Passed "+checks+" job save checks.");
    }
}

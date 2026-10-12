package io.github.swishhyy.vc;

import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public final class WorldGenerationChecks {
    @Test void untouchedLowCornerDoesNotExcavateFlatVillagePaths() {
        var ground=new ArrayList<>(Collections.nCopies(NpcVillageLayout.CELLS,0));
        ground.set(NpcVillageLayout.index(-23,-23),-20);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Natural paths",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=new NpcVillageLayout("oak",ground,ground);
        var paths=NpcVillagePlan.blueprint(town).stream().filter(p -> p.state().is(Blocks.DIRT_PATH)).toList();
        assertFalse(paths.isEmpty());
        assertTrue(paths.stream().allMatch(p -> p.pos().getY()==-1),"An untouched corner drags level streets into trenches");
        assertNull(NpcVillagePlan.earthworkIssue(town));
    }
    @Test void walledVillageGradesRollingGroundWithoutSteps() {
        var ground=new ArrayList<Integer>(NpcVillageLayout.CELLS);
        for(int x=-NpcVillageLayout.EXTENT;x<=NpcVillageLayout.EXTENT;x++) for(int z=-NpcVillageLayout.EXTENT;z<=NpcVillageLayout.EXTENT;z++)
            ground.add((int)Math.round(4*Math.sin(x/9.0)+3*Math.cos(z/11.0)));
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Rolling",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=new NpcVillageLayout("oak",ground,ground);
        assertNull(NpcVillagePlan.earthworkIssue(town),"Gently rolling ground must be accepted");
        int[] grade=NpcVillagePlan.grade(town); int e=NpcVillageLayout.EXTENT;
        for(int x=-e;x<=e;x++) for(int z=-e;z<=e;z++) {
            int here=grade[NpcVillageLayout.index(x,z)];
            if(x<e) assertTrue(Math.abs(here-grade[NpcVillageLayout.index(x+1,z)])<=1,"A step of more than one block at "+x+","+z);
            if(z<e) assertTrue(Math.abs(here-grade[NpcVillageLayout.index(x,z+1)])<=1,"A step of more than one block at "+x+","+z);
            if(Math.max(Math.abs(x),Math.abs(z))==e) assertTrue(Math.abs(here-ground.get(NpcVillageLayout.index(x,z)))<=1,"The verge does not meet the land around it");
        }
        for(var lot:NpcVillagePlan.lots(NpcVillageLayout.WALLED)) {
            int level=grade[NpcVillageLayout.index(lot.x(),lot.z())];
            for(int x=lot.x()-lot.radiusX();x<=lot.x()+lot.radiusX();x++) for(int z=lot.z()-lot.radiusZ();z<=lot.z()+lot.radiusZ();z++)
                assertEquals(level,grade[NpcVillageLayout.index(x,z)],"A building lot is not level");
        }
        var hill=new ArrayList<Integer>(NpcVillageLayout.CELLS);
        for(int x=-e;x<=e;x++) for(int z=-e;z<=e;z++) hill.add(x/2);
        town.trading.layout=new NpcVillageLayout("oak",hill,hill);
        assertNotNull(NpcVillagePlan.earthworkIssue(town),"A steep hillside must not be terraced into a village");
    }
    @Test void walledVillageHasGatesTowersAndSentries() {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Walled",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=NpcVillageLayout.flat("oak");
        var states=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:NpcVillagePlan.blueprint(town)) states.put(p.pos(),p.state());
        int wall=NpcVillagePlan.WALL;
        for(var gate:NpcVillagePlan.GATES) {
            var outside=NpcVillagePlan.gateOffset(gate.out());
            assertTrue(states.get(new BlockPos(gate.x(),-1,gate.z())).is(Blocks.DIRT_PATH),"A gate has no road through it");
            // Three blocks of headroom; a lantern hangs beneath the lintel.
            for(int y=0;y<NpcVillagePlan.WALL_HEIGHT-1;y++) assertTrue(states.get(new BlockPos(gate.x(),y,gate.z())).isAir(),"A gate is blocked");
            assertTrue(states.get(new BlockPos(outside.x()-gate.out().getStepX(),-1,outside.z()-gate.out().getStepZ())).is(Blocks.DIRT_PATH),"The road does not leave the gate");
        }
        for(int n=-20;n<=20;n+=8) {
            if(NpcVillagePlan.GATES.stream().anyMatch(g -> Math.abs(g.x()-(-wall))<=0 && Math.abs(g.z()-n)<=2)) continue;
            for(int y=0;y<NpcVillagePlan.WALL_HEIGHT;y++) assertFalse(states.get(new BlockPos(-wall,y,n)).isAir(),"The west wall has a gap at "+n);
        }
        for(var tower:NpcVillagePlan.TOWERS)
            assertTrue(states.get(new BlockPos(tower.x(),NpcVillagePlan.TOWER_HEIGHT,tower.z()+Integer.signum(tower.z()))).is(Blocks.LADDER),"A corner tower cannot be climbed");
        var guards=NpcSettlements.stations(town).stream().filter(s -> s.role()==io.github.swishhyy.vc.core.StructureRole.GUARD).toList();
        assertEquals(1+NpcVillagePlan.SENTRIES.size(),guards.size(),"Every gate needs its sentry");
        for(var guard:guards) assertTrue(states.get(guard.position()).getBlock() instanceof io.github.swishhyy.vc.block.StationBlock,"A guard post has no station block");
    }
    @Test void laterFoundingWaitsForElapsedTimeAndSupportsImmediateOverride() {
        int old=Config.NPC_FOUNDING_DAYS.get(); boolean enabled=Config.RANDOM_TOWNS.get();
        try {
            Config.RANDOM_TOWNS.set(true); Config.NPC_FOUNDING_DAYS.set(3);
            assertFalse(NpcSettlements.mayFoundNewTown(71999)); assertTrue(NpcSettlements.mayFoundNewTown(72000));
            Config.NPC_FOUNDING_DAYS.set(0); assertTrue(NpcSettlements.mayFoundNewTown(0));
            Config.RANDOM_TOWNS.set(false); assertFalse(NpcSettlements.mayFoundNewTown(72000));
        } finally { Config.NPC_FOUNDING_DAYS.set(old); Config.RANDOM_TOWNS.set(enabled); }
    }
    @Test void largeExcavationIsRejectedAndStructureIdentityIsStable() {
        // A real hill, not a lone spike: those are smoothed away and simply cut down.
        var ground=new ArrayList<>(Collections.nCopies(NpcVillageLayout.CELLS,0));
        for(int x=-16;x<=-10;x++) for(int z=-16;z<=-10;z++) ground.set(NpcVillageLayout.index(x,z),20);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Cliff",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=new NpcVillageLayout("oak",ground,ground);
        assertNotNull(NpcVillagePlan.earthworkIssue(town),"A twenty-block hill cut is accepted");
        var center=new BlockPos(-504,80,280);
        assertEquals(NpcTownPlan.worldgenCandidate(47,center),NpcTownPlan.worldgenCandidate(47,center));
        assertNotEquals(NpcTownPlan.worldgenCandidate(47,center).id(),NpcTownPlan.worldgenCandidate(47,center.east(16)).id());
    }
}

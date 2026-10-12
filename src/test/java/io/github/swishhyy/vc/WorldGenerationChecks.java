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
        var ground=new ArrayList<>(Collections.nCopies(NpcVillageLayout.CELLS,0)); ground.set(NpcVillageLayout.index(-13,-13),15);
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Cliff",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=new NpcVillageLayout("oak",ground,ground);
        assertNotNull(NpcVillagePlan.earthworkIssue(town),"A fifteen-block hill cut is accepted");
        var center=new BlockPos(-504,80,280);
        assertEquals(NpcTownPlan.worldgenCandidate(47,center),NpcTownPlan.worldgenCandidate(47,center));
        assertNotEquals(NpcTownPlan.worldgenCandidate(47,center).id(),NpcTownPlan.worldgenCandidate(47,center.east(16)).id());
    }
}

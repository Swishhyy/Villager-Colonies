package io.github.swishhyy.vc;

import io.github.swishhyy.vc.core.StructureRole;
import io.github.swishhyy.vc.settlement.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** The rare walled city, and the levelled square and streets of the villages built since. */
@ExtendWith(EphemeralTestServerProvider.class)
public final class CityChecks {
    private static Settlement settlement(NpcVillageLayout layout) {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"City",BlockPos.ZERO,120,List.of(),List.of(),"food");
        town.trading.layout=layout; return town;
    }
    private static NpcVillageLayout layout(int design,java.util.function.IntBinaryOperator height) {
        int extent=NpcVillageLayout.extent(design);
        List<Integer> ground=new ArrayList<>(NpcVillageLayout.cells(design));
        for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) ground.add(height.applyAsInt(x,z));
        return new NpcVillageLayout("oak",ground,ground,design);
    }
    private static Map<BlockPos,BlockState> states(Settlement town) {
        Map<BlockPos,BlockState> states=new HashMap<>();
        for(var p:NpcVillagePlan.blueprint(town)) assertNull(states.put(p.pos(),p.state()),"Each block has one final construction state");
        return states;
    }

    @Test void oneRegionInEachBlockOfNineOffersACity() {
        for(long seed:new long[]{42,-7,246813579}) for(int bi=-4;bi<4;bi++) for(int bj=-4;bj<4;bj++) {
            int cities=0;
            for(int i=0;i<3;i++) for(int j=0;j<3;j++) if(NpcTownPlan.cityRegion(seed,bi*3+i,bj*3+j)) cities++;
            assertEquals(1,cities,"A block of nine regions must offer exactly one city");
        }
        assertEquals(NpcTownPlan.cityRegion(42,5,-7),NpcTownPlan.cityRegion(42,5,-7));
        var center=new BlockPos(-504,80,280);
        assertTrue(NpcTownPlan.worldgenCandidate(47,center,true).name().contains(" City "));
        assertEquals(NpcTownPlan.worldgenCandidate(47,center).id(),NpcTownPlan.worldgenCandidate(47,center,true).id(),"A city keeps its site's identity");
    }

    @Test void cityHasHomesJobsWallsAndGatesForForty() {
        var town=settlement(NpcVillageLayout.flat("oak",NpcVillageLayout.CITY));
        assertNull(NpcVillagePlan.earthworkIssue(town));
        var states=states(town); int extent=NpcVillageLayout.CITY_EXTENT;
        for(var pos:states.keySet()) assertTrue(Math.abs(pos.getX())<=extent && Math.abs(pos.getZ())<=extent,"The city reaches beyond its land at "+pos);
        var stations=NpcSettlements.stations(town);
        assertEquals(32,stations.stream().filter(s -> s.role().providesWork()).count(),"A city has thirty-two work posts");
        assertEquals(1,stations.stream().filter(s -> s.role()==StructureRole.TRADER).count());
        assertEquals(2,stations.stream().filter(s -> s.role()==StructureRole.COURIER).count());
        assertEquals(12,stations.stream().filter(s -> s.role()==StructureRole.GUARD).count(),"Four watch posts and eight gate sentries");
        assertEquals(6,stations.stream().filter(s -> s.role()==StructureRole.FARM).count());
        for(var station:stations) assertTrue(states.get(station.position()).getBlock() instanceof io.github.swishhyy.vc.block.StationBlock,
                "A "+station.role()+" station has no block at "+station.position());
        var homes=stations.stream().filter(s -> s.role().providesHousing()).toList();
        int beds=0;
        for(var e:states.entrySet()) if(e.getValue().getBlock() instanceof BedBlock && e.getValue().getValue(BedBlock.PART)==BedPart.HEAD) {
            BlockPos foot=e.getKey().relative(e.getValue().getValue(BedBlock.FACING).getOpposite());
            assertTrue(StationDetection.completeBed(e.getValue(),states.get(foot)),"A bed is missing its foot at "+e.getKey());
            assertTrue(homes.stream().anyMatch(h -> h.contains(e.getKey()) && h.contains(foot)),"A bed belongs to no home at "+e.getKey());
            beds++;
        }
        assertEquals(46,beds,"Forty-six beds house forty citizens with room to grow");
        var walls=NpcVillagePlan.walls(NpcVillageLayout.CITY);
        for(var gate:walls.gates()) {
            assertTrue(states.get(new BlockPos(gate.x(),-1,gate.z())).is(Blocks.DIRT_PATH),"A city gate has no road through it");
            for(int y=0;y<walls.height()-1;y++) assertTrue(states.get(new BlockPos(gate.x(),y,gate.z())).isAir(),"A city gate is blocked");
            var outside=NpcVillagePlan.gateOffset(NpcVillageLayout.CITY,gate.out());
            assertTrue(states.get(new BlockPos(outside.x()-gate.out().getStepX(),-1,outside.z()-gate.out().getStepZ())).is(Blocks.DIRT_PATH),"The avenue does not leave the gate");
        }
        int wall=walls.wall();
        for(int row=-34;row<=34;row+=4) {
            int r=row;
            if(walls.gates().stream().anyMatch(g -> g.x()==-wall && Math.abs(g.z()-r)<=2) || walls.towers().stream().anyMatch(t -> t.x()==-wall && Math.abs(t.z()-r)<=2)) continue;
            for(int y=0;y<walls.height();y++) assertFalse(states.get(new BlockPos(-wall,y,row)).isAir(),"The west wall has a gap at "+row);
        }
        for(var tower:walls.towers())
            assertTrue(states.get(new BlockPos(tower.x(),walls.towerHeight(),tower.z()+Integer.signum(tower.z()))).is(Blocks.LADDER),"A city tower cannot be climbed");
    }

    @Test void cityGradesRollingGroundAndRejectsSteepHills() {
        // Ten blocks of relief: within what a site may have before it is graded at all.
        var town=settlement(layout(NpcVillageLayout.CITY,(x,z) -> (int)Math.round(3*Math.sin(x/13.0)+2*Math.cos(z/15.0))));
        assertNull(NpcVillagePlan.earthworkIssue(town),"Gently rolling ground must hold a city");
        int[] grade=NpcVillagePlan.grade(town); int e=NpcVillageLayout.CITY_EXTENT,d=NpcVillageLayout.CITY;
        for(int x=-e;x<=e;x++) for(int z=-e;z<=e;z++) {
            int here=grade[NpcVillageLayout.index(d,x,z)];
            if(x<e) assertTrue(Math.abs(here-grade[NpcVillageLayout.index(d,x+1,z)])<=1,"A step of more than one block at "+x+","+z);
            if(z<e) assertTrue(Math.abs(here-grade[NpcVillageLayout.index(d,x,z+1)])<=1,"A step of more than one block at "+x+","+z);
        }
        for(var lot:NpcVillagePlan.lots(d)) {
            int level=grade[NpcVillageLayout.index(d,lot.x(),lot.z())];
            for(int x=lot.x()-lot.radiusX();x<=lot.x()+lot.radiusX();x++) for(int z=lot.z()-lot.radiusZ();z<=lot.z()+lot.radiusZ();z++)
                assertEquals(level,grade[NpcVillageLayout.index(d,x,z)],"A city lot is not level");
        }
        assertNotNull(NpcVillagePlan.earthworkIssue(settlement(layout(d,(x,z) -> x/2))),"A steep hillside must not hold a city");
    }

    @Test void cityIsFoundedWithRoomForForty() {
        var center=new BlockPos(8,64,8);
        var city=NpcSettlements.createTown(NpcTownPlan.worldgenCandidate(5,center,true),
                new NpcVillageTerrain.Site(center,NpcVillageLayout.flat("oak",NpcVillageLayout.CITY)),120);
        assertTrue(NpcVillagePlan.city(city));
        assertEquals(NpcSettlements.CITY_POPULATION,SettlementService.populationLimit(city));
        assertEquals(NpcSettlements.CITY_POPULATION,NpcSettlements.startingPopulation(city));
        var town=NpcSettlements.createTown(NpcTownPlan.worldgenCandidate(5,center),new NpcVillageTerrain.Site(center,NpcVillageLayout.flat("oak")),120);
        assertFalse(NpcVillagePlan.city(town));
        assertTrue(SettlementService.populationLimit(town)<NpcSettlements.CITY_POPULATION,"A town is not founded as large as a city");
    }

    @Test void gatesSitJustOutsideEachDesignsLand() {
        int levelled=NpcVillageLayout.LEVELLED,city=NpcVillageLayout.CITY;
        assertEquals(new NpcVillagePlan.Column(6,-30),NpcVillagePlan.gateOffset(levelled,Direction.NORTH));
        assertEquals(new NpcVillagePlan.Column(0,30),NpcVillagePlan.gateOffset(levelled,Direction.SOUTH));
        assertEquals(new NpcVillagePlan.Column(30,-6),NpcVillagePlan.gateOffset(levelled,Direction.EAST));
        assertEquals(new NpcVillagePlan.Column(-30,-6),NpcVillagePlan.gateOffset(levelled,Direction.WEST));
        assertEquals(NpcVillagePlan.gateOffset(Direction.NORTH),NpcVillagePlan.gateOffset(NpcVillageLayout.WALLED,Direction.NORTH));
        assertEquals(new NpcVillagePlan.Column(0,-40),NpcVillagePlan.gateOffset(city,Direction.NORTH));
        assertEquals(new NpcVillagePlan.Column(40,0),NpcVillagePlan.gateOffset(city,Direction.EAST));
        assertEquals(29,NpcVillageLayout.extent(levelled)); assertEquals(39,NpcVillageLayout.extent(city)); assertEquals(6241,NpcVillageLayout.cells(city));
        assertEquals(25,NpcSettlements.chunks(new BlockPos(8,64,8)).size());
    }

    @Test void levelledSquareMeetsItsStreetsAndStreetsStepWithStairs() {
        // The gentle slope the hillside game test builds on: a block every six.
        int d=NpcVillageLayout.LEVELLED,e=NpcVillageLayout.EXTENT;
        var town=settlement(layout(d,(x,z) -> Math.floorDiv(x+e,6)-Math.floorDiv(e,6)));
        assertNull(NpcVillagePlan.earthworkIssue(town));
        int[] grade=NpcVillagePlan.grade(town);
        for(int x=-7;x<=7;x++) for(int z=-7;z<=7;z++) assertEquals(0,grade[NpcVillageLayout.index(d,x,z)],"The square and its ring of streets are not level at "+x+","+z);
        for(int n=-22;n<=22;n++) for(int ring:new int[]{-6,6}) {
            int across=grade[NpcVillageLayout.index(d,n,ring)];
            assertEquals(across,grade[NpcVillageLayout.index(d,n,ring-1)],"A street is not level across its width at "+n+","+ring);
            assertEquals(across,grade[NpcVillageLayout.index(d,n,ring+1)],"A street is not level across its width at "+n+","+ring);
        }
        // Steps are sunk into the street itself, at its graded surface; roofs and benches stand higher.
        long stairs=states(town).entrySet().stream().filter(s -> s.getValue().getBlock() instanceof StairBlock
                && s.getKey().getY()==grade[NpcVillageLayout.index(d,s.getKey().getX(),s.getKey().getZ())]-1).count();
        assertTrue(stairs>0,"Streets that step down a block have no stairs");
    }
}

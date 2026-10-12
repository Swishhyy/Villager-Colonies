package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.menu.MapData;
import io.github.swishhyy.wwmc.menu.MapGeometry;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
public final class NpcDevelopmentChecks {
    @Test void joinedRoadWidthAndLegacyWideningSurviveSaving() {
        var line=new ArrayList<BlockPos>(); for(int n=0;n<=20;n++) line.add(new BlockPos(n,70,n));
        var diagonal=RoadShape.columns(line,10);
        assertTrue(diagonal.contains(new BlockPos(8,70,10)) && diagonal.contains(new BlockPos(10,70,8)),"A diagonal narrows back to disconnected slices");
        var corner=List.of(new BlockPos(0,70,0),new BlockPos(1,70,0),new BlockPos(1,70,1));
        assertTrue(RoadShape.columns(corner,1).contains(new BlockPos(2,70,-1)),"A turn has an unfilled join");
        var road=new SharedRoads.Road(UUID.randomUUID(),UUID.randomUUID(),line,line.size(),0);
        var json=SharedRoads.Road.CODEC.encodeStart(JsonOps.INSTANCE,road).getOrThrow().getAsJsonObject(); json.remove("width_cursor");
        var migrated=SharedRoads.Road.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        assertTrue(migrated.complete()); assertEquals(0,migrated.widthCursor,"An old road must be scheduled for safe widening");
        migrated.widthCursor=7;
        assertEquals(7,SharedRoads.Road.CODEC.parse(JsonOps.INSTANCE,SharedRoads.Road.CODEC.encodeStart(JsonOps.INSTANCE,migrated).getOrThrow()).getOrThrow().widthCursor);
    }
    @Test void safetyAndFoodGateGrowthAndPaidWorkSurvivesSaving() {
        assertEquals(NpcDevelopment.Phase.RECOVER,NpcDirector.decide(new NpcDirector.Situation(true,100,8,true,true,true,true)));
        assertEquals(NpcDevelopment.Phase.SUPPLY,NpcDirector.decide(new NpcDirector.Situation(false,10,8,false,true,true,true)));
        assertEquals(NpcDevelopment.Phase.GROW,NpcDirector.decide(new NpcDirector.Situation(false,100,8,false,true,true,true)));
        assertNotEquals(NpcDevelopment.Phase.GROW,NpcDirector.decide(new NpcDirector.Situation(false,100,8,false,true,false,true)));
        assertEquals(NpcDevelopment.Phase.STABLE,NpcDirector.decide(new NpcDirector.Situation(false,100,8,false,true,true,false)));
        var state=new TradeSettings(); state.npc=true; state.development.enabled=true; state.development.completed=2; state.development.nextProject=99999;
        var station=new Station(new BlockPos(36,70,0),io.github.swishhyy.wwmc.core.StructureRole.HOUSING);
        var steps=List.of(new NpcConstruction.Step(station.position(),Blocks.AIR.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState(),true));
        state.development.construction=new NpcConstruction("cottage",station,steps,1);
        var restored=TradeSettings.CODEC.parse(JsonOps.INSTANCE,TradeSettings.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow()).getOrThrow();
        assertEquals(2,restored.development.completed); assertEquals(99999,restored.development.nextProject);
        assertEquals(steps,restored.development.construction.steps); assertTrue(restored.development.construction.complete());
        var oldJson=TradeSettings.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow().getAsJsonObject(); oldJson.remove("npc_development");
        assertTrue(TradeSettings.CODEC.parse(JsonOps.INSTANCE,oldJson).getOrThrow().development.enabled,"Existing saved NPC towns must migrate to the director");
    }
    @Test void independentVeinCooldownsAndOldTerrainDesignSurviveSaving() {
        var data=new WorldWorkData(); BlockPos iron=new BlockPos(1,70,0),coal=new BlockPos(0,70,1);
        data.veins.put(iron,1234L); data.veins.put(coal,5678L);
        var restored=WorldWorkData.CODEC.parse(JsonOps.INSTANCE,WorldWorkData.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(data.veins,restored.veins);
        var layout=NpcVillageLayout.CODEC.encodeStart(JsonOps.INSTANCE,NpcVillageLayout.flat("spruce")).getOrThrow().getAsJsonObject();
        layout.remove("design"); assertEquals(1,NpcVillageLayout.CODEC.parse(JsonOps.INSTANCE,layout).getOrThrow().design(),"Old saved construction must retain its blueprint order");
    }
    @Test void highwayGraphUsesConnectedRoadsAndHonoursBlockedLinks() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
        var towns=List.of(new Settlement(a,a,"A",BlockPos.ZERO,16,List.of(),List.of(),"balanced"),
                new Settlement(b,b,"B",new BlockPos(128,0,0),16,List.of(),List.of(),"balanced"),
                new Settlement(c,c,"C",new BlockPos(256,0,0),16,List.of(),List.of(),"balanced"));
        var data=new SharedRoads();
        var first=new SharedRoads.Road(a,b,List.of(BlockPos.ZERO,new BlockPos(64,0,50),new BlockPos(128,0,0)),3,0);
        data.roads.add(first); data.roads.add(new SharedRoads.Road(b,c,List.of(new BlockPos(128,0,0),new BlockPos(200,0,0),new BlockPos(256,0,0)),3,0));
        var route=data.route(BlockPos.ZERO,c,towns,10);
        assertEquals(new BlockPos(256,0,0),route.getLast()); assertTrue(route.contains(new BlockPos(64,0,50)),"The trader cut across the saved highway bend");
        assertEquals(BlockPos.ZERO,data.route(new BlockPos(256,0,0),a,towns,10).getLast());
        first.blockedUntil=100; assertTrue(data.route(BlockPos.ZERO,c,towns,10).isEmpty());
        var restored=SharedRoads.CODEC.parse(JsonOps.INSTANCE,SharedRoads.CODEC.encodeStart(JsonOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(2,restored.roads.size()); assertEquals(100,restored.roads.getFirst().blockedUntil);
        assertFalse(restored.route(BlockPos.ZERO,c,towns,101).isEmpty());
    }
    @Test void mapClaimsMatchInclusiveProtectionAndTradePathsRetainBends() {
        var path=List.of(new MapData.Point(-20,30),new MapData.Point(50,90),new MapData.Point(128,30));
        var map=new MapData("minecraft:overworld",0,0,List.of(new MapData.Town("A","Town A",-20,70,30,16,MapData.OWN)),
                List.of(new MapData.Route(-20,30,128,30,false,path)),List.of(),List.of());
        var shapes=MapGeometry.from(map); assertEquals(2,shapes.size());
        assertEquals(List.of(new MapData.Point(-36,14),new MapData.Point(-3,14),new MapData.Point(-3,47),new MapData.Point(-36,47)),shapes.getFirst().points());
        assertEquals("Town A",shapes.getFirst().label()); assertTrue(shapes.getFirst().closed());
        assertEquals(path,shapes.getLast().points()); assertFalse(shapes.getLast().closed());
    }
}

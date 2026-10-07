package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class CitizenNavigationTests {
    private static final TicketController TICKETS=new TicketController(Identifier.fromNamespaceAndPath("wwmc_tests","navigation"),(level,helper) -> {});
    static void registerTickets(RegisterTicketControllersEvent event) { event.register(TICKETS); }
    private static List<ChunkPos> pin(ServerLevel level,BlockPos start,int width) {
        List<ChunkPos> chunks=new ArrayList<>();
        for(int x=Math.floorDiv(start.getX()-8,16);x<=Math.floorDiv(start.getX()+24,16);x++)
            for(int z=Math.floorDiv(start.getZ()-width,16);z<=Math.floorDiv(start.getZ()+width,16);z++) {
                chunks.add(new ChunkPos(x,z)); TICKETS.forceChunk(level,start,x,z,true,false);
            }
        return chunks;
    }
    private static void release(ServerLevel level,BlockPos start,List<ChunkPos> chunks) {
        for(var pos:chunks) TICKETS.forceChunk(level,start,pos.x(),pos.z(),false,false);
    }
    @GameTest(timeoutTicks=800)
    @EmptyTemplate
    @TestHolder(description="A citizen chooses a paved detour instead of the grass shortcut and actually follows it.")
    static void prefersPaving(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-64));
            var chunks=pin(level,start,10);
            for(int x=-4;x<=24;x++) for(int z=-10;z<=10;z++) {
                level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            // A modest detour around a rectangle: six extra blocks instead of the straight grass route.
            for(int x=0;x<=20;x++) level.setBlockAndUpdate(start.offset(x,-1,3),Blocks.DIRT_PATH.defaultBlockState());
            for(int z=0;z<=3;z++) for(int x:new int[]{0,20})
                level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.DIRT_PATH.defaultBlockState());
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
            citizen.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            BlockPos end=start.east(20);
            var path=citizen.getNavigation().createPath(end,0);
            helper.assertTrue(path!=null && path.canReach(),"Paved route is unreachable");
            boolean paved=false;
            for(int n=0;n<path.getNodeCount();n++) if(path.getNode(n).z==start.getZ()+3) paved=true;
            helper.assertTrue(paved,"Pathfinder chose the grass shortcut instead of paving");
            citizen.getNavigation().moveTo(path,0.75);
            var followedRoad=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(citizen.getZ()>start.getZ()+2.5) followedRoad.set(true);
                helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not reached the paved route's end");
                helper.assertTrue(followedRoad.get(),"Citizen cut across the grass after planning the paved route");
                citizen.discard(); release(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=800)
    @EmptyTemplate
    @TestHolder(description="A citizen stays on a narrow, offset bridge across a river instead of entering water.")
    static void crossesNarrowBridge(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-128));
            var chunks=pin(level,start,20);
            for(int x=-4;x<=24;x++) for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(start.offset(x,-2,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(start.offset(x,-1,z),(x>=6 && x<=14 ? Blocks.WATER : Blocks.GRASS_BLOCK).defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            for(int x=4;x<=16;x++) level.setBlockAndUpdate(start.offset(x,-1,6),Blocks.OAK_PLANKS.defaultBlockState());
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
            citizen.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            BlockPos end=start.east(20);
            var path=citizen.getNavigation().createPath(end,0);
            helper.assertTrue(path!=null && path.canReach(),"Bridge is unreachable");
            citizen.getNavigation().moveTo(path,0.75);
            var wet=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(citizen.isInWater()) wet.set(true);
                helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not crossed the river");
                helper.assertTrue(!wet.get(),"Citizen entered water instead of staying on the bridge");
                citizen.discard(); release(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=800)
    @EmptyTemplate
    @TestHolder(description="A citizen on dry land does not plan to swim across a river without a bridge.")
    static void unbridgedRiverIsBlocked(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-192));
            var chunks=pin(level,start,52);
            for(int x=-8;x<=24;x++) for(int z=-52;z<=52;z++) {
                level.setBlockAndUpdate(start.offset(x,-2,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(start.offset(x,-1,z),(x>=5 && x<=13 ? Blocks.WATER : Blocks.GRASS_BLOCK).defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
            citizen.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32);
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            var path=citizen.getNavigation().createPath(start.east(20),0);
            helper.assertTrue(path==null || !path.canReach(),"Citizen planned a swim through an unbridged river");
            citizen.discard(); release(level,start,chunks); helper.succeed();
        });
    }
}

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
    static void release(ServerLevel level,BlockPos start,List<ChunkPos> chunks) {
        for(var pos:chunks) TICKETS.forceChunk(level,start,pos.x(),pos.z(),false,false);
    }
    static List<ChunkPos> pinArea(ServerLevel level,BlockPos start,int minX,int maxX,int minZ,int maxZ) {
        List<ChunkPos> chunks=new ArrayList<>();
        for(int x=Math.floorDiv(start.getX()+minX-8,16);x<=Math.floorDiv(start.getX()+maxX+8,16);x++)
            for(int z=Math.floorDiv(start.getZ()+minZ-8,16);z<=Math.floorDiv(start.getZ()+maxZ+8,16);z++) {
                chunks.add(new ChunkPos(x,z)); TICKETS.forceChunk(level,start,x,z,true,false);
            }
        return chunks;
    }
    /** Chunks within {@code radius} of a spot where entities tick, as they do near a player. */
    static List<ChunkPos> pinTicking(ServerLevel level,BlockPos center,int radius) {
        List<ChunkPos> chunks=new ArrayList<>();
        int cx=Math.floorDiv(center.getX(),16),cz=Math.floorDiv(center.getZ(),16);
        for(int x=cx-radius;x<=cx+radius;x++) for(int z=cz-radius;z<=cz+radius;z++) {
            chunks.add(new ChunkPos(x,z)); TICKETS.forceChunk(level,center,x,z,true,true);
        }
        return chunks;
    }
    static void releaseTicking(ServerLevel level,BlockPos center,List<ChunkPos> chunks) {
        for(var pos:chunks) TICKETS.forceChunk(level,center,pos.x(),pos.z(),false,true);
    }
    /** Grass on stone with open air above, like an ordinary meadow. */
    static void meadow(ServerLevel level,BlockPos start,int minX,int maxX,int minZ,int maxZ) {
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            level.setBlockAndUpdate(start.offset(x,-2,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(start.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    /** A citizen with no town and default navigation settings, standing at the start. */
    private static CitizenEntity walker(ServerLevel level,BlockPos start) {
        var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
        citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
        return citizen;
    }
    /** As jobs walk: keep the current plan, and plan the next leg toward the goal once it ends. */
    private static void travel(CitizenEntity citizen,BlockPos end) {
        var navigation=citizen.getNavigation();
        if(navigation.isDone()) navigation.moveTo(navigation.createPath(end,1),0.65);
    }
    /** Plans one route and reports how it went, so a failure says what the pathfinder found. */
    private static net.minecraft.world.level.pathfinder.Path plan(CitizenEntity citizen,BlockPos end,String name) {
        long started=System.nanoTime();
        var path=citizen.getNavigation().createPath(end,1);
        System.out.printf("[wwmc navigation] %s: planned in %.2f ms, %s%n",name,(System.nanoTime()-started)/1.0E6,describe(citizen,path));
        return path;
    }
    private static String describe(CitizenEntity citizen,net.minecraft.world.level.pathfinder.Path path) {
        String found=path==null ? "no path" : (path.canReach() ? "complete, " : "partial, ")+path.getNodeCount()+" nodes, "
                +String.format("%.1f",path.getDistToTarget())+" blocks short";
        return found+" (follow range "+citizen.getAttributeValue(Attributes.FOLLOW_RANGE)+")";
    }

    @GameTest(timeoutTicks=1000)
    @EmptyTemplate
    @TestHolder(description="A citizen plans a complete 43-block route across an open meadow, without a road, and walks it.")
    static void crossesOpenMeadow(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-384));
            var chunks=pinArea(level,start,-4,48,-6,24);
            meadow(level,start,-4,48,-6,24);
            var citizen=walker(level,start);
            BlockPos end=start.offset(40,0,16);
            helper.runAtTickTime(5,() -> {
                var path=plan(citizen,end,"open meadow");
                helper.assertTrue(path!=null && path.canReach(),"No complete route across an open meadow: "+describe(citizen,path));
                citizen.getNavigation().moveTo(path,0.65);
                helper.succeedWhen(() -> {
                    travel(citizen,end);
                    helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not crossed the meadow");
                    citizen.discard(); release(level,start,chunks);
                });
            });
        });
    }

    @GameTest(timeoutTicks=1200)
    @EmptyTemplate
    @TestHolder(description="A citizen finds the one gap in a long wall and walks through it to a goal on the far side.")
    static void findsGapInWall(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-448));
            var chunks=pinArea(level,start,-4,36,-20,20);
            meadow(level,start,-4,36,-20,20);
            // A long wall with a single opening fourteen blocks to the side of the straight line.
            for(int z=-20;z<=20;z++) if(z!=14) for(int y=0;y<=2;y++) level.setBlockAndUpdate(start.offset(16,y,z),Blocks.STONE.defaultBlockState());
            var citizen=walker(level,start);
            BlockPos end=start.east(32);
            helper.runAtTickTime(5,() -> {
                var path=plan(citizen,end,"gap in a wall");
                helper.assertTrue(path!=null && path.canReach(),"No complete route through the gap in the wall: "+describe(citizen,path));
                citizen.getNavigation().moveTo(path,0.65);
                helper.succeedWhen(() -> {
                    travel(citizen,end);
                    helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not come through the wall");
                    citizen.discard(); release(level,start,chunks);
                });
            });
        });
    }

    @GameTest(timeoutTicks=1800)
    @EmptyTemplate
    @TestHolder(description="A citizen reaches a goal 72 blocks away, beyond a single route's length, leg by leg.")
    static void walksBeyondOneRoute(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-512));
            var chunks=pinArea(level,start,-4,80,-4,4);
            meadow(level,start,-4,80,-4,4);
            var citizen=walker(level,start);
            BlockPos end=start.east(72);
            helper.runAtTickTime(5,() -> {
                plan(citizen,end,"beyond one route");
                helper.succeedWhen(() -> {
                    travel(citizen,end);
                    helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not walked the whole way");
                    citizen.discard(); release(level,start,chunks);
                });
            });
        });
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
            citizen.getNavigation().updatePathfinderMaxVisitedNodes();
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            BlockPos end=start.east(20);
            helper.runAtTickTime(5,() -> {
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
            // The deck bends twice over open water; corner cutting would leave this one-block-wide bridge.
            for(int x=4;x<=10;x++) level.setBlockAndUpdate(start.offset(x,-1,6),Blocks.OAK_PLANKS.defaultBlockState());
            for(int z=6;z<=9;z++) level.setBlockAndUpdate(start.offset(10,-1,z),Blocks.OAK_PLANKS.defaultBlockState());
            for(int x=10;x<=16;x++) level.setBlockAndUpdate(start.offset(x,-1,9),Blocks.OAK_PLANKS.defaultBlockState());
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
            citizen.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
            citizen.getNavigation().updatePathfinderMaxVisitedNodes();
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            BlockPos end=start.east(20);
            helper.runAtTickTime(5,() -> {
                // Jobs and traders use a one-block arrival tolerance; verify the actual crossing below.
                var path=citizen.getNavigation().createPath(end,1);
                helper.assertTrue(path!=null && path.canReach(),"Bridge route is unreachable");
                citizen.getNavigation().moveTo(path,0.75);
                var wet=new AtomicBoolean();
                helper.succeedWhen(() -> {
                    if(citizen.isInWater()) wet.set(true);
                    helper.assertTrue(citizen.blockPosition().distSqr(end)<4,"Citizen has not crossed the river");
                    helper.assertTrue(!wet.get(),"Citizen entered water instead of staying on the bridge");
                    citizen.discard(); release(level,start,chunks);
                });
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
            citizen.getNavigation().updatePathfinderMaxVisitedNodes();
            citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); level.addFreshEntity(citizen);
            helper.runAtTickTime(5,() -> {
                var path=citizen.getNavigation().createPath(start.east(20),0);
                helper.assertTrue(path==null || !path.canReach(),"Citizen planned a swim through an unbridged river");
                citizen.discard(); release(level,start,chunks); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=800)
    @EmptyTemplate
    @TestHolder(description="A citizen already in water can still swim out onto the nearby bank.")
    static void escapesWater(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-288));
            var chunks=pin(level,start,20);
            for(int x=-4;x<=24;x++) for(int z=-20;z<=20;z++) {
                level.setBlockAndUpdate(start.offset(x,-3,z),Blocks.STONE.defaultBlockState());
                for(int y=-2;y<=-1;y++) level.setBlockAndUpdate(start.offset(x,y,z),
                        (x>=5 && x<=13 ? Blocks.WATER : Blocks.GRASS_BLOCK).defaultBlockState());
                for(int y=0;y<=3;y++) level.setBlockAndUpdate(start.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
            citizen.setPos(start.getX()+10.5,start.getY()-1,start.getZ()+0.5); level.addFreshEntity(citizen);
            BlockPos bank=start.east(2);
            var swimming=new AtomicBoolean();
            helper.succeedWhen(() -> {
                if(!swimming.get()) {
                    helper.assertTrue(citizen.isInWater(),"Waiting for the spawned citizen to enter the water");
                    var path=citizen.getNavigation().createPath(bank,0);
                    helper.assertTrue(path!=null && path.canReach(),"Water escape path to the bank is unreachable");
                    citizen.getNavigation().moveTo(path,0.75); swimming.set(true);
                }
                helper.assertTrue(citizen.isAlive(),"Citizen died while trying to escape water");
                helper.assertTrue(!citizen.isInWater() && citizen.onGround() && citizen.blockPosition().distSqr(bank)<4,
                        "Citizen has not escaped onto the bank");
                citizen.discard(); release(level,start,chunks);
            });
        });
    }
}

package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Citizens stranded outside the loaded area while their station is loaded, in a player-free world. */
public final class RecallWorldTests {
    @GameTest(timeoutTicks=1600)
    @EmptyTemplate
    @TestHolder(description="A failed recall from a frozen chunk retries after thirty seconds when standing room opens, without starting the successful-recall cooldown.")
    static void retriesBlockedRecall(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,6400)),away=start.east(400);
            CitizenNavigationTests.meadow(level,start,-6,8,-6,6);
            CitizenNavigationTests.meadow(level,away,-3,3,-3,3);
            var home=CitizenNavigationTests.pinTicking(level,start,1);
            var job=new Station(start.east(3),StructureRole.FARM);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Blocked recall",start,240,List.of(),List.of(job),"balanced");
            var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
            // Block every candidate beside both home anchors, including the block above them.
            for(int x=-2;x<=5;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=2;y++)
                level.setBlockAndUpdate(start.offset(x,y,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(start,WWMC.BANNER.get().defaultBlockState());
            level.setBlockAndUpdate(job.position(),WWMC.STATIONS.get(job.role()).get().defaultBlockState());
            List<Long> attempts=new ArrayList<>();
            var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level) {
                @Override public boolean recall(net.minecraft.server.level.ServerLevel server,Settlement settlement,BlockPos anchor) {
                    attempts.add(server.getGameTime()); return super.recall(server,settlement,anchor);
                }
            };
            citizen.join(town.id); citizen.setNoAi(true); citizen.setPos(away.getX()+0.5,away.getY(),away.getZ()+0.5);
            town.citizens.add(citizen.getUUID()); town.jobs.assign(citizen.getUUID(),job.position());
            town.citizenPlaces.put(citizen.getUUID(),away); level.addFreshEntity(citizen);
            var opened=new AtomicInteger();
            helper.succeedWhen(() -> {
                // A temporary FULL ticket retains the entity without making this distant chunk entity-ticking.
                level.getChunkAt(away);
                helper.assertTrue(!level.isPositionEntityTicking(away),"The source chunk must stay frozen for the recall retry regression");
                helper.assertTrue(!attempts.isEmpty(),"The first recall has not been attempted");
                if(opened.get()==0) {
                    helper.assertTrue(citizen.blockPosition().distSqr(away)<9,"A blocked recall must not move the citizen");
                    for(int x=-2;x<=5;x++) for(int z=-2;z<=2;z++) for(int y=0;y<=3;y++) {
                        BlockPos pos=start.offset(x,y,z);
                        if(!pos.equals(start) && !pos.equals(job.position())) level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                    }
                    opened.set(1);
                }
                helper.assertTrue(attempts.size()>=2,"The failed recall has not retried");
                long waited=attempts.get(1)-attempts.getFirst();
                helper.assertTrue(waited>=600 && waited<=620,"Failed recall must retry after 600 ticks, not the successful recall cooldown: "+waited);
                helper.assertTrue(citizen.blockPosition().distSqr(job.position())<16,"The retry did not return the citizen home");
                helper.assertTrue(job.position().equals(town.jobs.home(citizen.getUUID())),"A failed recall must retain the job");
                citizen.discard(); data.settlements.remove(town); data.setDirty(); CitizenNavigationTests.releaseTicking(level,start,home); helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks=6000)
    @EmptyTemplate
    @TestHolder(description="A cook whose chunk unloads while its kitchen stays loaded is fetched back and keeps the job; a citizen nobody can find leaves the roster.")
    static void bringsBackStrandedCook(DynamicTest test) {
        recallsCook(test,false,2600);
    }
    @GameTest(timeoutTicks=6000)
    @EmptyTemplate
    @TestHolder(description="An injured cook is fetched from unloaded chunks without losing health, equipment or its job, so hospital care can resume.")
    static void bringsBackInjuredCook(DynamicTest test) {
        recallsCook(test,true,6000);
    }
    private static void recallsCook(DynamicTest test,boolean injured,int offset) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,offset));
            BlockPos away=start.east(400);
            CitizenNavigationTests.meadow(level,start,-6,6,-6,6);
            CitizenNavigationTests.meadow(level,away,-3,3,-3,3);
            // A pen keeps the cook in its own chunk until that chunk unloads.
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(dx!=0 || dz!=0)
                for(int y=0;y<=2;y++) level.setBlockAndUpdate(away.offset(dx,y,dz),Blocks.STONE.defaultBlockState());
            var home=CitizenNavigationTests.pinTicking(level,start,1);
            var far=CitizenNavigationTests.pinTicking(level,away,0);
            Station kitchen=new Station(start.east(3),StructureRole.COOK);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Recall",start,240,List.of(),List.of(kitchen),"balanced");
            var data=SettlementData.get(level); data.settlements.add(town);
            level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
            level.setBlockAndUpdate(kitchen.position(),WWMC.STATIONS.get(StructureRole.COOK).get().defaultBlockState());
            var cook=new CitizenEntity(WWMC.CITIZEN.get(),level);
            cook.join(town.id); cook.setPos(away.getX()+0.5,away.getY(),away.getZ()+0.5);
            if(injured) cook.setHealth(cook.getMaxHealth()-6);
            float hurtHealth=cook.getHealth();
            cook.bag().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
            UUID id=cook.getUUID();
            town.citizens.add(id); town.jobs.assign(id,kitchen.position()); level.addFreshEntity(cook);
            // Someone the town lists who is nowhere to be found, last seen in an empty field.
            UUID ghost=UUID.randomUUID();
            town.citizens.add(ghost); town.citizenNames.put(ghost,"Ghost"); town.citizenPlaces.put(ghost,start.east(200));
            data.setDirty();
            var stage=new AtomicInteger();
            helper.succeedWhen(() -> {
                helper.assertTrue(level.players().isEmpty(),"Recall test must run without player-loaded chunks");
                if(stage.get()==0) {
                    BlockPos seen=town.citizenPlaces.get(id);
                    helper.assertTrue(seen!=null && seen.distSqr(away)<9,"The cook's place is not recorded: "+seen);
                    CitizenNavigationTests.releaseTicking(level,away,far);
                    stage.set(1);
                }
                if(stage.get()==1) {
                    helper.assertTrue(level.getEntity(id)==null,"The cook's chunk has not unloaded yet");
                    stage.set(2);
                }
                if(stage.get()==2) {
                    var back=level.getEntity(id) instanceof CitizenEntity found ? found : null;
                    helper.assertTrue(back!=null && back.blockPosition().distSqr(kitchen.position())<16,
                            "The cook is not back at the kitchen: "+CitizenRecall.whereabouts(level,town,id));
                    helper.assertTrue(kitchen.position().equals(town.jobs.home(id)),"The cook lost its job");
                    helper.assertTrue(town.citizens.contains(id),"The cook left the roster");
                    helper.assertTrue(back.getHealth()==hurtHealth,"Recall must preserve the injury so hospital care can resume");
                    helper.assertTrue(back.bag().getItem(0).is(net.minecraft.world.item.Items.IRON_PICKAXE),"Recall must preserve carried equipment");
                    stage.set(3);
                }
                helper.assertTrue(!town.citizens.contains(ghost) && !town.citizenNames.containsKey(ghost) && !town.citizenPlaces.containsKey(ghost),
                        "The missing citizen is still listed: "+CitizenRecall.whereabouts(level,town,ghost));
                helper.assertTrue(CitizenRecall.searching(level,town)==0,"A search still holds chunks loaded");
                if(level.getEntity(id) instanceof CitizenEntity found) found.discard();
                data.settlements.remove(town); data.setDirty();
                CitizenNavigationTests.releaseTicking(level,start,home);
            });
        });
    }
}

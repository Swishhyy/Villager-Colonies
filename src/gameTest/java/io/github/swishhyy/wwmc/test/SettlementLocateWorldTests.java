package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.swishhyy.wwmc.settlement.Settlement;
import io.github.swishhyy.wwmc.settlement.SettlementData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class SettlementLocateWorldTests {
    private static Settlement town(BlockPos pos,boolean npc,int buildIndex) {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Locate test",pos,32,List.of(),List.of(),"balanced");
        town.trading.npc=npc; town.trading.buildIndex=buildIndex; return town;
    }

    @GameTest(timeoutTicks=20)
    @EmptyTemplate
    @TestHolder(description="The real /locate command finds completed NPC towns by horizontal distance without loading chunks, preserves vanilla subcommands and permissions, and explains missing towns and wrong dimensions.")
    static void locateSavedNpcSettlements(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var server=level.getServer(); var data=SettlementData.get(level);
            var saved=new ArrayList<>(data.settlements);
            var closest=town(new BlockPos(1_000_010,level.getMaxY()-1,1_000_000),true,-1);
            var farther=town(new BlockPos(1_000_020,0,1_000_000),true,-1);
            var pending=town(new BlockPos(1_000_001,0,1_000_000),true,0);
            var playerTown=town(new BlockPos(1_000_002,0,1_000_000),false,-1);
            try {
                data.settlements.clear(); data.settlements.addAll(List.of(closest,farther,pending,playerTown));
                var dispatcher=server.getCommands().getDispatcher(); var root=dispatcher.getRoot().getChild("locate");
                helper.assertTrue(root!=null && root.getChild("settlement")!=null,"Settlement locator was not registered");
                for(String child:List.of("structure","biome","poi"))
                    helper.assertTrue(root.getChild(child)!=null,"Vanilla locate lost its "+child+" subcommand");
                var source=server.createCommandSourceStack().withLevel(level)
                        .withPosition(new Vec3(1_000_000,0,1_000_000)).withSuppressedOutput();
                var visitor=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LocateVisitor"));
                helper.assertTrue(!root.canUse(visitor.createCommandSourceStack()),"Locate became available to an unprivileged visitor");
                helper.assertTrue(!level.hasChunkAt(closest.center),"Locator test target must start unloaded");
                helper.assertTrue(dispatcher.execute("locate settlement",source)==10,"Locator included a player town, unfinished town or vertical distance");
                helper.assertTrue(data.settlements.size()==4 && !level.hasChunkAt(closest.center),"Locate generated a town or loaded its chunks");
                data.settlements.remove(closest);
                helper.assertTrue(dispatcher.execute("locate settlement",source)==20,"Locator did not update after a saved town was removed");
                var nether=server.getLevel(Level.NETHER);
                if(nether!=null) {
                    try {
                        dispatcher.execute("locate settlement",source.withLevel(nether));
                        helper.assertTrue(false,"Locator accepted a Nether source");
                    } catch(CommandSyntaxException expected) {
                        helper.assertTrue(expected.getRawMessage().getString().contains("Overworld"),"Wrong dimension did not explain where to search");
                    }
                }
                data.settlements.remove(farther);
                try {
                    dispatcher.execute("locate settlement",source);
                    helper.assertTrue(false,"Locator returned an unfinished or player-owned town");
                } catch(CommandSyntaxException expected) {
                    helper.assertTrue(expected.getRawMessage().getString().contains("Explore suitable Overworld terrain"),"Missing-town result did not explain discovery");
                }
            } catch(CommandSyntaxException exception) {
                throw new AssertionError("Registered settlement locator failed",exception);
            } finally {
                data.settlements.clear(); data.settlements.addAll(saved); data.setDirty();
            }
            helper.succeed();
        });
    }
}

package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.SettlementBannerBlock;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** World behavior behind the visual changes: physical borders, inactive workers and saved tutorial completion. */
public final class VisualWorldTests {
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="Four loaded border corners use the saved town color, repair missing flags, recolor older markers and leave buildings intact.")
    static void coloredCorners(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos center=helper.absolutePos(new BlockPos(0,2,3900));
            var chunks=CitizenNavigationTests.pinArea(level,center,-18,18,-18,18);
            CitizenNavigationTests.meadow(level,center,-18,18,-18,18);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Corners",center,16,List.of(),List.of(),"balanced");
            SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(center,WWMC.BANNER.get().defaultBlockState());
            helper.assertTrue(TownBorders.choose(level,town,UUID.randomUUID(),DyeColor.RED.getId()).startsWith("Only"),"A stranger changed town color");
            TownBorders.choose(level,town,town.owner,DyeColor.CYAN.getId());
            helper.assertTrue(town.borderBanners.size()==4,"Missing corner flags");
            for(BlockPos p:town.borderBanners) helper.assertTrue(((BannerBlock)level.getBlockState(p).getBlock()).getColor()==DyeColor.CYAN,"Wrong corner color");
            helper.assertTrue(level.getBlockState(center).getValue(SettlementBannerBlock.COLOR)==DyeColor.CYAN,"Main flag has another color");
            BlockPos removed=town.borderBanners.getFirst(); level.setBlockAndUpdate(removed,Blocks.AIR.defaultBlockState());
            TownBorders.update(level,town); helper.assertTrue(level.getBlockState(removed).getBlock() instanceof BannerBlock,"Missing flag was not repaired");
            level.setBlockAndUpdate(removed,Blocks.GOLD_BLOCK.defaultBlockState()); TownBorders.update(level,town);
            helper.assertTrue(level.getBlockState(removed).is(Blocks.GOLD_BLOCK),"Repair replaced a building");
            TownBorders.choose(level,town,town.owner,DyeColor.PURPLE.getId());
            helper.assertTrue(town.borderBanners.size()==4,"Color change duplicated a corner");
            for(BlockPos p:town.borderBanners) helper.assertTrue(((BannerBlock)level.getBlockState(p).getBlock()).getColor()==DyeColor.PURPLE,"Marker was not recolored");
            SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.release(level,center,chunks); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=100)
    @EmptyTemplate
    @TestHolder(description="An assigned citizen keeps its synchronized job look while idle, work cues expire when work stops, and actual completed jobs award native tutorial progress.")
    static void idleAppearanceAndProgress(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos center=helper.absolutePos(new BlockPos(0,2,4000));
            var chunks=CitizenNavigationTests.pinArea(level,center,-8,16,-8,8); CitizenNavigationTests.meadow(level,center,-8,16,-8,8);
            Station station=new Station(center.east(6),StructureRole.ENCHANTER);
            var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Progress",center,32,List.of(),List.of(station),"balanced");
            SettlementData.get(level).settlements.add(town); level.setBlockAndUpdate(center,WWMC.BANNER.get().defaultBlockState());
            level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
            CitizenEntity citizen=WWMC.CITIZEN.get().create(level,EntitySpawnReason.COMMAND);
            citizen.join(town.id); citizen.setPos(station.position().getX()+0.5,station.position().getY(),station.position().getZ()+1.5);
            town.citizens.add(citizen.getUUID()); town.jobs.assign(citizen.getUUID(),station.position()); level.addFreshEntity(citizen);
            helper.assertTrue(!TutorialProgress.milestones(level,town).contains("enchanting"),"An empty station counted as completed enchanting");
            citizen.working(WorkFeedback.ENCHANTING);
            helper.runAfterDelay(25,() -> {
                helper.assertTrue(citizen.appearanceJob()==StructureRole.ENCHANTER,"Idle citizen lost its job outfit");
                helper.assertTrue(citizen.workAnimation()==WorkFeedback.NONE,"Unsupplied enchanter kept playing work animations");
                citizen.gainExperience(StructureRole.ENCHANTER,3);
                helper.assertTrue(town.progress.milestones.contains("enchanting"),"Completed job was not saved");
                var player=new ProgressPlayer(level,new GameProfile(town.owner,"ProgressOwner"));
                TutorialProgress.award(player,"enchanting");
                var holder=level.getServer().getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("wwmc","tutorial/enchanting"));
                helper.assertTrue(holder!=null && player.getAdvancements().getOrStartProgress(holder).isDone(),"Native advancement was not awarded");
                citizen.discard(); SettlementData.get(level).settlements.remove(town); CitizenNavigationTests.release(level,center,chunks); helper.succeed();
            });
        });
    }
    /** FakePlayer's default progress intentionally ignores awards; use Minecraft's real tracker for this check. */
    private static final class ProgressPlayer extends FakePlayer {
        private PlayerAdvancements progress;
        ProgressPlayer(ServerLevel level,GameProfile profile) {
            super(level,profile);
            var server=level.getServer();
            progress=new PlayerAdvancements(server.getFixerUpper(),server.getPlayerList(),server.getAdvancements(),
                Path.of("build","gametest-advancements",profile.id()+".json"),this);
        }
        @Override public PlayerAdvancements getAdvancements() { return progress==null ? super.getAdvancements() : progress; }
    }
}

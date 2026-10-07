package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Citizens doing their jobs in a real world: choosing the station, walking there and working. */
public final class WorkWorldTests {
    /** A small town on an open meadow whose only station is a mine, with one miner holding a pickaxe at the start. */
    private record MineTown(Settlement town,CitizenEntity miner) {}
    private static MineTown mineTown(ServerLevel level,BlockPos start,BlockPos mine) {
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Mine test",start.west(4),48,List.of(),
                List.of(new Station(mine,StructureRole.MINE)),"balanced");
        var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
        level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
        level.setBlockAndUpdate(mine,WWMC.STATIONS.get(StructureRole.MINE).get().defaultBlockState());
        var miner=new CitizenEntity(WWMC.CITIZEN.get(),level);
        miner.join(town.id); miner.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        miner.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
        town.citizens.add(miner.getUUID()); level.addFreshEntity(miner);
        return new MineTown(town,miner);
    }
    private static void leave(ServerLevel level,MineTown test) {
        var data=SettlementData.get(level);
        test.miner().discard(); data.settlements.remove(test.town()); data.setDirty();
    }

    @GameTest(timeoutTicks=2400)
    @EmptyTemplate
    @TestHolder(description="A miner walks from across town to a mine station and works the ore vein touching it, leaving the ore in place.")
    static void minesTouchingVein(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-640));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,40,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,40,-8,8);
            BlockPos mine=start.east(32),ore=mine.east();
            level.setBlockAndUpdate(ore,Blocks.IRON_ORE.defaultBlockState());
            var town=mineTown(level,start,mine);
            helper.succeedWhen(() -> {
                helper.assertTrue(OreVeins.readyAt(level,ore)>0,"Vein not worked: "+town.miner().activity()+" at "+town.miner().blockPosition());
                helper.assertTrue(level.getBlockState(ore).is(Blocks.IRON_ORE),"The vein's ore was removed");
                leave(level,town); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=2400)
    @EmptyTemplate
    @TestHolder(description="A miner works an ore vein set in the ground two blocks from the mine station.")
    static void minesVeinTwoBlocksAway(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-720));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,24,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,24,-8,8);
            BlockPos mine=start.east(12),ore=mine.offset(2,-1,0);
            // Only the ore's top face is open, like ore uncovered at the bottom of a shallow dig.
            level.setBlockAndUpdate(ore,Blocks.IRON_ORE.defaultBlockState());
            var town=mineTown(level,start,mine);
            helper.succeedWhen(() -> {
                helper.assertTrue(OreVeins.find(level,town.town(),town.town().stations.getFirst())!=null,"Ore two blocks away was not found as the mine's vein");
                helper.assertTrue(OreVeins.readyAt(level,ore)>0,"Vein not worked: "+town.miner().activity()+" at "+town.miner().blockPosition());
                leave(level,town); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }
}

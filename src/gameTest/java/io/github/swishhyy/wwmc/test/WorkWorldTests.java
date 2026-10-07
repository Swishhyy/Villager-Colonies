package io.github.swishhyy.wwmc.test;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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
    /** A small town on an open meadow with these stations, and one citizen at the start. */
    private record WorkTown(Settlement town,CitizenEntity citizen) {}
    private static WorkTown town(ServerLevel level,BlockPos start,Station... stations) {
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Work test",start.west(4),48,List.of(),List.of(stations),"balanced");
        var data=SettlementData.get(level); data.settlements.add(town); data.setDirty();
        level.setBlockAndUpdate(town.center,WWMC.BANNER.get().defaultBlockState());
        for(Station station:stations) level.setBlockAndUpdate(station.position(),WWMC.STATIONS.get(station.role()).get().defaultBlockState());
        var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level);
        citizen.join(town.id); citizen.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
        town.citizens.add(citizen.getUUID()); level.addFreshEntity(citizen);
        return new WorkTown(town,citizen);
    }
    /** A town whose only station is a mine, with one miner holding a pickaxe at the start. */
    private static WorkTown mineTown(ServerLevel level,BlockPos start,BlockPos mine) {
        var town=town(level,start,new Station(mine,StructureRole.MINE));
        town.citizen().setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));
        return town;
    }
    private static void leave(ServerLevel level,WorkTown test) {
        var data=SettlementData.get(level);
        test.citizen().discard(); data.settlements.remove(test.town()); data.setDirty();
    }
    private static WorkTown enchanterTown(ServerLevel level,BlockPos start,BlockPos table) {
        BlockPos station=new BlockPos(table.getX(),start.getY(),table.getZ()-4);
        level.setBlockAndUpdate(table,Blocks.ENCHANTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(station.west(2),Blocks.BARREL.defaultBlockState());
        Container barrel=(Container)level.getBlockEntity(station.west(2));
        barrel.setItem(0,new ItemStack(Items.BOOK)); barrel.setItem(1,new ItemStack(Items.LAPIS_LAZULI,9));
        var work=town(level,start,new Station(station,StructureRole.ENCHANTER));
        work.town().jobs.assign(work.citizen().getUUID(),station);
        return work;
    }

    @GameTest(timeoutTicks=1800)
    @EmptyTemplate
    @TestHolder(description="An enchanter walks to clear ground in reach of a raised table even when no path can enter the table block itself.")
    static void enchantsAtRaisedTable(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1480));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,40,-12,12);
            CitizenNavigationTests.meadow(level,start,-8,40,-12,12);
            BlockPos table=start.offset(28,2,0);
            level.setBlockAndUpdate(table.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(table.below(2),Blocks.STONE.defaultBlockState());
            var work=enchanterTown(level,start,table); var citizen=work.citizen();
            helper.runAtTickTime(5,() -> {
                var path=citizen.getNavigation().createPath(table,1);
                helper.assertTrue(path==null || !path.canReach(),"Fixture must prevent walking into the raised table itself");
                helper.succeedWhen(() -> {
                    helper.assertTrue(citizen.enchantProgress()>0,"Not walking to or using the raised table: "+citizen.activity()+" at "+citizen.blockPosition());
                    helper.assertTrue(citizen.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(start))>100,"Enchanter worked from across town");
                    helper.assertTrue(CitizenReach.canUse(level,citizen.getEyePosition(),table),"Enchanter bypassed four-block reach or clear view");
                    leave(level,work); CitizenNavigationTests.release(level,start,chunks);
                });
            });
        });
    }

    @GameTest(timeoutTicks=7800)
    @EmptyTemplate
    @TestHolder(description="An enchanter walks to a bookshelf room, completes a real book enchantment and returns it to its job barrel.")
    static void enchantsInBookshelfRoom(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1640));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,40,-12,12);
            CitizenNavigationTests.meadow(level,start,-8,40,-12,12);
            BlockPos table=start.east(28);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) if((Math.abs(x)==2 || Math.abs(z)==2) && !(x==-2 && z==0))
                for(int y=0;y<2;y++) level.setBlockAndUpdate(table.offset(x,y,z),Blocks.BOOKSHELF.defaultBlockState());
            var work=enchanterTown(level,start,table); var citizen=work.citizen();
            Container barrel=(Container)level.getBlockEntity(work.town().stations.getFirst().position().west(2));
            helper.succeedWhen(() -> {
                helper.assertTrue(InventoryOps.count(List.of(barrel),s -> s.is(Items.ENCHANTED_BOOK))==1,"Book was not enchanted and returned: "+citizen.activity()+" at "+citizen.blockPosition());
                helper.assertTrue(InventoryOps.count(List.of(barrel),s -> s.is(Items.BOOK))==0,"The input book was duplicated");
                helper.assertTrue(citizen.bag().count(Items.LAPIS_LAZULI)<9,"Enchanting spent no lapis");
                leave(level,work); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=900)
    @EmptyTemplate
    @TestHolder(description="An enchanter cannot work through a sealed room, but starts walking and enchanting soon after its entrance opens.")
    static void retriesEnchantingWhenEntranceOpens(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-1800));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,40,-12,12);
            CitizenNavigationTests.meadow(level,start,-8,40,-12,12);
            BlockPos table=start.east(28);
            for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
                if(Math.abs(x)==2 || Math.abs(z)==2) for(int y=0;y<3;y++)
                    level.setBlockAndUpdate(table.offset(x,y,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(table.offset(x,3,z),Blocks.STONE.defaultBlockState());
            }
            var work=enchanterTown(level,start,table); var citizen=work.citizen();
            helper.runAtTickTime(250,() -> {
                helper.assertTrue(citizen.enchantProgress()==0,"Enchanter worked through the closed wall");
                for(int y=0;y<2;y++) level.setBlockAndUpdate(table.offset(-2,y,0),Blocks.AIR.defaultBlockState());
                helper.succeedWhen(() -> {
                    helper.assertTrue(citizen.enchantProgress()>0,"Did not retry after opening the entrance: "+citizen.activity()+" at "+citizen.blockPosition());
                    helper.assertTrue(CitizenReach.canUse(level,citizen.getEyePosition(),table),"Enchanter worked without clear reach after the entrance opened");
                    leave(level,work); CitizenNavigationTests.release(level,start,chunks);
                });
            });
        });
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
                helper.assertTrue(OreVeins.readyAt(level,ore)>0,"Vein not worked: "+town.citizen().activity()+" at "+town.citizen().blockPosition());
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
                helper.assertTrue(OreVeins.readyAt(level,ore)>0,"Vein not worked: "+town.citizen().activity()+" at "+town.citizen().blockPosition());
                leave(level,town); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }

    @GameTest(timeoutTicks=3000)
    @EmptyTemplate
    @TestHolder(description="A farmer whose farm has no crops waits there instead of taking the open mine, then moves to the mine once mining is raised to High.")
    static void keepsJobUntilPromoted(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel();
            BlockPos start=helper.absolutePos(new BlockPos(0,2,-800));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,28,-8,8);
            CitizenNavigationTests.meadow(level,start,-8,28,-8,8);
            BlockPos farm=start.east(4),mine=start.east(18),ore=mine.east();
            level.setBlockAndUpdate(ore,Blocks.IRON_ORE.defaultBlockState());
            // The mine's job barrel holds the pickaxe the farmer needs once it becomes a miner.
            level.setBlockAndUpdate(mine.north(),Blocks.BARREL.defaultBlockState());
            ((Container)level.getBlockEntity(mine.north())).setItem(0,new ItemStack(Items.IRON_PICKAXE));
            var work=town(level,start,new Station(farm,StructureRole.FARM),new Station(mine,StructureRole.MINE));
            Settlement town=work.town();
            CitizenEntity citizen=work.citizen();
            town.jobs.assign(citizen.getUUID(),farm);
            helper.runAtTickTime(400,() -> {
                helper.assertTrue(farm.equals(town.jobs.home(citizen.getUUID())),"The farmer left the farm: "+citizen.activity());
                helper.assertTrue(!mine.equals(citizen.workplace()) && OreVeins.readyAt(level,ore)==0,"The farmer took the open mine without a promotion: "+citizen.activity());
                SettlementService.setJobLevel(level,town,StructureRole.MINE,JobBoard.HIGH);
                helper.succeedWhen(() -> {
                    helper.assertTrue(mine.equals(town.jobs.home(citizen.getUUID())),"Not moved to the higher-priority mine: "+citizen.activity());
                    helper.assertTrue(OreVeins.readyAt(level,ore)>0,"Moved to the mine but not mining: "+citizen.activity()+" at "+citizen.blockPosition());
                    leave(level,work); CitizenNavigationTests.release(level,start,chunks);
                });
            });
        });
    }
}

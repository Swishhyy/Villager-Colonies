package io.github.swishhyy.vc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.vc.VC;
import io.github.swishhyy.vc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

public final class TerrainAndBorderWorldTests {
    private static void hillside(ServerLevel level,BlockPos base,int extent) {
        for(int x=-extent;x<=extent;x++) for(int z=-extent;z<=extent;z++) {
            int height=Math.floorDiv(x,3)+Math.floorDiv(z,8);
            for(int y=-28;y<height-1;y++) level.setBlock(base.offset(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(base.offset(x,height-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
            for(int y=height;y<=45;y++) level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
    }
    private static void tree(ServerLevel level,BlockPos root) {
        for(int y=0;y<36;y++) level.setBlock(root.above(y),Blocks.SPRUCE_LOG.defaultBlockState(),2);
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.setBlock(root.offset(x,36,z),Blocks.SPRUCE_LEAVES.defaultBlockState(),2);
    }
    @GameTest(timeoutTicks=1600,batch="ruggedVillage") @EmptyTemplate
    @TestHolder(description="A forest-covered hillside with more than twelve blocks of relief generates a complete furnished town with grounded terraces, eight workers, twelve beds and reachable houses; tall trees are surveyed down to actual soil.")
    static void furnishedVillageOnRuggedForest(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var candidate=NpcTownPlan.candidate(level.getSeed(),745,745,1408);
            var base=new BlockPos(candidate.center().getX(),level.getSeaLevel()+30,candidate.center().getZ());
            var chunks=CitizenNavigationTests.pinArea(level,base,-23,23,-23,23); hillside(level,base,23);
            var root=base.offset(-13,Math.floorDiv(-13,3)+Math.floorDiv(-13,8),-13); tree(level,root);
            var town=NpcSettlements.discover(level,candidate,List.of(candidate.center()));
            helper.assertTrue(town!=null,"A normal hillside and tall forest still rejected settlement generation");
            test.whenDisabled(() -> {
                for(var id:town.citizens) { var citizen=level.getEntity(id); if(citizen!=null) citizen.discard(); }
                SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
                NpcWorldData.get(level).generated.remove(candidate.region()); NpcWorldData.get(level).setDirty();
                CitizenNavigationTests.release(level,base,chunks);
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(town.trading.buildIndex<0,"Rugged village construction is incomplete: "+town.trading.status);
                helper.assertTrue(town.citizens.size()==8 && SettlementService.housingBeds(level,town).size()==12,"Town lost its native population or beds");
                var walker=new io.github.swishhyy.vc.entity.CitizenEntity(VC.CITIZEN.get(),level);
                walker.setPos(town.center.getX()+0.5,town.center.getY(),town.center.getZ()+0.5); walker.setOnGround(true);
                try {
                    for(var station:town.stations) if(station.role()==io.github.swishhyy.vc.core.StructureRole.HOUSING) {
                        var path=walker.getNavigation().createPath(station.position().relative(station.facing(),2),0);
                        helper.assertTrue(path!=null && path.canReach(),"Terraced house is unreachable: "+station.position());
                        helper.assertTrue(!level.getBlockState(station.position().below()).isAir(),"House is floating above the slope");
                    }
                } finally { walker.discard(); }
            });
        });
    }
    @GameTest(timeoutTicks=50,batch="slopedRuins") @EmptyTemplate
    @TestHolder(description="Mine and fort ruins can use dry forest slopes and real foundations, while a protected player block still prevents generation without being overwritten.")
    static void ruinsOnForestSlopes(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var base=helper.absolutePos(new BlockPos(-410000,50,-30000));
            var chunks=CitizenNavigationTests.pinArea(level,base,-12,12,-12,12); hillside(level,base,12); tree(level,base);
            BlockPos edit=base.east(3); level.setBlock(edit,Blocks.DIAMOND_BLOCK.defaultBlockState(),2); WorldWorkData.get(level).protect(edit);
            helper.assertTrue(ExpeditionService.discover(level,base,"fort","protected:"+UUID.randomUUID())==null,"Ruins overwrote player construction");
            helper.assertTrue(level.getBlockState(edit).is(Blocks.DIAMOND_BLOCK),"Terrain survey changed the edit");
            WorldWorkData.get(level).protectedBlocks.remove(edit); level.setBlock(edit,Blocks.AIR.defaultBlockState(),2);
            var site=ExpeditionService.discover(level,base,"fort","sloped:"+UUID.randomUUID());
            helper.assertTrue(site!=null,"A dry sloped forest still rejects fort generation: "+ExpeditionService.siteIssue(level,base));
            helper.assertTrue(!level.getBlockState(site.pos.offset(-10,-2,0)).isAir(),"The ruin has an unsupported floor");
            test.whenDisabled(() -> { ExpeditionData.get(level).sites.remove(site); ExpeditionData.get(level).setDirty(); CitizenNavigationTests.release(level,base,chunks); });
            helper.succeed();
        });
    }
    @GameTest(timeoutTicks=50,batch="borderUpgrade") @EmptyTemplate
    @TestHolder(description="Only the nearby owner can buy border expansion; payment, overlap checks, the More button and saved radius/upgrade cost agree, and blocked purchases spend nothing.")
    static void borderPurchaseAndClaimConflict(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); var base=helper.absolutePos(new BlockPos(-420000,2,-32000));
            var chunks=CitizenNavigationTests.pinArea(level,base,-8,8,-8,8); CitizenNavigationTests.meadow(level,base,-8,8,-8,8);
            var owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"BorderOwner")); owner.setGameMode(GameType.SURVIVAL);
            owner.setPos(base.getX()+2.5,base.getY(),base.getZ()+0.5); owner.getInventory().clearContent();
            var town=new Settlement(UUID.randomUUID(),owner.getUUID(),"Border town",base,120,List.of(),List.of(),"balanced");
            var neighbour=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Neighbour",base.east(290),120,List.of(),List.of(),"balanced");
            var data=SettlementData.get(level); data.settlements.add(town); data.settlements.add(neighbour);
            level.setBlock(base,VC.BANNER.get().defaultBlockState(),2);
            test.whenDisabled(() -> { data.settlements.remove(town); data.settlements.remove(neighbour); data.setDirty(); CitizenNavigationTests.release(level,base,chunks); });
            int firstCost=ClaimUpgrades.cost(town); owner.getInventory().setItem(0,new ItemStack(Items.EMERALD,firstCost-1));
            helper.assertTrue(!ClaimUpgrades.buy(level,owner,town) && town.radius==120 && SettlementService.emeralds(owner)==firstCost-1,"An unaffordable purchase changed land or money");
            owner.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
            var outsider=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"BorderOutsider")); outsider.setPos(owner.getX(),owner.getY(),owner.getZ());
            outsider.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));
            helper.assertTrue(!ClaimUpgrades.buy(level,outsider,town),"An outsider purchased another town's land");
            helper.assertTrue(ClaimUpgrades.buy(level,owner,town) && town.radius==152 && town.borderLevel==1,"The paid upgrade did not expand by 32 blocks");
            int remaining=SettlementService.emeralds(owner);
            helper.assertTrue(remaining==64-firstCost && ClaimUpgrades.cost(town)==firstCost*2,"Paid land or the next price is incorrect");
            var restored=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow()).getOrThrow();
            helper.assertTrue(restored.radius==152 && restored.borderLevel==1,"A restart loses purchased land or its price");
            helper.assertTrue(!ClaimUpgrades.buy(level,owner,town) && town.radius==152 && SettlementService.emeralds(owner)==remaining,"A neighbouring claim was overwritten or a blocked purchase charged money");
            var row=io.github.swishhyy.vc.menu.Panels.town(level,town,owner).tabs().getLast().rows().stream().filter(r -> r.key().equals("act:border")).findFirst().orElseThrow();
            helper.assertTrue(row.value()==1 && row.detail().getString().contains("Neighbour"),"The banner hides why border expansion is blocked");
            helper.succeed();
        });
    }
}

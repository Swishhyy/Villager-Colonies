package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Campaign mechanics exercised through actual entity navigation, local storage and server death events. */
public final class CampaignWorldTests {
    private static Settlement town(ServerLevel level,BlockPos center,Station... stations) {
        var town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Campaign test",center,32,List.of(),List.of(stations),"balanced");
        SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
        level.setBlockAndUpdate(center,WWMC.BANNER.get().defaultBlockState());
        for(Station s:stations) level.setBlockAndUpdate(s.position(),WWMC.STATIONS.get(s.role()).get().defaultBlockState());
        return town;
    }
    private static CitizenEntity citizen(ServerLevel level,Settlement town,BlockPos pos) {
        var citizen=new CitizenEntity(WWMC.CITIZEN.get(),level); citizen.join(town.id); citizen.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        town.citizens.add(citizen.getUUID()); level.addFreshEntity(citizen); return citizen;
    }
    private static void leave(ServerLevel level,Settlement town,CitizenEntity... citizens) {
        for(var citizen:citizens) citizen.discard(); SettlementData.get(level).settlements.remove(town); SettlementData.get(level).setDirty();
    }
    @GameTest(timeoutTicks=1600)
    @EmptyTemplate
    @TestHolder(description="A wounded citizen walks to an actual hospital bed and a working medic spends real barrel food and paper to treat them.")
    static void hospitalTreatsPatients(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-2240));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,32,-12,12); CitizenNavigationTests.meadow(level,start,-8,32,-12,12);
            BlockPos station=start.east(14),bed=station.north(2); var beds=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
            level.setBlockAndUpdate(bed,beds.setValue(BedBlock.PART,BedPart.FOOT)); level.setBlockAndUpdate(bed.north(),beds.setValue(BedBlock.PART,BedPart.HEAD));
            level.setBlockAndUpdate(station.east(2),Blocks.BARREL.defaultBlockState()); Container supplies=(Container)level.getBlockEntity(station.east(2));
            supplies.setItem(0,new ItemStack(Items.BREAD,16)); supplies.setItem(1,new ItemStack(Items.PAPER,16));
            Settlement town=town(level,start.west(4),new Station(station,StructureRole.HOSPITAL)); town.campaign.projects.add("hospital");
            CitizenEntity medic=citizen(level,town,station.west(2)),patient=citizen(level,town,start.east(2)); patient.setHealth(4);
            town.jobs.assign(medic.getUUID(),station);
            helper.succeedWhen(() -> {
                helper.assertTrue(patient.getHealth()>=patient.getMaxHealth()*0.95F,"Patient untreated: "+patient.activity()+"; medic: "+medic.activity());
                helper.assertTrue(patient.blockPosition().distSqr(bed)<36,"Patient never reached a hospital bed");
                int dressings=InventoryOps.count(List.of(supplies),s -> s.is(Items.PAPER)); int meals=InventoryOps.count(List.of(supplies),FoodHealing::food);
                helper.assertTrue(dressings<16 && dressings==meals,"Treatment did not spend equal real dressings and meals: "+dressings+" / "+meals);
                helper.assertTrue(!patient.recovering(),"Recovered patient remains in treatment");
                leave(level,town,medic,patient); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }
    @GameTest(timeoutTicks=5000)
    @EmptyTemplate
    @TestHolder(description="A deployed guard whose leader disconnects physically returns from beyond the town claim and resumes normal duty.")
    static void disconnectedLeaderReturnsSquad(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-2360));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,100,-12,12); CitizenNavigationTests.meadow(level,start,-8,100,-12,12);
            Station station=new Station(start.east(4),StructureRole.GUARD); Settlement town=town(level,start,station); town.campaign.projects.add("armory");
            CitizenEntity guard=citizen(level,town,start.east(80)); guard.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD)); town.jobs.assign(guard.getUUID(),station.position());
            town.campaign.squads.add(new CampaignState.Squad(town.owner,List.of(guard.getUUID()),"follow",start.east(80),Optional.empty()));
            helper.succeedWhen(() -> {
                helper.assertTrue(town.campaign.squads.isEmpty(),"Squad never returned: "+guard.activity()+" at "+guard.blockPosition());
                helper.assertTrue(guard.blockPosition().distSqr(town.center)<144,"Guard disappeared or teleported far away");
                helper.assertTrue(guard.getMainHandItem().is(Items.IRON_SWORD),"Returning squad lost equipment");
                leave(level,town,guard); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }
    @GameTest(timeoutTicks=300)
    @EmptyTemplate
    @TestHolder(description="Expedition discovery rejects protected construction, creates finite defenders in natural terrain, and records a cleared site after their actual deaths.")
    static void expeditionDefendersAndProtection(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(2400,2,-2500));
            var chunks=CitizenNavigationTests.pinArea(level,start,-12,12,-12,12); CitizenNavigationTests.meadow(level,start,-12,12,-12,12);
            BlockPos protectedBlock=start.east(4); WorldWorkData.get(level).protect(protectedBlock);
            helper.assertTrue(!ExpeditionService.clearSite(level,start),"Encounter overwrote protected construction");
            WorldWorkData.get(level).protectedBlocks.remove(protectedBlock);
            var site=ExpeditionService.discover(level,start,"mine","test:"+UUID.randomUUID()); helper.assertTrue(site!=null,"Natural site was rejected");
            int spawned=ExpeditionService.spawn(level,site,site.pos,3); helper.assertTrue(spawned==3,"Defenders did not spawn on clear ground"); site.spawned=true;
            helper.assertTrue(!site.cleared,"Site cleared before defenders died");
            for(UUID id:new ArrayList<>(site.guards)) level.getEntity(id).kill(level);
            helper.assertTrue(site.cleared && site.guards.isEmpty(),"Death events failed to clear the saved site");
            var data=ExpeditionData.get(level); data.sites.remove(site); data.setDirty(); CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=6500)
    @EmptyTemplate
    @TestHolder(description="A physical trader fills a requested iron target, reserves the shipment in transit, returns, and stops before overstocking or consuming the home reserve.")
    static void requestedShipmentStopsAtTarget(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(0,2,-2650));
            var chunks=CitizenNavigationTests.pinArea(level,start,-8,144,-12,12); CitizenNavigationTests.meadow(level,start,-8,144,-12,12);
            Settlement a=town(level,start,new Station(start.south(2),StructureRole.WAREHOUSE),new Station(start.east(2),StructureRole.TRADER));
            Settlement b=town(level,start.east(128),new Station(start.east(128).south(2),StructureRole.WAREHOUSE),new Station(start.east(130),StructureRole.TRADER));
            b.campaign.requests.put("minecraft:iron_ingot",32); a.campaign.requests.put("minecraft:iron_ingot",32); a.trading.exports.add(new TradeSettings.Export("minecraft:iron_ingot",0,64));
            for(Settlement t:List.of(a,b)) level.setBlockAndUpdate(t.center.south(3),Blocks.BARREL.defaultBlockState());
            Container source=(Container)level.getBlockEntity(a.center.south(3)),target=(Container)level.getBlockEntity(b.center.south(3));
            source.setItem(0,new ItemStack(Items.IRON_INGOT,64)); source.setItem(1,new ItemStack(Items.BREAD,64)); target.setItem(0,new ItemStack(Items.IRON_INGOT,8));
            b.campaign.stock.put("minecraft:iron_ingot",8); TradeRoutes.link(a,b,SettlementData.get(level).settlements,8192);
            TradeRoutes.link(b,a,SettlementData.get(level).settlements,8192);
            CitizenEntity trader=citizen(level,a,start.north(2));
            helper.succeedWhen(() -> {
                helper.assertTrue(a.trading.delivered==24 && trader.tradeCargoCount()==0 && trader.blockPosition().distSqr(a.center)<100,"Shipment unfinished: "+a.trading.status);
                helper.assertTrue(InventoryOps.count(List.of(source),s -> s.is(Items.IRON_INGOT))==40,"Home stock consumed or duplicated");
                helper.assertTrue(InventoryOps.count(List.of(target),s -> s.is(Items.IRON_INGOT))==32,"Destination not at requested stock");
                helper.assertTrue(b.campaign.incoming.isEmpty(),"Delivered shipment still reserves incoming demand");
                helper.assertTrue(SupplyRequests.deficit(b,"minecraft:iron_ingot")==0,"Town still requests a full shipment");
                TradeChunks.release(level,a.id); TradeChunks.release(level,b.id); leave(level,a,trader); leave(level,b); CitizenNavigationTests.release(level,start,chunks);
            });
        });
    }
    @GameTest(timeoutTicks=300)
    @EmptyTemplate
    @TestHolder(description="Claiming a cleared site requires a funded charter and an in-person manager, then creates one supplied outpost with housing, workers and a reciprocal route without replacing the main partner.")
    static void charterClaimsSuppliedOutpost(DynamicTest test) {
        test.onGameTest(helper -> {
            ServerLevel level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(3400,2,-2950));
            var chunks=CitizenNavigationTests.pinArea(level,start,-12,12,-12,12); CitizenNavigationTests.meadow(level,start,-12,12,-12,12);
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"CampaignManager")); player.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
            Settlement parent=new Settlement(UUID.randomUUID(),player.getUUID(),"Parent town",start.west(640),32,List.of(),List.of(),"balanced");
            parent.campaign.members.put(UUID.randomUUID(),"steward"); UUID primary=UUID.randomUUID(); parent.trading.partner=primary;
            SettlementData.get(level).settlements.add(parent);
            var site=ExpeditionService.discover(level,start,"mine","outpost-test:"+UUID.randomUUID()); helper.assertTrue(site!=null,"Outpost site was rejected"); site.cleared=true; site.spawned=true;
            ExpeditionService.claim(level,parent,player,site.id); helper.assertTrue(site.claimed==null,"Unfunded charter allowed a claim");
            parent.campaign.projects.add("frontier"); player.setPos(start.getX()+40,start.getY(),start.getZ());
            ExpeditionService.claim(level,parent,player,site.id); helper.assertTrue(site.claimed==null,"Remote player claimed the site");
            player.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5); ExpeditionService.claim(level,parent,player,site.id);
            Settlement outpost=SettlementData.get(level).byId(site.claimed); helper.assertTrue(outpost!=null,"Cleared site could not be claimed");
            helper.assertTrue(outpost.owner.equals(parent.owner) && outpost.campaign.members.equals(parent.campaign.members),"Outpost ownership or membership changed");
            helper.assertTrue(outpost.citizens.size()==4 && SettlementService.housingBeds(level,outpost).size()==4,"Outpost did not recruit its four housed workers");
            helper.assertTrue(TradeRoutes.agreed(parent,outpost) && primary.equals(parent.trading.partner),"Outpost displaced the main route");
            helper.assertTrue(outpost.campaign.requests.get("minecraft:bread")==32 && outpost.campaign.requests.get("minecraft:stone_pickaxe")==2,"Outpost has no supply demand");
            helper.assertTrue(outpost.jobs.level(StructureRole.MINE)==JobBoard.HIGH,"Small starting crew will leave its miner idle");
            int population=outpost.citizens.size(); ExpeditionService.claim(level,parent,player,site.id);
            helper.assertTrue(outpost.citizens.size()==population && parent.campaign.extraRoutes.size()==1,"Claiming twice duplicated citizens or routes");
            for(UUID id:new ArrayList<>(outpost.citizens)) if(level.getEntity(id) instanceof CitizenEntity citizen) citizen.discard();
            leave(level,outpost); leave(level,parent); ExpeditionData.get(level).sites.remove(site); ExpeditionData.get(level).setDirty();
            CitizenNavigationTests.release(level,start,chunks); helper.succeed();
        });
    }
}

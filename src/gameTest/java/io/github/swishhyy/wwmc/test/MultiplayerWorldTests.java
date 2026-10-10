package io.github.swishhyy.wwmc.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

/** Actual inventory transfers, damage hooks, consent, timed capture and saved-state recovery in a server world. */
public final class MultiplayerWorldTests {
    /** Use ordinary damage and team permission behavior while retaining a network sink for the dedicated test server. */
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level,String name,BlockPos pos) {
            super(level,new GameProfile(UUID.randomUUID(),name)); setInvulnerable(false); setHealth(getMaxHealth());
            setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5); level.addNewPlayer(this);
            connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        }
        @Override public boolean canHarmPlayer(Player other) { return getTeam()==null || getTeam()!=other.getTeam() || getTeam().isAllowFriendlyFire(); }
    }
    private static final class Fixture {
        final ServerLevel level; final BlockPos start;
        final List<Settlement> towns=new ArrayList<>(); final List<TestPlayer> players=new ArrayList<>();
        final Map<BlockPos,List<ChunkPos>> chunks=new HashMap<>(); final List<CitizenEntity> citizens=new ArrayList<>();
        final List<ExpeditionData.Site> sites=new ArrayList<>();
        Fixture(ServerLevel level,BlockPos start) { this.level=level; this.start=start; MultiplayerData.get(level); }
        TestPlayer player(String name,BlockPos pos) { var player=new TestPlayer(level,name,pos); players.add(player); return player; }
        Settlement town(UUID owner,BlockPos pos,String name) {
            chunks.put(pos,CitizenNavigationTests.pinArea(level,pos,-10,10,-10,10)); CitizenNavigationTests.meadow(level,pos,-10,10,-10,10);
            Station warehouse=new Station(pos.west(3),StructureRole.WAREHOUSE);
            var town=new Settlement(UUID.randomUUID(),owner,name,pos,Settlement.MIN_RADIUS,List.of(),List.of(warehouse),"balanced");
            towns.add(town); SettlementData.get(level).settlements.add(town); SettlementData.get(level).setDirty();
            level.setBlockAndUpdate(pos,WWMC.BANNER.get().defaultBlockState());
            level.setBlockAndUpdate(warehouse.position(),WWMC.STATIONS.get(warehouse.role()).get().defaultBlockState());
            level.setBlockAndUpdate(pos.west(4),Blocks.BARREL.defaultBlockState()); return town;
        }
        Container stock(Settlement town) { return (Container)level.getBlockEntity(town.center.west(4)); }
        void close() {
            var data=MultiplayerData.get(level); Set<UUID> ids=new HashSet<>(); towns.forEach(t -> ids.add(t.id));
            data.contracts.removeIf(c -> ids.contains(c.issuer) || ids.contains(c.supplier));
            data.contests.removeIf(c -> ids.contains(c.outpost) || ids.contains(c.challenger) || ids.contains(c.defender));
            for(var player:players) { data.duels.removeIf(d -> d.includes(player.getUUID())); data.payments.remove(player.getUUID()); level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED); }
            data.setDirty(); citizens.forEach(CitizenEntity::discard);
            SettlementData.get(level).settlements.removeAll(towns); SettlementData.get(level).setDirty();
            ExpeditionData.get(level).sites.removeAll(sites); ExpeditionData.get(level).setDirty();
            chunks.forEach((pos,pinned) -> CitizenNavigationTests.release(level,pos,pinned));
        }
    }
    private static PlayerInteractEvent.RightClickBlock use(ServerPlayer player,BlockPos pos) {
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
    }
    @GameTest(timeoutTicks=120)
    @EmptyTemplate
    @TestHolder(description="The command posts one real reserved payment, another settlement accepts exclusively, public banner access grants no storage/research rights, partial delivery respects full storage, and a full recipient inventory retains payment without duplication.")
    static void playerContractEscrowAndDelivery(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(14000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer issuer=f.player("ContractIssuer",start.east()),supplier=f.player("ContractSupplier",start.east(601));
            Settlement a=f.town(issuer.getUUID(),start,"Requesting Town"),b=f.town(supplier.getUUID(),start.east(600),"Supplying Town");
            issuer.getInventory().setItem(0,new ItemStack(Items.BREAD)); issuer.getInventory().setItem(1,new ItemStack(Items.EMERALD,8));
            try { level.getServer().getCommands().getDispatcher().execute("wwmc playercontract post 10 4",issuer.createCommandSourceStack()); }
            catch(Exception error) { throw new AssertionError("Posting command failed",error); }
            var data=MultiplayerData.get(level); var offers=data.contracts.stream().filter(c -> c.issuer.equals(a.id)).toList();
            helper.assertTrue(offers.size()==1 && issuer.getInventory().countItem(Items.EMERALD)==4 && issuer.getInventory().countItem(Items.BREAD)==1,"Posting duplicated an offer/payment or spent the example");
            var order=offers.getFirst(); PlayerContracts.accept(level,b,supplier,order.id);
            helper.assertTrue(b.id.equals(order.supplier) && supplier.getUUID().equals(order.recipient),"Exclusive supplier or payment recipient was not reserved");
            PlayerContracts.accept(level,a,issuer,order.id); helper.assertTrue(b.id.equals(order.supplier),"A second accept stole a reserved contract");
            supplier.setPos(start.getX()+1.5,start.getY(),start.getZ()+1.5); supplier.getInventory().setItem(0,ItemStack.EMPTY);
            a.progress.research.add("iron_age");
            helper.assertTrue(!use(supplier,start).isCanceled() && MultiplayerViews.valid(supplier,start) && use(supplier,start.west(4)).isCanceled(),"Public banner access opened storage or blocked the board");
            helper.assertTrue(ClaimProtection.denied(level,supplier,start) && !TownAccess.manages(a,supplier.getUUID()) && !AgeProgression.allowed(supplier,new ItemStack(Items.IRON_SWORD)),"Public board granted permissions or shared research");
            PlayerContracts.cancel(level,issuer,order.id); helper.assertTrue(data.contract(order.id)==order,"Issuer cancelled an accepted contract and reclaimed payment");
            Container stock=f.stock(a); for(int slot=0;slot<stock.getContainerSize();slot++) stock.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            stock.setItem(0,new ItemStack(Items.BREAD,61)); supplier.getInventory().setItem(9,new ItemStack(Items.BREAD,10));
            MultiplayerViews.act(supplier,start,MultiplayerViews.ROW_ACTION,1,"act:contract-deliver:"+order.id);
            helper.assertTrue(order.delivered==3 && supplier.getInventory().countItem(Items.BREAD)==7 && stock.getItem(0).getCount()==64 && !data.payments.containsKey(supplier.getUUID()),"Full warehouse credited, lost or paid for undelivered goods");
            PlayerContracts.deliver(level,supplier,order.id); helper.assertTrue(order.delivered==3 && supplier.getInventory().countItem(Items.BREAD)==7,"Zero-capacity retry lost goods or advanced progress");
            stock.setItem(1,ItemStack.EMPTY); PlayerContracts.deliver(level,supplier,order.id);
            helper.assertTrue(data.contract(order.id)==null && supplier.getInventory().countItem(Items.BREAD)==0 && stock.getItem(1).is(Items.BREAD) && stock.getItem(1).getCount()==7
                    && data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Completion did not conserve real goods and reserved payment");
            PlayerContracts.deliver(level,supplier,order.id); helper.assertTrue(data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Duplicate delivery paid twice");
            for(int slot=0;slot<supplier.getInventory().getContainerSize();slot++) supplier.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
            helper.assertTrue(PlayerContracts.collect(level,supplier)==0 && data.payments.getOrDefault(supplier.getUUID(),0L)==4,"Full inventory lost reserved payment");
            supplier.getInventory().setItem(9,ItemStack.EMPTY);
            helper.assertTrue(PlayerContracts.collect(level,supplier)==4 && PlayerContracts.collect(level,supplier)==0 && supplier.getInventory().countItem(Items.EMERALD)==4,"Collection duplicated or dropped the escrow");
            PlayerContracts.post(level,a,issuer,new ItemStack(Items.BREAD),1,2);
            var cancelled=data.contracts.stream().filter(c -> c.issuer.equals(a.id)).findFirst().orElseThrow(); PlayerContracts.cancel(level,issuer,cancelled.id);
            helper.assertTrue(data.payments.getOrDefault(issuer.getUUID(),0L)==2 && data.contract(cancelled.id)==null,"Unaccepted cancellation did not refund the original publisher");
            f.close(); helper.succeed();
        });
    }
    @GameTest(timeoutTicks=220)
    @EmptyTemplate
    @TestHolder(description="A real player damage pipeline respects duel consent/countdown, permits only the paired visitors inside a claim, finishes nonlethally at one heart, prevents an immediate follow-up kill and pays both reserved stakes once.")
    static void consensualDuelDamageAndStakes(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(16000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("DuelOwner",start.east()),b=f.player("DuelGuest",start.south(2)),c=f.player("DuelBystander",start.west(2));
            f.town(a.getUUID(),start,"Duel Town");
            a.getInventory().setItem(0,new ItemStack(Items.WOODEN_SWORD)); a.getInventory().setItem(1,new ItemStack(Items.EMERALD,5));
            b.getInventory().setItem(0,new ItemStack(Items.WOODEN_SWORD)); b.getInventory().setItem(1,new ItemStack(Items.EMERALD,5));
            PlayerDuels.challenge(level,a,b,5); var data=MultiplayerData.get(level); var duel=data.duel(a.getUUID());
            helper.assertTrue(duel!=null && !duel.accepted && a.getInventory().countItem(Items.EMERALD)==0 && b.getInventory().countItem(Items.EMERALD)==5,"Duel proposal did not reserve only the challenger's stake");
            helper.assertTrue(!MultiplayerCombat.claimException(level,b,a),"An unaccepted duel bypassed a claim");
            PlayerDuels.accept(level,b);
            helper.assertTrue(duel.accepted && b.getInventory().countItem(Items.EMERALD)==0 && !PlayerDuels.allows(level,b,a),"Acceptance did not match the stake or bypassed countdown");
            a.hurtServer(level,level.damageSources().playerAttack(b),100); helper.assertTrue(a.getHealth()==a.getMaxHealth(),"Countdown allowed player damage");
            helper.runAfterDelay(110,() -> {
                helper.assertTrue(PlayerDuels.allows(level,b,a) && MultiplayerCombat.claimException(level,b,a) && ClaimProtection.denied(level,b,a.blockPosition()),"Active duel did not make a narrow player-only claim exception");
                c.hurtServer(level,level.damageSources().playerAttack(a),5); helper.assertTrue(c.getHealth()==c.getMaxHealth(),"Duel participant could attack an unconsenting bystander");
                b.hurtServer(level,level.damageSources().playerAttack(a),100);
                helper.assertTrue(b.isAlive() && b.getHealth()==2 && data.duel(a.getUUID())==null,"Finishing duel hit killed the loser or failed to end the duel: "+b.getHealth());
                b.invulnerableTime=0; b.hurtServer(level,level.damageSources().playerAttack(a),100);
                helper.assertTrue(b.getHealth()==2 && data.payments.getOrDefault(a.getUUID(),0L)==10 && data.payments.getOrDefault(b.getUUID(),0L)==0,"Finishing grace or winner payment failed");
                PlayerDuels.finish(level,duel,a.getUUID(),"Duplicate callback");
                helper.assertTrue(PlayerContracts.collect(level,a)==10 && PlayerContracts.collect(level,a)==0 && a.getInventory().countItem(Items.WOODEN_SWORD)==1 && b.getInventory().countItem(Items.WOODEN_SWORD)==1,"Duel duplicated stakes or removed equipment");
                f.close(); helper.succeed();
            });
        });
    }
    @GameTest(timeoutTicks=120)
    @EmptyTemplate
    @TestHolder(description="Leaving an accepted duel arena forfeits the matched stakes to the remaining player once; a declined pending invitation refunds only the paid challenger.")
    static void duelLeavingAndDecline(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(20000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("DuelStay",start.east()),b=f.player("DuelLeave",start.south(2));
            f.town(a.getUUID(),start,"Forfeit Town"); a.getInventory().setItem(1,new ItemStack(Items.EMERALD,8)); b.getInventory().setItem(1,new ItemStack(Items.EMERALD,4));
            PlayerDuels.challenge(level,a,b,4); PlayerDuels.cancel(level,b);
            helper.assertTrue(MultiplayerData.get(level).payments.getOrDefault(a.getUUID(),0L)==4 && b.getInventory().countItem(Items.EMERALD)==4,"Decline did not refund only the paid stake");
            PlayerContracts.collect(level,a); PlayerDuels.challenge(level,a,b,4); PlayerDuels.accept(level,b);
            helper.runAfterDelay(5,() -> {
                b.setPos(start.getX()+40.5,start.getY(),start.getZ()+0.5); PlayerDuels.tick(level); PlayerDuels.tick(level);
                var data=MultiplayerData.get(level);
                helper.assertTrue(data.duel(a.getUUID())==null && data.payments.getOrDefault(a.getUUID(),0L)==8 && data.payments.getOrDefault(b.getUUID(),0L)==0,"Leaving the arena refunded or duplicated a forfeit instead of paying the remaining player");
                f.close(); helper.succeed();
            });
        });
    }
    @GameTest(timeoutTicks=2850)
    @EmptyTemplate
    @TestHolder(description="An outpost never starts without defending-owner consent, its assembly period and defending presence block capture, and 60 seconds of real uncontested flag presence transfers the existing miner, stock, routes and saved ownership exactly once.")
    static void agreedOutpostCapture(DynamicTest test) {
        test.onGameTest(helper -> {
            var level=helper.getLevel(); BlockPos start=helper.absolutePos(new BlockPos(18000,2,-4000)); var f=new Fixture(level,start);
            TestPlayer a=f.player("OutpostDefender",start.east()),b=f.player("OutpostAttacker",start.east(601));
            Settlement defender=f.town(a.getUUID(),start,"Defending Town"),challenger=f.town(b.getUUID(),start.east(600),"Challenging Town"),outpost=f.town(a.getUUID(),start.east(1200),"Copper Outpost");
            challenger.campaign.projects.add("frontier"); outpost.campaign.parent=defender.id;
            defender.campaign.extraRoutes.add(outpost.id); outpost.campaign.extraRoutes.add(defender.id);
            outpost.progress.research.add("iron_age"); challenger.progress.research.add("bronze_age");
            f.stock(outpost).setItem(0,new ItemStack(Items.RAW_COPPER,31));
            var miner=new CitizenEntity(WWMC.CITIZEN.get(),level); miner.join(outpost.id); miner.setNoAi(true);
            miner.setPos(outpost.center.getX()+2.5,outpost.center.getY(),outpost.center.getZ()+2.5); miner.bag().offer(new ItemStack(Items.STONE_PICKAXE));
            outpost.citizens.add(miner.getUUID()); f.citizens.add(miner); level.addFreshEntity(miner);
            var site=new ExpeditionData.Site(UUID.randomUUID(),outpost.center,"mine","test"); site.spawned=true; site.cleared=true; site.claimed=outpost.id;
            ExpeditionData.get(level).sites.add(site); f.sites.add(site); ExpeditionData.get(level).setDirty();
            helper.runAfterDelay(5,() -> {
                OutpostContests.challenge(level,challenger,b,outpost.id); var data=MultiplayerData.get(level);
                var contest=data.contests.stream().filter(c -> c.outpost.equals(outpost.id)).findFirst().orElseThrow();
                b.setPos(outpost.center.getX()+2.5,outpost.center.getY(),outpost.center.getZ()+0.5);
                a.setPos(outpost.center.getX()+0.5,outpost.center.getY(),outpost.center.getZ()+10.5);
                OutpostContests.tick(level); helper.assertTrue(outpost.owner.equals(a.getUUID()) && contest.progress==0 && !OutpostContests.allows(level,b,a),"Unaccepted challenge changed ownership or combat access");
                OutpostContests.accept(level,b,contest.id); helper.assertTrue(!contest.accepted,"Attacker accepted its own outpost offer");
                OutpostContests.accept(level,a,contest.id);
                helper.assertTrue(contest.accepted && contest.starts==level.getGameTime()+1200 && !OutpostContests.allows(level,b,a),"Defending owner could not consent or assembly allowed early combat");
                helper.runAfterDelay(1240,() -> {
                    helper.assertTrue(contest.progress==0 && outpost.owner.equals(a.getUUID()) && OutpostContests.allows(level,b,a),"Defender presence allowed capture or active consensual combat stayed blocked");
                    helper.assertTrue(use(b,outpost.center.west(4)).isCanceled(),"Outpost battle opened its warehouse to attackers");
                    a.setPos(outpost.center.getX()+40.5,outpost.center.getY(),outpost.center.getZ()+0.5);
                    helper.runAfterDelay(1160,() -> helper.assertTrue(outpost.owner.equals(a.getUUID()) && contest.progress<1200,"Outpost was captured before 60 seconds"));
                    helper.runAfterDelay(1260,() -> {
                        helper.assertTrue(outpost.owner.equals(b.getUUID()) && challenger.id.equals(outpost.campaign.parent) && site.claimed.equals(outpost.id) && data.contest(contest.id)==null,"Agreed capture did not transfer the existing saved outpost");
                        helper.assertTrue(!defender.campaign.extraRoutes.contains(outpost.id) && challenger.campaign.extraRoutes.contains(outpost.id) && outpost.campaign.extraRoutes.equals(Set.of(challenger.id)),"Capture retained the old supply route or failed to connect the new home");
                        helper.assertTrue(outpost.citizens.contains(miner.getUUID()) && miner.town(level)==outpost && f.stock(outpost).getItem(0).getCount()==31 && miner.bag().count(Items.STONE_PICKAXE)==1,"Capture generated, lost or duplicated existing citizens and supplies");
                        helper.assertTrue(TownAccess.manages(outpost,b.getUUID()) && !TownAccess.builds(outpost,a.getUUID()) && outpost.progress.research.contains("bronze_age") && !outpost.progress.research.contains("iron_age"),"Capture retained defeated permissions or bypassed the new owner's research");
                        var saved=Settlement.CODEC.parse(JsonOps.INSTANCE,Settlement.CODEC.encodeStart(JsonOps.INSTANCE,outpost).getOrThrow()).getOrThrow();
                        helper.assertTrue(saved.id.equals(outpost.id) && saved.owner.equals(b.getUUID()) && saved.campaign.parent.equals(challenger.id),"Captured ownership or routes did not round-trip through the world save codec");
                        f.close(); helper.succeed();
                    });
                });
            });
        });
    }
    @GameTest(timeoutTicks=30)
    @EmptyTemplate
    @TestHolder(description="Save decoding preserves accepted partial contracts and pending payouts; restart recovery refunds both accepted duel stakes, only the pending challenger stake, cancels offline contests, and cannot refund twice.")
    static void multiplayerRestartRecovery(DynamicTest test) {
        test.onGameTest(helper -> {
            UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),d=UUID.randomUUID(),issuer=UUID.randomUUID(),supplier=UUID.randomUUID();
            var order=new MultiplayerData.Contract(UUID.randomUUID(),issuer,a,"minecraft:bread",10,3,4,Optional.of(supplier),Optional.of(b));
            var duel=new MultiplayerData.Duel(UUID.randomUUID(),a,b,5,BlockPos.ZERO,true,100,6100);
            var pending=new MultiplayerData.Duel(UUID.randomUUID(),c,d,4,BlockPos.ZERO,false,0,1200);
            var contest=new MultiplayerData.Contest(UUID.randomUUID(),UUID.randomUUID(),issuer,supplier,100,6100,true,600);
            var original=new MultiplayerData(List.of(order),List.of(duel,pending),List.of(contest),Map.of(a,2L));
            var saved=MultiplayerData.CODEC.parse(JsonOps.INSTANCE,MultiplayerData.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow()).getOrThrow(); saved.recover();
            var restored=saved.contract(order.id);
            helper.assertTrue(restored!=null && restored.remaining()==7 && restored.payment==4 && supplier.equals(restored.supplier) && b.equals(restored.recipient),"Restart discarded partial delivery, reserved payment or exclusive supplier");
            helper.assertTrue(saved.duels.isEmpty() && saved.contests.isEmpty() && saved.payments.getOrDefault(a,0L)==7 && saved.payments.getOrDefault(b,0L)==5 && saved.payments.getOrDefault(c,0L)==4 && saved.payments.getOrDefault(d,0L)==0,"Restart awarded a forfeit, lost a paid stake, or invented an unpaid stake");
            saved.recover(); helper.assertTrue(saved.payments.get(a)==7 && saved.payments.get(b)==5 && saved.payments.get(c)==4,"Repeated recovery duplicated refunds"); helper.succeed();
        });
    }
}

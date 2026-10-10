package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.menu.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

/** Public banner board. Every mutation checks its own settlement authority; reading this menu grants no claim access. */
public final class MultiplayerViews {
    public static final int OPEN=90,ROW_ACTION=91,BACK=92,COLLECT=93;
    private static final class Draft { int amount=32,payment=4,stake; }
    private static final Map<ServerPlayer,Draft> DRAFTS=new WeakHashMap<>();
    private MultiplayerViews() {}
    private static ServerLevel level(ServerPlayer player) { return (ServerLevel)player.level(); }
    private static Draft draft(ServerPlayer player) { return DRAFTS.computeIfAbsent(player,p -> new Draft()); }
    private static PanelView.Row row(ItemLike icon,String title,String detail,String key,int value) {
        return new PanelView.Row(new ItemStack(icon),Component.literal(title),Component.literal(detail),0,-1,value,key);
    }
    private static PanelView.Row action(ItemLike icon,String title,String detail,String key,boolean enabled) {
        return row(icon,title,detail,"act:"+key,enabled ? 0 : 1);
    }
    public static boolean valid(ServerPlayer player,BlockPos pos) {
        ServerLevel level=level(player); Settlement town=SettlementData.get(level).at(pos);
        return player.isAlive() && !player.isSpectator() && town!=null && !town.trading.npc && town.center.equals(pos)
                && player.distanceToSqr(Vec3.atCenterOf(pos))<=64 && level.hasChunkAt(pos) && level.getBlockState(pos).is(WWMC.BANNER.get());
    }
    public static Settlement home(ServerLevel level,ServerPlayer player) {
        return SettlementData.get(level).settlements.stream().filter(t -> !t.trading.npc && t.campaign.parent==null && TownAccess.manages(t,player.getUUID()))
                .min(Comparator.comparingDouble(t -> t.center.distSqr(player.blockPosition()))).orElse(null);
    }
    public static void open(ServerPlayer player,Settlement town) {
        if(town==null || !valid(player,town.center)) return;
        PanelView view=view(player,town.center);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.MULTIPLAYER,town.center,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.MULTIPLAYER,town.center,view));
    }
    public static PanelView view(ServerPlayer player,BlockPos pos) {
        if(!valid(player,pos)) return null;
        ServerLevel level=level(player); var data=MultiplayerData.get(level); var towns=SettlementData.get(level);
        Settlement board=towns.at(pos),home=home(level,player); Draft draft=draft(player);
        List<PanelView.Row> contracts=new ArrayList<>(),duels=new ArrayList<>(),outposts=new ArrayList<>();
        contracts.add(row(Items.PAPER,home==null ? "Join a settlement to supply contracts" : "Supplying for: "+home.name,"Deliver at the issuer's banner. Only plain, undamaged goods count; the accepting player receives the reserved payment.","",-1));
        if(home==board && TownAccess.manages(board,player.getUUID())) {
            ItemStack held=player.getMainHandItem();
            contracts.add(row(held.isEmpty() ? Items.PAPER : held.getItem(),"Request: "+(held.isEmpty() ? "hold an example item" : held.getHoverName().getString()),"Hold a plain example before opening this board; the example stays yours.","",-1));
            contracts.add(row(Items.CHEST,"Requested amount: "+draft.amount,"1–256 plain items; quantities can be delivered in parts","choice:amount",draft.amount));
            contracts.add(row(Items.EMERALD,"Reserved payment: "+draft.payment,"1–64 emeralds from your inventory, held until delivery or cancellation","choice:payment",draft.payment));
            contracts.add(action(Items.PAPER,"Post supply contract","Reserve "+draft.payment+" emeralds for "+draft.amount+" requested items. Up to eight active offers per main settlement.","contract-post",!held.isEmpty()));
        }
        for(var order:data.contracts) {
            Settlement issuer=towns.byId(order.issuer); if(issuer==null) continue;
            var item=PlayerContracts.item(order); boolean own=TownAccess.manages(issuer,player.getUUID());
            Settlement supplier=order.supplier==null ? null : towns.byId(order.supplier);
            boolean supplying=TownAccess.manages(supplier,player.getUUID());
            contracts.add(row(item,issuer.name+": "+order.remaining()+" × "+new ItemStack(item).getHoverName().getString(),order.payment+" emeralds reserved · "+order.delivered+"/"+order.amount+" delivered · "+(supplier==null ? "available" : "accepted by "+supplier.name)+" · banner "+issuer.center.toShortString(),"",-1));
            if(order.supplier==null) {
                contracts.add(action(Items.PAPER,"Accept: "+issuer.name,"Accept exclusively for "+(home==null ? "a main settlement you manage" : home.name)+". Payment goes to the player accepting; deliver the remaining goods at the issuer's banner.","contract-accept:"+order.id,home!=null && !home.owner.equals(issuer.owner)));
                if(own) contracts.add(action(Items.DYE.red(),"Cancel unaccepted offer","Refunds all reserved emeralds to the original publisher's payment balance","contract-cancel:"+order.id,true));
            } else if(supplying) {
                contracts.add(action(Items.CHEST,"Deliver requested goods","Move only matching plain items from your inventory into the issuer's loaded warehouse; full storage keeps your remaining goods and payment safe.","contract-deliver:"+order.id,board==issuer));
                contracts.add(action(Items.DYE.red(),"Release accepted contract","Already delivered goods stay credited. The remaining request becomes available to another supplier; no payment is released yet.","contract-release:"+order.id,true));
            }
        }
        if(data.contracts.isEmpty()) contracts.add(row(Items.PAPER,"No player offers yet","Owners and stewards can post at their main settlement banner by holding an example and reserving emeralds.","",-1));
        duels.add(row(Items.WOODEN_SWORD,"Consensual, nonlethal duels","Five-second countdown; finish at one heart. 24-block arena, five-minute limit. Leaving or disconnecting forfeits; normal equipment wear applies.","",-1));
        duels.add(row(Items.EMERALD,"Stake each: "+draft.stake,"0–64 emeralds per player, reserved on offer and acceptance; winner receives both stakes","choice:stake",draft.stake));
        var duel=data.duel(player.getUUID());
        if(duel!=null) {
            var other=PlayerDuels.online(level,duel.other(player.getUUID()));
            duels.add(row(Items.WOODEN_SWORD,"Duel with "+(other==null ? "offline player" : other.getName().getString()),duel.accepted ? "Arena "+duel.arena.toShortString()+" · "+duel.stake+" emeralds each · "+Math.max(0,(duel.deadline-level.getGameTime())/20)+"s remaining" : "Invitation waiting · "+duel.stake+" emeralds each · arena "+duel.arena.toShortString(),"",-1));
            if(!duel.accepted && duel.opponent.equals(player.getUUID())) duels.add(action(Items.WOODEN_SWORD,"Accept duel","Match the stake. Starts in five seconds; winner gets both stakes. Finish at one heart.","duel-accept",true));
            duels.add(action(Items.DYE.red(),duel.accepted ? "Surrender duel" : "Decline / cancel invitation",duel.accepted ? "Forfeit your stake; the opponent wins" : "Closes the invitation and refunds the challenger","duel-cancel",true));
        } else for(ServerPlayer other:level.players()) if(other!=player && player.distanceToSqr(other)<=16*16) {
            duels.add(action(Items.WOODEN_SWORD,"Challenge "+other.getName().getString(),"Reserve "+draft.stake+" emeralds. Your opponent must explicitly accept and match the stake. Arena centered on your current position.","duel-challenge:"+other.getUUID(),data.duel(other.getUUID())==null));
        }
        outposts.add(row(Items.BANNER.red(),"Agreed resource outpost battles","Both main settlement owners must accept. One-minute assembly, five-minute battle. Normal PvP death and drops apply within the 32-block battle area.","",-1));
        outposts.add(row(Items.COMPASS,"Capture the existing flag","Stand within six blocks for 60 seconds, with no defender within 16 blocks. Both owners must remain online; the existing miners and warehouse change hands.","",-1));
        for(var contest:data.contests) {
            Settlement outpost=towns.byId(contest.outpost),defender=towns.byId(contest.defender),challenger=towns.byId(contest.challenger);
            if(outpost==null || defender==null || challenger==null) continue;
            String state=!contest.accepted ? "Awaiting defending owner's consent" : level.getGameTime()<contest.starts ? "Assemble: "+(contest.starts-level.getGameTime()+19)/20+"s" : "Capture "+contest.progress/20+"/60s · "+Math.max(0,(contest.deadline-level.getGameTime())/20)+"s left";
            outposts.add(row(Items.BANNER.red(),outpost.name,challenger.name+" vs "+defender.name+" · "+state+" · "+outpost.center.toShortString(),"",-1).bar(contest.progress/(float)OutpostContests.CAPTURE_TICKS,0x77BB77));
            if(!contest.accepted && TownAccess.owner(defender,player.getUUID())) outposts.add(action(Items.BANNER.red(),"Accept outpost battle","Consent to normal PvP and possible transfer of this resource outpost. Both owners must remain online; other claim permissions stay closed.","outpost-accept:"+contest.id,true));
            if(!contest.accepted && (TownAccess.owner(defender,player.getUUID()) || TownAccess.owner(challenger,player.getUUID()))) outposts.add(action(Items.DYE.red(),"Decline / withdraw challenge","No battle or capture starts without the defending owner's acceptance","outpost-decline:"+contest.id,true));
        }
        if(home!=null && TownAccess.owner(home,player.getUUID())) for(Settlement target:towns.settlements.stream().filter(t -> OutpostContests.resourceOutpost(level,t)).limit(40).toList()) {
            Settlement parent=towns.byId(target.campaign.parent);
            if(parent==null || parent.owner.equals(home.owner) || TownAccess.allied(home,parent)) continue;
            outposts.add(action(Items.BANNER.red(),"Challenge: "+target.name,"Send an offer to "+parent.name+"'s owner. Requires your Frontier Charter and a free supply route; flag at "+target.center.toShortString(),"outpost-challenge:"+target.id,data.contests.stream().noneMatch(c -> c.outpost.equals(target.id))));
        }
        return new PanelView(Component.literal(board.name+" · Multiplayer"),Component.literal("Contracts, duels, outposts · payment balance: "+data.payments.getOrDefault(player.getUUID(),0L)+" emeralds"),
                List.of(new PanelView.Tab("Contracts",contracts),new PanelView.Tab("Duels",duels),new PanelView.Tab("Outposts",outposts)),
                List.of(new PanelView.Action(COLLECT,"Collect payment",data.payments.getOrDefault(player.getUUID(),0L)>0,"Full inventory leaves remaining emeralds in your saved payment balance"),
                        new PanelView.Action(BACK,"Town overview",TownAccess.manages(board,player.getUUID()))));
    }
    public static void act(ServerPlayer player,BlockPos pos,int action,int value,String key) {
        if(!valid(player,pos) || key.length()>256) return;
        ServerLevel level=level(player); Settlement board=SettlementData.get(level).at(pos),home=home(level,player); String message="";
        if(action==BACK) { if(TownAccess.manages(board,player.getUUID())) Panels.openTown(player,board); return; }
        if(action==COLLECT) { int moved=PlayerContracts.collect(level,player); SettlementService.notify(player,moved>0 ? "Collected "+moved+" emeralds." : "Make room in your inventory; remaining payment stays reserved."); return; }
        if(action!=ROW_ACTION) return;
        if(key.startsWith("choice:")) {
            Draft draft=draft(player);
            switch(key) { case "choice:amount" -> draft.amount=Math.clamp(value,1,256); case "choice:payment" -> draft.payment=Math.clamp(value,1,64); case "choice:stake" -> draft.stake=Math.clamp(value,0,64); default -> { return; } }
            return;
        }
        if(!key.startsWith("act:")) return;
        String[] parts=key.substring(4).split(":",2); String op=parts[0],arg=parts.length==2 ? parts[1] : "";
        try {
            switch(op) {
                case "contract-post" -> { Draft draft=draft(player); message=PlayerContracts.post(level,board,player,player.getMainHandItem(),draft.amount,draft.payment); }
                case "contract-accept" -> message=PlayerContracts.accept(level,home,player,UUID.fromString(arg));
                case "contract-deliver" -> message=PlayerContracts.deliver(level,player,UUID.fromString(arg));
                case "contract-cancel" -> message=PlayerContracts.cancel(level,player,UUID.fromString(arg));
                case "contract-release" -> message=PlayerContracts.abandon(level,player,UUID.fromString(arg));
                case "duel-challenge" -> message=PlayerDuels.challenge(level,player,PlayerDuels.online(level,UUID.fromString(arg)),draft(player).stake);
                case "duel-accept" -> message=PlayerDuels.accept(level,player);
                case "duel-cancel" -> message=PlayerDuels.cancel(level,player);
                case "outpost-challenge" -> message=OutpostContests.challenge(level,home,player,UUID.fromString(arg));
                case "outpost-accept" -> message=OutpostContests.accept(level,player,UUID.fromString(arg));
                case "outpost-decline" -> message=OutpostContests.decline(level,player,UUID.fromString(arg));
                default -> { return; }
            }
        } catch(IllegalArgumentException ignored) { return; }
        if(!message.isEmpty()) SettlementService.notify(player,message);
    }
}

package io.github.swishhyy.wwmc.settlement;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Every player action resolves its town and rechecks permissions on the server. No operator rights are needed. */
public final class CampaignCommands {
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return c.getSource().getPlayerOrException(); }
    private static ServerLevel level(CommandContext<CommandSourceStack> c) { return c.getSource().getLevel(); }
    private static Settlement town(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(c); var local=SettlementData.get(level(c)).at(p.blockPosition());
        if(TownAccess.manages(local,p.getUUID())) return local;
        Settlement marching=SettlementData.get(level(c)).settlements.stream().filter(t -> TownAccess.manages(t,p.getUUID())
                && t.campaign.squads.stream().anyMatch(s -> s.leader().equals(p.getUUID()))).findFirst().orElse(null);
        return marching==null ? CampaignService.local(p) : marching;
    }
    private static int say(CommandContext<CommandSourceStack> c,String text) { c.getSource().sendSuccess(() -> Component.literal(text),false); return 1; }
    private static int missing(CommandContext<CommandSourceStack> c) { c.getSource().sendFailure(Component.literal("Join or found a town first.")); return 0; }
    private static int invite(CommandContext<CommandSourceStack> c,String role) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Settlement t=town(c); if(t==null) return missing(c);
        ServerPlayer friend=EntityArgument.getPlayer(c,"player");
        String result=TownAccess.invite(t,player(c).getUUID(),friend.getUUID(),role);
        if(t.campaign.invitations.containsKey(friend.getUUID())) {
            t.campaign.playerNames.put(friend.getUUID(),friend.getName().getString());
            SettlementData.get(level(c)).setDirty(); SettlementService.tell(friend,"You were invited to "+t.name+" as "+role+". Use /wwmc town accept to join.");
        }
        return say(c,result);
    }
    private static int accept(CommandContext<CommandSourceStack> c,UUID id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p=player(c); var data=SettlementData.get(level(c));
        Settlement t=id==null ? data.settlements.stream().filter(s -> s.campaign.invitations.containsKey(p.getUUID()))
                .min(Comparator.comparingDouble(s -> s.center.distSqr(p.blockPosition()))).orElse(null) : data.byId(id);
        if(t==null || !TownAccess.accept(t,p.getUUID())) return say(c,"No invitation is waiting for you in that town.");
        t.campaign.playerNames.put(p.getUUID(),p.getName().getString());
        CampaignService.record(level(c),t,p.getName().getString()+" joined as "+t.campaign.members.get(p.getUUID())+"."); return say(c,"Joined "+t.name+".");
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwmc")
            .then(Commands.literal("campaign").executes(c -> {
                Settlement t=town(c); if(t==null) return missing(c);
                if(player(c).distanceToSqr(Vec3.atCenterOf(t.center))>64) return say(c,"Open the Campaign button at your town banner.");
                CampaignViews.open(player(c),t); return 1;
            }))
            .then(Commands.literal("town")
                .then(Commands.literal("recover").executes(c -> say(c,SettlementService.recoverBanner(level(c),player(c),SettlementData.get(level(c)).at(player(c).blockPosition())))))
                .then(Commands.literal("list").executes(c -> {
                    StringBuilder text=new StringBuilder("Towns (UUIDs can be used with alliance commands):");
                    for(Settlement t:SettlementData.get(level(c)).settlements) text.append("\n").append(t.name).append(" · ").append(t.center.toShortString()).append(" · ").append(t.id);
                    return say(c,text.toString());
                }))
                .then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(c -> invite(c,"steward"))
                    .then(Commands.argument("role",StringArgumentType.word()).suggests((c,b) -> { TownAccess.ROLES.forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> invite(c,StringArgumentType.getString(c,"role"))))))
                .then(Commands.literal("accept").executes(c -> accept(c,null)).then(Commands.argument("town",UuidArgument.uuid()).executes(c -> accept(c,UuidArgument.getUuid(c,"town")))))
                .then(Commands.literal("remove").then(Commands.argument("player",EntityArgument.player()).executes(c -> {
                    Settlement t=town(c); if(t==null) return missing(c); if(!TownAccess.owner(t,player(c).getUUID())) return say(c,"Only the owner may remove members.");
                    UUID friend=EntityArgument.getPlayer(c,"player").getUUID(); t.campaign.members.remove(friend); t.campaign.invitations.remove(friend);
                    t.campaign.playerNames.remove(friend); EntityArgument.getPlayer(c,"player").closeContainer();
                    CampaignService.record(level(c),t,"Membership revoked for "+EntityArgument.getPlayer(c,"player").getName().getString()+"."); return 1;
                })))
                .then(Commands.literal("leave").executes(c -> {
                    ServerPlayer p=player(c); Settlement t=SettlementData.get(level(c)).at(p.blockPosition());
                    if(t==null || TownAccess.owner(t,p.getUUID())) return say(c,"Stand inside the town you want to leave. The owner keeps ownership.");
                    t.campaign.members.remove(p.getUUID()); t.campaign.playerNames.remove(p.getUUID()); p.closeContainer(); SettlementData.get(level(c)).setDirty(); return say(c,"Left "+t.name+".");
                }))
                .then(Commands.literal("ally").then(Commands.argument("town",UuidArgument.uuid()).executes(c -> {
                    Settlement t=town(c),other=SettlementData.get(level(c)).byId(UuidArgument.getUuid(c,"town")); if(t==null) return missing(c);
                    if(other==null) return say(c,"Unknown town."); String result=TownAccess.alliance(t,other,player(c).getUUID()); SettlementData.get(level(c)).setDirty(); return say(c,result);
                })))
                .then(Commands.literal("unally").then(Commands.argument("town",UuidArgument.uuid()).executes(c -> {
                    Settlement t=town(c),other=SettlementData.get(level(c)).byId(UuidArgument.getUuid(c,"town")); if(t==null) return missing(c);
                    if(other==null || !TownAccess.owner(t,player(c).getUUID())) return say(c,"Only the owner may end an alliance.");
                    TownAccess.leaveAlliance(t,other); SettlementData.get(level(c)).setDirty(); return say(c,"Alliance ended. Extra allied routes stop safely.");
                }))))
            .then(Commands.literal("request").then(Commands.argument("item",StringArgumentType.word())
                .suggests((c,b) -> { for(String item:List.of("minecraft:bread","minecraft:iron_ingot","minecraft:oak_log","minecraft:paper","minecraft:arrow","minecraft:stone_pickaxe")) b.suggest(item); return b.buildFuture(); })
                .then(Commands.argument("target",IntegerArgumentType.integer(0,4096)).executes(c -> {
                    Settlement t=town(c); if(t==null) return missing(c); String key=StringArgumentType.getString(c,"item");
                    int target=IntegerArgumentType.getInteger(c,"target"); if(!SupplyRequests.set(t,key,target)) return say(c,"Use a valid item and up to 24 stock requests.");
                    CampaignService.record(level(c),t,"Warehouse target for "+key+": "+target+"."); return 1;
                }))))
            .then(Commands.literal("industry").then(Commands.argument("industry",StringArgumentType.word())
                .suggests((c,b) -> { Specialization.NAMES.forEach(b::suggest); return b.buildFuture(); }).executes(c -> {
                    Settlement t=town(c); if(t==null) return missing(c); String name=StringArgumentType.getString(c,"industry");
                    if(!Specialization.NAMES.contains(name)) return say(c,"Choose balanced, farming, fishing, timber or mining.");
                    CampaignService.chooseSpecialty(level(c),t,name); return 1;
                })))
            .then(Commands.literal("project").then(Commands.argument("project",StringArgumentType.word())
                .suggests((c,b) -> { TownProjects.ALL.forEach(p -> b.suggest(p.id())); return b.buildFuture(); }).executes(c -> {
                    Settlement t=town(c); return t==null ? missing(c) : say(c,TownProjects.build(level(c),t,StringArgumentType.getString(c,"project")));
                })))
            .then(Commands.literal("squad")
                .executes(c -> { Settlement t=town(c); if(t==null) return missing(c); CampaignViews.openOrders(player(c),t); return 1; })
                .then(Commands.literal("muster").then(Commands.argument("count",IntegerArgumentType.integer(1,6)).executes(c -> {
                    Settlement t=town(c); return t==null ? missing(c) : say(c,SquadService.muster(level(c),t,player(c),IntegerArgumentType.getInteger(c,"count")));
                })))
                .then(Commands.argument("order",StringArgumentType.word()).suggests((c,b) -> { List.of("follow","hold","defend","retreat","escort","release").forEach(b::suggest); return b.buildFuture(); })
                    .executes(c -> { Settlement t=town(c); return t==null ? missing(c) : say(c,SquadService.order(level(c),t,player(c),StringArgumentType.getString(c,"order"))); })))
            .then(Commands.literal("route")
                .then(Commands.literal("add").then(Commands.argument("town",UuidArgument.uuid()).executes(c -> {
                    Settlement t=town(c),other=SettlementData.get(level(c)).byId(UuidArgument.getUuid(c,"town"));
                    return t==null ? missing(c) : say(c,other==null ? "Unknown town." : CampaignService.extraRoute(level(c),t,other));
                })))
                .then(Commands.literal("remove").then(Commands.argument("town",UuidArgument.uuid()).executes(c -> {
                    Settlement t=town(c),other=SettlementData.get(level(c)).byId(UuidArgument.getUuid(c,"town")); if(t==null) return missing(c);
                    if(other!=null) { t.campaign.extraRoutes.remove(other.id); other.campaign.extraRoutes.remove(t.id); SettlementData.get(level(c)).setDirty(); }
                    return say(c,"Extra route removed; any carrier returns undelivered goods.");
                }))))
            .then(Commands.literal("outpost").then(Commands.literal("claim").executes(c -> {
                Settlement t=town(c); if(t==null) return missing(c);
                ServerPlayer p=player(c);
                var site=ExpeditionData.get(level(c)).sites.stream().filter(s -> s.cleared && s.claimed==null && s.pos.distSqr(p.blockPosition())<12*12)
                        .findFirst().orElse(null);
                return say(c,site==null ? "Stand at an unclaimed, cleared expedition site." : ExpeditionService.claim(level(c),t,player(c),site.id));
            })))
            .then(Commands.literal("contract")
                .then(Commands.literal("accept").then(Commands.argument("contract",UuidArgument.uuid()).executes(c -> {
                    Settlement t=town(c); if(t==null) return missing(c); UUID id=UuidArgument.getUuid(c,"contract");
                    Settlement issuer=SettlementData.get(level(c)).settlements.stream().filter(s -> s.campaign.contracts.stream().anyMatch(o -> o.id.equals(id))).findFirst().orElse(null);
                    return say(c,issuer==null ? "Unknown contract." : CampaignContracts.accept(level(c),t,issuer,id));
                })))
                .then(Commands.literal("deliver").then(Commands.argument("contract",UuidArgument.uuid()).executes(CampaignCommands::deliver))))
            .then(Commands.literal("journal").executes(c -> {
                Settlement t=town(c); if(t==null) return missing(c); StringBuilder log=new StringBuilder(t.name+" journal:");
                for(var entry:t.campaign.journal.subList(Math.max(0,t.campaign.journal.size()-12),t.campaign.journal.size())) log.append("\nDay ").append(entry.time()/24000+1).append(": ").append(entry.text());
                return say(c,log.toString());
            })));
    }
    private static int deliver(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID id=UuidArgument.getUuid(c,"contract"); ServerPlayer player=player(c); ServerLevel level=level(c);
        for(Settlement npc:SettlementData.get(level).settlements) for(SupplyContract order:npc.campaign.contracts) if(order.id.equals(id)) {
            Settlement customer=order.customer==null ? null : SettlementData.get(level).byId(order.customer);
            if(!TownAccess.manages(customer,player.getUUID())) return say(c,"Accept this contract for your town first.");
            if(player.distanceToSqr(Vec3.atCenterOf(npc.center))>16*16) return say(c,"Bring the goods to the requesting town's warehouse.");
            List<Container> warehouses=SettlementService.storage(level,npc);
            if(warehouses.isEmpty()) return say(c,"That town needs loaded warehouse storage.");
            Map<String,Integer> moved=new LinkedHashMap<>(); int remaining=order.remaining();
            Container inventory=player.getInventory();
            for(int slot=0;slot<inventory.getContainerSize() && remaining>0;slot++) {
                ItemStack original=inventory.getItem(slot); if(!original.is(SupplyRequests.item(order.item))) continue;
                int amount=Math.min(remaining,original.getCount()); ItemStack rest=original.copyWithCount(amount);
                for(Container warehouse:warehouses) rest=InventoryOps.insert(warehouse,rest);
                int delivered=amount-rest.getCount(); if(delivered>0) { inventory.removeItem(slot,delivered); remaining-=delivered; moved.merge(order.item,delivered,Integer::sum); }
            }
            CampaignContracts.delivered(level,customer,npc,moved,inventory); inventory.setChanged();
            return say(c,order.complete() ? "Contract completed; the reward is in your inventory or safely held on the board if your bag is full." : "Delivered goods; "+order.remaining()+" still needed.");
        }
        return say(c,"Unknown contract.");
    }
}

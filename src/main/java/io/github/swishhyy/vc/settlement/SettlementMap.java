package io.github.swishhyy.vc.settlement;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.swishhyy.vc.menu.MapData;
import io.github.swishhyy.vc.menu.VcNetwork;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The shared settlement map: every town's claim, coloured by its relation to the viewer, the trade routes of the
 * viewer's own and allied towns, expedition sites, and pings. A ping belongs to the town of the player who placed it,
 * and that town's managers and its allies' managers see it.
 */
public final class SettlementMap {
    private static final int LIMIT=29_999_000;
    private static List<Settlement> managed(ServerLevel level,UUID player) {
        return SettlementData.get(level).settlements.stream().filter(t -> !t.trading.npc && TownAccess.manages(t,player)).toList();
    }
    /** Towns whose pings this player sees: the ones it manages and their allies. */
    private static List<Settlement> circle(ServerLevel level,UUID player) {
        List<Settlement> own=managed(level,player);
        return SettlementData.get(level).settlements.stream().filter(t -> !t.trading.npc
                && (own.contains(t) || own.stream().anyMatch(o -> TownAccess.allied(o,t)))).toList();
    }
    public static MapData build(ServerLevel level,ServerPlayer viewer) {
        UUID id=viewer.getUUID();
        List<Settlement> own=managed(level,id),circle=circle(level,id);
        List<MapData.Town> towns=new ArrayList<>();
        Map<UUID,Integer> relation=new HashMap<>();
        for(Settlement town:SettlementData.get(level).settlements) {
            if(town.trading.npc && town.trading.buildIndex>=0) continue;
            int kind=own.contains(town) ? MapData.OWN : circle.contains(town) ? MapData.ALLY
                    : town.trading.npc ? (town.trading.relations.getOrDefault(id,0)<0 ? MapData.HOSTILE : MapData.NEUTRAL) : MapData.OTHER;
            relation.put(town.id,kind);
            towns.add(new MapData.Town(town.id.toString(),town.name,town.center.getX(),town.center.getY(),town.center.getZ(),town.radius,kind));
        }
        List<MapData.Route> routes=new ArrayList<>();
        Set<String> drawn=new HashSet<>();
        var data=SettlementData.get(level);
        for(Settlement town:data.settlements) {
            int kind=relation.getOrDefault(town.id,MapData.OTHER);
            List<UUID> partners=new ArrayList<>();
            if(town.trading.partner!=null) partners.add(town.trading.partner);
            partners.addAll(town.campaign.extraRoutes);
            for(UUID partnerId:partners) {
                Settlement partner=data.byId(partnerId);
                if(partner==null || !TradeRoutes.agreed(town,partner) || partner.trading.buildIndex>=0) continue;
                if(kind!=MapData.OWN && kind!=MapData.ALLY && !(town.trading.npc && partner.trading.npc)) continue;
                String key=town.id.compareTo(partnerId)<0 ? town.id+"|"+partnerId : partnerId+"|"+town.id;
                if(!drawn.add(key)) continue;
                routes.add(new MapData.Route(town.center.getX(),town.center.getZ(),partner.center.getX(),partner.center.getZ(),!partnerId.equals(town.trading.partner),
                        SharedRoads.get(level).mapPath(town,partner)));
            }
        }
        List<MapData.Site> sites=new ArrayList<>();
        for(var site:ExpeditionData.get(level).sites) {
            int state=site.kind.equals("raid") ? MapData.RAID : site.claimed!=null ? MapData.CLAIMED : site.cleared ? MapData.CLEARED : MapData.UNCLEARED;
            Regions.Region land=Regions.byId(site.resource);
            sites.add(new MapData.Site(site.id.toString(),site.pos.getX(),site.pos.getY(),site.pos.getZ(),site.title(),site.goal()+(land==null ? "" : " · "+land.title()+": "+land.ore().getName().getString())
                    +(site.claimed!=null ? " · claimed as an outpost" : site.cleared ? " · cleared" : site.spawned ? " · "+site.guards.size()+" defenders" : ""),state));
        }
        List<MapData.Mark> pings=new ArrayList<>();
        for(Settlement town:circle) for(TownProgress.Ping ping:town.progress.pings)
            pings.add(new MapData.Mark(ping.id().toString(),ping.pos().getX(),ping.pos().getY(),ping.pos().getZ(),ping.kind(),ping.label(),
                    (ping.name().isEmpty() ? "A player" : ping.name())+" of "+town.name,ping.author().equals(id) || TownAccess.manages(town,id)));
        return new MapData(level.dimension().identifier().toString(),viewer.getBlockX(),viewer.getBlockZ(),towns,routes,sites,pings);
    }
    /** The client opens whichever optional map is installed. No map mod is needed on the server. */
    public static void open(ServerPlayer player) {
        send(player,true);
    }
    private static void send(ServerPlayer player,boolean open) {
        PacketDistributor.sendToPlayer(player,new VcNetwork.MapPayload(build((ServerLevel)player.level(),player),open));
    }
    public static void refresh(ServerLevel level) {
        for(ServerPlayer player:level.players()) if(!(player instanceof FakePlayer)) send(player,false);
    }
    /** Reads saved town locations; never loads terrain to populate a map. */
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level && level.getGameTime()%200==0)
            refresh(level);
    }
    /** The town a player's pings belong to: the managed town they stand in, or their nearest. */
    private static Settlement home(ServerLevel level,ServerPlayer player) {
        Settlement local=SettlementData.get(level).at(player.blockPosition());
        if(local!=null && !local.trading.npc && TownAccess.manages(local,player.getUUID())) return local;
        return managed(level,player.getUUID()).stream().min(Comparator.comparingDouble(t -> t.center.distSqr(player.blockPosition()))).orElse(null);
    }
    public static String ping(ServerPlayer player,int x,int z,String kind,String note) {
        ServerLevel level=(ServerLevel)player.level();
        Settlement town=home(level,player);
        if(town==null) return "Found or manage a town to share pings with it and its allies.";
        if(!TownProgress.Ping.KINDS.contains(kind)) return "Ping kinds: "+String.join(", ",TownProgress.Ping.KINDS)+".";
        x=Math.clamp(x,-LIMIT,LIMIT); z=Math.clamp(z,-LIMIT,LIMIT);
        BlockPos column=new BlockPos(x,player.getBlockY(),z);
        int y=level.hasChunkAt(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z) : player.getBlockY();
        var ping=new TownProgress.Ping(UUID.randomUUID(),player.getUUID(),player.getName().getString(),new BlockPos(x,y,z),kind,note,level.getGameTime());
        town.progress.ping(ping);
        SettlementData.get(level).setDirty();
        CampaignService.record(level,town,ping.name()+" marked \""+ping.label()+"\" at "+x+", "+z+".");
        return "Ping shared with "+town.name+" and its allies: "+ping.label()+" at "+x+", "+z+".";
    }
    public static String remove(ServerPlayer player,String id) {
        ServerLevel level=(ServerLevel)player.level();
        for(Settlement town:circle(level,player.getUUID())) for(TownProgress.Ping ping:List.copyOf(town.progress.pings))
            if(ping.id().toString().equals(id) && (ping.author().equals(player.getUUID()) || TownAccess.manages(town,player.getUUID()))) {
                town.progress.pings.remove(ping); SettlementData.get(level).setDirty();
                return "Removed the ping "+ping.label()+".";
            }
        return "No removable shared ping has that ID.";
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vc")
            .then(Commands.literal("map").executes(c -> { open(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("ping")
                .then(Commands.literal("remove").then(Commands.argument("id",StringArgumentType.word()).executes(c -> {
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    String result=remove(p,StringArgumentType.getString(c,"id"));
                    c.getSource().sendSuccess(() -> Component.literal(result),false); send(p,false); return 1;
                })))
                .then(Commands.argument("kind",StringArgumentType.word())
                    .suggests((c,b) -> { TownProgress.Ping.KINDS.forEach(b::suggest); return b.buildFuture(); })
                    .executes(c -> {
                        ServerPlayer p=c.getSource().getPlayerOrException();
                        String result=ping(p,p.getBlockX(),p.getBlockZ(),StringArgumentType.getString(c,"kind"),"");
                        c.getSource().sendSuccess(() -> Component.literal(result),false); return 1;
                    })
                    .then(Commands.argument("note",StringArgumentType.greedyString()).executes(c -> {
                        ServerPlayer p=c.getSource().getPlayerOrException();
                        String result=ping(p,p.getBlockX(),p.getBlockZ(),StringArgumentType.getString(c,"kind"),StringArgumentType.getString(c,"note"));
                        c.getSource().sendSuccess(() -> Component.literal(result),false); return 1;
                    })))));
    }
}

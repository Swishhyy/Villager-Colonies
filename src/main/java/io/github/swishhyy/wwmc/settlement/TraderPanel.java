package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.menu.PanelView;
import io.github.swishhyy.wwmc.menu.TraderMenu;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Ownership, route consent and row identity are checked on the server for every click. */
public final class TraderPanel {
    public static final int LINK=40,RESERVE=41,LOAD=42,FORGET=43,PAUSE=44,DISCONNECT=45;
    private TraderPanel() {}
    private static ServerLevel level(ServerPlayer p) { return (ServerLevel)p.level(); }
    public static Settlement owned(ServerPlayer p,BlockPos pos) {
        if(!p.isAlive() || p.distanceToSqr(Vec3.atCenterOf(pos))>64) return null;
        ServerLevel level=level(p);
        Settlement town=SettlementData.get(level).at(pos); Station station=town==null ? null : town.station(pos);
        return SettlementService.owns(p,town) && station!=null && station.role()==StructureRole.TRADER && SettlementService.active(level,station) ? town : null;
    }
    public static void open(ServerPlayer player,Settlement town,Station station) {
        BlockPos pos=station.position(); PanelView view=view(player,pos,"");
        if(view==null) return;
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new TraderMenu(id,inventory,pos,player,view),view.title()),buf -> TraderMenu.write(buf,pos,view));
    }
    public static List<Settlement> destinations(ServerLevel level,Settlement source) {
        return SettlementData.get(level).settlements.stream().filter(t -> t!=source && TradeRoutes.checkpoint(t)!=null && t.trading.buildIndex<0
                && source.center.distSqr(t.center)<=(double)Config.TRADE_DISTANCE.get()*Config.TRADE_DISTANCE.get())
                .sorted(Comparator.comparingDouble((Settlement t) -> source.center.distSqr(t.center)).thenComparing(t -> t.id)).limit(PanelView.MAX_ROWS).toList();
    }
    public static PanelView view(ServerPlayer player,BlockPos pos,String feedback) {
        Settlement town=owned(player,pos); if(town==null) return null;
        ServerLevel level=level(player); List<PanelView.Row> routes=new ArrayList<>(),exports=new ArrayList<>(),status=new ArrayList<>();
        for(Settlement other:destinations(level,town)) {
            boolean agreed=TradeRoutes.agreed(town,other),ours=other.owner.equals(town.owner);
            int goodwill=other.trading.relations.getOrDefault(town.owner,0);
            String relationship=agreed ? "Connected" : other.trading.npc ? (goodwill<0 ? "Hostile" : goodwill>=64 ? "Friendly" : "Neutral")+" · "+other.trading.specialty : ours ? "Your town" : "Needs both owners' consent";
            routes.add(new PanelView.Row(new ItemStack(Items.COMPASS),Component.literal(other.name),Component.literal(
                    relationship+" · "+Math.round(Math.sqrt(town.center.distSqr(other.center)))+" blocks"),0,-1,agreed ? 1 : 0,other.id.toString()));
        }
        var warehouse=SettlementService.storage(level,town);
        for(var order:town.trading.exports) {
            ItemStack icon=new ItemStack(order.resolve());
            int have=InventoryOps.count(warehouse,s -> s.is(order.resolve()));
            exports.add(new PanelView.Row(icon,icon.getHoverName(),Component.literal(have+" in warehouse"),0,order.load(),order.reserve(),order.item()));
        }
        Settlement partner=town.trading.partner==null ? null : SettlementData.get(level).byId(town.trading.partner);
        status.add(new PanelView.Row(new ItemStack(Items.COMPASS),"Partner",partner==null ? "None: choose one on Routes" : partner.name+(TradeRoutes.agreed(town,partner) ? " · connected" : " · awaiting acceptance")));
        status.add(new PanelView.Row(new ItemStack(Items.BUNDLE),"Trader",town.trading.paused ? "New departures paused" : town.trading.status));
        status.add(new PanelView.Row(new ItemStack(Items.CHEST),"Delivered",town.trading.delivered+" items sent; cargo stays with its carrier until unloaded"));
        status.add(new PanelView.Row(new ItemStack(Items.PAPER),"How it works","One trader; stock requests and extra allied routes are on the Campaign board"));
        return new PanelView(Component.literal("Trader Block · "+town.name),Component.literal(feedback.isEmpty() ? "Supply routes: each town chooses its own exports" : feedback),
                List.of(new PanelView.Tab("Routes",routes),new PanelView.Tab("Exports",exports),new PanelView.Tab("Status",status)),
                List.of(new PanelView.Action(PAUSE,town.trading.paused ? "Resume" : "Pause",true),new PanelView.Action(DISCONNECT,"Disconnect",town.trading.partner!=null)));
    }
    public static String example(ServerPlayer p,BlockPos pos,ItemStack item) {
        Settlement town=owned(p,pos); if(town==null) return "";
        String key=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        if(town.trading.exports.stream().anyMatch(e -> e.item().equals(key))) return "That item is already listed.";
        if(town.trading.exports.size()>=TradeSettings.MAX_EXPORTS) return "Up to six export items per town.";
        town.trading.exports.add(new TradeSettings.Export(key,64,32)); SettlementData.get(level(p)).setDirty();
        return "Export added. Keep 64 at home; send up to 32 per trip.";
    }
    public static String act(ServerPlayer p,BlockPos pos,int action,int index,int value,String key) {
        Settlement town=owned(p,pos); if(town==null) return "";
        ServerLevel level=level(p); var data=SettlementData.get(level); String message="";
        if(action==LINK) {
            List<Settlement> choices=destinations(level,town);
            if(index<0 || index>=choices.size() || !choices.get(index).id.toString().equals(key)) return "Town list changed. Select it again.";
            message=TradeRoutes.link(town,choices.get(index),data.settlements,Config.TRADE_DISTANCE.get());
        } else if(action==PAUSE) town.trading.paused=!town.trading.paused;
        else if(action==DISCONNECT) { TradeRoutes.disconnect(town,data.settlements); message="Disconnected. Any departing carrier returns its remaining goods."; }
        else {
            var orders=town.trading.exports;
            if(index<0 || index>=orders.size() || !orders.get(index).item().equals(key)) return "";
            switch(action) {
                case RESERVE -> orders.set(index,orders.get(index).withReserve(value));
                case LOAD -> orders.set(index,orders.get(index).withLoad(value));
                case FORGET -> orders.remove(index);
                default -> { return ""; }
            }
        }
        data.setDirty(); return message;
    }
}

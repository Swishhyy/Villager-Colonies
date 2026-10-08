package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.menu.*;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Server-owned campaign and field-order panels. Row keys identify operations; every click is authorized afresh. */
public final class CampaignViews {
    public static final int OPEN=70,ROW_ACTION=71,BACK=72;
    private CampaignViews() {}
    private static PanelView.Row row(net.minecraft.world.level.ItemLike icon,String title,String detail,String key,int value) {
        return new PanelView.Row(new ItemStack(icon),Component.literal(title),Component.literal(detail),0,-1,value,key);
    }
    private static PanelView.Row action(net.minecraft.world.level.ItemLike icon,String title,String detail,String key,boolean enabled) {
        return row(icon,title,detail,"act:"+key,enabled ? 0 : 1);
    }
    public static void open(ServerPlayer player,Settlement town) { open(player,town,false); }
    public static void openOrders(ServerPlayer player,Settlement town) { open(player,town,true); }
    private static void open(ServerPlayer player,Settlement town,boolean orders) {
        if(!TownAccess.manages(town,player.getUUID())) return;
        PanelMenu.Kind kind=orders ? PanelMenu.Kind.ARMY : PanelMenu.Kind.CAMPAIGN;
        PanelView view=build((ServerLevel)player.level(),town,player,orders);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,kind,town.center,player,view),view.title()),buf -> PanelMenu.write(buf,kind,town.center,view));
    }
    public static boolean valid(ServerPlayer player,BlockPos pos,boolean orders) {
        if(!player.isAlive()) return false;
        Settlement town=SettlementData.get((ServerLevel)player.level()).at(pos);
        return TownAccess.manages(town,player.getUUID()) && town.center.equals(pos)
                && (orders || player.distanceToSqr(Vec3.atCenterOf(pos))<=64);
    }
    public static PanelView view(ServerPlayer player,BlockPos pos,boolean orders) {
        if(!valid(player,pos,orders)) return null;
        return build((ServerLevel)player.level(),SettlementData.get((ServerLevel)player.level()).at(pos),player,orders);
    }
    private static String name(ServerLevel level,UUID id) {
        var player=level.getServer().getPlayerList().getPlayer(id); return player==null ? id.toString().substring(0,8)+" (offline)" : player.getName().getString();
    }
    public static PanelView build(ServerLevel level,Settlement town,ServerPlayer viewer,boolean orders) {
        if(orders) return new PanelView(Component.literal(town.name+" · Field Orders"),Component.literal("Lead your guards in person; troops use their own equipment and supplies"),
                List.of(new PanelView.Tab("Army",army(level,town,viewer)),new PanelView.Tab("Sites",sites(level,town))),List.of());
        List<PanelView.Row> supply=new ArrayList<>(),projects=new ArrayList<>(),journal=new ArrayList<>();
        SupplyRequests.snapshotLoaded(level,town);
        supply.add(row(Items.COMPASS,"Industry: "+town.campaign.specialty,"Click the industry rows to choose a focus; matching terrain adds a larger bonus","",-1));
        for(String name:Specialization.NAMES) supply.add(action(Items.COMPASS,"Industry: "+name,name.equals("balanced") ? "Normal production speed for all jobs" : Specialization.terrain(level,town,name) ? "Matching terrain: 25% faster matching work" : "10% faster matching work; all other jobs remain available",
                "industry:"+name,!town.campaign.specialty.equals(name)));
        Set<String> requests=new LinkedHashSet<>(town.campaign.requests.keySet());
        requests.addAll(List.of("minecraft:bread","minecraft:iron_ingot","minecraft:oak_log","minecraft:paper","minecraft:arrow","minecraft:stone_pickaxe"));
        for(String key:requests) {
            var item=SupplyRequests.item(key); int target=town.campaign.requests.getOrDefault(key,0),have=town.campaign.stock.getOrDefault(key,0);
            supply.add(row(item,new ItemStack(item).getHoverName().getString(),have+" in warehouse; target "+target+". Zero removes the request","request:"+key,target));
        }
        for(Settlement other:TraderPanel.destinations(level,town)) if(TownAccess.allied(town,other)) {
            boolean extra=town.campaign.extraRoutes.contains(other.id);
            supply.add(action(Items.MINECART,(extra ? "Remove extra route: " : "Add allied route: ")+other.name,extra ? "Carrier returns any cargo safely" : "Needs the Transport Depot; the one trader visits routes in turn",
                    (extra ? "unroute:" : "route:")+other.id,extra || town.campaign.projects.contains("depot") && !TradeRoutes.agreed(town,other)));
        }
        for(Settlement npc:SettlementData.get(level).settlements) if(npc.trading.npc && npc.center.distSqr(town.center)<4096.0*4096.0)
            for(SupplyContract order:npc.campaign.contracts) {
                String state=order.customer==null ? "Open offer" : order.customer.equals(town.id) ? order.complete() ? "Complete; collect any remaining reward" : "Accepted: "+order.delivered+" / "+order.amount+" delivered" : "Accepted by another town";
                supply.add(action(Items.EMERALD,npc.name+": "+order.amount+" "+order.item.replace("minecraft:",""),state+"; reward "+order.reward.getCount()+" "+order.reward.getHoverName().getString(),
                        "contract:"+order.id,order.customer==null && order.expires>level.getGameTime()));
                if(town.id.equals(order.customer)) supply.add(row(Items.PAPER,"Manual delivery / reward collection","/wwmc contract deliver "+order.id+" at "+npc.name,"",-1));
            }
        for(var project:TownProjects.ALL) {
            boolean complete=town.campaign.projects.contains(project.id()); String missing=complete ? "Complete" : TownProjects.missing(level,town,project);
            String costs=String.join(", ",project.costs().stream().map(c -> c.count()+" "+c.name()).toList());
            projects.add(action(Items.IRON_INGOT,project.title()+": "+(complete ? "complete" : missing.isEmpty() ? "ready" : "waiting"),project.benefit()+". Costs "+costs+(missing.isEmpty() ? "" : "; "+missing),
                    "project:"+project.id(),!complete && missing.isEmpty()));
        }
        for(int n=town.campaign.journal.size()-1;n>=0;n--) {
            var entry=town.campaign.journal.get(n); journal.add(row(Items.WRITABLE_BOOK,"Day "+(entry.time()/24000+1)+", "+Math.max(0,(level.getGameTime()-entry.time())/1200)+" min ago",entry.text(),"",-1));
        }
        return new PanelView(Component.literal(town.name+" · Campaign"),Component.literal("Shared management, supply goals, projects and expeditions"),
                List.of(new PanelView.Tab("Supply",supply),new PanelView.Tab("Projects",projects),
                        new PanelView.Tab("Army",army(level,town,viewer)),new PanelView.Tab("Sites",sites(level,town)),new PanelView.Tab("Journal",journal)),
                List.of(new PanelView.Action(BACK,"Town overview",true),new PanelView.Action(RelationshipViews.OPEN,"Relationships",true)));
    }
    private static List<PanelView.Row> army(ServerLevel level,Settlement town,ServerPlayer viewer) {
        List<PanelView.Row> rows=new ArrayList<>(); boolean led=town.campaign.squads.stream().anyMatch(s -> s.leader().equals(viewer.getUUID()));
        rows.add(row(Items.IRON_SWORD,"Your squad",led ? "One squad per player per town; guards keep their original posts" : "Equip healthy guards, then muster them near you","",-1));
        for(int size:List.of(1,2,4,6)) if(size<=SquadService.limit(town)) rows.add(action(Items.IRON_SWORD,"Muster "+size+" guards","Needs the Armory and enough equipped, healthy guards nearby","muster:"+size,!led && town.campaign.projects.contains("armory")));
        for(String order:List.of("follow","hold","defend","retreat","escort","release")) rows.add(action(Items.COMPASS,"Order: "+order,switch(order) {
            case "hold" -> "Hold this position; attack only in self-defense"; case "defend" -> "Defend a 24-block area around your current position";
            case "retreat" -> "Return to the banner and await new orders"; case "escort" -> "Follow this town's trader; shipment waits for the escort";
            case "release" -> "Return home, then resume normal guard shifts"; default -> "Follow you and fight visible nearby hostiles";
        },"order:"+order,led));
        for(var squad:town.campaign.squads) for(UUID guard:squad.guards()) {
            var entity=level.getEntity(guard); String status=entity instanceof CitizenEntity citizen ? citizen.activity() : "Out of loaded range; orders persist";
            rows.add(row(Items.SHIELD,town.citizenNames.getOrDefault(guard,guard.toString().substring(0,8)),name(level,squad.leader())+" · "+squad.order()+" · "+status,"",-1));
        }
        return rows;
    }
    private static List<PanelView.Row> sites(ServerLevel level,Settlement town) {
        List<PanelView.Row> rows=new ArrayList<>();
        for(var site:ExpeditionData.get(level).sites) {
            String status=site.claimed!=null ? "Claimed as an outpost" : site.cleared ? "Cleared: walk here and use /wwmc outpost claim with a Frontier Charter" : site.spawned ? site.guards.size()+" defenders remain" : "Uncleared; defenders gather when approached";
            rows.add(row(Items.FILLED_MAP,site.title()+" · "+site.pos.toShortString(),status+" · "+Math.round(Math.sqrt(town.center.distSqr(site.pos)))+" blocks from town","",-1));
        }
        for(Settlement outpost:SettlementData.get(level).settlements) if(town.id.equals(outpost.campaign.parent))
            rows.add(row(Items.IRON_PICKAXE,outpost.name,"Food target "+outpost.campaign.requests.getOrDefault("minecraft:bread",0)+"; supplies move by trader, production by courier","",-1));
        return rows;
    }
    public static void act(ServerPlayer player,BlockPos pos,boolean orders,int action,int value,String key) {
        if(!valid(player,pos,orders)) return; ServerLevel level=(ServerLevel)player.level(); Settlement town=SettlementData.get(level).at(pos);
        if(action==RelationshipViews.OPEN && !orders) { RelationshipViews.open(player,town); return; }
        if(action==BACK && !orders) { Panels.openTown(player,town); return; }
        if(action!=ROW_ACTION || key.length()>256) return;
        String message="";
        try {
            if(key.startsWith("request:") && !orders) { if(SupplyRequests.set(town,key.substring(8),value)) CampaignService.record(level,town,"Warehouse request: "+key.substring(8)+" target "+value+"."); }
            else if(key.startsWith("member:") && !orders && TownAccess.owner(town,player.getUUID())) {
                UUID id=UUID.fromString(key.substring(7)); if(!town.campaign.members.containsKey(id)) return;
                if(value==-1) town.campaign.members.remove(id); else if(value==0 || value==1) town.campaign.members.put(id,value==0 ? "builder" : "steward"); else return;
                CampaignService.record(level,town,"Changed membership for "+name(level,id)+".");
            } else if(key.startsWith("act:")) {
                String[] parts=key.substring(4).split(":",2); if(parts.length!=2) return;
                String op=parts[0],arg=parts[1];
                if(orders && !List.of("muster","order").contains(op)) return;
                switch(op) {
                    case "invite" -> {
                        UUID id=UUID.fromString(arg); message=TownAccess.invite(town,player.getUUID(),id,"steward");
                        var friend=level.getServer().getPlayerList().getPlayer(id); if(friend!=null && town.campaign.invitations.containsKey(id)) SettlementService.tell(friend,"Invited to "+town.name+" as steward. Use /wwmc town accept.");
                    }
                    case "cancel" -> { if(TownAccess.owner(town,player.getUUID())) town.campaign.invitations.remove(UUID.fromString(arg)); }
                    case "ally","unally" -> {
                        Settlement other=SettlementData.get(level).byId(UUID.fromString(arg)); if(other==null) return;
                        if(op.equals("ally")) message=TownAccess.alliance(town,other,player.getUUID());
                        else if(TownAccess.owner(town,player.getUUID())) { TownAccess.leaveAlliance(town,other); message="Alliance ended."; }
                    }
                    case "industry" -> CampaignService.chooseSpecialty(level,town,arg);
                    case "project" -> message=TownProjects.build(level,town,arg);
                    case "muster" -> message=SquadService.muster(level,town,player,Integer.parseInt(arg));
                    case "order" -> message=SquadService.order(level,town,player,arg);
                    case "route","unroute" -> {
                        Settlement other=SettlementData.get(level).byId(UUID.fromString(arg)); if(other==null) return;
                        if(op.equals("route")) message=CampaignService.extraRoute(level,town,other);
                        else { town.campaign.extraRoutes.remove(other.id); other.campaign.extraRoutes.remove(town.id); message="Extra route removed."; }
                    }
                    case "contract" -> {
                        UUID id=UUID.fromString(arg); Settlement npc=SettlementData.get(level).settlements.stream().filter(t -> t.campaign.contracts.stream().anyMatch(c -> c.id.equals(id))).findFirst().orElse(null);
                        if(npc!=null) message=CampaignContracts.accept(level,town,npc,id);
                    }
                    default -> { return; }
                }
            } else return;
        } catch(IllegalArgumentException ignored) { return; }
        SettlementData.get(level).setDirty(); if(!message.isEmpty()) SettlementService.tell(player,message);
    }
}

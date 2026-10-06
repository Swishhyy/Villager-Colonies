package io.github.swishhyy.wwmc.menu;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.menu.PanelView.Action;
import io.github.swishhyy.wwmc.menu.PanelView.Row;
import io.github.swishhyy.wwmc.menu.PanelView.Tab;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

/** Builds every settlement screen on the server and applies its buttons. Only the town's owner sees these screens. */
public final class Panels {
    public static final int PRIORITY=1,ALARM=2,RECRUIT=3,BREAD=4,POSTS=5,TARGET=10,RAISE=11,FORGET=12;
    public static final int GREEN=0xFF3FA34D,RED=0xFFC0392B,AMBER=0xFFD39B1E,GRAY=0xFF707070;
    private static final List<String> PRIORITIES=List.of("balanced","food","materials");
    private Panels() {}
    private static ServerLevel level(ServerPlayer player) { return (ServerLevel)player.level(); }
    private static ItemStack icon(ItemLike item) { return new ItemStack(item); }
    public static ItemStack stationIcon(StructureRole role) { return new ItemStack(WWMC.STATION_ITEMS.get(role).get()); }
    /** The owner's settlement at this position, while the owner stands within eight blocks of it. */
    private static Settlement owned(ServerPlayer player,BlockPos pos) {
        if(!player.isAlive() || player.distanceToSqr(Vec3.atCenterOf(pos))>64) return null;
        Settlement town=SettlementData.get(level(player)).at(pos);
        return SettlementService.owns(player,town) ? town : null;
    }
    private static Station stationAt(ServerPlayer player,BlockPos pos) {
        Settlement town=owned(player,pos);
        Station station=town==null ? null : town.station(pos);
        return station!=null && SettlementService.active(level(player),station) ? station : null;
    }
    /** Cheap check the open screen runs every tick. */
    public static boolean valid(ServerPlayer player,BlockPos pos,boolean banner) {
        if(banner) { Settlement town=owned(player,pos); return town!=null && town.center.equals(pos); }
        return stationAt(player,pos)!=null;
    }
    private static void dirty(ServerPlayer player) { SettlementData.get(level(player)).setDirty(); }

    // ---------- Opening ----------
    public static void openTown(ServerPlayer player,Settlement town) {
        PanelView view=town(level(player),town);
        BlockPos pos=town.center;
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.TOWN,pos,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.TOWN,pos,view));
    }
    public static void openStation(ServerPlayer player,Settlement town,Station station) {
        BlockPos pos=station.position();
        if(station.role()==StructureRole.CRAFTSMAN) {
            PanelView view=craftsman(level(player),town,station,"");
            player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new CraftsmanMenu(id,inventory,pos,player,view),view.title()),
                    buf -> CraftsmanMenu.write(buf,pos,view));
            return;
        }
        PanelView view=station(level(player),town,station);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> new PanelMenu(id,PanelMenu.Kind.STATION,pos,player,view),view.title()),
                buf -> PanelMenu.write(buf,PanelMenu.Kind.STATION,pos,view));
    }
    public static void openCitizen(ServerPlayer player,CitizenEntity citizen,CitizenInventory bag) {
        PanelView view=citizen(citizen);
        player.openMenu(new SimpleMenuProvider((id,inventory,p) -> {
            CitizenMenu menu=new CitizenMenu(id,inventory,bag,citizen,player,view);
            bag.opened(p,menu);
            return menu;
        },citizen.getName()),buf -> PanelView.STREAM_CODEC.encode(buf,view));
    }

    // ---------- Town ----------
    public static PanelView town(ServerPlayer player,BlockPos pos) {
        Settlement town=owned(player,pos);
        return town==null ? null : town(level(player),town);
    }
    private static int freeBeds(ServerLevel level,Settlement town) {
        return Math.max(0,Math.min(SettlementService.housingBeds(level,town).size(),Config.MAX_CITIZENS.get())-town.citizens.size());
    }
    public static PanelView town(ServerLevel level,Settlement town) {
        int beds=SettlementService.housingBeds(level,town).size(),free=freeBeds(level,town);
        List<Container> everything=SettlementService.townStorage(level,town);
        List<Row> overview=new ArrayList<>();
        overview.add(new Row(icon(WWMC.BANNER_ITEM.get()),"Population",town.citizens.size()+" citizens, "+beds+" housing beds (limit "+Config.MAX_CITIZENS.get()+")"));
        overview.add(new Row(icon(Items.BREAD),"Food",InventoryOps.count(everything,FoodHealing::food)+" meals in storage"));
        overview.add(storage(icon(Items.CHEST),"Warehouse",SettlementService.storage(level,town),"No loaded warehouse with a chest or barrel in range"));
        int barrels=0;
        for(Station station:town.stations) barrels+=SettlementService.jobBarrels(level,town,station).size();
        overview.add(new Row(icon(Items.BARREL),"Job barrels",barrels==0 ? "None: put a barrel within 3 blocks of a work station"
                : barrels+" barrels"+(SettlementService.couriers(level,town) ? ", emptied by couriers" : "; add a Courier Station to collect from them")));
        overview.add(new Row(icon(Items.COMPASS),"Claim",town.radius+" blocks from the banner on X and Z"));
        overview.add(new Row(icon(Items.WHEAT),"Work priority",switch(town.priority) {
            case "food" -> "Food: farms and cooks first";
            case "materials" -> "Materials: forestry, mining and workshops first";
            default -> "Balanced";
        }));
        overview.add(alarm(town));
        overview.add(new Row(icon(Items.IRON_SWORD),"Enemy waves",WaveService.status(level,town)+"; "+town.waves+" repelled"));
        List<Row> people=new ArrayList<>();
        List<CitizenEntity> loaded=new ArrayList<>(DefenseService.loadedCitizens(level,town));
        loaded.sort(Comparator.comparing(citizen -> citizen.getName().getString()));
        for(CitizenEntity citizen:loaded) people.add(person(town,citizen));
        if(loaded.size()<town.citizens.size()) people.add(new Row(icon(Items.MAP),(town.citizens.size()-loaded.size())+" more citizens","Out of range: their chunks are not loaded"));
        List<Row> stations=new ArrayList<>();
        for(Station station:town.stations) stations.add(summary(level,town,station));
        List<Action> actions=List.of(
            new Action(PRIORITY,"Priority: "+town.priority,true),
            new Action(ALARM,DefenseService.alarmed(town) ? "Sound the all-clear" : "Sound the alarm",true),
            new Action(RECRUIT,free>0 ? "Recruit ("+free+" beds free)" : "Recruit (needs beds)",free>0));
        return new PanelView(Component.literal(town.name),Component.literal(town.citizens.size()+" citizens · "+town.stations.size()+" stations · claim "+town.radius),
                List.of(new Tab("Overview",overview),new Tab("Citizens",people),new Tab("Stations",stations)),actions);
    }
    private static Row alarm(Settlement town) {
        Row row=new Row(icon(Items.BELL),"Alarm",DefenseService.status(town));
        return DefenseService.alarmed(town) ? new Row(row.icon(),row.text(),row.detail(),RED,PanelView.NO_BAR,PanelView.NO_VALUE) : row;
    }
    private static Row person(Settlement town,CitizenEntity citizen) {
        Station job=citizen.workplace()==null ? null : town.station(citizen.workplace());
        float health=citizen.getHealth()/Math.max(1,citizen.getMaxHealth());
        return new Row(job==null ? icon(Items.PAPER) : stationIcon(job.role()),citizen.getName().getString(),
                (job==null ? "No job" : job.role().title())+": "+citizen.activity()).bar(health,health<0.5F ? RED : GREEN);
    }
    private static Row storage(ItemStack icon,String name,List<Container> containers,String none) {
        if(containers.isEmpty()) return new Row(icon,name,none);
        int slots=0,used=0;
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) { slots++; if(!container.getItem(slot).isEmpty()) used++; }
        float fill=used/(float)Math.max(1,slots);
        return new Row(icon,name,containers.size()+" containers, "+Math.round(fill*100)+"% of slots used").bar(fill,fill>0.9F ? RED : fill>0.7F ? AMBER : GREEN);
    }
    public static void townAction(ServerPlayer player,BlockPos pos,int action) {
        Settlement town=owned(player,pos);
        if(town==null) return;
        ServerLevel level=level(player);
        switch(action) {
            case PRIORITY -> { town.priority=PRIORITIES.get((PRIORITIES.indexOf(town.priority)+1)%PRIORITIES.size()); dirty(player); }
            case ALARM -> DefenseService.toggle(level,town);
            case RECRUIT -> {
                int added=SettlementService.recruit(level,town,1);
                SettlementService.notify(player,added>0 ? "A new citizen joined "+town.name+"." : "No citizen could join: free housing beds and open ground beside the banner are needed.");
            }
            default -> {}
        }
    }

    // ---------- Stations ----------
    public static PanelView station(ServerPlayer player,BlockPos pos) {
        Station station=stationAt(player,pos);
        return station==null ? null : station(level(player),owned(player,pos),station);
    }
    private static String crew(ServerLevel level,Station station) {
        return station.role().providesWork() ? "Crew "+SettlementService.workers(level).count(station.position(),level.getGameTime())+"/"+SettlementService.workerLimit(station) : "No crew";
    }
    /** One line for the town's station list. */
    private static Row summary(ServerLevel level,Settlement town,Station station) {
        List<Row> lines=status(level,town,station);
        String first=lines.isEmpty() ? "" : lines.getFirst().detail().getString();
        return new Row(stationIcon(station.role()),station.role().title()+" Station",station.position().toShortString()+" · "
                +(station.role().providesWork() ? crew(level,station)+" · " : "")+first);
    }
    public static PanelView station(ServerLevel level,Settlement town,Station station) {
        StructureRole role=station.role();
        List<Tab> tabs=new ArrayList<>();
        tabs.add(new Tab("Status",status(level,town,station)));
        if(role.providesWork()) tabs.add(new Tab("Crew",crewRows(level,town,station)));
        if(role==StructureRole.WAREHOUSE) tabs.add(new Tab("Contents",contents(SettlementService.storageAt(level,town,station.position()))));
        else if(role.keepsJobStorage()) tabs.add(new Tab("Barrels",contents(SettlementService.jobStorage(level,town,station))));
        List<Action> actions=new ArrayList<>();
        if(role==StructureRole.COOK) actions.add(new Action(BREAD,town.disabledRecipes.contains("bread") ? "Bread: off" : "Bread: on",true));
        if(role==StructureRole.GUARD) actions.add(new Action(POSTS,"Choose guard posts",true));
        return new PanelView(Component.literal(role.title()+" Station"),Component.literal(crew(level,station)+" · "
                +(role.excavates() ? "facing "+station.facing().getName() : "range 7x7x7")),tabs,actions);
    }
    private static List<Row> crewRows(ServerLevel level,Settlement town,Station station) {
        List<Row> rows=new ArrayList<>();
        for(UUID id:SettlementService.workers(level).members(station.position(),level.getGameTime()))
            if(level.getEntity(id) instanceof CitizenEntity citizen) rows.add(person(town,citizen));
        if(rows.isEmpty()) rows.add(new Row(icon(Items.PAPER),"Nobody assigned",station.role().providesWork() ? "Idle citizens take open crew slots" : ""));
        return rows;
    }
    /** Totals of each item in these containers, largest first. */
    private static List<Row> contents(List<Container> containers) {
        if(containers.isEmpty()) return List.of(new Row(icon(Items.BARREL),"No storage","Put a barrel within 3 blocks of the station"));
        Map<Item,Integer> totals=new LinkedHashMap<>();
        Map<Item,ItemStack> examples=new HashMap<>();
        for(Container container:containers) for(int slot=0;slot<container.getContainerSize();slot++) {
            ItemStack stack=container.getItem(slot);
            if(stack.isEmpty()) continue;
            totals.merge(stack.getItem(),stack.getCount(),Integer::sum);
            examples.putIfAbsent(stack.getItem(),stack.copyWithCount(1));
        }
        List<Row> rows=new ArrayList<>(List.of(storage(icon(Items.BARREL),"Storage",containers,"")));
        totals.entrySet().stream().sorted(Map.Entry.<Item,Integer>comparingByValue().reversed()).limit(PanelView.MAX_ROWS-1)
                .forEach(entry -> rows.add(new Row(examples.get(entry.getKey()),examples.get(entry.getKey()).getHoverName(),
                        Component.literal("× "+entry.getValue()),0,PanelView.NO_BAR,PanelView.NO_VALUE)));
        return rows;
    }
    /** Detected resources and the job's state, one row each. */
    public static List<Row> status(ServerLevel level,Settlement town,Station station) {
        List<Row> rows=new ArrayList<>();
        switch(station.role()) {
            case HOUSING,BARRACKS -> rows.add(new Row(stationIcon(station.role()),"Beds",SettlementService.beds(level,town,station).size()+" complete beds in range house citizens"));
            case HOSPITAL -> rows.add(new Row(stationIcon(StructureRole.HOSPITAL),"Patient beds",SettlementService.beds(level,town,station).size()+" beds; treatment is planned"));
            case WAREHOUSE -> rows.add(storage(icon(Items.CHEST),"Storage",SettlementService.storageAt(level,town,station.position()),"Put chests or barrels within 3 blocks"));
            case FARM -> rows.add(new Row(icon(Items.WHEAT),"Crops",SettlementService.workBlocks(level,town,station)+" ripe crops in range"));
            case LUMBER -> rows.add(new Row(icon(Items.OAK_SAPLING),"Forest","Fells whole natural trees and replants saplings in range"));
            case MINE -> {
                BlockPos vein=OreVeins.find(level,town,station);
                if(vein!=null) {
                    var ore=level.getBlockState(vein);
                    long wait=Math.max(0,OreVeins.readyAt(level,vein)-level.getGameTime());
                    rows.add(new Row(new ItemStack(ore.getBlock()),ore.getBlock().getName(),Component.literal("Endless vein at "+vein.toShortString()
                            +(wait>0 ? ", replenishes in "+(wait+19)/20+"s" : ", ready")),0,PanelView.NO_BAR,PanelView.NO_VALUE));
                    rows.add(new Row(icon(Items.IRON_PICKAXE),"Yield","One harvest every "+OreVeins.interval(ore,Config.ORE_VEIN_SECONDS.get())/20+"s with a pickaxe that can mine it"));
                } else rows.add(new Row(icon(Items.STONE_PICKAXE),"Tunnels",ExcavationService.status(level,town,station)));
                if(vein==null) rows.add(new Row(icon(Items.RAW_IRON),"Ore vein","Place the station touching an ore to mine it forever instead"));
            }
            case QUARRY -> rows.add(new Row(icon(Items.IRON_PICKAXE),"Excavation",ExcavationService.status(level,town,station)));
            case GUARD -> rows.add(new Row(icon(Items.IRON_SWORD),"Posts",GuardService.status(level,station)));
            case SMELTERY -> rows.add(new Row(icon(Items.FURNACE),"Furnaces",SettlementService.processingDevices(level,town,station).size()+" furnaces or blast furnaces in range"));
            case COOK -> rows.add(new Row(icon(Items.SMOKER),"Kitchen",SettlementService.processingDevices(level,town,station).size()+" smokers or lit campfires; bread "+(town.disabledRecipes.contains("bread") ? "off" : "on")));
            case BLACKSMITH -> rows.add(new Row(icon(Items.ANVIL),"Anvils",SettlementService.anvils(level,town,station).size()+" anvils; repairs warehouse gear and worn stand armor"));
            case CRAFTSMAN -> rows.add(new Row(icon(Items.CRAFTING_TABLE),"Orders",town.craftOrders.size()+" learned recipes"));
            case COURIER -> rows.add(new Row(icon(Items.BUNDLE),"Deliveries","Carries goods from job barrels to the warehouse and stocks smelter and kitchen barrels"));
        }
        if(station.role().keepsJobStorage()) {
            List<Container> barrels=SettlementService.jobStorage(level,town,station);
            rows.add(storage(icon(Items.BARREL),"Job barrels",barrels,"None: a barrel within 3 blocks keeps tools, supplies and goods here"));
        }
        return rows;
    }
    public static void stationAction(ServerPlayer player,BlockPos pos,int action) {
        Station station=stationAt(player,pos);
        if(station==null) return;
        Settlement town=owned(player,pos);
        if(action==BREAD && station.role()==StructureRole.COOK) {
            if(!town.disabledRecipes.remove("bread")) town.disabledRecipes.add("bread");
            dirty(player);
        } else if(action==POSTS && station.role()==StructureRole.GUARD) {
            player.closeContainer();
            GuardService.begin(level(player),player,pos);
        }
    }

    // ---------- Craftsman ----------
    public static PanelView craftsman(ServerPlayer player,BlockPos pos,String feedback) {
        Station station=stationAt(player,pos);
        return station==null ? null : craftsman(level(player),owned(player,pos),station,feedback);
    }
    public static PanelView craftsman(ServerLevel level,Settlement town,Station station,String feedback) {
        List<Container> stock=SettlementService.townStorage(level,town);
        Workshop.Recipes recipes=Workshop.Recipes.of(level);
        List<Row> orders=new ArrayList<>();
        for(Workshop.Order order:town.craftOrders) {
            Item item=order.resolve();
            int have=Workshop.stock(stock,order);
            boolean materials=Workshop.plans(recipes,order).stream().anyMatch(plan -> Workshop.batches(recipes,town.craftOrders,order,plan,stock,1)>0);
            String note=item==Items.AIR ? "Unknown item" : order.target()==0 ? "Paused" : have>=order.target() ? "Stocked"
                    : materials ? "Ready to craft" : "Missing materials";
            int color=order.target()==0 ? GRAY : have>=order.target() ? GREEN : materials ? AMBER : RED;
            orders.add(new Row(new ItemStack(item),new ItemStack(item).getHoverName().copy().append(order.anyWood() ? " (any wood)" : ""),
                    Component.literal(have+" in town · "+note),color,order.target()==0 ? 0 : have/(float)order.target(),order.target()));
        }
        return new PanelView(Component.literal("Craftsman Station · "+crew(level,station)),
                Component.literal(feedback.isEmpty() ? "Click the slot with an item, or shift-click one, to teach its recipe" : feedback),
                List.of(new Tab("Orders",orders),new Tab("Crew",crewRows(level,town,station))),List.of());
    }
    /** How a craft order's row is named: its item's id, which the screen also reads from the row's icon. */
    public static String rowKey(Workshop.Order order) { return BuiltInRegistries.ITEM.getKey(order.resolve()).toString(); }
    public static String teach(ServerPlayer player,BlockPos pos,ItemStack example) {
        Station station=stationAt(player,pos);
        if(station==null || station.role()!=StructureRole.CRAFTSMAN) return "";
        Settlement town=owned(player,pos);
        int before=town.craftOrders.size();
        String result=Workshop.learn(Workshop.Recipes.of(level(player)),town,example);
        if(town.craftOrders.size()!=before) dirty(player);
        return result;
    }
    /** The row's item key must still name the order at that index; a click on a list that has changed is dropped. */
    public static void craftAction(ServerPlayer player,BlockPos pos,int action,int index,int value,String key) {
        Station station=stationAt(player,pos);
        if(station==null || station.role()!=StructureRole.CRAFTSMAN) return;
        Settlement town=owned(player,pos);
        List<Workshop.Order> orders=town.craftOrders;
        if(index<0 || index>=orders.size() || !key.equals(rowKey(orders.get(index)))) return;
        switch(action) {
            case TARGET -> orders.set(index,orders.get(index).withTarget(value));
            case RAISE -> { if(index>0) Collections.swap(orders,index,index-1); }
            case FORGET -> orders.remove(index);
            default -> { return; }
        }
        dirty(player);
    }

    // ---------- Citizens ----------
    public static PanelView citizen(CitizenEntity citizen) {
        ServerLevel level=(ServerLevel)citizen.level();
        Settlement town=citizen.town(level);
        Station job=town==null || citizen.workplace()==null ? null : town.station(citizen.workplace());
        List<Row> status=new ArrayList<>();
        status.add(new Row(job==null ? icon(Items.PAPER) : stationIcon(job.role()),job==null ? "No job" : job.role().title()+" at "+job.position().toShortString(),citizen.activity()));
        float health=citizen.getHealth()/Math.max(1,citizen.getMaxHealth());
        status.add(new Row(icon(Items.GOLDEN_APPLE),"Health",Math.round(citizen.getHealth())+" / "+Math.round(citizen.getMaxHealth())).bar(health,health<0.5F ? RED : GREEN));
        int meal=Math.max(0,citizen.mealTicks());
        status.add(new Row(icon(Items.BREAD),"Next meal",meal==0 ? "Hungry now" : "In about "+Math.max(1,meal/1200)+" min").bar(meal/(float)Math.max(1,Config.RATION_TICKS.get()),meal==0 ? RED : AMBER));
        if(citizen.overflowing()) status.add(new Row(icon(Items.CHEST),"Overflow","Carrying a harvest larger than the bag; it waits for delivery"));
        List<Row> gear=new ArrayList<>();
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            ItemStack worn=citizen.getItemBySlot(slot);
            if(!worn.isEmpty()) gear.add(new Row(worn.copy(),worn.getHoverName(),Component.literal(slot.getName()),0,
                    worn.isDamageableItem() ? 1F-worn.getDamageValue()/(float)worn.getMaxDamage() : PanelView.NO_BAR,PanelView.NO_VALUE));
        }
        return new PanelView(citizen.getName(),Component.literal(town==null ? "" : town.name),List.of(new Tab("Status",status),new Tab("Equipment",gear)),List.of());
    }
}

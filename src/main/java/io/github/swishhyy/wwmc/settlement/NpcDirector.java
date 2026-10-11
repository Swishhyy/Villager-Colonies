package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.VC;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One round-robin macro controller and shared construction budget for every generated NPC town. */
public final class NpcDirector {
    public static final int BUILD_BUDGET=64;
    private int decisionCursor,buildCursor;
    public record Situation(boolean alarm,int food,int population,boolean pending,boolean due,boolean funded,boolean room) {}
    public static NpcDevelopment.Phase decide(Situation s) {
        if(s.alarm()) return NpcDevelopment.Phase.RECOVER;
        if(s.pending()) return NpcDevelopment.Phase.BUILD;
        if(s.food()<Math.max(24,s.population()*3)) return NpcDevelopment.Phase.SUPPLY;
        return s.due() && s.funded() && s.room() ? NpcDevelopment.Phase.GROW : s.room() ? NpcDevelopment.Phase.CONNECT : NpcDevelopment.Phase.STABLE;
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        SharedRoads.tick(level);
        if(!Config.NPC_GROWTH.get()) return;
        var towns=SettlementData.get(level).settlements.stream().filter(t -> t.trading.npc && t.trading.buildIndex<0 && t.trading.development.enabled).toList();
        if(towns.isEmpty()) return;
        var pending=towns.stream().filter(t -> t.trading.development.construction!=null).toList();
        if(!pending.isEmpty()) advance(level,pending.get(Math.floorMod(buildCursor++,pending.size())));
        if(level.getGameTime()%20==0) evaluate(level,towns.get(Math.floorMod(decisionCursor++,towns.size())));
    }
    private static long interval() { return Config.NPC_GROWTH_DAYS.get()*24000L; }
    public static void evaluate(ServerLevel level,Settlement town) {
        var d=town.trading.development; long now=level.getGameTime();
        repairDetails(level,town);
        if(d.nextProject==0) d.nextProject=now+interval();
        if(d.nextImmigrant==0) d.nextImmigrant=now+interval();
        SupplyRequests.snapshotLoaded(level,town);
        int food=town.campaign.stock.entrySet().stream().filter(e -> FoodHealing.food(new net.minecraft.world.item.ItemStack(SupplyRequests.item(e.getKey())))).mapToInt(Map.Entry::getValue).sum();
        List<Container> stock=loadedStock(level,town);
        int logs=InventoryOps.count(stock,s -> s.is(net.minecraft.tags.ItemTags.LOGS));
        int stone=InventoryOps.count(stock,s -> s.is(Items.COBBLESTONE));
        d.phase=decide(new Situation(DefenseService.alarmed(town),food,town.citizens.size(),d.construction!=null,now>=d.nextProject,
                logs>=32 && stone>=16,d.completed<Config.NPC_BUILDINGS.get()));
        priority(town,StructureRole.COURIER,JobBoard.HIGH);
        priority(town,StructureRole.FARM,d.phase==NpcDevelopment.Phase.SUPPLY ? JobBoard.HIGH : JobBoard.NORMAL);
        priority(town,StructureRole.COOK,d.phase==NpcDevelopment.Phase.SUPPLY ? JobBoard.HIGH : JobBoard.NORMAL);
        if(d.phase==NpcDevelopment.Phase.RECOVER || d.phase==NpcDevelopment.Phase.BUILD) { SettlementData.get(level).setDirty(); return; }
        if(d.phase==NpcDevelopment.Phase.GROW && NpcConstruction.away(level,town.center)) {
            String kind=d.completed%4==0 || d.completed%4==3 ? "cottage" : d.completed%4==1 ? "workshop" : "inn";
            var work=NpcConstruction.plan(level,town,kind);
            if(work!=null) {
                for(int n=0;n<32;n++) InventoryOps.takeOne(stock,s -> s.is(net.minecraft.tags.ItemTags.LOGS));
                for(int n=0;n<16;n++) InventoryOps.takeOne(stock,s -> s.is(Items.COBBLESTONE));
                d.construction=work; d.phase=NpcDevelopment.Phase.BUILD;
                CampaignService.journal(level,town,"Our community is building a new "+kind+".");
            }
        }
        if(town.progress.growthEnabled && now>=d.nextImmigrant && food>=Math.max(24,(town.citizens.size()+1)*3)+4 && !stock.isEmpty()
                && NpcConstruction.away(level,town.center) && d.construction==null) {
            // Immigration is a paid, real spawn; never invent citizens in unloaded terrain.
            d.nextImmigrant=now+interval();
            if(SettlementService.recruit(level,town,1)>0) {
                for(int n=0;n<4;n++) FoodHealing.take(stock,remainder -> {
                    for(var box:stock) { remainder=InventoryOps.insert(box,remainder); if(remainder.isEmpty()) break; }
                });
                NpcSettlements.assignCrew(town);
            }
        }
        NeighbourTrade.update(level,town);
        SettlementData.get(level).setDirty();
    }
    private static void priority(Settlement town,StructureRole role,int priority) { if(town.jobs.level(role)!=priority) town.jobs.setLevel(role,priority); }
    private static void repairDetails(ServerLevel level,Settlement town) {
        var old=town.trading.layout;
        if(old==null || old.design()>=2 || town.trading.buildIndex>=0 || !NpcSettlements.loaded(level,town.center) || !NpcConstruction.away(level,town.center)) return;
        var original=new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(var p:NpcVillagePlan.blueprint(town)) original.put(p.pos(),p.state());
        town.trading.layout=new NpcVillageLayout(old.palette(),old.ground(),old.surface(),2);
        var protection=WorldWorkData.get(level);
        for(var p:NpcVillagePlan.blueprint(town)) {
            var before=original.get(p.pos());
            if(p.state().equals(before)) continue;
            var current=level.getBlockState(p.pos());
            if(before!=null ? !current.equals(before) : !current.isAir() || protection.protectedBlocks.contains(p.pos())) continue;
            if(level.getBlockEntity(p.pos())!=null || !level.getFluidState(p.pos()).isEmpty()) continue;
            level.setBlock(p.pos(),p.state(),2); if(p.protect()) protection.protect(p.pos());
        }
        SettlementData.get(level).setDirty();
    }
    private static List<Container> loadedStock(ServerLevel level,Settlement town) {
        return town.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE).allMatch(s -> level.hasChunkAt(s.position()))
                ? SettlementService.storage(level,town) : List.of();
    }
    public static void advance(ServerLevel level,Settlement town) {
        var d=town.trading.development; var work=d.construction; if(work==null) return;
        int done=work.build(level,town,BUILD_BUDGET);
        d.phase=done>0 ? NpcDevelopment.Phase.BUILD : NpcDevelopment.Phase.PAUSED;
        if(!work.complete()) return;
        if(town.station(work.station.position())==null) town.stations.add(work.station);
        if(work.kind.equals("cottage") && town.citizens.size()>=SettlementService.populationLimit(town)-2 && SettlementService.canGrow(town))
            town.populationLevel=SettlementService.populationLevel(town)+1;
        d.completed++; d.construction=null; d.phase=NpcDevelopment.Phase.CONNECT; d.nextProject=level.getGameTime()+interval();
        NpcSettlements.assignCrew(town); SupplyRequests.snapshotLoaded(level,town);
        CampaignService.journal(level,town,"Our new "+work.kind+" is ready.");
        VC.LOGGER.info("[VC][npc-growth] town={} building={} additions={}",town.id,work.kind,d.completed);
        SettlementData.get(level).setDirty();
    }
}

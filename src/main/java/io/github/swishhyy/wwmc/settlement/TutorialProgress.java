package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.core.StructureRole;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Native advancement progress comes from functioning town setups and completed jobs, never a client request. */
public final class TutorialProgress {
    public static void award(ServerPlayer player,String id) {
        var holder=((ServerLevel)player.level()).getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(WWMC.MODID,"tutorial/"+id));
        if(holder!=null && !player.getAdvancements().getOrStartProgress(holder).isDone()) player.getAdvancements().award(holder,"done");
    }
    public static void record(ServerLevel level,Settlement town,String id) {
        if(town!=null && !town.trading.npc && town.progress.milestones.add(id)) SettlementData.get(level).setDirty();
    }
    public static void completed(ServerLevel level,Settlement town,StructureRole role) {
        String id=switch(role) {
            case FARM -> "harvest"; case LUMBER -> "timber"; case MINE,QUARRY -> "mining";
            case COOK -> "meals"; case SMELTERY -> "smelting"; case CRAFTSMAN -> "crafting";
            case BLACKSMITH -> "repair"; case ENCHANTER -> "enchanting"; case BUTCHER -> "butchering";
            default -> "";
        };
        if(!id.isEmpty()) record(level,town,id);
    }
    public static Set<String> milestones(ServerLevel level,Settlement town) {
        Set<String> done=new LinkedHashSet<>(town.progress.milestones); done.add("found_town");
        if(!SettlementService.housingBeds(level,town).isEmpty()) done.add("housing");
        if(!SettlementService.storage(level,town).isEmpty()) done.add("warehouse");
        if(!town.citizens.isEmpty()) done.add("recruit");
        if(town.citizens.stream().anyMatch(id -> town.station(town.jobs.home(id))!=null)) done.add("first_job");
        if(town.stations.stream().anyMatch(s -> s.role()==StructureRole.COURIER && SettlementService.active(level,s) && town.jobs.assigned(s.position())>0)) done.add("courier");
        if(!town.campaign.projects.isEmpty()) done.add("project");
        if(!town.progress.research.isEmpty()) done.add("research");
        if(!town.campaign.members.isEmpty()) done.add("friends");
        if(town.trading.delivered>0) done.add("shipment");
        if(!town.progress.schematics.isEmpty()) done.add("schematic");
        return done;
    }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) award(player,"root");
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%100!=0) return;
        for(ServerPlayer player:level.players()) award(player,"root");
        for(Settlement town:SettlementData.get(level).settlements) {
            if(town.trading.npc) continue;
            var managers=level.players().stream().filter(p -> TownAccess.manages(town,p.getUUID())).toList();
            if(managers.isEmpty()) continue;
            Set<String> done=milestones(level,town);
            for(ServerPlayer player:managers) for(String id:done) award(player,id);
        }
    }
}

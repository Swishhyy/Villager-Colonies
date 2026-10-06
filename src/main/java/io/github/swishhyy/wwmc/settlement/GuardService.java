package io.github.swishhyy.wwmc.settlement;

import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.GuardDuty;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class GuardService {
    public static boolean onDuty(ServerLevel level,Settlement town,BlockPos station,UUID guard) {
        return GuardDuty.active(SettlementService.workers(level).members(station,level.getGameTime()),guard,
                SettlementService.night(level),DefenseService.alarmed(town));
    }
    public static Station uncovered(ServerLevel level,Settlement town) {
        return town.stations.stream().filter(s -> s.role()==StructureRole.GUARD && SettlementService.active(level,s)
                && SettlementService.workers(level).count(s.position(),level.getGameTime())==0).findFirst().orElse(null);
    }
    private record Selection(BlockPos station,BlockPos day,long until) {}
    private static final Map<ServerLevel,Map<UUID,Selection>> SELECTING=new WeakHashMap<>();
    public static boolean walkable(ServerLevel level,Settlement town,BlockPos pos) {
        if(!town.contains(pos) || pos.getY()<=level.getMinY() || pos.getY()+1>=level.getMaxY()
                || !level.hasChunkAt(pos)) return false;
        return level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    public static GuardPosts posts(ServerLevel level,Station station) {
        return WorldWorkData.get(level).guardPosts.getOrDefault(station.position(),
                new GuardPosts(station.position(),station.position(),station.position()));
    }
    public static String status(ServerLevel level,Station station) {
        GuardPosts p=posts(level,station);
        return "day post "+p.day().toShortString()+", night post "+p.night().toShortString()
                +"; one guard on duty per staffed station, full crew during alarms. Sneak-use the Inspector to set both posts. Armor stands in the station's 7x7x7 range supply armor, and weapons held in their hands";
    }
    public static void begin(ServerLevel level,Player player,BlockPos station) {
        Settlement town=SettlementData.get(level).at(station);
        Station s=town==null ? null : town.station(station);
        if(!SettlementService.owns(player,town) || s==null || s.role()!=StructureRole.GUARD) {
            SettlementService.tell(player,"Choose a Guard Station in your own town."); return;
        }
        SELECTING.computeIfAbsent(level,l -> new HashMap<>()).put(player.getUUID(),new Selection(station.immutable(),null,level.getGameTime()+12000));
        SettlementService.tell(player,"Right-click the ground for the daytime post, then the nighttime post. Both must be walkable inside your town.");
    }
    public static boolean select(ServerLevel level,Player player,BlockPos post) {
        Map<UUID,Selection> choices=SELECTING.get(level);
        Selection choice=choices==null ? null : choices.get(player.getUUID());
        if(choice==null) return false;
        if(player.isShiftKeyDown() || choice.until()<=level.getGameTime()) {
            choices.remove(player.getUUID()); SettlementService.tell(player,"Guard post selection canceled."); return true;
        }
        Settlement town=SettlementData.get(level).at(choice.station());
        Station station=town==null ? null : town.station(choice.station());
        if(!SettlementService.owns(player,town) || station==null || station.role()!=StructureRole.GUARD || !SettlementService.active(level,station)) {
            choices.remove(player.getUUID()); SettlementService.tell(player,"That Guard Station is no longer available."); return true;
        }
        if(!walkable(level,town,post)) { SettlementService.tell(player,"Choose clear, dry ground with headroom inside your town."); return true; }
        if(choice.day()==null) {
            choices.put(player.getUUID(),new Selection(choice.station(),post.immutable(),level.getGameTime()+12000));
            SettlementService.tell(player,"Day post selected at "+post.toShortString()+". Now right-click the nighttime post.");
        } else if(!walkable(level,town,choice.day())) {
            choices.remove(player.getUUID()); SettlementService.tell(player,"The daytime post was obstructed. Select both posts again.");
        } else {
            WorldWorkData data=WorldWorkData.get(level);
            data.guardPosts.put(choice.station(),new GuardPosts(choice.station(),choice.day(),post)); data.setDirty();
            choices.remove(player.getUUID()); SettlementService.tell(player,"Guard posts saved. The crew will switch posts with the day/night cycle.");
        }
        return true;
    }
    public static List<ArmorStand> stands(ServerLevel level,Settlement town,Station station) {
        var area=station.area();
        return level.getEntitiesOfClass(ArmorStand.class,new AABB(area.minX(),area.minY(),area.minZ(),area.maxX()+1,area.maxY()+1,area.maxZ()+1),
                stand -> stand.isAlive() && !stand.isInvisible() && town.contains(stand.blockPosition())
                        && station.contains(stand.blockPosition()) && SettlementService.ownsBlock(level,town,station,stand.blockPosition()));
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        Map<UUID,Selection> choices=SELECTING.get(level);
        if(choices!=null) { choices.values().removeIf(s -> s.until()<=level.getGameTime()); if(choices.isEmpty()) SELECTING.remove(level); }
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { SELECTING.keySet().removeIf(l -> l.getServer()==event.getServer()); }
}

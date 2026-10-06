package io.github.swishhyy.wwmc.settlement;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.swishhyy.wwmc.Config;
import io.github.swishhyy.wwmc.WWMC;
import io.github.swishhyy.wwmc.block.StationBlock;
import io.github.swishhyy.wwmc.core.ReservationBook;
import io.github.swishhyy.wwmc.core.RoomBounds;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.entity.CitizenEntity;
import io.github.swishhyy.wwmc.item.SurveyorItem;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class SettlementService {
    private static final Map<ServerLevel,ReservationBook<BlockPos>> RESERVATIONS=new WeakHashMap<>();
    public static ReservationBook<BlockPos> reservations(ServerLevel level) {
        return RESERVATIONS.computeIfAbsent(level, l -> new ReservationBook<>());
    }
    private static void tell(Player player,String text) { player.displayClientMessage(Component.literal(text),false); }
    public static boolean owns(Player player,Settlement settlement) { return settlement!=null && settlement.owner.equals(player.getUUID()); }

    public static void foundOrInspect(ServerLevel level,Player player,BlockPos pos) {
        if(!level.dimension().equals(Level.OVERWORLD)) { tell(player,"Settlements currently belong in the Overworld."); return; }
        SettlementData data=SettlementData.get(level);
        Settlement present=data.at(pos);
        if(present!=null) { tell(player,status(level,present)); return; }
        int radius=Config.SETTLEMENT_RADIUS.get();
        if(data.settlements.stream().anyMatch(s -> s.overlaps(pos,radius))) { tell(player,"Move your banner farther away: settlement claims cannot overlap."); return; }
        if(data.settlements.stream().anyMatch(s -> s.owner.equals(player.getUUID()))) { tell(player,"You already own a settlement. Multiple towns are planned for a later build."); return; }
        Settlement settlement=new Settlement(UUID.randomUUID(),player.getUUID(),player.getName().getString()+"'s settlement",pos,radius,List.of(),List.of(),"balanced");
        data.settlements.add(settlement); data.setDirty();
        tell(player,"Founded "+settlement.name+". Place housing, warehouse, and work stations inside the "+radius+"-block claim.");
    }
    public static void registerStation(ServerLevel level,Player player,BlockPos pos,StructureRole role) {
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(pos);
        if(!owns(player,settlement)) { tell(player,"Place stations inside your own settlement claim."); return; }
        if(settlement.station(pos)==null) {
            if(settlement.stations.stream().anyMatch(s -> s.room().isPresent() && s.room().get().contains(pos.getX(),pos.getY(),pos.getZ()))) {
                tell(player,"This position already belongs to a registered room. Give this station a separate area."); return;
            }
            settlement.stations.add(new Station(pos,role)); data.setDirty();
            tell(player,"Registered "+role.id()+" station. Use the surveyor to define its room or storage area.");
        }
    }
    public static boolean active(ServerLevel level,Station station) {
        return level.hasChunkAt(station.position()) && level.getBlockState(station.position()).getBlock() instanceof StationBlock block && block.role()==station.role();
    }
    public static boolean bindRoom(ServerLevel level,Player player,BlockPos pos,RoomBounds room) {
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(pos);
        if(!owns(player,settlement)) { tell(player,"This station must be in your settlement."); return false; }
        if(!(level.getBlockState(pos).getBlock() instanceof StationBlock block)) return false;
        if(settlement.station(pos)==null) registerStation(level,player,pos,block.role());
        Station station=settlement.station(pos);
        if(station==null) return false;
        if(!room.withinLimit(4096)) { tell(player,"Select an area of at most 4096 blocks."); return false; }
        if(!room.contains(pos.getX(),pos.getY(),pos.getZ())) { tell(player,"The role station must be inside the selected room."); return false; }
        if(!settlement.contains(new BlockPos(room.minX(),pos.getY(),room.minZ())) || !settlement.contains(new BlockPos(room.maxX(),pos.getY(),room.maxZ()))) {
            tell(player,"The entire room must be inside your settlement claim."); return false;
        }
        if(settlement.stations.stream().anyMatch(s -> !s.position().equals(pos) && s.room().isPresent() && s.room().get().intersects(room))) {
            tell(player,"This overlaps another registered room. Select separate volumes so beds have one purpose."); return false;
        }
        for(BlockPos cell:BlockPos.betweenClosed(room.minX(),room.minY(),room.minZ(),room.maxX(),room.maxY(),room.maxZ())) {
            if(cell.getY()<level.getMinY() || cell.getY()>=level.getMaxY() || !level.hasChunkAt(cell)) {
                tell(player,"The room must fit inside the world and all its chunks must be loaded."); return false;
            }
            if(!cell.equals(pos) && level.getBlockState(cell).getBlock() instanceof StationBlock) {
                tell(player,"Keep one role station per selected room."); return false;
            }
        }
        settlement.stations.remove(station);
        settlement.stations.add(new Station(pos,station.role(),Optional.of(room))); data.setDirty();
        inspectStation(level,player,pos); return true;
    }
    public static List<BlockPos> beds(ServerLevel level,Station station) {
        List<BlockPos> result=new ArrayList<>();
        if(!active(level,station) || station.room().isEmpty()) return result;
        RoomBounds room=station.room().get();
        if(!room.withinLimit(4096)) return result;
        for(BlockPos pos:BlockPos.betweenClosed(room.minX(),room.minY(),room.minZ(),room.maxX(),room.maxY(),room.maxZ())) {
            if(!level.hasChunkAt(pos)) continue;
            var state=level.getBlockState(pos);
            if(state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART)==BedPart.HEAD) result.add(pos.immutable());
        }
        return result;
    }
    public static List<BlockPos> housingBeds(ServerLevel level,Settlement settlement) {
        List<BlockPos> result=new ArrayList<>();
        for(Station station:settlement.stations) if(station.role().providesHousing()) result.addAll(beds(level,station));
        return result;
    }
    public static void inspectStation(ServerLevel level,Player player,BlockPos pos) {
        Settlement settlement=SettlementData.get(level).at(pos);
        if(settlement==null || settlement.station(pos)==null) return;
        Station station=settlement.station(pos);
        int beds=beds(level,station).size();
        String purpose=station.role()==StructureRole.HOSPITAL ? "patient beds" : station.role().providesHousing() ? "housing beds" : "beds";
        tell(player,station.role().id()+" station: "+(station.room().isPresent() ? beds+" "+purpose : "no selected room")+". "+
                (station.role()==StructureRole.HOSPITAL ? "Medical treatment is planned; these beds do not recruit citizens." : ""));
    }
    public static List<Container> storage(ServerLevel level,Settlement settlement) {
        return storageAt(level,settlement,null);
    }
    public static List<Container> storageAt(ServerLevel level,Settlement settlement,BlockPos selectedWarehouse) {
        Set<BlockPos> positions=new HashSet<>();
        for(Station station:settlement.stations) {
            if(station.role()!=StructureRole.WAREHOUSE || !active(level,station)) continue;
            if(selectedWarehouse!=null && !station.position().equals(selectedWarehouse)) continue;
            if(station.room().isPresent() && station.room().get().withinLimit(4096)) {
                RoomBounds r=station.room().get();
                for(BlockPos pos:BlockPos.betweenClosed(r.minX(),r.minY(),r.minZ(),r.maxX(),r.maxY(),r.maxZ())) positions.add(pos.immutable());
            } else {
                for(Direction direction:Direction.values()) positions.add(station.position().relative(direction));
            }
        }
        List<Container> containers=new ArrayList<>();
        for(BlockPos pos:positions) if(settlement.contains(pos) && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Container container) containers.add(container);
        return containers;
    }
    public static BlockPos warehouse(ServerLevel level,Settlement settlement) {
        return settlement.stations.stream().filter(s -> s.role()==StructureRole.WAREHOUSE && active(level,s)).map(Station::position).findFirst().orElse(null);
    }
    public static boolean protectedFurniture(Settlement settlement,BlockPos pos) {
        return settlement.stations.stream().anyMatch(s -> s.position().equals(pos) ||
                (!s.role().providesWork() && s.room().isPresent() && s.room().get().contains(pos.getX(),pos.getY(),pos.getZ())));
    }
    public static String status(ServerLevel level,Settlement settlement) {
        return settlement.name+": "+settlement.citizens.size()+" citizens / "+housingBeds(level,settlement).size()+
                " loaded housing beds, "+settlement.stations.size()+" stations, priority: "+settlement.priority+". Claim radius: "+settlement.radius+".";
    }
    private static Settlement owned(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        return SettlementData.get(source.getLevel()).settlements.stream().filter(s -> s.owner.equals(player.getUUID())).findFirst().orElse(null);
    }
    private static int recruit(CommandSourceStack source,int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Settlement settlement=owned(source);
        if(settlement==null) { source.sendFailure(Component.literal("Right-click a settlement banner to found a town first.")); return 0; }
        ServerLevel level=source.getLevel();
        List<BlockPos> beds=housingBeds(level,settlement);
        int limit=Math.min(beds.size(),Config.MAX_CITIZENS.get());
        int added=0;
        for(int i=0;i<count && settlement.citizens.size()<limit;i++) {
            CitizenEntity citizen=WWMC.CITIZEN.get().create(level,EntitySpawnReason.COMMAND);
            if(citizen==null) break;
            BlockPos spawn=null;
            for(int x=-3;x<=3 && spawn==null;x++) for(int z=-3;z<=3;z++) {
                BlockPos trial=settlement.center.offset(x,0,z);
                if(!level.hasChunkAt(trial)) continue;
                citizen.setPos(trial.getX()+0.5,trial.getY(),trial.getZ()+0.5);
                if(level.noCollision(citizen) && !level.getBlockState(trial.below()).isAir()) { spawn=trial; break; }
            }
            if(spawn==null) break;
            citizen.join(settlement.id);
            citizen.setCustomName(Component.literal("Citizen "+(settlement.citizens.size()+1)));
            if(level.addFreshEntity(citizen)) { settlement.citizens.add(citizen.getUUID()); added++; }
        }
        SettlementData.get(level).setDirty();
        final int result=added;
        source.sendSuccess(() -> Component.literal("Recruited "+result+" citizens. "+status(level,settlement)+" Supply food and tools in the warehouse."),false);
        return added;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwmc")
            .then(Commands.literal("status").executes(c -> {
                Settlement s=owned(c.getSource());
                if(s==null) { c.getSource().sendFailure(Component.literal("You do not own a settlement here.")); return 0; }
                c.getSource().sendSuccess(() -> Component.literal(status(c.getSource().getLevel(),s)),false); return 1;
            }))
            .then(Commands.literal("recruit").executes(c -> recruit(c.getSource(),1))
                .then(Commands.argument("count",IntegerArgumentType.integer(1,8)).executes(c -> recruit(c.getSource(),IntegerArgumentType.getInteger(c,"count")))))
            .then(Commands.literal("name").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c -> {
                Settlement s=owned(c.getSource()); if(s==null) return 0;
                String name=StringArgumentType.getString(c,"name").strip();
                if(name.isEmpty() || name.length()>48) { c.getSource().sendFailure(Component.literal("Use a town name of 1 to 48 characters.")); return 0; }
                s.name=name; SettlementData.get(c.getSource().getLevel()).setDirty(); return 1;
            })))
            .then(Commands.literal("priority").then(Commands.argument("priority",StringArgumentType.word())
                .suggests((c,b) -> { for(String p:List.of("balanced","food","materials")) b.suggest(p); return b.buildFuture(); })
                .executes(c -> {
                    Settlement s=owned(c.getSource()); if(s==null) return 0;
                    String p=StringArgumentType.getString(c,"priority");
                    if(!List.of("balanced","food","materials").contains(p)) { c.getSource().sendFailure(Component.literal("Choose balanced, food, or materials.")); return 0; }
                    s.priority=p; SettlementData.get(c.getSource().getLevel()).setDirty();
                    c.getSource().sendSuccess(() -> Component.literal("Town priority set to "+p+". Idle workers will prefer those stations."),false); return 1;
                }))));
    }
    @SubscribeEvent public void breakStation(BlockEvent.BreakEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)) return;
        SettlementData data=SettlementData.get(level);
        Settlement settlement=data.at(event.getPos());
        if(settlement==null) return;
        boolean banner=settlement.center.equals(event.getPos()) && event.getState().is(WWMC.BANNER.get());
        boolean station=event.getState().getBlock() instanceof StationBlock;
        if(!banner && !station) return;
        if(!owns(event.getPlayer(),settlement)) { event.setCanceled(true); tell(event.getPlayer(),"Only this town's owner can remove its stations."); return; }
        if(banner && !settlement.citizens.isEmpty()) {
            event.setCanceled(true); tell(event.getPlayer(),"This banner belongs to an occupied settlement. Keep it as your town's rally point."); return;
        }
        if(banner) data.settlements.remove(settlement);
        else settlement.stations.removeIf(s -> s.position().equals(event.getPos()));
        data.setDirty();
    }
    @SubscribeEvent public void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level) || level.getGameTime()%200!=0) return;
        reservations(level).prune(level.getGameTime());
        SettlementData data=SettlementData.get(level);
        for(Settlement s:data.settlements) {
            if(s.stations.removeIf(station -> level.hasChunkAt(station.position()) && !active(level,station))) data.setDirty();
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity().level() instanceof ServerLevel level) SurveyorItem.clear(level.getServer(),event.getEntity().getUUID());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        RESERVATIONS.keySet().removeIf(level -> level.getServer()==event.getServer());
        SurveyorItem.clear(event.getServer());
    }
}
